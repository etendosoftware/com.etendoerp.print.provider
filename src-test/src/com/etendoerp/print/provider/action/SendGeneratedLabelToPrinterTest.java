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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.dal.service.Restriction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.client.application.process.ResponseActionsBuilder;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
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
import com.smf.jobs.ActionResult;
import com.smf.jobs.Result;

/**
 * Unit tests for the SendGeneratedLabelToPrinter class.
 * Validates error handling and label printing logic, including scenarios
 * with missing parameters, not found entities, and exceptions thrown by the print provider.
 */
class SendGeneratedLabelToPrinterTest {

  // Constants used for test scenarios
  private static final String PROV_OK = "prov-ok";
  private static final String PROV_MISSING = "prov-missing";
  private static final String PRN_OK = "p-ok";
  private static final String PRN_MISSING = "p-missing";
  private static final String ENTITY = "M_InOut";
  private static final String REC_1 = "rec-1";
  private static final String REC_2 = "rec-2";
  private static final String TRUE = "true";

  // Message templates for assertions
  private static final String MSG_MISSING_PARAM = "Missing parameter: %s";
  private static final String MSG_PROVIDER_NOT_FOUND = "Provider not found";
  private static final String MSG_PRINTER_NOT_FOUND_FMT = "Printer %s not found";
  private static final String MSG_TABLE_NOT_FOUND_FMT = "Table %s not found";
  private static final String MSG_PROVIDER_ERROR_FMT = "Provider error: %s";
  private static final String MSG_ALL_FAILED = "All print jobs failed";
  private static final String MSG_PRINT_JOB_SENT = "Print jobs sent: %s";
  private static final String MSG_SOME_FAILED = "%s print jobs failed";
  private static final String MSG_PRINT_FAILED_DOWNLOAD = "Print failed but download ready";
  private static final String MSG_DOWNLOAD_FAILED = "Download failed";
  private static final String MSG_DOWNLOAD_ONLY_SUCCESS = "Labels generated and ready for download";
  private static final String MSG_ALL_GEN_FAILED = "All label generations failed";
  private static final String MSG_SOME_GEN_FAILED = "%s label(s) failed to generate";
  private static final String MSG_NO_JOBS = "There are no jobs to download or send to printers.";

  // Temp directory for download-enabled tests
  @TempDir
  File tmpDir;

  // Static mocks for dependencies
  private MockedStatic<OBMessageUtils> sMsg;
  private MockedStatic<OBContext> sObc;
  private MockedStatic<OBDal> sDal;
  private MockedStatic<ProviderStrategyResolver> sResolver;
  private MockedStatic<PrinterUtils> sUtil;

  // Mocks for DAL and domain objects
  private OBDal dal;
  private Provider provider;
  private OBCriteria<Table> tableCrit;
  private Table table;
  private TemplateLine tLine;
  private PrintProviderStrategy strategy;

  // Instance of the action under test (spy for download tests)
  private SendGeneratedLabelToPrinter action;

  /**
   * Creates a base JSON object with the minimum required parameters for
   * SendGeneratedLabelToPrinter.action.
   * Used as a starting point for most tests.
   */
  private JSONObject baseParams() throws Exception {
    // Build a JSON object with all required parameters for the label printing action
    JSONObject json = new JSONObject();
    json.put(PrinterUtils.PROVIDER, PROV_OK);
    json.put(PrinterUtils.ENTITY_NAME, ENTITY);
    json.put(PrinterUtils.PRINTERS, PRN_OK);
    json.put(PrinterUtils.NUMBER_OF_COPIES, 1);
    json.put(PrinterUtils.RECORDS, new JSONArray().put(REC_1));
    // Disable download by default; tests that need it override to "true"
    json.put(PrinterUtils.DOWNLOAD_LABEL, "false");
    return json;
  }

