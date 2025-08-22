package com.etendoerp.print.provider.action;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
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

class UpdatePrintersTest {

  private UpdatePrinters action;

  /**
   * Resets the {@code action} field to a new instance of the action class being
   * tested. This is a JUnit 5 {@code @BeforeEach} method.
   */
  @BeforeEach
  void setUp() {
    action = new UpdatePrinters();
  }

  /**
   * Helper to create a JSON object with the expected parameter structure.
   *
   * <p>Creates a JSON object with the following structure:
   * <pre>
   * {
   *   "_params": {
   *     "Provider": &lt;providerId&gt;
   *   }
   * }
   * </pre>
   * </p>
   *
   * @param providerId
   *     provider id to be used in the JSON object
   * @return a JSON object with the expected structure
   * @throws JSONException
   *     if JSON creation fails
   */
  private static JSONObject params(String providerId) throws JSONException {
    JSONObject root = new JSONObject();
    JSONObject p = new JSONObject();
    p.put(PrinterUtils.PROVIDER, providerId);
    root.put(PrinterUtils.PARAMS, p);
    return root;
  }

  /**
   * Creates a mock for an {@link OBCriteria} object that always returns itself
   * (i.e., {@code criteria.add(Restrictions.eq("foo", "bar"))} returns the same
   * mock instance).
   *
   * @param <T>
   *     Entity class
   * @return Mocked {@link OBCriteria} object
   */
  @SuppressWarnings("unchecked")
  private static <T extends BaseOBObject> OBCriteria<T> criteriaMockRETURNS_SELF() {
    return (OBCriteria<T>) Mockito.mock(
        OBCriteria.class,
        Mockito.withSettings().defaultAnswer(Mockito.RETURNS_SELF));
  }

  /**
   * Tests that the action creates new printers, updates existing ones, inactivates
   * orphan printers and generates a message with the number of created, updated
   * and inactivated printers.
   *
   * <p>This test verifies:</p>
   * <ul>
   *   <li>Three printers are returned by the provider: one matching an existing
   *       printer in the DB, two new ones.</li>
   *   <li>One existing printer is not present in the response from the provider
   *       and gets inactivated.</li>
   *   <li>The action calls the {@code save} method on the {@code OBProvider}
   *       instance <em>three times</em> (i.e., one for each printer returned by
   *       the provider).</li>
   *   <li>The action calls the {@code flush} method on the {@code OBDal} instance
   *       once.</li>
   *   <li>The action logs a message with the number of created, updated and
   *       inactivated printers.</li>
   * </ul>
   */
  @Test
  void actionSuccessCreatesUpdatesInactivatesAndBuildsMessage() throws Exception {
    final String providerId = "prov-1";
    JSONObject p = params(providerId);

    Provider provider = mock(Provider.class);

    List<PrinterDTO> remote = List.of(
        new PrinterDTO("1", "One", true),
        new PrinterDTO("2", "Two", false),
        new PrinterDTO("3", "Three", false)
    );

    Printer existing1 = mock(Printer.class);
    Printer orphan99 = mock(Printer.class);
    when(orphan99.isActive()).thenReturn(true);

    OBDal obdal = mock(OBDal.class);

    OBCriteria<Printer> c1 = criteriaMockRETURNS_SELF();
    OBCriteria<Printer> c2 = criteriaMockRETURNS_SELF();
    OBCriteria<Printer> c3 = criteriaMockRETURNS_SELF();
    OBCriteria<Printer> cInact = criteriaMockRETURNS_SELF();

    when(obdal.createCriteria(eq(Printer.class))).thenReturn(c1, c2, c3, cInact);

    when(c1.uniqueResult()).thenReturn(existing1);
    when(c2.uniqueResult()).thenReturn(null);
    when(c3.uniqueResult()).thenReturn(null);

    when(cInact.list()).thenReturn(List.of(orphan99));

    OBProvider obProvider = mock(OBProvider.class);
    Printer new2 = mock(Printer.class);
    Printer new3 = mock(Printer.class);
    when(obProvider.get(Printer.class)).thenReturn(new2, new3);

    PrintProviderStrategy strategy = mock(PrintProviderStrategy.class);
    when(strategy.fetchPrinters(eq(provider))).thenReturn(remote);

    // --- Static mocks
    try (MockedStatic<OBDal> sDal = Mockito.mockStatic(OBDal.class);
         MockedStatic<OBMessageUtils> sMsg = Mockito.mockStatic(OBMessageUtils.class);
         MockedStatic<ProviderStrategyResolver> sResolver = Mockito.mockStatic(ProviderStrategyResolver.class);
         MockedStatic<OBProvider> sOBProv = Mockito.mockStatic(OBProvider.class)) {

      sDal.when(OBDal::getInstance).thenReturn(obdal);
      sResolver.when(() -> ProviderStrategyResolver.resolveForProvider(eq(provider))).thenReturn(strategy);
      sOBProv.when(OBProvider::getInstance).thenReturn(obProvider);

      sMsg.when(() -> OBMessageUtils.messageBD("ETPP_PrintersUpdated")).thenReturn("Printers updated.");

      when(obdal.get(Provider.class, providerId)).thenReturn(provider);

      ActionResult res = action.action(p, new MutableBoolean(false));

      assertThat(res.getType(), is(Result.Type.SUCCESS));
      assertThat(res.getMessage(), is("Printers updated. Created=2, Updated=1, Inactivated=1"));

      verify(obdal, times(4)).save(any(Printer.class));
      verify(obdal, times(1)).flush();

      sOBProv.verify(OBProvider::getInstance, times(2));
      verify(obProvider, times(2)).get(Printer.class);
    }
  }

