package com.etendoerp.print.provider.action;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.datamodel.Table;

import com.etendoerp.print.provider.api.PrintProviderException;
import com.etendoerp.print.provider.data.Printer;
import com.etendoerp.print.provider.data.Provider;
import com.etendoerp.print.provider.data.Template;
import com.etendoerp.print.provider.data.TemplateLine;
import com.etendoerp.print.provider.strategy.PrintProviderStrategy;
import com.etendoerp.print.provider.utils.PrinterUtils;
import com.etendoerp.print.provider.utils.ProviderStrategyResolver;
import com.smf.jobs.ActionResult;
import com.smf.jobs.Result;

/**
 * Unit tests for SendGeneratedLabelToPrinter.
 */
class SendGeneratedLabelToPrinterTest {

  private SendGeneratedLabelToPrinter action;

  /**
   * Set up the test by creating a new {@link SendGeneratedLabelToPrinter} instance.
   * <p>This method is called once before each test method.</p>
   */
  @BeforeEach
  void setUp() {
    action = new SendGeneratedLabelToPrinter();
  }

  /**
   * Convenience method to create a minimal JSON object with parameters for a
   * {@link SendGeneratedLabelToPrinter} action invocation.
   *
   * @param providerId
   *     {@link Provider#getId()}
   * @param entityName
   *     {@link Table#getName()}
   * @param recordId
   *     Target record ID
   * @param printerId
   *     {@link Printer#getId()}
   * @return
   *     JSON object with the four required parameters
   * @throws Exception
   *     if the JSON object could not be created
   */
  private static JSONObject params(String providerId, String entityName, String recordId,
      String printerId) throws Exception {
    JSONObject p = new JSONObject();
    p.put(PrinterUtils.PROVIDER, providerId);
    p.put(PrinterUtils.ENTITY_NAME, entityName);
    p.put(PrinterUtils.RECORDS, new JSONArray().put(recordId));
    p.put(PrinterUtils.PRINTERS, printerId);
    return p;
  }

  /**
   * Creates a mock for an {@link OBCriteria} object that always returns itself
   * (i.e., {@code criteria.add(Restrictions.eq("foo", "bar"))} returns the same
   * mock instance).
   *
   * @param <T>
   *     Entity class
   * @return
   *     Mocked {@link OBCriteria} object
   */
  @SuppressWarnings("unchecked")
  private static <T extends BaseOBObject> OBCriteria<T> criteriaMock() {
    return (OBCriteria<T>) Mockito.mock(OBCriteria.class, Mockito.withSettings().defaultAnswer(Mockito.RETURNS_SELF));
  }

  /**
   * Test that the action throws an error if the {@link PrinterUtils#PROVIDER}
   * parameter is missing.
   * <p>
   * The test creates a JSON object with the other required parameters and then
   * calls the action. It verifies that the result is an error with a message
   * indicating that the provider parameter is missing.
   */
  @Test
  void actionErrorMissingProviderParam() throws Exception {
    JSONObject p = new JSONObject();
    p.put(PrinterUtils.ENTITY_NAME, "M_InOut");
    p.put(PrinterUtils.RECORDS, new JSONArray().put("r1"));
    p.put(PrinterUtils.PRINTERS, "p1");

    try (MockedStatic<OBMessageUtils> s4 = Mockito.mockStatic(OBMessageUtils.class);
         MockedStatic<OBContext> s2 = Mockito.mockStatic(OBContext.class)) {
      s4.when(() -> OBMessageUtils.messageBD(PrinterUtils.MISSING_PARAMETER_MSG)).thenReturn("Missing parameter: %s");

      ActionResult res = action.action(p, new MutableBoolean(false));
      assertThat(res.getType(), is(Result.Type.ERROR));
      assertThat(res.getMessage(), is("Missing parameter: " + PrinterUtils.PROVIDER));
    }
  }