  /**
   * Stubs all static calls to OBMessageUtils.getI18NMessage used in this class.
   * This allows tests to verify error messages without relying on actual i18n logic.
   */
  private void stubI18N(MockedStatic<OBMessageUtils> sMsgStatic) {
    // Stub static i18n message calls for predictable test output
    sMsgStatic.when(() -> OBMessageUtils.getI18NMessage(PrinterUtils.MISSING_PARAMETER_MSG))
        .thenReturn(MSG_MISSING_PARAM);
    sMsgStatic.when(() -> OBMessageUtils.getI18NMessage(PrinterUtils.PROVIDER_NOT_FOUND_MSG))
        .thenReturn(MSG_PROVIDER_NOT_FOUND);
    sMsgStatic.when(() -> OBMessageUtils.getI18NMessage("ETPP_PrinterNotFoundById"))
        .thenReturn(MSG_PRINTER_NOT_FOUND_FMT);
    sMsgStatic.when(() -> OBMessageUtils.getI18NMessage("ETPP_TableNotFound"))
        .thenReturn(MSG_TABLE_NOT_FOUND_FMT);
    sMsgStatic.when(() -> OBMessageUtils.getI18NMessage("ETPP_ProviderError"))
        .thenReturn(MSG_PROVIDER_ERROR_FMT);
    sMsgStatic.when(() -> OBMessageUtils.getI18NMessage("ETPP_AllPrintJobsFailed"))
        .thenReturn(MSG_ALL_FAILED);

    // Stub i18n message calls with arguments for dynamic error messages
    sMsgStatic.when(() -> OBMessageUtils.getI18NMessage(eq(PrinterUtils.MISSING_PARAMETER_MSG), Mockito.any()))
        .thenAnswer(inv -> {
          Object[] args = inv.getArgument(1, Object[].class);
          return "Missing parameter: " + (args != null && args.length > 0 ? args[0] : "%s");
        });
    sMsgStatic.when(() -> OBMessageUtils.getI18NMessage(eq("ETPP_PrinterNotFoundById"), Mockito.any()))
        .thenAnswer(inv -> "Printer " + inv.getArgument(1, Object[].class)[0] + " not found");
    sMsgStatic.when(() -> OBMessageUtils.getI18NMessage(eq("ETPP_TableNotFound"), Mockito.any()))
        .thenAnswer(inv -> "Table " + inv.getArgument(1, Object[].class)[0] + " not found");
    sMsgStatic.when(() -> OBMessageUtils.getI18NMessage(eq("ETPP_ProviderError"), Mockito.any()))
        .thenAnswer(inv -> "Provider error: " + inv.getArgument(1, Object[].class)[0]);

    sMsgStatic.when(() -> OBMessageUtils.messageBD("ETPP_AllPrintJobsFailed"))
        .thenReturn(MSG_ALL_FAILED);

    // Stubs for success, download, and partial-failure flows
    sMsgStatic.when(() -> OBMessageUtils.getI18NMessage("ETPP_PrintJobSent"))
        .thenReturn(MSG_PRINT_JOB_SENT);
    sMsgStatic.when(() -> OBMessageUtils.getI18NMessage("ETPP_SomePrintJobsFailed"))
        .thenReturn(MSG_SOME_FAILED);
    sMsgStatic.when(() -> OBMessageUtils.getI18NMessage("ETPP_PrintFailedDownloadReady"))
        .thenReturn(MSG_PRINT_FAILED_DOWNLOAD);
    sMsgStatic.when(() -> OBMessageUtils.getI18NMessage("ETPP_DownloadFailed"))
        .thenReturn(MSG_DOWNLOAD_FAILED);
    sMsgStatic.when(() -> OBMessageUtils.messageBD("Success"))
        .thenReturn("Success");
    sMsgStatic.when(() -> OBMessageUtils.messageBD("Warning"))
        .thenReturn("Warning");

    // Stubs for download-only mode messages
    sMsgStatic.when(() -> OBMessageUtils.getI18NMessage("ETPP_DownloadOnlySuccess"))
        .thenReturn(MSG_DOWNLOAD_ONLY_SUCCESS);
    sMsgStatic.when(() -> OBMessageUtils.getI18NMessage("ETPP_AllGenerationsFailed"))
        .thenReturn(MSG_ALL_GEN_FAILED);
    sMsgStatic.when(() -> OBMessageUtils.getI18NMessage("ETPP_SomeGenerationsFailed"))
        .thenReturn(MSG_SOME_GEN_FAILED);
    sMsgStatic.when(() -> OBMessageUtils.getI18NMessage("ETPP_NoJobsToDownloadOrSend"))
        .thenReturn(MSG_NO_JOBS);
  }

  /**
   * Stubs out OBContext static methods used by the code under test.
   * Prevents actual admin mode changes during tests.
   */
  private void stubOBContext(MockedStatic<OBContext> sObcStatic) {
    // Make admin mode methods no-ops for test isolation
    sObcStatic.when(OBContext::setAdminMode).then(inv -> null);
    sObcStatic.when(OBContext::restorePreviousMode).then(inv -> null);
  }

