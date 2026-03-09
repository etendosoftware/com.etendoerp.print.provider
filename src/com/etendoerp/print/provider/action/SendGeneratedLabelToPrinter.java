/*
 *************************************************************************
 * The contents of this file are subject to the Etendo License
 * (the "License"), you may not use this file except in compliance with
 * the License.
 * You may obtain a copy of the License at
 * https://github.com/etendosoftware/etendo_core/blob/main/legal/Etendo_license.txt
 * Software distributed under the License is distributed on an
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing rights
 * and limitations under the License.
 * All portions are Copyright © 2021–2026 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */
package com.etendoerp.print.provider.action;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.client.application.process.ResponseActionsBuilder;
import org.openbravo.client.application.report.ReportingUtils;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.datamodel.Table;

import com.etendoerp.print.provider.api.PrintProviderException;
import com.etendoerp.print.provider.data.Printer;
import com.etendoerp.print.provider.data.Provider;
import com.etendoerp.print.provider.data.TemplateLine;
import com.etendoerp.print.provider.strategy.PrintProviderStrategy;
import com.etendoerp.print.provider.utils.PrinterUtils;
import com.etendoerp.print.provider.utils.ProviderStrategyResolver;
import com.lowagie.text.Document;
import com.lowagie.text.pdf.PdfCopy;
import com.lowagie.text.pdf.PdfReader;
import com.smf.jobs.Action;
import com.smf.jobs.ActionResult;
import com.smf.jobs.Result;

/**
 * Action to generate labels for a given table and send them to a printer.
 *
 * <p>When the {@code downloadlabel} parameter is set to {@code "Y"} (the default),
 * the generated label PDF is also made available for download in the browser.
 * For multiple records the individual PDFs are merged into a single file.</p>
 */
public class SendGeneratedLabelToPrinter extends Action {

  private static final Logger log = LogManager.getLogger();

  private static final String ACTION_HANDLER =
      "com.etendoerp.print.provider.action.LabelDownloadHandler";
  private static final String PROCESSID = "processId";
  private static final String FILE_EXTENSION = ".pdf";
  private static final String LABEL_PREFIX = "etendo-label-";

  /**
   * Processes the given {@code parameters} by generating labels for the given
   * {@code entityName} records and sending them to the selected printer. The
   * following parameters are expected:
   * <ul>
   *   <li>{@code provider}: the ID of the provider to use (e.g. a PrintNode
   *       provider)</li>
   *   <li>{@code entityName}: the name of the table to generate labels for
   *       (e.g. {@code C_Order}</li>
   *   <li>{@code records}: a JSON array of record IDs to generate labels for</li>
   *   <li>{@code printers}: the ID of the printer to send the labels to</li>
   *   <li>{@code numberOfCopies}: the number of copies to print</li>
   *   <li>{@code downloadlabel}: {@code "Y"} to download the generated PDF
   *       (default), {@code "N"} to only print without downloading</li>
   * </ul>
   *
   * <p>This action will log errors if there are any issues generating or sending
   * the labels but will not throw an exception. Instead, it will return an
   * {@link ActionResult} with the result of the action.</p>
   **/
  @Override
  protected ActionResult action(JSONObject parameters, MutableBoolean isStopped) {
    final ActionResult res = new ActionResult();
    res.setType(Result.Type.SUCCESS);
    log.debug("SendGeneratedLabelToPrinter process started: {}", parameters);

    final List<File> downloadableLabels = new ArrayList<>();

    try {
      OBContext.setAdminMode(true);

      // --- Validate & read params (via PrinterUtils) ---
      final String providerId = PrinterUtils.requireParam(parameters, PrinterUtils.PROVIDER);
      final String entityName = PrinterUtils.requireParam(parameters, PrinterUtils.ENTITY_NAME);
      final JSONArray records = PrinterUtils.requireJSONArray(parameters, PrinterUtils.RECORDS);
      final String printerId = PrinterUtils.requireParam(parameters, PrinterUtils.PRINTERS);
      final int numberOfCopies = PrinterUtils.requirePositiveInt(parameters, PrinterUtils.NUMBER_OF_COPIES);

      // Download parameter: Y by default
      final boolean downloadEnabled = !"N".equalsIgnoreCase(
          parameters.optString(PrinterUtils.DOWNLOAD_LABEL, "Y"));

      // --- Resolve DAL entities / strategy ---
      final Provider provider = PrinterUtils.requireProvider(providerId);
      final PrintProviderStrategy strategy = ProviderStrategyResolver.resolveForProvider(provider);
      final Printer targetPrinter = PrinterUtils.requirePrinter(printerId);
      final Table table = PrinterUtils.requireTableByName(entityName);
      final TemplateLine templateLine = PrinterUtils.resolveTemplateLineFor(table);

      // --- Iterate over records: generate label + send to printer ---
      final List<String> jobIds = new ArrayList<>(records.length());
      int printFailureCount = 0;

      for (int i = 0; i < records.length(); i++) {
        final String recordId = records.getString(i);
        File label = null;
        boolean labelRetained = false;
        try {
          label = strategy.generateLabel(provider, table, recordId, templateLine, parameters);

          // Retain the file for download BEFORE attempting to print,
          // so that a printer failure does not prevent the download.
          if (downloadEnabled && label != null && label.exists()) {
            downloadableLabels.add(label);
            labelRetained = true;
          }

          final String jobId = strategy.sendToPrinter(provider, targetPrinter, numberOfCopies, label);
          jobIds.add(StringUtils.defaultIfBlank(jobId, "-"));
        } catch (Exception e) {
          log.error("Error printing record {} on printer {}: {}", recordId, printerId, e.getMessage(), e);
          printFailureCount++;
        } finally {
          // Only delete the file if it was NOT retained for download
          if (!labelRetained) {
            safeDelete(label, recordId);
          }
        }
      }

      // --- Build result message & handle download ---
      final boolean canDownload = downloadEnabled && !downloadableLabels.isEmpty();

      if (printFailureCount > 0) {
        return handlePrintFailures(res, parameters, printFailureCount, records.length(),
            canDownload, downloadableLabels);
      }

      // All print jobs succeeded
      final StringJoiner sj = new StringJoiner(", ");
      jobIds.forEach(sj::add);
      final String resultMessage = String.format(
          OBMessageUtils.getI18NMessage("ETPP_PrintJobSent"), sj.toString());

      if (canDownload) {
        return buildDownloadResponse(res, parameters, downloadableLabels, resultMessage,
            ResponseActionsBuilder.MessageType.SUCCESS);
      }

      res.setMessage(resultMessage);
      log.debug("SendGeneratedLabelToPrinter process finished: {}", res.getMessage());
      return res;

    } catch (PrintProviderException e) {
      log.error(e.getMessage(), e);
      OBDal.getInstance().rollbackAndClose();
      return PrinterUtils.fail(res, String.format(OBMessageUtils.getI18NMessage("ETPP_ProviderError"), e.getMessage()));
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      OBDal.getInstance().rollbackAndClose();
      return PrinterUtils.fail(res, e.getMessage());
    } finally {
      OBContext.restorePreviousMode();
      cleanupFiles(downloadableLabels);
    }
  }

