package com.etendoerp.print.provider.strategy.impl;

import java.io.File;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.datamodel.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.etendoerp.print.provider.utils.PrintProviderUtils;
import com.etendoerp.print.provider.api.PrintProviderException;
import com.etendoerp.print.provider.api.PrinterDTO;
import com.etendoerp.print.provider.data.Printer;
import com.etendoerp.print.provider.data.Provider;
import com.etendoerp.print.provider.data.ProviderParam;
import com.etendoerp.print.provider.data.TemplateLine;
import com.etendoerp.print.provider.strategy.PrintProviderStrategy;
import com.etendoerp.print.provider.utils.PrinterUtils;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;


/**
 * PrintNode provider implementation for fetching printers and managing print jobs.
 * This class communicates with PrintNode's API to retrieve available printers
 * and send print jobs. The label generation and job sending are left for future
 * implementation.
 */
public class PrintNodeProvider extends PrintProviderStrategy {

  private static final Logger log = LoggerFactory.getLogger(PrintNodeProvider.class);

  // Timeout configuration
  private static final int CONNECT_TIMEOUT_SECONDS = 10;
  private static final int REQUEST_TIMEOUT_SECONDS = 20;

  private static final String FILE_PREFIX = "etendo-label-";
  private static final String FILE_EXTENSION = ".pdf";
  private static final String ETENDO_ERP = "Etendo ERP";