  /**
   * Sets up the test harness before each test.
   * Initializes mocks and stubs for all dependencies.
   */
  @BeforeEach
  void setUp() {
    // Create spy of the action to allow overriding createResponseBuilder / mergeLabels
    action = Mockito.spy(new SendGeneratedLabelToPrinter());

    // Initialize static mocks for dependencies
    sMsg = Mockito.mockStatic(OBMessageUtils.class);
    sObc = Mockito.mockStatic(OBContext.class);
    sDal = Mockito.mockStatic(OBDal.class);
    sResolver = Mockito.mockStatic(ProviderStrategyResolver.class);
    sUtil = Mockito.mockStatic(PrinterUtils.class, Mockito.CALLS_REAL_METHODS);

    // Stub i18n and context methods for predictable test behavior
    stubI18N(sMsg);
    stubOBContext(sObc);

    // ResponseActionsBuilder mock with fluent chaining
    ResponseActionsBuilder responseBuilder = Mockito.mock(ResponseActionsBuilder.class);
    Mockito.when(responseBuilder.showMsgInProcessView(
            any(ResponseActionsBuilder.MessageType.class), anyString(), anyString()))
        .thenReturn(responseBuilder);
    Mockito.when(responseBuilder.addCustomResponseAction(anyString(), any(JSONObject.class)))
        .thenReturn(responseBuilder);
    Mockito.doReturn(responseBuilder).when(action).createResponseBuilder();
    Mockito.doReturn(tmpDir.getAbsolutePath()).when(action).getReportingTempFolder();

    // Mock DAL and domain objects
    dal = Mockito.mock(OBDal.class);
    sDal.when(OBDal::getInstance).thenReturn(dal);

    provider = Mockito.mock(Provider.class);
    Mockito.when(dal.get(Provider.class, PROV_OK)).thenReturn(provider);

    Printer printer = Mockito.mock(Printer.class);
    Mockito.when(dal.get(Printer.class, PRN_OK)).thenReturn(printer);

    // Mock criteria for table lookup
    @SuppressWarnings("unchecked")
    OBCriteria<Table> tmpCrit = Mockito.mock(OBCriteria.class);
    tableCrit = tmpCrit;
    Mockito.when(dal.createCriteria(Table.class)).thenReturn(tableCrit);
    Mockito.when(tableCrit.add(any(Criterion.class))).thenReturn(tableCrit);
    Mockito.when(tableCrit.setMaxResults(anyInt())).thenReturn(tableCrit);

    table = Mockito.mock(Table.class);
    Mockito.when(tableCrit.uniqueResult()).thenReturn(table);

    // Mock template line resolution
    tLine = Mockito.mock(TemplateLine.class);
    sUtil.when(() -> PrinterUtils.resolveTemplateLineFor(any(Table.class))).thenReturn(tLine);

    // Mock provider strategy resolution
    strategy = Mockito.mock(PrintProviderStrategy.class);
    sResolver.when(() -> ProviderStrategyResolver.resolveForProvider(provider)).thenReturn(strategy);
    sResolver.when(ProviderStrategyResolver::resolveDefault).thenReturn(strategy);
  }

  /**
   * Cleans up static mocks after each test to avoid interference between tests.
   */
  @AfterEach
  void tearDown() {
    // Close all static mocks to release resources (null-safe to prevent cascading)
    safeClose(sUtil);
    safeClose(sResolver);
    safeClose(sDal);
    safeClose(sObc);
    safeClose(sMsg);
  }

  private static void safeClose(MockedStatic<?> mock) {
    if (mock != null) {
      try {
        mock.close();
      } catch (Exception ignored) {
        // already closed or never opened
      }
    }
  }

  /**
   * Creates a temporary file in {@code tmpDir} that acts as a generated label.
   */
  private File createTempLabel(String name) throws IOException {
    File f = new File(tmpDir, name + ".pdf");
    Files.write(f.toPath(), "fake-pdf-content".getBytes(StandardCharsets.UTF_8));
    return f;
  }

  /**
   * When the provider parameter is absent, the action operates in download-only
   * mode: generates labels using the default strategy and offers them for download.
   */
  @Test
  void actionDownloadOnlyWhenProviderMissing() throws Exception {
    JSONObject params = baseParams();
    params.remove(PrinterUtils.PROVIDER);
    params.put(PrinterUtils.DOWNLOAD_LABEL, TRUE);

    File labelFile = createTempLabel("dl-only-no-prov");
    Mockito.when(strategy.generateLabel(
            eq(null), eq(table), eq(REC_1), eq(tLine), any(JSONObject.class)))
        .thenReturn(labelFile);

    ActionResult res = action.action(params, new MutableBoolean(false));

    assertThat(res.getType(), is(Result.Type.SUCCESS));
    assertThat(res.getMessage(), is(MSG_DOWNLOAD_ONLY_SUCCESS));
    assertThat(res.getResponseActionsBuilder(), is(notNullValue()));
  }

