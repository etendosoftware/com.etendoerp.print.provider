package com.etendoerp.print.provider.action;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.print.provider.api.PrintProviderException;
import com.etendoerp.print.provider.api.PrinterDTO;
import com.etendoerp.print.provider.data.Printer;
import com.etendoerp.print.provider.data.Provider;
import com.etendoerp.print.provider.strategy.PrintProviderStrategy;
import com.etendoerp.print.provider.utils.PrinterUtils;
import com.etendoerp.print.provider.utils.ProviderStrategyResolver;
import com.smf.jobs.ActionResult;
import com.smf.jobs.Result;

/**
 * Unit tests for the UpdatePrinters action.
 * <p>
 * This class verifies the correct behavior of printer creation, update, inactivation,
 * error handling, and message generation when synchronizing printers with a provider.
 * It uses Mockito to mock dependencies and static methods, and JUnit 5 for test execution.
 */
class UpdatePrintersTest {

  // Constants for provider IDs and message keys
  private static final String PROV_1 = "prov-1";
  private static final String PROV_X = "prov-x";

  // Message keys used for internationalization
  private static final String MSG_MISSING_PARAM_KEY = PrinterUtils.MISSING_PARAMETER_MSG;
  private static final String MSG_PROVIDER_NOT_FOUND_KEY = PrinterUtils.PROVIDER_NOT_FOUND_MSG;
  private static final String MSG_PROVIDER_ERROR_KEY = "ETPP_ProviderError";
  private static final String MSG_PRINTERS_UPDATED_KEY = "ETPP_PrintersUpdated";

  // Message templates for test assertions
  private static final String MSG_MISSING_PARAM = "Missing parameter: %s";
  private static final String MSG_PROVIDER_NOT_FOUND = "Provider not found";
  private static final String MSG_PROVIDER_ERROR = "Provider error: %s";
  private static final String MSG_PRINTERS_UPDATED = "Printers updated. Created=%d, Updated=%d, Inactivated=%d";

  // Test objects and mocks
  private UpdatePrinters action;

  private MockedStatic<OBMessageUtils> sMsg;
  private MockedStatic<OBContext> sCtx;
  private MockedStatic<OBDal> sDal;
  private MockedStatic<ProviderStrategyResolver> sResolver;
  private MockedStatic<OBProvider> sOBProv;
  private MockedStatic<PrinterUtils> sUtils;

  private OBDal obdal;
  private OBProvider obProvider;
  private Provider provider;
  private PrintProviderStrategy strategy;

  /**
   * Sets up the test environment before each test.
   * Initializes mocks and stubs required static methods.
   */
  @BeforeEach
  void setUp() {
    action = new UpdatePrinters();

    // Mock static methods for dependencies
    sMsg = Mockito.mockStatic(OBMessageUtils.class);
    sCtx = Mockito.mockStatic(OBContext.class);
    sDal = Mockito.mockStatic(OBDal.class);
    sResolver = Mockito.mockStatic(ProviderStrategyResolver.class);
    sOBProv = Mockito.mockStatic(OBProvider.class);
    sUtils = Mockito.mockStatic(PrinterUtils.class, Mockito.CALLS_REAL_METHODS);

    // Stub admin mode and I18N messages
    stubOBContextNoOp(sCtx);
    stubI18N(sMsg);

    // Create mocks for DAL and provider objects
    obdal = mock(OBDal.class);
    obProvider = mock(OBProvider.class);
    provider = mock(Provider.class);
    strategy = mock(PrintProviderStrategy.class);

    // Configure static mocks to return test objects
    sDal.when(OBDal::getInstance).thenReturn(obdal);
    sOBProv.when(OBProvider::getInstance).thenReturn(obProvider);
    sResolver.when(() -> ProviderStrategyResolver.resolveForProvider(eq(provider))).thenReturn(strategy);

    // Configure provider resolution and error simulation
    sUtils.when(() -> PrinterUtils.requireProvider(PROV_1)).thenReturn(provider);
    sUtils.when(() -> PrinterUtils.requireProvider(PROV_X)).thenThrow(new OBException(MSG_PROVIDER_NOT_FOUND));
  }