  /**
   * Test that the action throws an error if the provider referenced by the
   * {@link PrinterUtils#PROVIDER} parameter is not found.
   * <p>
   * The test creates a JSON object with the required parameters and then calls
   * the action. It verifies that the result is an error with a message
   * indicating that the provider was not found.
   */
  @Test
  void actionErrorProviderNotFound() throws Exception {
    JSONObject p = params("prov-x", "M_InOut", "r1", "p1");

    OBDal obdal = mock(OBDal.class);
    when(obdal.get(Provider.class, "prov-x")).thenReturn(null);

    try (MockedStatic<OBDal> s1 = Mockito.mockStatic(OBDal.class);
         MockedStatic<OBMessageUtils> s4 = Mockito.mockStatic(OBMessageUtils.class);
         MockedStatic<OBContext> s2 = Mockito.mockStatic(OBContext.class)) {
      s1.when(OBDal::getInstance).thenReturn(obdal);
      s4.when(() -> OBMessageUtils.getI18NMessage("ETPP_ProviderNotFound")).thenReturn("Provider not found");

      ActionResult res = action.action(p, new MutableBoolean(false));
      assertThat(res.getType(), is(Result.Type.ERROR));
      assertThat(res.getMessage(), is("Provider not found"));
    }
  }

  /**
   * Test that the action throws an error if the printer referenced by the
   * {@link PrinterUtils#PRINTERS} parameter is not found.
   * <p>
   * The test creates a JSON object with the required parameters and then calls
   * the action. It verifies that the result is an error with a message
   * indicating that the printer was not found.
   */
  @Test
  void actionErrorPrinterNotFound() throws Exception {
    JSONObject p = params("prov-1", "M_InOut", "r1", "p-missing");

    Provider provider = mock(Provider.class);
    OBDal obdal = mock(OBDal.class);
    OBCriteria<Table> tableCrit = criteriaMock();
    Table table = mock(Table.class);

    when(obdal.get(Provider.class, "prov-1")).thenReturn(provider);
    when(obdal.get(Printer.class, "p-missing")).thenReturn(null);
    when(obdal.createCriteria(Table.class)).thenReturn(tableCrit);
    when(tableCrit.uniqueResult()).thenReturn(table);

    try (MockedStatic<OBDal> s1 = Mockito.mockStatic(OBDal.class);
         MockedStatic<OBMessageUtils> s4 = Mockito.mockStatic(OBMessageUtils.class);
         MockedStatic<ProviderStrategyResolver> s3 = Mockito.mockStatic(ProviderStrategyResolver.class);
         MockedStatic<OBContext> s2 = Mockito.mockStatic(OBContext.class)) {

      s1.when(OBDal::getInstance).thenReturn(obdal);
      s4.when(() -> OBMessageUtils.messageBD("ETPP_PrinterNotFound")).thenReturn("Printer %s not found");

      ActionResult res = action.action(p, new MutableBoolean(false));

      assertThat(res.getType(), is(Result.Type.ERROR));
      assertThat(res.getMessage(), is("Printer p-missing not found"));
    }
  }

  /**
   * Test that the action throws an error if the table referenced by the
   * {@link PrinterUtils#ENTITY_NAME} parameter is not found.
   * <p>
   * The test creates a JSON object with the required parameters and then calls
   * the action. It verifies that the result is an error with a message
   * indicating that the table was not found.
   */
  @Test
  void actionErrorTableNotFound() throws Exception {
    SendGeneratedLabelToPrinter action = new SendGeneratedLabelToPrinter();

    JSONObject p = params("prov-1", "M_InOut", "r1", "p1");

    Provider provider = mock(Provider.class);
    Printer printer = mock(Printer.class);

    OBDal obdal = mock(OBDal.class);
    @SuppressWarnings("unchecked")
    OBCriteria<Table> tableCrit = (OBCriteria<Table>) mock(OBCriteria.class);

    when(obdal.get(eq(Provider.class), eq("prov-1"))).thenReturn(provider);
    when(obdal.get(eq(Printer.class), eq("p1"))).thenReturn(printer);
    when(obdal.createCriteria(eq(Table.class))).thenReturn(tableCrit);

    when(tableCrit.add(any())).thenReturn(tableCrit);
    when(tableCrit.setMaxResults(anyInt())).thenReturn(tableCrit);
    when(tableCrit.uniqueResult()).thenReturn(null);

    try (MockedStatic<OBDal> sOBDal = Mockito.mockStatic(OBDal.class);
         MockedStatic<OBMessageUtils> sMsg = Mockito.mockStatic(OBMessageUtils.class);
         MockedStatic<OBContext> sCtx = Mockito.mockStatic(OBContext.class);
         MockedStatic<ProviderStrategyResolver> sResolver = Mockito.mockStatic(ProviderStrategyResolver.class)) {

      sOBDal.when(OBDal::getInstance).thenReturn(obdal);

      PrintProviderStrategy strategy = mock(PrintProviderStrategy.class);
      sResolver.when(() -> ProviderStrategyResolver.resolveForProvider(eq(provider)))
          .thenReturn(strategy);

      sMsg.when(() -> OBMessageUtils.messageBD(eq("ETPP_TableNotFound")))
          .thenReturn("Table %s not found");

      ActionResult res = action.action(p, new MutableBoolean(false));

      assertThat(res.getType(), is(Result.Type.ERROR));
      assertThat(res.getMessage(), is("Table M_InOut not found"));
    }
  }