  /**
   * When neither provider nor download are enabled there is nothing to do,
   * so the action returns WARNING with {@code ETPP_NoJobsToDownloadOrSend}.
   */
  @Test
  void actionNoProviderNoDownloadReturnsWarning() throws Exception {
    JSONObject params = baseParams();
    params.remove(PrinterUtils.PROVIDER);
    // download is already "false" from baseParams

    ActionResult res = action.action(params, new MutableBoolean(false));

    assertThat(res.getType(), is(Result.Type.WARNING));
    assertThat(res.getMessage(), is(MSG_NO_JOBS));
  }

  /**
   * Tests that the action throws an error if the provider is not found.
   * Ensures error message is returned when provider lookup fails.
   */
  @Test
  void actionErrorProviderNotFound() throws Exception {
    // Set provider to a value that does not exist
    JSONObject params = baseParams();
    params.put(PrinterUtils.PROVIDER, PROV_MISSING);

    Mockito.when(dal.get(Provider.class, PROV_MISSING)).thenReturn(null);

    // Execute action and verify error result
    ActionResult res = action.action(params, new MutableBoolean(false));
    assertThat(res.getType(), is(Result.Type.ERROR));
    assertThat(res.getMessage(), is(MSG_PROVIDER_NOT_FOUND));
  }

  /**
   * Tests that the action throws an error if the printer is not found.
   * Ensures error message is returned when printer lookup fails.
   */
  @Test
  void actionErrorPrinterNotFound() throws Exception {
    // Set printer to a value that does not exist
    JSONObject params = baseParams();
    params.put(PrinterUtils.PRINTERS, PRN_MISSING);

    Mockito.when(dal.get(Printer.class, PRN_MISSING)).thenReturn(null);

    // Execute action and verify error result
    ActionResult res = action.action(params, new MutableBoolean(false));
    assertThat(res.getType(), is(Result.Type.ERROR));
    assertThat(res.getMessage(), is(String.format(MSG_PRINTER_NOT_FOUND_FMT, PRN_MISSING)));
  }

  /**
   * Verifies that the action throws an error if the table is not found.
   * Ensures error message is returned when table lookup fails.
   */
  @Test
  void actionErrorTableNotFound() throws Exception {
    // Simulate table lookup returning null
    JSONObject params = baseParams();

    Mockito.when(tableCrit.uniqueResult()).thenReturn(null);

    // Execute action and verify error result
    ActionResult res = action.action(params, new MutableBoolean(false));
    assertThat(res.getType(), is(Result.Type.ERROR));
    assertThat(res.getMessage(), is(String.format(MSG_TABLE_NOT_FOUND_FMT, ENTITY)));
  }

  /**
   * Verifies that the action throws an error if the strategy throws PrintProviderException.
   * Ensures rollback and proper error message when label generation fails.
   */
  @Test
  void actionErrorStrategyThrowsPrintProviderExceptionAndRollsBack() throws Exception {
    // Simulate strategy throwing an exception during label generation
    JSONObject params = baseParams();

    Mockito.when(strategy.generateLabel(
            eq(provider),
            eq(table),
            anyString(),
            eq(tLine),
            any(JSONObject.class)))
        .thenThrow(new PrintProviderException("boom"));

    // Execute action and verify error result
    ActionResult res = action.action(params, new MutableBoolean(false));

    assertThat(res.getType(), is(Result.Type.ERROR));
    assertThat(res.getMessage(), is(MSG_ALL_FAILED));
  }

  // ─── Happy-path tests ──────────────────────────────────────────────────

  /**
   * Single record printed successfully with download disabled.
   */
  @Test
  void actionSuccessfulPrintSingleRecordNoDownload() throws Exception {
    JSONObject params = baseParams();

    File labelFile = createTempLabel("success-1");
    Mockito.when(strategy.generateLabel(
            eq(provider), eq(table), eq(REC_1), eq(tLine), any(JSONObject.class)))
        .thenReturn(labelFile);
    Mockito.when(strategy.sendToPrinter(
            eq(provider), any(Printer.class), eq(1), any(File.class)))
        .thenReturn("JOB-100");

    ActionResult res = action.action(params, new MutableBoolean(false));

    assertThat(res.getType(), is(Result.Type.SUCCESS));
    assertThat(res.getMessage(), containsString("JOB-100"));
  }