  /**
   * Cleans up the test environment after each test.
   * Closes all mocked static resources.
   */
  @AfterEach
  void tearDown() {
    // Close all static mocks to avoid memory leaks
    sUtils.close();
    sOBProv.close();
    sResolver.close();
    sDal.close();
    sCtx.close();
    sMsg.close();
  }

  /**
   * Helper method to create JSON parameters for the provider.
   *
   * @param providerId
   *     The provider ID to include in the parameters.
   * @return JSONObject with provider parameters.
   * @throws JSONException
   *     if JSON construction fails.
   */
  private static JSONObject params(String providerId) throws JSONException {
    // Build a JSON object with the provider parameter
    JSONObject root = new JSONObject();
    JSONObject p = new JSONObject();
    p.put(PrinterUtils.PROVIDER, providerId);
    root.put(PrinterUtils.PARAMS, p);
    return root;
  }

  /**
   * Helper method to create a mocked OBCriteria that returns itself for method chaining.
   *
   * @param <T>
   *     Type of BaseOBObject.
   * @return Mocked OBCriteria instance.
   */
  @SuppressWarnings("unchecked")
  private static <T extends BaseOBObject> OBCriteria<T> criteriaReturnsSelf() {
    // Return a mock OBCriteria that supports method chaining
    return (OBCriteria<T>) Mockito.mock(
        OBCriteria.class,
        Mockito.withSettings().defaultAnswer(Mockito.RETURNS_SELF));
  }

  /**
   * Stubs OBContext static methods to no-op for admin mode changes.
   *
   * @param sCtx
   *     MockedStatic of OBContext.
   */
  private static void stubOBContextNoOp(MockedStatic<OBContext> sCtx) {
    // Make admin mode methods do nothing for tests
    sCtx.when(() -> OBContext.setAdminMode(anyBoolean())).then(inv -> null);
    sCtx.when(OBContext::restorePreviousMode).then(inv -> null);
  }

  /**
   * Stubs OBMessageUtils static methods for internationalization messages.
   *
   * @param sMsg
   *     MockedStatic of OBMessageUtils.
   */
  private static void stubI18N(MockedStatic<OBMessageUtils> sMsg) {
    // Stub I18N message methods for test messages
    // Without varargs
    sMsg.when(() -> OBMessageUtils.getI18NMessage(MSG_MISSING_PARAM_KEY))
        .thenReturn(MSG_MISSING_PARAM);
    sMsg.when(() -> OBMessageUtils.getI18NMessage(MSG_PROVIDER_NOT_FOUND_KEY))
        .thenReturn(MSG_PROVIDER_NOT_FOUND);
    sMsg.when(() -> OBMessageUtils.getI18NMessage(MSG_PROVIDER_ERROR_KEY))
        .thenReturn(MSG_PROVIDER_ERROR);
    sMsg.when(() -> OBMessageUtils.getI18NMessage(MSG_PRINTERS_UPDATED_KEY))
        .thenReturn(MSG_PRINTERS_UPDATED);

    // With varargs for dynamic messages
    sMsg.when(() -> OBMessageUtils.getI18NMessage(eq(MSG_MISSING_PARAM_KEY), Mockito.any()))
        .thenAnswer(inv -> String.format(MSG_MISSING_PARAM, inv.<Object[]>getArgument(1)[0]));
    sMsg.when(() -> OBMessageUtils.getI18NMessage(eq(MSG_PROVIDER_ERROR_KEY), Mockito.any()))
        .thenAnswer(inv -> String.format(MSG_PROVIDER_ERROR, inv.<Object[]>getArgument(1)[0]));
    sMsg.when(() -> OBMessageUtils.getI18NMessage(eq(MSG_PRINTERS_UPDATED_KEY), Mockito.any()))
        .thenAnswer(inv -> {
          Object[] a = inv.getArgument(1, Object[].class);
          return String.format(MSG_PRINTERS_UPDATED, a[0], a[1], a[2]);
        });
  }

  // =======================
  // Tests
  // =======================