  /**
   * Tests that the action throws an error if the {@link PrinterUtils#PROVIDER}
   * parameter is missing.
   * <p>
   * The test creates a JSON object with the other required parameters and then
   * calls the action. It verifies that the result is an error with a message
   * indicating that the provider parameter is missing.
   */
  @Test
  void actionErrorMissingProviderParam() throws Exception {
    JSONObject root = new JSONObject();
    root.put(PrinterUtils.PARAMS, new JSONObject().put(PrinterUtils.PROVIDER, "")); // en blanco

    try (MockedStatic<OBMessageUtils> sMsg = Mockito.mockStatic(OBMessageUtils.class);
         MockedStatic<OBDal> sDal = Mockito.mockStatic(OBDal.class)) {

      sMsg.when(() -> OBMessageUtils.messageBD("ETPP_MissingParameter")).thenReturn("Missing parameter: %s");

      OBDal obdal = mock(OBDal.class);
      sDal.when(OBDal::getInstance).thenReturn(obdal);
      doNothing().when(obdal).rollbackAndClose();

      ActionResult res = action.action(root, new MutableBoolean(false));

      assertThat(res.getType(), is(Result.Type.ERROR));
      assertThat(res.getMessage(), is("Missing parameter: " + PrinterUtils.PROVIDER));
    }
  }

  /**
   * Tests that the action throws an error if the provider referenced by the
   * {@link PrinterUtils#PROVIDER} parameter is not found.
   * <p>
   * The test creates a JSON object with the required parameter and then calls
   * the action. It verifies that the result is an error with a message
   * indicating that the provider was not found.
   */
  @Test
  void actionErrorProviderNotFound() throws Exception {
    JSONObject p = params("prov-x");

    OBDal obdal = mock(OBDal.class);
    when(obdal.get(Provider.class, "prov-x")).thenReturn(null);

    try (MockedStatic<OBDal> sDal = Mockito.mockStatic(OBDal.class);
         MockedStatic<OBContext> sCtx = Mockito.mockStatic(OBContext.class);
         MockedStatic<OBMessageUtils> sMsg = Mockito.mockStatic(OBMessageUtils.class)) {

      sDal.when(OBDal::getInstance).thenReturn(obdal);
      sMsg.when(() -> OBMessageUtils.messageBD("ETPP_ProviderNotFound")).thenReturn("Provider not found");

      ActionResult res = action.action(p, new MutableBoolean(false));

      assertThat(res.getType(), is(Result.Type.ERROR));
      assertThat(res.getMessage(), is("Provider not found"));
    }
  }

  /**
   * Tests that the action handles {@link PrintProviderException}s thrown by the
   * strategy's {@link PrintProviderStrategy#fetchPrinters(Provider)} method by
   * rolling back and returning an error result with a message indicating the
   * problem.
   */
  @Test
  void actionErrorStrategyThrowsPrintProviderExceptionAndRollsBack() throws Exception {
    JSONObject p = params("prov-1");

    Provider provider = mock(Provider.class);
    OBDal obdal = mock(OBDal.class);

    PrintProviderStrategy strategy = mock(PrintProviderStrategy.class);
    when(strategy.fetchPrinters(eq(provider))).thenThrow(new PrintProviderException("boom"));

    try (MockedStatic<OBDal> sDal = Mockito.mockStatic(OBDal.class);
         MockedStatic<ProviderStrategyResolver> sResolver = Mockito.mockStatic(ProviderStrategyResolver.class);
         MockedStatic<OBMessageUtils> sMsg = Mockito.mockStatic(OBMessageUtils.class)) {

      sDal.when(OBDal::getInstance).thenReturn(obdal);
      when(obdal.get(Provider.class, "prov-1")).thenReturn(provider);

      sResolver.when(() -> ProviderStrategyResolver.resolveForProvider(eq(provider))).thenReturn(strategy);

      sMsg.when(() -> OBMessageUtils.messageBD("ETPP_ProviderErrorPrefix")).thenReturn("Provider error: %s");

      ActionResult res = action.action(p, new MutableBoolean(false));

      assertThat(res.getType(), is(Result.Type.ERROR));
      assertThat(res.getMessage(), is("Provider error: boom"));
      verify(obdal, times(1)).rollbackAndClose();
    }
  }

  /**
   * Verifies that the action returns the correct input class, which is the base
   * class of all Openbravo objects: {@code BaseOBObject}.
   */
  @Test
  void getInputClassReturnsBaseOBObject() {
    assertThat(action.getInputClass(), equalTo(BaseOBObject.class));
  }
}