  /**
   * Multiple records printed successfully, message contains all job IDs.
   */
  @Test
  void actionSuccessfulPrintMultipleRecordsNoDownload() throws Exception {
    JSONObject params = baseParams();
    params.put(PrinterUtils.RECORDS, new JSONArray().put(REC_1).put(REC_2));

    File label1 = createTempLabel("multi-1");
    File label2 = createTempLabel("multi-2");
    Mockito.when(strategy.generateLabel(
            eq(provider), eq(table), eq(REC_1), eq(tLine), any(JSONObject.class)))
        .thenReturn(label1);
    Mockito.when(strategy.generateLabel(
            eq(provider), eq(table), eq(REC_2), eq(tLine), any(JSONObject.class)))
        .thenReturn(label2);
    Mockito.when(strategy.sendToPrinter(
            eq(provider), any(Printer.class), eq(1), any(File.class)))
        .thenReturn("JOB-A", "JOB-B");

    ActionResult res = action.action(params, new MutableBoolean(false));

    assertThat(res.getType(), is(Result.Type.SUCCESS));
    assertThat(res.getMessage(), containsString("JOB-A"));
    assertThat(res.getMessage(), containsString("JOB-B"));
  }

  /**
   * Single record printed + download enabled. buildDownloadResponse is invoked
   * and the result carries a {@link ResponseActionsBuilder}.
   */
  @Test
  void actionSuccessfulPrintWithDownload() throws Exception {
    JSONObject params = baseParams();
    params.put(PrinterUtils.DOWNLOAD_LABEL, TRUE);

    File labelFile = createTempLabel("dl-1");
    Mockito.when(strategy.generateLabel(
            eq(provider), eq(table), eq(REC_1), eq(tLine), any(JSONObject.class)))
        .thenReturn(labelFile);
    Mockito.when(strategy.sendToPrinter(
            eq(provider), any(Printer.class), eq(1), any(File.class)))
        .thenReturn("JOB-DL");

    ActionResult res = action.action(params, new MutableBoolean(false));

    assertThat(res.getType(), is(Result.Type.SUCCESS));
    assertThat(res.getResponseActionsBuilder(), is(notNullValue()));
  }

  /**
   * When {@code downloadlabel} is absent the default is {@code "Y"}, so
   * download should still be triggered.
   */
  @Test
  void actionDownloadDefaultIsEnabled() throws Exception {
    JSONObject params = baseParams();
    params.remove(PrinterUtils.DOWNLOAD_LABEL);

    File labelFile = createTempLabel("default-dl");
    Mockito.when(strategy.generateLabel(
            eq(provider), eq(table), eq(REC_1), eq(tLine), any(JSONObject.class)))
        .thenReturn(labelFile);
    Mockito.when(strategy.sendToPrinter(
            eq(provider), any(Printer.class), eq(1), any(File.class)))
        .thenReturn("JOB-DEF");

    ActionResult res = action.action(params, new MutableBoolean(false));

    assertThat(res.getType(), is(Result.Type.SUCCESS));
    assertThat(res.getResponseActionsBuilder(), is(notNullValue()));
  }

  // ─── All-failed tests ──────────────────────────────────────────────────

  /**
   * All records fail with download disabled → ERROR.
   */
  @Test
  void actionAllFailedNoDownloadReturnsError() throws Exception {
    JSONObject params = baseParams();

    Mockito.when(strategy.generateLabel(
            eq(provider), eq(table), anyString(), eq(tLine), any(JSONObject.class)))
        .thenThrow(new RuntimeException("gen fail"));

    ActionResult res = action.action(params, new MutableBoolean(false));

    assertThat(res.getType(), is(Result.Type.ERROR));
    assertThat(res.getMessage(), is(MSG_ALL_FAILED));
  }

  /**
   * All records' print jobs fail but labels are generated, download=Y →
   * WARNING with download response and {@code ETPP_PrintFailedDownloadReady}.
   */
  @Test
  void actionAllFailedDownloadEnabledWithLabelsReturnsWarningWithDownload() throws Exception {
    JSONObject params = baseParams();
    params.put(PrinterUtils.DOWNLOAD_LABEL, TRUE);

    File labelFile = createTempLabel("fail-dl-1");
    Mockito.when(strategy.generateLabel(
            eq(provider), eq(table), eq(REC_1), eq(tLine), any(JSONObject.class)))
        .thenReturn(labelFile);
    Mockito.when(strategy.sendToPrinter(
            eq(provider), any(Printer.class), eq(1), any(File.class)))
        .thenThrow(new RuntimeException("Printer offline"));

    ActionResult res = action.action(params, new MutableBoolean(false));

    assertThat(res.getType(), is(Result.Type.WARNING));
    assertThat(res.getMessage(), is(MSG_PRINT_FAILED_DOWNLOAD));
    assertThat(res.getResponseActionsBuilder(), is(notNullValue()));
  }