  /**
   * Builds an {@link ActionResult} that includes both the success message and a
   * download response action for the generated label PDF file(s).
   *
   * <p>If multiple label files were generated, they are merged into a single PDF
   * before being placed in the reporting temp folder for download.</p>
   *
   * @param res
   *     the action result to populate
   * @param parameters
   *     the original process parameters (used to extract {@code processId})
   * @param labelFiles
   *     the list of generated label PDF files to download
   * @param message
   *     the message to display in the process view
   * @param messageType
   *     the type of message to show ({@code SUCCESS} or {@code WARNING})
   * @return the populated {@link ActionResult} with download action
   */
  private ActionResult buildDownloadResponse(ActionResult res, JSONObject parameters,
      List<File> labelFiles, String message, ResponseActionsBuilder.MessageType messageType) {
    File fileToDownload = null;
    try {
      fileToDownload = mergeLabels(labelFiles);

      // Copy the label to the report temp folder
      final String tmpFileName = UUID.randomUUID() + FILE_EXTENSION;
      final String tmpFolder = getReportingTempFolder();
      final File destFile = new File(tmpFolder, tmpFileName);
      Files.copy(fileToDownload.toPath(), destFile.toPath());

      // Build the download response action
      final JSONObject processParams = new JSONObject();
      processParams.put(PROCESSID, parameters.optString(PROCESSID, ""));
      processParams.put("reportId", "");
      processParams.put("actionHandler", ACTION_HANDLER);

      final JSONObject downloadAction = new JSONObject();
      downloadAction.put("processParameters", processParams);
      downloadAction.put("tmpfileName", destFile.getName());
      downloadAction.put("fileName", LABEL_PREFIX + System.currentTimeMillis() + FILE_EXTENSION);

      final String msgTitle = (messageType == ResponseActionsBuilder.MessageType.SUCCESS)
          ? OBMessageUtils.messageBD("Success")
          : OBMessageUtils.messageBD("Warning");

      final ResponseActionsBuilder builder = createResponseBuilder()
          .showMsgInProcessView(messageType, msgTitle, message)
          .addCustomResponseAction("OBUIAPP_downloadReport", downloadAction);

      res.setType(messageType == ResponseActionsBuilder.MessageType.SUCCESS
          ? Result.Type.SUCCESS : Result.Type.WARNING);
      res.setMessage(message);
      res.setResponseActionsBuilder(builder);
      return res;

    } catch (Exception e) {
      log.error("Error preparing label download: {}", e.getMessage(), e);
      // Download failed — return warning with the original message
      res.setType(Result.Type.WARNING);
      res.setMessage(message + " " +
          OBMessageUtils.getI18NMessage("ETPP_DownloadFailed"));
      return res;
    } finally {
      // Clean up the merged file if it was created separately from the originals
      if (fileToDownload != null && !labelFiles.contains(fileToDownload)) {
        safeDelete(fileToDownload, "merged");
      }
    }
  }

