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
 * All portions are Copyright © 2021–2025 FUTIT SERVICES, S.L
 * All Rights Reserved.
 * Contributor(s): Futit Services S.L.
 *************************************************************************
 */
package com.etendoerp.print.provider.strategy.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.File;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.datamodel.Table;

import com.etendoerp.print.provider.api.PrintProviderException;
import com.etendoerp.print.provider.api.PrinterDTO;
import com.etendoerp.print.provider.data.Printer;
import com.etendoerp.print.provider.data.Provider;
import com.etendoerp.print.provider.data.ProviderParam;
import com.etendoerp.print.provider.data.TemplateLine;
import com.etendoerp.print.provider.utils.PrintProviderUtils;
import com.etendoerp.print.provider.utils.PrinterUtils;

import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;

/**
 * Unit tests for {@link PrintNodeProvider}.
 * Uses MockitoExtension to enable Mockito mocks for dependencies and static methods.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("java:S5443")
class PrintNodeProviderTest {

  private static final String BASIC_ABC = "Basic abc";
  private static final String API_KEY_VALUE = "api-key-xyz";
  private static final String PDF_EXTENSION = ".pdf";
  private static final String TEMP_FILE_PREFIX = "key";
  private static final String UNNAMED = "Unnamed";
  private static final String LABEL_PREFIX = "Label ";
  private static final String PROVIDER_NOT_FOUND = "Provider not found";
  private static final String FILE_NOT_FOUND = "File not found";
  private static final String ETPP_FILE_NOT_FOUND = "ETPP_FileNotFound";
  private static final String PRINTERS_URL = "https://api.printnode.com/printers";
  private static final String PRINT_JOB_URL = "https://api.printnode.com/printjobs";
  private PrintNodeProvider hook;
  private MockedStatic<OBMessageUtils> obMsgStatic;

  @Mock
  private Printer printer;

  @Mock
  private Provider provider;

  @Mock
  private ProviderParam providerParam;

  @Mock
  private ProviderParam providerParam2;

  @Mock
  private HttpClient httpClient;

  @Mock
  private HttpRequest httpRequest;

  @Mock
  private HttpResponse<String> httpResponse;

  @Mock
  private Table table;

  @Mock
  private TemplateLine templateLine;

  @Mock
  private JasperReport jasperReport;

  @Mock
  private JasperPrint jasperPrint;

  @Mock
  private OBDal obDal;

  @Mock
  private Session session;

  /**
   * Initializes the PrintNodeProvider and sets up the necessary mocks before each test.
   */
  @BeforeEach
  void setUp() {
    hook = spy(new PrintNodeProvider());

    obMsgStatic = mockStatic(OBMessageUtils.class);
    lenient().when(OBMessageUtils.messageBD(anyString())).thenAnswer(inv -> "%s %s %s");
    lenient().when(OBMessageUtils.getI18NMessage("ETPP_UnnamedPrinter")).thenReturn(UNNAMED);
    lenient().when(OBMessageUtils.getI18NMessage("ETPP_EtendoPrintNodeLabel")).thenReturn("Label %s");
  }

  /**
   * Releases static mocks for OBMessageUtils after each test.
   * Ensures that resources are properly closed to avoid memory leaks.
   */
  @AfterEach
  void tearDown() {
    if (obMsgStatic != null) obMsgStatic.close();
  }

  /**
   * Ensures fetchPrinters throws PrintProviderException when provider is null.
   */
  @Test
  void fetchPrintersWhenProviderNullThrowsPrintProviderException() {
    obMsgStatic.when(() -> OBMessageUtils.messageBD("ETPP_ProviderNotFound")).thenReturn(PROVIDER_NOT_FOUND);

    PrintProviderException ex = assertThrows(PrintProviderException.class, () -> hook.fetchPrinters(null));
    assertEquals(PROVIDER_NOT_FOUND, ex.getMessage());
  }

  /**
   * Ensures fetchPrinters returns the parsed list of printers on a successful (2xx) response.
   * Verifies that required params are obtained and content-checked, and the HTTP pipeline is executed.
   */
  @Test
  void fetchPrintersWhenOkReturnsParsedList() {
    when(providerParam.getParamContent()).thenReturn(PRINTERS_URL);

    when(providerParam2.getParamContent()).thenReturn(API_KEY_VALUE);

    when(httpResponse.statusCode()).thenReturn(200);
    when(httpResponse.body()).thenReturn(
        "[{\"id\":123,\"name\":\"Zebra ZD420\",\"default\":true}," + " {\"id\":456,\"name\":\"HP OfficeJet\",\"is_default\":false}]");

    try (MockedStatic<PrinterUtils> pu = mockStatic(
        PrinterUtils.class); MockedStatic<PrintProviderUtils> ppu = mockStatic(PrintProviderUtils.class)) {

      pu.when(() -> PrinterUtils.getRequiredParam(provider, PrinterUtils.PRINTERS_URL)).thenReturn(providerParam);
      pu.when(() -> PrinterUtils.getRequiredParam(provider, PrinterUtils.API_KEY)).thenReturn(providerParam2);
      pu.when(() -> PrinterUtils.providerParamContentCheck(any(ProviderParam.class), anyString())).thenAnswer(
          inv -> null);

      ppu.when(() -> PrintProviderUtils.buildBasicAuth(API_KEY_VALUE)).thenReturn(BASIC_ABC);
      ppu.when(() -> PrintProviderUtils.newHttpClient(anyInt())).thenReturn(httpClient);
      ppu.when(() -> PrintProviderUtils.buildJsonGet(eq(PRINTERS_URL), eq(BASIC_ABC),
          anyInt())).thenReturn(httpRequest);
      ppu.when(() -> PrintProviderUtils.send(eq(httpClient), eq(httpRequest), anyString())).thenReturn(httpResponse);

      List<PrinterDTO> list = hook.fetchPrinters(provider);

      assertNotNull(list);
      assertEquals(2, list.size());
      assertEquals("123", list.get(0).getId());
      assertEquals("Zebra ZD420", list.get(0).getName());
      assertTrue(list.get(0).isDefault());

      assertEquals("456", list.get(1).getId());
      assertEquals("HP OfficeJet", list.get(1).getName());
      assertFalse(list.get(1).isDefault());

      pu.verify(() -> PrinterUtils.providerParamContentCheck(providerParam, PrinterUtils.PRINTERS_URL));
      pu.verify(() -> PrinterUtils.providerParamContentCheck(providerParam2, PrinterUtils.API_KEY));
    }
  }

  /**
   * Ensures fetchPrinters wraps non-2xx HTTP responses into a PrintProviderException
   * with a formatted message using OBMessageUtils.getI18NMessage("ETPP_FetchJobError").
   */
  @Test
  void fetchPrintersWhenResponseNot2xxThrowsPrintProviderException() throws Exception {

    when(providerParam.getParamContent()).thenReturn(PRINTERS_URL);

    when(providerParam2.getParamContent()).thenReturn(API_KEY_VALUE);

    when(httpResponse.statusCode()).thenReturn(401);
    when(httpResponse.body()).thenReturn("Unauthorized");

    obMsgStatic.when(() -> OBMessageUtils.getI18NMessage("ETPP_FetchJobError")).thenReturn(
        "Fetch job error: %s - %s");

    try (MockedStatic<PrinterUtils> pu = mockStatic(
        PrinterUtils.class); MockedStatic<PrintProviderUtils> ppu = mockStatic(PrintProviderUtils.class)) {

      pu.when(() -> PrinterUtils.getRequiredParam(provider, PrinterUtils.PRINTERS_URL)).thenReturn(providerParam);
      pu.when(() -> PrinterUtils.getRequiredParam(provider, PrinterUtils.API_KEY)).thenReturn(providerParam2);
      pu.when(() -> PrinterUtils.providerParamContentCheck(any(ProviderParam.class), anyString())).thenAnswer(
          inv -> null);

      ppu.when(() -> PrintProviderUtils.buildBasicAuth(API_KEY_VALUE)).thenReturn(BASIC_ABC);
      ppu.when(() -> PrintProviderUtils.newHttpClient(anyInt())).thenReturn(httpClient);
      ppu.when(() -> PrintProviderUtils.buildJsonGet(eq(PRINTERS_URL), eq(BASIC_ABC),
          anyInt())).thenReturn(httpRequest);
      ppu.when(() -> PrintProviderUtils.send(eq(httpClient), eq(httpRequest), anyString())).thenReturn(httpResponse);

      PrintProviderException ex = assertThrows(PrintProviderException.class, () -> hook.fetchPrinters(provider));

      assertNotNull(ex.getMessage(), "Wrapped message must not be null");
      assertTrue(ex.getMessage().contains("401"), "Message should include status code");
    }
  }

  /**
   * Ensures generateLabel produces a PDF file when Jasper fill/export succeed.
   * Mocks:
   * - PrinterUtils.resolveTemplateFile / loadOrCompileJasperReport
   * - OBDal.getReadOnlyInstance().getSession().doReturningWork(...)
   * - JasperExportManager.exportReportToPdfFile (writes bytes to the temp path)
   */
  @Test
  void generateLabelWhenOkReturnsPdfFile() throws Exception {
    File jrFile = File.createTempFile("tpl", ".jasper");
    jrFile.deleteOnExit();

    try (MockedStatic<PrinterUtils> pu = mockStatic(PrinterUtils.class); MockedStatic<OBDal> ob = mockStatic(
        OBDal.class); MockedStatic<JasperExportManager> jem = mockStatic(JasperExportManager.class)) {

      pu.when(() -> PrinterUtils.resolveTemplateFile(templateLine)).thenReturn(jrFile);
      pu.when(() -> PrinterUtils.loadOrCompileJasperReport(jrFile.getAbsolutePath(), jrFile)).thenReturn(jasperReport);

      ob.when(OBDal::getReadOnlyInstance).thenReturn(obDal);
      when(obDal.getSession()).thenReturn(session);

      when(session.doReturningWork(any())).thenReturn(jasperPrint);

      jem.when(() -> JasperExportManager.exportReportToPdfFile(eq(jasperPrint), anyString())).thenAnswer(inv -> {
        String outPath = inv.getArgument(1, String.class);
        Files.write(new File(outPath).toPath(), "PDF".getBytes(StandardCharsets.UTF_8));
        return null;
      });

      File out = hook.generateLabel(provider, table, "REC-1", templateLine, new JSONObject());
      assertNotNull(out);
      assertTrue(out.exists(), "Generated PDF must exist");
      assertTrue(out.isFile());
      assertTrue(out.getName().endsWith(PDF_EXTENSION), "Expected a .pdf temp file");
    }
  }

  /**
   * Ensures generateLabel wraps underlying exceptions into PrintProviderException.
   * Here we simulate a failure while filling the report via Session#doReturningWork.
   */
  @Test
  void generateLabelWhenFillReportFailsThrowsPrintProviderException() throws Exception {
    File jrFile = File.createTempFile("tpl", ".jasper");
    jrFile.deleteOnExit();

    try (MockedStatic<PrinterUtils> pu = mockStatic(PrinterUtils.class); MockedStatic<OBDal> ob = mockStatic(
        OBDal.class)) {

      pu.when(() -> PrinterUtils.resolveTemplateFile(templateLine)).thenReturn(jrFile);
      pu.when(() -> PrinterUtils.loadOrCompileJasperReport(jrFile.getAbsolutePath(), jrFile)).thenReturn(jasperReport);

      ob.when(OBDal::getReadOnlyInstance).thenReturn(obDal);
      when(obDal.getSession()).thenReturn(session);

      when(session.doReturningWork(any())).thenThrow(new RuntimeException("db fill error"));

      PrintProviderException ex = assertThrows(PrintProviderException.class,
          () -> hook.generateLabel(provider, table, "REC-1", templateLine, new JSONObject()));
      assertNotNull(ex.getMessage());
      assertTrue(ex.getMessage().contains("db fill error"));
    }
  }

  /**
   * Ensures sendToPrinter returns the job id when all steps succeed (a happy path).
   */
  @Test
  void sendToPrinterWhenOkReturnsJobId() throws Exception {
    when(printer.getValue()).thenReturn("123");

    File file = File.createTempFile(TEMP_FILE_PREFIX, PDF_EXTENSION);
    file.deleteOnExit();

    when(providerParam.getParamContent()).thenReturn(PRINT_JOB_URL);

    when(providerParam2.getParamContent()).thenReturn(API_KEY_VALUE);

    when(httpResponse.statusCode()).thenReturn(201);
    when(httpResponse.body()).thenReturn("{\"jobId\":\"IGNORED-BY-EXTRACTOR\"}");

    try (MockedStatic<PrinterUtils> pu = mockStatic(
        PrinterUtils.class); MockedStatic<PrintProviderUtils> ppu = mockStatic(PrintProviderUtils.class)) {

      pu.when(() -> PrinterUtils.getRequiredParam(provider, PrinterUtils.PRINTJOB_URL)).thenReturn(providerParam);
      pu.when(() -> PrinterUtils.getRequiredParam(provider, PrinterUtils.API_KEY)).thenReturn(providerParam2);
      pu.when(() -> PrinterUtils.providerParamContentCheck(any(ProviderParam.class), anyString())).thenAnswer(
          inv -> null);

      ppu.when(() -> PrintProviderUtils.encodeFileToBase64(file)).thenReturn("BASE64");
      ppu.when(() -> PrintProviderUtils.buildBasicAuth(API_KEY_VALUE)).thenReturn(BASIC_ABC);
      ppu.when(() -> PrintProviderUtils.newHttpClient(anyInt())).thenReturn(httpClient);
      ppu.when(
          () -> PrintProviderUtils.buildJsonPost(eq(PRINT_JOB_URL), eq(BASIC_ABC), anyInt(),
              anyString())).thenReturn(httpRequest);
      ppu.when(() -> PrintProviderUtils.send(eq(httpClient), eq(httpRequest), anyString())).thenReturn(httpResponse);
      ppu.when(() -> PrintProviderUtils.extractJobIdOrPreview(anyString(), any(), eq(200))).thenReturn("JOB-999");

      String result = hook.sendToPrinter(provider, printer, 2, file);

      assertEquals("JOB-999", result);
    }
  }

  /**
   * Ensures sendToPrinter rethrows PrintProviderException when validation fails (e.g., provider null).
   */
  @Test
  void sendToPrinterWhenValidationFailsRethrowsPrintProviderException() {
    PrintProviderException ex = assertThrows(PrintProviderException.class,
        () -> hook.sendToPrinter(null, printer, 1, new File("nope.pdf")));
    assertNotNull(ex.getMessage());
  }

  /**
   * Ensures sendToPrinter wraps non-2xx HTTP responses into a PrintProviderException with a formatted message.
   */
  @Test
  void sendToPrinterWhenResponseNot2xxThrowsPrintProviderException() throws Exception {
    when(printer.getValue()).thenReturn("123");

    File file = File.createTempFile("err", PDF_EXTENSION);
    file.deleteOnExit();

    when(providerParam.getParamContent()).thenReturn(PRINT_JOB_URL);
    when(providerParam2.getParamContent()).thenReturn(API_KEY_VALUE);

    when(httpResponse.statusCode()).thenReturn(400);
    when(httpResponse.body()).thenReturn("Bad Request");

    obMsgStatic.when(() -> OBMessageUtils.getI18NMessage("ETPP_PrintJobError")).thenReturn(
        "Print job error: %s - %s");

    try (MockedStatic<PrinterUtils> pu = mockStatic(
        PrinterUtils.class); MockedStatic<PrintProviderUtils> ppu = mockStatic(PrintProviderUtils.class)) {

      pu.when(() -> PrinterUtils.getRequiredParam(provider, PrinterUtils.PRINTJOB_URL)).thenReturn(providerParam);
      pu.when(() -> PrinterUtils.getRequiredParam(provider, PrinterUtils.API_KEY)).thenReturn(providerParam2);
      pu.when(() -> PrinterUtils.providerParamContentCheck(any(ProviderParam.class), anyString())).thenAnswer(
          inv -> null);

      ppu.when(() -> PrintProviderUtils.encodeFileToBase64(file)).thenReturn("BASE64");
      ppu.when(() -> PrintProviderUtils.buildBasicAuth(API_KEY_VALUE)).thenReturn(BASIC_ABC);
      ppu.when(() -> PrintProviderUtils.newHttpClient(anyInt())).thenReturn(httpClient);
      ppu.when(
          () -> PrintProviderUtils.buildJsonPost(eq(PRINT_JOB_URL), eq(BASIC_ABC), anyInt(),
              anyString())).thenReturn(httpRequest);
      ppu.when(() -> PrintProviderUtils.send(eq(httpClient), eq(httpRequest), anyString())).thenReturn(httpResponse);

      PrintProviderException ex = assertThrows(PrintProviderException.class,
          () -> hook.sendToPrinter(provider, printer, 1, file));

      assertNotNull(ex.getMessage(), "Wrapped message must not be null");
      assertTrue(ex.getMessage().contains("400"), "Message should include status code");
    }
  }

  /**
   * Ensures validatePrintJobInputs throws PrintProviderException when provider is null.
   */
  @Test
  void validatePrintJobInputsWhenProviderNullThrowsException() throws Exception {
    obMsgStatic.when(() -> OBMessageUtils.messageBD("ETPP_ProviderNotFound")).thenReturn(PROVIDER_NOT_FOUND);

    File file = File.createTempFile(TEMP_FILE_PREFIX, PDF_EXTENSION);
    file.deleteOnExit();

    PrintProviderException ex = assertThrows(PrintProviderException.class,
        () -> PrintNodeProvider.validatePrintJobInputs(null, printer, file));
    assertEquals(PROVIDER_NOT_FOUND, ex.getMessage());
  }

  /**
   * Ensures validatePrintJobInputs throws PrintProviderException when printer is null.
   */
  @Test
  void validatePrintJobInputsWhenPrinterNullThrowsException() throws Exception {
    obMsgStatic.when(() -> OBMessageUtils.messageBD("ETPP_PrinterNotFound")).thenReturn("Printer not found");

    File file = File.createTempFile(TEMP_FILE_PREFIX, PDF_EXTENSION);
    file.deleteOnExit();

    PrintProviderException ex = assertThrows(PrintProviderException.class,
        () -> PrintNodeProvider.validatePrintJobInputs(provider, null, file));
    assertEquals("Printer not found", ex.getMessage());
  }

  /**
   * Ensures validatePrintJobInputs throws PrintProviderException when file is null.
   */
  @Test
  void validatePrintJobInputsWhenFileNullThrowsException() {
    obMsgStatic.when(() -> OBMessageUtils.messageBD(ETPP_FILE_NOT_FOUND)).thenReturn(FILE_NOT_FOUND);

    PrintProviderException ex = assertThrows(PrintProviderException.class,
        () -> PrintNodeProvider.validatePrintJobInputs(provider, printer, null));
    assertEquals(FILE_NOT_FOUND, ex.getMessage());
  }

  /**
   * Ensures validatePrintJobInputs throws PrintProviderException when file does not exist.
   */
  @Test
  void validatePrintJobInputsWhenFileDoesNotExistThrowsException() {
    obMsgStatic.when(() -> OBMessageUtils.messageBD(ETPP_FILE_NOT_FOUND)).thenReturn(FILE_NOT_FOUND);

    File file = new File("definitely-not-existing-" + System.nanoTime() + PDF_EXTENSION);

    PrintProviderException ex = assertThrows(PrintProviderException.class,
        () -> PrintNodeProvider.validatePrintJobInputs(provider, printer, file));
    assertEquals(FILE_NOT_FOUND, ex.getMessage());
  }

  /**
   * Ensures validatePrintJobInputs throws PrintProviderException when the path is a directory, not a file.
   */
  @Test
  void validatePrintJobInputsWhenPathIsDirectoryThrowsException() throws Exception {
    obMsgStatic.when(() -> OBMessageUtils.messageBD(ETPP_FILE_NOT_FOUND)).thenReturn(FILE_NOT_FOUND);

    File dir = Files.createTempDirectory("print-dir").toFile();
    dir.deleteOnExit();

    PrintProviderException ex = assertThrows(PrintProviderException.class,
        () -> PrintNodeProvider.validatePrintJobInputs(provider, printer, dir));
    assertEquals(FILE_NOT_FOUND, ex.getMessage());
  }

  /**
   * Ensures validatePrintJobInputs completes without throwing when inputs are valid.
   */
  @Test
  void validatePrintJobInputsWhenValidInputsOk() throws Exception {
    File file = File.createTempFile(TEMP_FILE_PREFIX, PDF_EXTENSION);
    file.deleteOnExit();

    assertDoesNotThrow(() -> PrintNodeProvider.validatePrintJobInputs(provider, printer, file));
  }

  /**
   * Ensures parseExternalPrinterId parses a numeric external id and returns its integer value.
   */
  @Test
  void parseExternalPrinterIdWhenNumericReturnsInt() {
    int id = PrintNodeProvider.parseExternalPrinterId("123");
    assertEquals(123, id);
  }

  /**
   * Ensures parseExternalPrinterId wraps NumberFormatException into PrintProviderException
   * when the external id is not a valid integer.
   */
  @Test
  void parseExternalPrinterIdWhenNonNumericThrowsPrintProviderException() {
    PrintProviderException ex = assertThrows(PrintProviderException.class,
        () -> PrintNodeProvider.parseExternalPrinterId("abc"));

    assertNotNull(ex.getCause(), "Cause should be present");
    assertInstanceOf(NumberFormatException.class, ex.getCause(), "Cause should be NumberFormatException");
    assertNotNull(ex.getMessage(), "Wrapped message should not be null");
    assertTrue(ex.getMessage().contains("abc") || ex.getMessage().contains("For input string"),
        "Message should reference the invalid input");
  }

  /**
   * Ensures parseExternalPrinterId throws PrintProviderException when the external id is null.
   * Integer.parseInt(null) throws NumberFormatException and should be wrapped accordingly.
   */
  @Test
  void parseExternalPrinterIdWhenNullThrowsPrintProviderException() {
    PrintProviderException ex = assertThrows(PrintProviderException.class,
        () -> PrintNodeProvider.parseExternalPrinterId(null));

    assertNotNull(ex.getCause(), "Cause should be present");
    assertInstanceOf(NumberFormatException.class, ex.getCause(), "Cause should be NumberFormatException");
  }

  /**
   * Ensures buildPrintJobBody constructs a well-formed JSON object with
   * printerId, title, contentType=pdf_base64, content, source, and options.copies.
   */
  @Test
  void buildPrintJobBodyWhenValidReturnsWellFormedJson() throws JSONException {
    JSONObject body = PrintNodeProvider.buildPrintJobBody(123, 2, "BASE64DATA");

    assertEquals(123, body.getInt("printerId"));
    assertEquals("pdf_base64", body.getString("contentType"));
    assertEquals("BASE64DATA", body.getString("content"));

    assertNotNull(body.getString("source"));
    assertFalse(body.getString("source").isEmpty(), "source must not be empty");

    JSONObject options = body.getJSONObject("options");
    assertEquals(2, options.getInt("copies"));

    String title = body.getString("title");
    assertTrue(title.startsWith(LABEL_PREFIX), "title should start with 'Label '");
    assertTrue(title.substring(LABEL_PREFIX.length()).matches("\\d+"), "title should append a numeric timestamp");
  }

  /**
   * Ensures buildPrintJobBody can build a JSON even with zero copies and empty content.
   */
  @Test
  void buildPrintJobBodyWhenZeroCopiesAndEmptyContentStillBuildsJson() throws JSONException {
    JSONObject body = PrintNodeProvider.buildPrintJobBody(0, 0, "");

    assertEquals(0, body.getInt("printerId"));
    assertEquals("pdf_base64", body.getString("contentType"));
    assertEquals("", body.getString("content"));

    JSONObject options = body.getJSONObject("options");
    assertEquals(0, options.getInt("copies"));

    String title = body.getString("title");
    assertTrue(title.startsWith(LABEL_PREFIX), "title should start with 'Label '");
    assertTrue(title.substring(LABEL_PREFIX.length()).matches("\\d+"), "title should append a numeric timestamp");
  }

  /**
   * Ensures parsePrinters returns an empty list when the JSON array is empty.
   */
  @Test
  void parsePrintersWhenEmptyArrayReturnsEmptyList() throws JSONException {
    List<PrinterDTO> printers = PrintNodeProvider.parsePrinters("[]");
    assertNotNull(printers);
    assertTrue(printers.isEmpty());
  }

  /**
   * Ensures parsePrinters correctly parses a printer with all fields (id, name, default).
   */
  @Test
  void parsePrintersWhenPrinterHasAllFieldsParsesCorrectly() throws JSONException {
    String body = "[{\"id\":123,\"name\":\"Office\",\"default\":true}]";

    List<PrinterDTO> printers = PrintNodeProvider.parsePrinters(body);

    assertEquals(1, printers.size());
    PrinterDTO printerDTO = printers.get(0);
    assertEquals("123", printerDTO.getId());
    assertEquals("Office", printerDTO.getName());
    assertTrue(printerDTO.isDefault());
  }

  /**
   * Ensures parsePrinters uses fallback name from OBMessageUtils when "name" is missing.
   */
  @Test
  void parsePrintersWhenNameMissingUsesFallback() throws JSONException {
    String body = "[{\"id\":999,\"default\":false}]";

    List<PrinterDTO> printers = PrintNodeProvider.parsePrinters(body);

    assertEquals(1, printers.size());
    PrinterDTO printerDTO = printers.get(0);
    assertEquals("999", printerDTO.getId());
    assertEquals(UNNAMED, printerDTO.getName());
    assertFalse(printerDTO.isDefault());
  }

  /**
   * Ensures parsePrinters reads "is_default" when "default" is not present.
   */
  @Test
  void parsePrintersWhenIsDefaultPresentParsesCorrectly() throws JSONException {
    String body = "[{\"id\":\"A1\",\"name\":\"Lab\",\"is_default\":true}]";

    List<PrinterDTO> printers = PrintNodeProvider.parsePrinters(body);

    assertEquals(1, printers.size());
    PrinterDTO printerDTO = printers.get(0);
    assertEquals("A1", printerDTO.getId());
    assertEquals("Lab", printerDTO.getName());
    assertTrue(printerDTO.isDefault());
  }

  /**
   * Ensures parsePrinters can handle multiple printers in the same array.
   */
  @Test
  void parsePrintersWhenMultiplePrintersParsesAll() throws JSONException {
    String body = "[" + "{\"id\":\"1\",\"name\":\"A\",\"default\":true}," + "{\"id\":\"2\",\"is_default\":true}," + "{\"id\":\"3\",\"name\":\"C\"}" + "]";

    List<PrinterDTO> printers = PrintNodeProvider.parsePrinters(body);

    assertEquals(3, printers.size());
    assertEquals("1", printers.get(0).getId());
    assertEquals("A", printers.get(0).getName());
    assertTrue(printers.get(0).isDefault());

    assertEquals("2", printers.get(1).getId());
    assertEquals(UNNAMED, printers.get(1).getName());
    assertTrue(printers.get(1).isDefault());

    assertEquals("3", printers.get(2).getId());
    assertEquals("C", printers.get(2).getName());
    assertFalse(printers.get(2).isDefault());
  }

  /**
   * Ensures truncate returns null when the input string is null.
   */
  @Test
  void truncateWhenNulReturnsNull() {
    assertNull(PrintNodeProvider.truncate(null, 5));
  }

  /**
   * Ensures truncate returns the original string when its length is less than or equal to max.
   */
  @Test
  void truncateWhenLengthLessOrEqualToMaxReturnsSame() {
    assertEquals("AB", PrintNodeProvider.truncate("AB", 5));
    assertEquals("Hello", PrintNodeProvider.truncate("Hello", 5));
  }

  /**
   * Ensures truncate shortens strings longer than max and appends an ellipsis ("...").
   */
  @Test
  void truncateWhenLongerThanMaxAppendsEllipsis() {
    assertEquals("Hello...", PrintNodeProvider.truncate("HelloWorld", 5));
  }

  /**
   * Ensures truncate handles max = 0 by returning only the ellipsis ("...").
   */
  @Test
  void truncateWhenMaxIsZeroReturnsJustEllipsis() {
    assertEquals("...", PrintNodeProvider.truncate("Data", 0));
  }

  /**
   * Ensures truncate throws an exception when max is negative (substring(0, negative)).
   */
  @Test
  void truncateWhenMaxNegativeThrowsStringIndexOutOfBounds() {
    assertThrows(StringIndexOutOfBoundsException.class, () -> PrintNodeProvider.truncate("AB", -1));
  }

}