  /**
   * All records fail, download=Y but generateLabel returns null (no labels
   * to download) → ERROR without download.
   */
  @Test
  void actionAllFailedDownloadEnabledNoLabelsReturnsError() throws Exception {
    JSONObject params = baseParams();
    params.put(PrinterUtils.DOWNLOAD_LABEL, TRUE);

    Mockito.when(strategy.generateLabel(
            eq(provider), eq(table), eq(REC_1), eq(tLine), any(JSONObject.class)))
        .thenReturn(null);
    Mockito.when(strategy.sendToPrinter(
            eq(provider), any(Printer.class), eq(1), nullable(File.class)))
        .thenThrow(new RuntimeException("No label"));

    ActionResult res = action.action(params, new MutableBoolean(false));

    assertThat(res.getType(), is(Result.Type.ERROR));
    assertThat(res.getMessage(), is(MSG_ALL_FAILED));
  }

  // ─── Partial-failure tests ──────────────────────────────────────────────

  /**
   * Two records: first succeeds, second fails. Download disabled → WARNING.
   */
  @Test
  void actionSomeFailedNoDownloadReturnsWarning() throws Exception {
    JSONObject params = baseParams();
    params.put(PrinterUtils.RECORDS, new JSONArray().put(REC_1).put(REC_2));

    File label1 = createTempLabel("partial-1");
    Mockito.when(strategy.generateLabel(
            eq(provider), eq(table), eq(REC_1), eq(tLine), any(JSONObject.class)))
        .thenReturn(label1);
    Mockito.when(strategy.generateLabel(
            eq(provider), eq(table), eq(REC_2), eq(tLine), any(JSONObject.class)))
        .thenThrow(new RuntimeException("gen fail for rec-2"));
    Mockito.when(strategy.sendToPrinter(
            eq(provider), any(Printer.class), eq(1), any(File.class)))
        .thenReturn("JOB-PARTIAL");

    ActionResult res = action.action(params, new MutableBoolean(false));

    assertThat(res.getType(), is(Result.Type.WARNING));
    assertThat(res.getMessage(), containsString("1 print jobs failed"));
  }

  /**
   * Two records: both labels generated, second sendToPrinter fails.
   * Download=Y → WARNING with download.
   */
  @Test
  void actionSomeFailedWithDownloadReturnsWarningWithDownload() throws Exception {
    JSONObject params = baseParams();
    params.put(PrinterUtils.DOWNLOAD_LABEL, TRUE);
    params.put(PrinterUtils.RECORDS, new JSONArray().put(REC_1).put(REC_2));

    File label1 = createTempLabel("part-dl-1");
    File label2 = createTempLabel("part-dl-2");
    Mockito.when(strategy.generateLabel(
            eq(provider), eq(table), eq(REC_1), eq(tLine), any(JSONObject.class)))
        .thenReturn(label1);
    Mockito.when(strategy.generateLabel(
            eq(provider), eq(table), eq(REC_2), eq(tLine), any(JSONObject.class)))
        .thenReturn(label2);
    Mockito.when(strategy.sendToPrinter(
            eq(provider), any(Printer.class), eq(1), any(File.class)))
        .thenReturn("JOB-OK")
        .thenThrow(new RuntimeException("Printer jam"));

    // Override mergeLabels to avoid iText real-PDF dependency
    Mockito.doReturn(label1).when(action).mergeLabels(any(List.class));

    ActionResult res = action.action(params, new MutableBoolean(false));

    assertThat(res.getType(), is(Result.Type.WARNING));
    assertThat(res.getMessage(), is(MSG_PRINT_FAILED_DOWNLOAD));
    assertThat(res.getResponseActionsBuilder(), is(notNullValue()));
  }

  // ─── Outer exception catches ──────────────────────────────────────────

  /**
   * PrintProviderException thrown OUTSIDE the inner loop (e.g. strategy
   * resolution) → outer catch → ERROR + rollback.
   */
  @Test
  void actionPrintProviderExceptionOuterCatchRollsBack() throws Exception {
    JSONObject params = baseParams();

    sResolver.when(() -> ProviderStrategyResolver.resolveForProvider(provider))
        .thenThrow(new PrintProviderException("strategy init failed"));

    ActionResult res = action.action(params, new MutableBoolean(false));

    assertThat(res.getType(), is(Result.Type.ERROR));
    assertThat(res.getMessage(), containsString("strategy init failed"));
    Mockito.verify(dal).rollbackAndClose();
  }

  /**
   * Generic RuntimeException thrown outside the loop → outer catch → ERROR + rollback.
   */
  @Test
  void actionGenericExceptionOuterCatchRollsBack() throws Exception {
    JSONObject params = baseParams();

    sResolver.when(() -> ProviderStrategyResolver.resolveForProvider(provider))
        .thenThrow(new RuntimeException("unexpected error"));

    ActionResult res = action.action(params, new MutableBoolean(false));

    assertThat(res.getType(), is(Result.Type.ERROR));
    assertThat(res.getMessage(), is("unexpected error"));
    Mockito.verify(dal).rollbackAndClose();
  }