  /**
   * Fetches the list of available printers from the configured PrintNode provider.
   *
   * <p>This method is a no-op by default, returning an empty list. It is expected
   * that subclasses override this method to implement the printing provider's
   * logic for fetching the available printers.</p>
   *
   * @param provider
   *     the provider configuration containing API endpoint and key.
   * @return a list of {@link PrinterDTO} representing available printers.
   * @throws PrintProviderException
   *     if provider configuration is invalid,
   *     connection fails, or response cannot be parsed.
   */
  @Override
  public List<PrinterDTO> fetchPrinters(final Provider provider) throws PrintProviderException {
    try {
      if (provider == null) {
        throw new PrintProviderException(OBMessageUtils.messageBD("ETPP_ProviderNotFound"));
      }

      final ProviderParam printersUrl = PrinterUtils.getRequiredParam(provider, PrinterUtils.PRINTERS_URL);
      final ProviderParam apiKey = PrinterUtils.getRequiredParam(provider, PrinterUtils.API_KEY);

      PrinterUtils.providerParamContentCheck(printersUrl, PrinterUtils.PRINTERS_URL);
      PrinterUtils.providerParamContentCheck(apiKey, PrinterUtils.API_KEY);

      final String basicAuth = PrintProviderUtils.buildBasicAuth(apiKey.getParamContent());
      final HttpClient client = PrintProviderUtils.newHttpClient(CONNECT_TIMEOUT_SECONDS);

      final HttpRequest req = PrintProviderUtils.buildJsonGet(
          printersUrl.getParamContent(), basicAuth, REQUEST_TIMEOUT_SECONDS);

      final HttpResponse<String> resp = PrintProviderUtils.send(client, req, "ETPP_InterruptedSendException");

      if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
        throw new OBException(String.format(OBMessageUtils.getI18NMessage("ETPP_FetchJobError"), resp.statusCode(),
            truncate(resp.body(), 500)));
      }
      return parsePrinters(resp.body());

    } catch (PrintProviderException e) {
      throw e;

    } catch (Exception e) {
      throw new PrintProviderException(e.getMessage(), e);
    }
  }

  /**
   * Generates a label for the given record based on the template location.
   *
   * <p>This method takes the {@code templateRef} as a parameter and resolves it
   * to an absolute path. It then compiles it (if it's a .jrxml file) or loads it
   * (if it's a .jasper file) and then fills it with the given {@code recordId}
   * and {@code parameters}.</p>
   *
   * <p>The generated PDF is saved to a temporary file and that file is
   * returned.</p>
   *
   * @param provider
   *     the provider configuration containing API endpoint and key.
   * @param table
   *     Target table
   * @param recordId
   *     Target record ID
   * @param templateRef
   *     TemplateLine instance
   * @param parameters
   *     Extra parameters for the report
   * @return Generated PDF file
   * @throws PrintProviderException
   *     If there is an error generating the label
   */
  @Override
  public File generateLabel(final Provider provider,
      final Table table,
      final String recordId,
      final TemplateLine templateRef,
      final JSONObject parameters) throws PrintProviderException {
    try {
      final File jrFile = PrinterUtils.resolveTemplateFile(templateRef);
      final String absPath = jrFile.getAbsolutePath();

      final JasperReport jasperReport = PrinterUtils.loadOrCompileJasperReport(absPath, jrFile);

      final Map<String, Object> jrParams = new HashMap<>();
      jrParams.put("DOCUMENT_ID", recordId);

      final String subdir = jrFile.getParentFile() == null
          ? ""
          : jrFile.getParentFile().getAbsolutePath() + File.separator;
      jrParams.put("SUBREPORT_DIR", subdir);

      final Path tmp = Files.createTempFile(FILE_PREFIX, FILE_EXTENSION);
      final JasperPrint print =
          OBDal.getReadOnlyInstance()
              .getSession()
              .doReturningWork(conn -> {
                try {
                  return JasperFillManager.fillReport(jasperReport, jrParams, conn);
                } catch (JRException e) {
                  throw new SQLException(e.getMessage(), e);
                }
              });

      JasperExportManager.exportReportToPdfFile(print, tmp.toString());
      return tmp.toFile();

    } catch (Exception e) {
      throw new PrintProviderException(e.getMessage(), e);
    }
  }

  /**
   * Sends the given {@code labelFile} to the printer specified by
   * {@code printer} using the configuration in {@code provider}.
   *
   * <p>This method is a no-op by default, throwing an
   * {@link UnsupportedOperationException}. It is expected that subclasses
   * override this method to implement the printing provider's logic for
   * sending print jobs.</p>
   *
   * @param provider
   *     the provider configuration containing API endpoint and key.
   * @param printer
   *     the target printer
   * @param numberOfCopies
   *     the number of copies to print
   * @param labelFile
   *     the generated PDF file to send to the printer
   * @return the print job ID returned by the provider
   * @throws PrintProviderException
   *     If there is an error sending the print job
   */
  @Override
  public String sendToPrinter(final Provider provider,
      final Printer printer,
      final int numberOfCopies,
      final File labelFile) throws PrintProviderException {
    try {
      validatePrintJobInputs(provider, printer, labelFile);

      final ProviderParam printJobUrl = PrinterUtils.getRequiredParam(provider, PrinterUtils.PRINTJOB_URL);
      final ProviderParam apiKey = PrinterUtils.getRequiredParam(provider, PrinterUtils.API_KEY);

      PrinterUtils.providerParamContentCheck(printJobUrl, PrinterUtils.PRINTJOB_URL);
      PrinterUtils.providerParamContentCheck(apiKey, PrinterUtils.API_KEY);

      final int printerId = parseExternalPrinterId(printer.getValue());
      final String base64 = PrintProviderUtils.encodeFileToBase64(labelFile);

      final JSONObject body = buildPrintJobBody(printerId, numberOfCopies, base64);
      final String basicAuth = PrintProviderUtils.buildBasicAuth(apiKey.getParamContent());
      final HttpClient client = PrintProviderUtils.newHttpClient(CONNECT_TIMEOUT_SECONDS);

      final HttpRequest req = PrintProviderUtils.buildJsonPost(
          printJobUrl.getParamContent(), basicAuth, REQUEST_TIMEOUT_SECONDS, body.toString());

      final HttpResponse<String> resp = PrintProviderUtils.send(client, req, "ETPP_InterruptedSendException");

      if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
        throw new OBException(String.format(
            OBMessageUtils.getI18NMessage("ETPP_PrintJobError"), resp.statusCode(),
            PrintProviderUtils.truncate(resp.body(), 500)));
      }

      return PrintProviderUtils.extractJobIdOrPreview(resp.body(), log, 200);

    } catch (PrintProviderException e) {
      throw e;

    } catch (Exception e) {
      throw new PrintProviderException(e.getMessage(), e);
    }
  }

  /**
   * Validates the inputs for printing a label via PrintNode.
   *
   * @param provider
   *     the provider configuration containing API endpoint and key.
   * @param printer
   *     the target printer
   * @param labelFile
   *     the generated PDF file to send to the printer
   * @throws PrintProviderException
   *     If there is an error in the inputs (e.g. the provider or printer is not
   *     found, or the file does not exist)
   */
  public static void validatePrintJobInputs(Provider provider, Printer printer, File labelFile)
      throws PrintProviderException {
    if (provider == null) {
      throw new PrintProviderException(OBMessageUtils.messageBD("ETPP_ProviderNotFound"));
    }
    if (printer == null) {
      throw new PrintProviderException(OBMessageUtils.messageBD("ETPP_PrinterNotFound"));
    }
    if (labelFile == null || !labelFile.exists() || !labelFile.isFile()) {
      throw new PrintProviderException(OBMessageUtils.messageBD("ETPP_FileNotFound"));
    }
  }

  /**
   * Parses the given external ID as an integer, rethrowing any
   * {@link NumberFormatException} as a {@link PrintProviderException}.
   *
   * @param externalId
   *     the external ID to parse
   * @return the parsed integer
   * @throws PrintProviderException
   *     if the external ID could not be parsed as an integer
   */
  public static int parseExternalPrinterId(String externalId) throws PrintProviderException {
    try {
      return Integer.parseInt(externalId);
    } catch (NumberFormatException nfe) {
      throw new PrintProviderException(nfe.getMessage(), nfe);
    }
  }

  /**
   * Builds a JSON object representing a PrintNode print job body.
   *
   * @param printerId
   *     the external ID of the target printer
   * @param copies
   *     the number of copies to print
   * @param base64
   *     the base64-encoded PDF content to print
   * @return a JSON object with the print job fields
   * @throws JSONException
   *     if there is an error building the JSON object
   */
  public static JSONObject buildPrintJobBody(int printerId, int copies, String base64) throws JSONException {
    final String title = String.format(OBMessageUtils.getI18NMessage("ETPP_EtendoPrintNodeLabel"),
        System.currentTimeMillis());
    final JSONObject options = new JSONObject().put("copies", copies);
    return new JSONObject()
        .put("printerId", printerId)
        .put("title", title)
        .put("contentType", "pdf_base64")
        .put("content", base64)
        .put("source", ETENDO_ERP)
        .put("options", options);
  }

  /**
   * Parses a PrintNode JSON response (from /printers) into a list of {@link PrinterDTO}.
   *
   * @param body
   *     the JSON string to parse
   * @return a list of {@link PrinterDTO}, never {@code null}
   * @throws JSONException
   *     if the JSON string is not valid
   */
  public static List<PrinterDTO> parsePrinters(final String body) throws JSONException {
    final JSONArray arr = new JSONArray(body);
    final List<PrinterDTO> result = new ArrayList<>(arr.length());

    for (int i = 0; i < arr.length(); i++) {
      final JSONObject jsonPrinter = arr.getJSONObject(i);
      final String id = String.valueOf(jsonPrinter.opt("id"));
      final String name = jsonPrinter.optString("name", OBMessageUtils.getI18NMessage("ETPP_UnnamedPrinter"));
      final boolean isDefault = jsonPrinter.optBoolean("default", jsonPrinter.optBoolean("is_default", false));
      result.add(new PrinterDTO(id, name, isDefault));
    }
    return result;
  }

  /**
   * Truncates the given string to the given maximum length.
   *
   * <p>
   * If the given string is {@code null}, the method returns {@code null}.
   * Otherwise, if the string is shorter than the given maximum length, the
   * method returns the string as-is. Otherwise, the method returns a
   * substring of the given string, starting at the beginning and ending at
   * the given maximum length, followed by three dots ("...").
   * </p>
   *
   * @param s
   *     The string to truncate.
   * @param max
   *     The maximum length of the string.
   * @return The truncated string, or {@code null} if the given string is
   *     {@code null}.
   */
  public static String truncate(final String s, final int max) {
    if (s == null) return null;
    return s.length() <= max ? s : s.substring(0, max) + "...";
  }
}