  /**
   * Test that verifies printers are created, updated, inactivated and the correct message is built.
   *
   * @throws Exception
   *     if any error occurs during the test.
   */
  @Test
  void actionSuccessCreatesUpdatesInactivatesAndBuildsMessage() throws Exception {
    // Prepare remote printers and mock strategy response
    List<PrinterDTO> remote = List.of(
        new PrinterDTO("1", "One", true),
        new PrinterDTO("2", "Two", false),
        new PrinterDTO("3", "Three", false)
    );
    when(strategy.fetchPrinters(eq(provider))).thenReturn(remote);

    // Mock criteria for each printer
    OBCriteria<Printer> c1 = criteriaReturnsSelf();
    OBCriteria<Printer> c2 = criteriaReturnsSelf();
    OBCriteria<Printer> c3 = criteriaReturnsSelf();
    OBCriteria<Printer> cInact = criteriaReturnsSelf();
    when(obdal.createCriteria(eq(Printer.class))).thenReturn(c1, c2, c3, cInact);

    // Simulate existing printer found for "1", not found for "2" and "3"
    Printer existing1 = mock(Printer.class);
    when(c1.uniqueResult()).thenReturn(existing1);
    when(c2.uniqueResult()).thenReturn(null);
    when(c3.uniqueResult()).thenReturn(null);

    // Simulate orphan printer to be inactivated
    Printer orphan99 = mock(Printer.class);
    when(orphan99.isActive()).thenReturn(true);
    when(cInact.list()).thenReturn(List.of(orphan99));

    // Mock creation of new printers
    Printer new2 = mock(Printer.class);
    Printer new3 = mock(Printer.class);
    when(obProvider.get(Printer.class)).thenReturn(new2, new3);

    // Execute the action and verify results
    ActionResult res = action.action(params(PROV_1), new MutableBoolean(false));

    // Assert the result type and message
    assertThat(res.getType(), is(Result.Type.SUCCESS));
    assertThat(res.getMessage(), is("Printers updated. Created=2, Updated=1, Inactivated=1"));

    // Verify that printers were saved and flushed
    verify(obdal, times(4)).save(any(Printer.class));
    verify(obdal, times(1)).flush();
    verify(obProvider, times(2)).get(Printer.class);
  }

  /**
   * Test that verifies error handling when provider parameter is missing.
   *
   * @throws Exception
   *     if any error occurs during the test.
   */
  @Test
  void actionErrorMissingProviderParam() throws Exception {
    // Prepare input with missing provider and verify error response
    JSONObject root = new JSONObject()
        .put(PrinterUtils.PARAMS, new JSONObject().put(PrinterUtils.PROVIDER, ""));

    ActionResult res = action.action(root, new MutableBoolean(false));

    // Assert error type and message
    assertThat(res.getType(), is(Result.Type.ERROR));
    assertThat(res.getMessage(), is(String.format(MSG_MISSING_PARAM, PrinterUtils.PROVIDER)));
    verify(obdal, times(1)).rollbackAndClose();
  }

  /**
   * Test that verifies error handling when provider is not found.
   *
   * @throws Exception
   *     if any error occurs during the test.
   */
  @Test
  void actionErrorProviderNotFound() throws Exception {
    // Prepare input with unknown provider and verify error response
    ActionResult res = action.action(params(PROV_X), new MutableBoolean(false));

    // Assert error type and message
    assertThat(res.getType(), is(Result.Type.ERROR));
    assertThat(res.getMessage(), is(MSG_PROVIDER_NOT_FOUND));
    verify(obdal, times(1)).rollbackAndClose();
  }

  /**
   * Test that verifies error handling when strategy throws PrintProviderException.
   *
   * @throws Exception
   *     if any error occurs during the test.
   */
  @Test
  void actionErrorStrategyThrowsPrintProviderExceptionAndRollsBack() throws Exception {
    // Simulate strategy throwing exception and verify error response
    when(strategy.fetchPrinters(eq(provider))).thenThrow(new PrintProviderException("boom"));

    ActionResult res = action.action(params(PROV_1), new MutableBoolean(false));

    // Assert error type and message
    assertThat(res.getType(), is(Result.Type.ERROR));
    assertThat(res.getMessage(), is("Provider error: boom"));
    verify(obdal, times(1)).rollbackAndClose();
  }

  /**
   * Test that verifies getInputClass returns Printer.class.
   */
  @Test
  void getInputClassReturnsPrinter() {
    // Assert that getInputClass returns Printer.class
    assertThat(action.getInputClass(), equalTo(Printer.class));
  }
}