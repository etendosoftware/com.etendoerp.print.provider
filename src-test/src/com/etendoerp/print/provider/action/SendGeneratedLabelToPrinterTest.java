package com.etendoerp.print.provider.action;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Criterion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
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

public class SendGeneratedLabelToPrinterTest {

  // Constants used for test scenarios
  private static final String PROV_OK = "prov-ok";
  private static final String PROV_MISSING = "prov-missing";
  private static final String PRN_OK = "p-ok";
  private static final String PRN_MISSING = "p-missing";
  private static final String ENTITY = "M_InOut";
  private static final String REC_1 = "rec-1";

  // Message templates for assertions
  private static final String MSG_MISSING_PARAM = "Missing parameter: %s";
  private static final String MSG_PROVIDER_NOT_FOUND = "Provider not found";
  private static final String MSG_PRINTER_NOT_FOUND_FMT = "Printer %s not found";
  private static final String MSG_TABLE_NOT_FOUND_FMT = "Table %s not found";
  private static final String MSG_PROVIDER_ERROR_FMT = "Provider error: %s";
  private static final String MSG_ALL_FAILED = "All print jobs failed";

  // Static mocks for dependencies
  private MockedStatic<OBMessageUtils> sMsg;
  private MockedStatic<OBContext> sObc;
  private MockedStatic<OBDal> sDal;
  private MockedStatic<ProviderStrategyResolver> sResolver;
  private MockedStatic<PrinterUtils> sUtil;

  // Mocks for DAL and domain objects
  private OBDal dal;
  private Provider provider;
  private Printer printer;
  private OBCriteria<Table> tableCrit;
  private Table table;
  private TemplateLine tLine;
  private PrintProviderStrategy strategy;

  // Instance of the action under test
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
    sMsgStatic.when(() -> OBMessageUtils.getI18NMessage("ETPP_PrinterNotFound"))
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
    sMsgStatic.when(() -> OBMessageUtils.getI18NMessage(eq("ETPP_PrinterNotFound"), Mockito.any()))
        .thenAnswer(inv -> "Printer " + inv.getArgument(1, Object[].class)[0] + " not found");
    sMsgStatic.when(() -> OBMessageUtils.getI18NMessage(eq("ETPP_TableNotFound"), Mockito.any()))
        .thenAnswer(inv -> "Table " + inv.getArgument(1, Object[].class)[0] + " not found");
    sMsgStatic.when(() -> OBMessageUtils.getI18NMessage(eq("ETPP_ProviderError"), Mockito.any()))
        .thenAnswer(inv -> "Provider error: " + inv.getArgument(1, Object[].class)[0]);

    sMsgStatic.when(() -> OBMessageUtils.messageBD("ETPP_AllPrintJobsFailed"))
        .thenReturn(MSG_ALL_FAILED);
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
    // Create instance of the action to be tested
    action = new SendGeneratedLabelToPrinter();

    // Initialize static mocks for dependencies
    sMsg = Mockito.mockStatic(OBMessageUtils.class);
    sObc = Mockito.mockStatic(OBContext.class);
    sDal = Mockito.mockStatic(OBDal.class);
    sResolver = Mockito.mockStatic(ProviderStrategyResolver.class);
    sUtil = Mockito.mockStatic(PrinterUtils.class, Mockito.CALLS_REAL_METHODS);

    // Stub i18n and context methods for predictable test behavior
    stubI18N(sMsg);
    stubOBContext(sObc);

    // Mock DAL and domain objects
    dal = Mockito.mock(OBDal.class);
    sDal.when(OBDal::getInstance).thenReturn(dal);

    provider = Mockito.mock(Provider.class);
    Mockito.when(dal.get(Provider.class, PROV_OK)).thenReturn(provider);

    printer = Mockito.mock(Printer.class);
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
  }

  /**
   * Cleans up static mocks after each test to avoid interference between tests.
   */
  @AfterEach
  void tearDown() {
    // Close all static mocks to release resources
    sUtil.close();
    sResolver.close();
    sDal.close();
    sObc.close();
    sMsg.close();
  }

  /**
   * Verifies that the action throws an error if the provider parameter is missing.
   * Ensures proper error handling for missing required parameters.
   */
  @Test
  void actionErrorMissingProviderParam() throws Exception {
    // Remove provider parameter to simulate missing input
    JSONObject params = baseParams();
    params.remove(PrinterUtils.PROVIDER);

    // Execute action and verify error result
    ActionResult res = action.action(params, new MutableBoolean(false));
    assertThat(res.getType(), is(Result.Type.ERROR));
    assertThat(res.getMessage(), is("Missing parameter: Provider"));
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
}