  /**
   * Handles the result when one or more print jobs have failed.
   *
   * <p>If downloadable labels are available, returns a download response with a
   * warning message. Otherwise, returns an error (all failed) or a warning
   * (some failed) without download.</p>
   *
   * @param res
   *     the action result to populate
   * @param parameters
   *     the original process parameters
   * @param printFailureCount
   *     number of records that failed to print
   * @param totalRecords
   *     total number of records that were processed
   * @param canDownload
   *     whether label download is available
   * @param downloadableLabels
   *     the list of label files available for download
   * @return the populated {@link ActionResult}
   */
  private ActionResult handlePrintFailures(ActionResult res, JSONObject parameters,
      int printFailureCount, int totalRecords, boolean canDownload,
      List<File> downloadableLabels) {
    if (canDownload) {
      final String msg = OBMessageUtils.getI18NMessage("ETPP_PrintFailedDownloadReady");
      return buildDownloadResponse(res, parameters, downloadableLabels, msg,
          ResponseActionsBuilder.MessageType.WARNING);
    }
    if (printFailureCount == totalRecords) {
      return PrinterUtils.fail(res, OBMessageUtils.getI18NMessage("ETPP_AllPrintJobsFailed"));
    }
    final String msg = String.format(
        OBMessageUtils.getI18NMessage("ETPP_SomePrintJobsFailed"), printFailureCount);
    return PrinterUtils.warning(res, msg);
  }

  /**
   * Creates a new {@link ResponseActionsBuilder}. Package-visible to allow
   * spy overriding in unit tests without requiring access to the protected
   * {@code getResponseBuilder()} from {@code BaseProcessActionHandler}.
   */
  ResponseActionsBuilder createResponseBuilder() {
    return getResponseBuilder();
  }

  /**
   * Returns the reporting temp folder path. Package-visible to allow spy
   * overriding in unit tests without requiring a static mock of
   * {@link ReportingUtils}.
   */
  String getReportingTempFolder() {
    return ReportingUtils.getTempFolder();
  }

  /**
   * Merges multiple label PDF files into a single PDF document using iText.
   *
   * <p>If the list contains only one file, that file is returned as-is. If iText
   * classes are not available on the classpath, the first file is returned and a
   * warning is logged.</p>
   *
   * @param labelFiles
   *     the label PDF files to merge (must contain at least one entry)
   * @return the merged PDF file, or the single file if only one was provided
   * @throws IOException
   *     if reading/writing files fails
   */
  @SuppressWarnings("java:S5443")
  protected File mergeLabels(List<File> labelFiles) throws IOException {
    if (labelFiles.size() == 1) {
      return labelFiles.get(0);
    }
    try {
      final Path merged = Files.createTempFile(LABEL_PREFIX + "merged-", FILE_EXTENSION);
      final Document document = new Document();
      final PdfCopy copy = new PdfCopy(document, new FileOutputStream(merged.toFile()));
      document.open();
      for (File f : labelFiles) {
        final PdfReader reader = new PdfReader(f.getAbsolutePath());
        for (int page = 1; page <= reader.getNumberOfPages(); page++) {
          copy.addPage(copy.getImportedPage(reader, page));
        }
        reader.close();
      }
      document.close();
      return merged.toFile();
    } catch (NoClassDefFoundError e) {
      log.warn("PDF merge not available (iText not found on classpath), downloading first label only");
      return labelFiles.get(0);
    } catch (IOException e) {
      throw e;
    } catch (Exception e) {
      throw new IOException("Failed to merge label PDFs: " + e.getMessage(), e);
    }
  }

  /**
   * Deletes the temp file using NIO, logging the outcome with context.
   */
  private void safeDelete(File file, String recordId) {
    if (file == null) return;

    final Path p = file.toPath();
    try {
      final boolean deleted = Files.deleteIfExists(p);
      if (!deleted) {
        // File was already removed or never existed
        log.debug("Temp label file for record {} was not present: {}", recordId, p);
      }
    } catch (IOException ex) {
      log.warn("Could not delete temp label file for record {} ({}): {}", recordId, p, ex.getMessage());
    }
  }

  /**
   * Deletes all files in the list, logging any failures.
   */
  private void cleanupFiles(List<File> files) {
    for (File f : files) {
      safeDelete(f, "cleanup");
    }
  }

  /**
   * This method returns the input class for this action.
   *
   * @return the input class for this action, which is {@code BaseOBObject}
   */
  @Override
  protected Class<BaseOBObject> getInputClass() {
    return BaseOBObject.class;
  }
}