  // ─── Edge cases ──────────────────────────────────────────────────────

  /**
   * sendToPrinter returns blank → StringUtils.defaultIfBlank yields "-".
   */
  @Test
  void actionSendToPrinterReturnsBlankUsesHyphen() throws Exception {
    JSONObject params = baseParams();

    File labelFile = createTempLabel("blank-job");
    Mockito.when(strategy.generateLabel(
            eq(provider), eq(table), eq(REC_1), eq(tLine), any(JSONObject.class)))
        .thenReturn(labelFile);
    Mockito.when(strategy.sendToPrinter(
            eq(provider), any(Printer.class), eq(1), any(File.class)))
        .thenReturn("");

    ActionResult res = action.action(params, new MutableBoolean(false));

    assertThat(res.getType(), is(Result.Type.SUCCESS));
    assertThat(res.getMessage(), containsString("-"));
  }

  /**
   * sendToPrinter returns null → StringUtils.defaultIfBlank yields "-".
   */
  @Test
  void actionSendToPrinterReturnsNullUsesHyphen() throws Exception {
    JSONObject params = baseParams();

    File labelFile = createTempLabel("null-job");
    Mockito.when(strategy.generateLabel(
            eq(provider), eq(table), eq(REC_1), eq(tLine), any(JSONObject.class)))
        .thenReturn(labelFile);
    Mockito.when(strategy.sendToPrinter(
            eq(provider), any(Printer.class), eq(1), any(File.class)))
        .thenReturn(null);

    ActionResult res = action.action(params, new MutableBoolean(false));

    assertThat(res.getType(), is(Result.Type.SUCCESS));
    assertThat(res.getMessage(), containsString("-"));
  }

  /**
   * generateLabel returns null with download=Y → label is NOT retained,
   * so no download even though it is enabled.
   */
  @Test
  void actionLabelNullNotRetainedForDownload() throws Exception {
    JSONObject params = baseParams();
    params.put(PrinterUtils.DOWNLOAD_LABEL, TRUE);

    Mockito.when(strategy.generateLabel(
            eq(provider), eq(table), eq(REC_1), eq(tLine), any(JSONObject.class)))
        .thenReturn(null);
    Mockito.when(strategy.sendToPrinter(
            eq(provider), any(Printer.class), eq(1), nullable(File.class)))
        .thenReturn("JOB-NL");

    ActionResult res = action.action(params, new MutableBoolean(false));

    // No download because downloadableLabels is empty
    assertThat(res.getType(), is(Result.Type.SUCCESS));
    assertThat(res.getMessage(), containsString("JOB-NL"));
  }

  // ─── Missing parameter tests ──────────────────────────────────────────

  /**
   * Missing entityName parameter → ERROR.
   */
  @Test
  void actionErrorMissingEntityNameParam() throws Exception {
    JSONObject params = baseParams();
    params.remove(PrinterUtils.ENTITY_NAME);

    ActionResult res = action.action(params, new MutableBoolean(false));

    assertThat(res.getType(), is(Result.Type.ERROR));
    assertThat(res.getMessage(), containsString(PrinterUtils.ENTITY_NAME));
  }

  /**
   * Missing records parameter → ERROR.
   */
  @Test
  void actionErrorMissingRecordsParam() throws Exception {
    JSONObject params = baseParams();
    params.remove(PrinterUtils.RECORDS);

    ActionResult res = action.action(params, new MutableBoolean(false));

    assertThat(res.getType(), is(Result.Type.ERROR));
    assertThat(res.getMessage(), containsString(PrinterUtils.RECORDS));
  }

  /**
   * When provider is set but printers is absent, the action operates in
   * download-only mode using the provider's strategy.
   */
  @Test
  void actionDownloadOnlyWhenPrinterMissing() throws Exception {
    JSONObject params = baseParams();
    params.remove(PrinterUtils.PRINTERS);
    params.put(PrinterUtils.DOWNLOAD_LABEL, TRUE);

    File labelFile = createTempLabel("dl-only-no-prn");
    Mockito.when(strategy.generateLabel(
            eq(provider), eq(table), eq(REC_1), eq(tLine), any(JSONObject.class)))
        .thenReturn(labelFile);

    ActionResult res = action.action(params, new MutableBoolean(false));

    assertThat(res.getType(), is(Result.Type.SUCCESS));
    assertThat(res.getMessage(), is(MSG_DOWNLOAD_ONLY_SUCCESS));
    assertThat(res.getResponseActionsBuilder(), is(notNullValue()));
  }