  /**
   * Tests that the action handles {@link PrintProviderException}s thrown by the
   * strategy's {@link PrintProviderStrategy#generateLabel(Provider, Table, String, TemplateLine, JSONObject)}
   * method by rolling back and returning an error result with a message indicating
   * the problem.
   */
  @Test
  void actionErrorStrategyThrowsPrintProviderExceptionAndRollsBack() throws Exception {
    JSONObject p = params("prov-1", "M_InOut", "r1", "p1");

    Provider provider = mock(Provider.class);
    Printer printer = mock(Printer.class);
    Table table = mock(Table.class);
    Template template = mock(Template.class);
    TemplateLine templateLine = mock(TemplateLine.class);
    PrintProviderStrategy strategy = mock(PrintProviderStrategy.class);

    OBDal obdal = mock(OBDal.class);
    OBCriteria<Table> tableCrit = criteriaMock();
    OBCriteria<Template> tmplCrit = criteriaMock();
    OBCriteria<TemplateLine> lineCrit = criteriaMock();

    when(obdal.get(Provider.class, "prov-1")).thenReturn(provider);
    when(obdal.get(Printer.class, "p1")).thenReturn(printer);

    when(obdal.createCriteria(Table.class)).thenReturn(tableCrit);
    when(obdal.createCriteria(Template.class)).thenReturn(tmplCrit);
    when(obdal.createCriteria(TemplateLine.class)).thenReturn(lineCrit);

    when(tableCrit.uniqueResult()).thenReturn(table);
    when(tmplCrit.uniqueResult()).thenReturn(template);
    when(lineCrit.uniqueResult()).thenReturn(templateLine);

    when(strategy.generateLabel(any(), any(), any(String.class), any(), isA(JSONObject.class)))
        .thenThrow(new PrintProviderException("boom"));

    try (MockedStatic<OBDal> s1 = Mockito.mockStatic(OBDal.class);
         MockedStatic<OBContext> s2 = Mockito.mockStatic(OBContext.class);
         MockedStatic<ProviderStrategyResolver> s3 = Mockito.mockStatic(ProviderStrategyResolver.class);
         MockedStatic<OBMessageUtils> s4 = Mockito.mockStatic(OBMessageUtils.class)) {

      s1.when(OBDal::getInstance).thenReturn(obdal);
      s3.when(() -> ProviderStrategyResolver.resolveForProvider(provider)).thenReturn(strategy);
      s4.when(() -> OBMessageUtils.messageBD("ETPP_ProviderErrorPrefix")).thenReturn("Provider error: %s");

      ActionResult res = action.action(p, new MutableBoolean(false));

      assertThat(res.getType(), is(Result.Type.ERROR));
      assertThat(res.getMessage(), is("Provider error: boom"));
      verify(obdal, times(1)).rollbackAndClose();
    }
  }

  /**
   * Verifies that the action returns the correct input class, which is the base class of
   * all Openbravo objects: {@code BaseOBObject}.
   */
  @Test
  void getInputClassReturnsBaseOBObject() {
    assertThat(action.getInputClass(), equalTo(BaseOBObject.class));
  }
}