  /**
   * Missing numberOfCopies parameter → ERROR.
   */
  @Test
  void actionErrorMissingNumberOfCopiesParam() throws Exception {
    JSONObject params = baseParams();
    params.remove(PrinterUtils.NUMBER_OF_COPIES);

    ActionResult res = action.action(params, new MutableBoolean(false));

    assertThat(res.getType(), is(Result.Type.ERROR));
    assertThat(res.getMessage(), containsString(PrinterUtils.NUMBER_OF_COPIES));
  }

  // ─── buildDownloadResponse error path ──────────────────────────────────

  /**
   * When mergeLabels throws inside buildDownloadResponse, the catch block
   * returns WARNING with the original message + ETPP_DownloadFailed.
   */
  @Test
  void actionDownloadBuildFailureReturnsWarningWithDownloadFailed() throws Exception {
    JSONObject params = baseParams();
    params.put(PrinterUtils.DOWNLOAD_LABEL, TRUE);

    File labelFile = createTempLabel("build-fail");
    Mockito.when(strategy.generateLabel(
            eq(provider), eq(table), eq(REC_1), eq(tLine), any(JSONObject.class)))
        .thenReturn(labelFile);
    Mockito.when(strategy.sendToPrinter(
            eq(provider), any(Printer.class), eq(1), any(File.class)))
        .thenReturn("JOB-BF");

    // Force mergeLabels to throw so buildDownloadResponse enters the catch
    Mockito.doThrow(new IOException("merge crash"))
        .when(action).mergeLabels(any(List.class));

    ActionResult res = action.action(params, new MutableBoolean(false));

    assertThat(res.getType(), is(Result.Type.WARNING));
    assertThat(res.getMessage(), containsString(MSG_DOWNLOAD_FAILED));
  }

  // ─── Download-only mode tests ──────────────────────────────────────────

  /**
   * Download-only mode (no provider): all label generations fail → ERROR.
   */
  @Test
  void actionDownloadOnlyAllGenerationsFailed() throws Exception {
    JSONObject params = baseParams();
    params.remove(PrinterUtils.PROVIDER);
    params.put(PrinterUtils.DOWNLOAD_LABEL, TRUE);

    Mockito.when(strategy.generateLabel(
            eq(null), eq(table), eq(REC_1), eq(tLine), any(JSONObject.class)))
        .thenThrow(new RuntimeException("gen fail"));

    ActionResult res = action.action(params, new MutableBoolean(false));

    assertThat(res.getType(), is(Result.Type.ERROR));
    assertThat(res.getMessage(), is(MSG_ALL_GEN_FAILED));
  }

  /**
   * Download-only mode (no provider): two records, second generation fails →
   * WARNING with partial download.
   */
  @Test
  void actionDownloadOnlySomeGenerationsFailed() throws Exception {
    JSONObject params = baseParams();
    params.remove(PrinterUtils.PROVIDER);
    params.put(PrinterUtils.DOWNLOAD_LABEL, TRUE);
    params.put(PrinterUtils.RECORDS, new JSONArray().put(REC_1).put(REC_2));

    File label1 = createTempLabel("dl-partial-1");
    Mockito.when(strategy.generateLabel(
            eq(null), eq(table), eq(REC_1), eq(tLine), any(JSONObject.class)))
        .thenReturn(label1);
    Mockito.when(strategy.generateLabel(
            eq(null), eq(table), eq(REC_2), eq(tLine), any(JSONObject.class)))
        .thenThrow(new RuntimeException("gen fail for rec-2"));

    ActionResult res = action.action(params, new MutableBoolean(false));

    assertThat(res.getType(), is(Result.Type.WARNING));
    assertThat(res.getMessage(), containsString("1 label(s) failed"));
    assertThat(res.getResponseActionsBuilder(), is(notNullValue()));
  }

  // ─── mergeLabels direct test ──────────────────────────────────────────

  /**
   * When mergeLabels receives a single-element list it returns that same file.
   */
  @Test
  void mergeLabelsReturnsFileWhenSingle() throws IOException {
    File single = createTempLabel("single");
    File result = action.mergeLabels(Collections.singletonList(single));
    assertThat(result, is(single));
  }

  // ─── getInputClass ────────────────────────────────────────────────────

  /**
   * Verifies that getInputClass returns BaseOBObject.
   */
  @Test
  void getInputClassReturnsBaseOBObject() {
    assertThat(action.getInputClass(), equalTo(BaseOBObject.class));
  }
}