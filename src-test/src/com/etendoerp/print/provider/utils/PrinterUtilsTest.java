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
package com.etendoerp.print.provider.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import javax.servlet.ServletContext;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Criterion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.core.DalContextListener;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.datamodel.Table;

import com.etendoerp.print.provider.api.PrintProviderException;
import com.etendoerp.print.provider.data.Printer;
import com.etendoerp.print.provider.data.Provider;
import com.etendoerp.print.provider.data.ProviderParam;
import com.etendoerp.print.provider.data.Template;
import com.etendoerp.print.provider.data.TemplateLine;
import com.smf.jobs.ActionResult;
import com.smf.jobs.Result;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.util.JRLoader;

/**
 * Unit tests for {@link PrinterUtils}.
 * Uses MockitoExtension to enable Mockito mocks for dependencies and static methods.
 */
@ExtendWith(MockitoExtension.class)
class PrinterUtilsTest {
  private static final String PROVIDER_NOT_FOUND = "Provider not found";
  private static final String EMPTY_TEMPLATE_LOCATION = "Empty template location";
  private static final String MESSAGE_SHOULD_INCLUDE_KEY = "Message should include the missing key";
  private static final String MISSING_PARAMETER = "Missing parameter: %s";
  private static final String REPORTS_MY_LABEL_JASPER = "reports/my-label.jasper";
  private static final String SRC_LOC_DESIGN = "src-loc/design/";
  private static final String FOO_BAR = "foo/bar";
  private static final String API_KEY = "API_KEY";
  private static final String PRINTERS_URL = "PRINTERS_URL";
  private static final String PARAM = "param";
  private static final String ITEMS = "items";
  private static final String COPIES = "copies";
  private static final String JASPER_EXT = ".jasper";
  private static final String SAMPLE_JRXML_PATH = "/tmp/sample.jrxml";
  private MockedStatic<OBMessageUtils> obMsgStatic;

  @Mock
  private ProviderParam providerParam;

  @Mock
  private Provider provider;

  @Mock
  private OBDal obDal;

  @Mock
  private JasperReport jasperReport;

  @Mock
  private ServletContext servletContext;

  @Mock
  private Table table;

  @Mock
  private Printer printer;

  @Mock
  private Template template;

  @Mock
  private TemplateLine templateLine;

  @Mock
  private ActionResult actionResult;

  /**
   * Initializes the PrinterUtils and sets up the necessary mocks before each test.
   */
  @BeforeEach
  void setUp() {
    obMsgStatic = mockStatic(OBMessageUtils.class);
    lenient().when(OBMessageUtils.messageBD(anyString())).thenReturn("Provider parameter without content: %s");
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
   * Ensures the protected constructor throws UnsupportedOperationException to prevent instantiation.
   */
  @Test
  void constructorThrowsUnsupportedOperationException() {
    assertThrows(UnsupportedOperationException.class, PrinterUtils::new);
  }

  /**
   * Ensures the constructor is declared as protected (not public).
   */
  @Test
  void constructorIsProtected() throws Exception {
    Constructor<PrinterUtils> ctor = PrinterUtils.class.getDeclaredConstructor();
    assertTrue(Modifier.isProtected(ctor.getModifiers()), "Constructor should be protected");
  }

  /**
   * Ensures requireProvider returns the Provider when it exists for the given id.
   */
  @Test
  void requireProviderWhenFoundReturnsProvider() {
    String providerId = "PROV-123";

    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
      obDalStatic.when(OBDal::getInstance).thenReturn(obDal);

      when(obDal.get(Provider.class, providerId)).thenReturn(provider);

      assertSame(provider, PrinterUtils.requireProvider(providerId));
    }
  }

  /**
   * Ensures requireProvider throws OBException when no Provider exists for the given id.
   * Verifies the error message comes from OBMessageUtils using the expected key.
   */
  @Test
  void requireProviderWhenNotFoundThrowsOBException() {
    String providerId = "MISSING-999";

    obMsgStatic.when(() -> OBMessageUtils.getI18NMessage(org.mockito.ArgumentMatchers.anyString())).thenReturn(
        PROVIDER_NOT_FOUND);

    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
      obDalStatic.when(OBDal::getInstance).thenReturn(obDal);

      when(obDal.get(Provider.class, providerId)).thenReturn(null);

      OBException ex = assertThrows(OBException.class, () -> PrinterUtils.requireProvider(providerId));
      assertTrue(ex.getMessage().contains(PROVIDER_NOT_FOUND));
    }
  }

  /**
   * Ensures requirePrinter returns the Printer when it exists for the given id.
   */
  @Test
  void requirePrinterWhenFoundReturnsPrinter() {
    String printerId = "PRN-123";

    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
      obDalStatic.when(OBDal::getInstance).thenReturn(obDal);

      when(obDal.get(Printer.class, printerId)).thenReturn(printer);

      assertSame(printer, PrinterUtils.requirePrinter(printerId));
    }
  }

  /**
   * Ensures requirePrinter throws OBException when no Printer exists for the given id.
   * Verifies the formatted message includes the requested printer id.
   */
  @Test
  void requirePrinterWhenNotFoundThrowsOBException() {
    String printerId = "MISSING-999";

    obMsgStatic.when(() -> OBMessageUtils.getI18NMessage("ETPP_PrinterNotFoundById")).thenReturn(
        "Printer not found: %s");

    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
      obDalStatic.when(OBDal::getInstance).thenReturn(obDal);

      when(obDal.get(Printer.class, printerId)).thenReturn(null);

      OBException ex = assertThrows(OBException.class, () -> PrinterUtils.requirePrinter(printerId));
      assertTrue(ex.getMessage().contains(printerId));
    }
  }

  /**
   * Ensures requireTableByName returns the Table when it exists.
   */
  @Test
  void requireTableByNameWhenFoundReturnsTable() {
    String entityName = "C_Order";

    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
      obDalStatic.when(OBDal::getInstance).thenReturn(obDal);

      @SuppressWarnings("unchecked") OBCriteria<Table> criteria = mock(OBCriteria.class);
      when(obDal.createCriteria(Table.class)).thenReturn(criteria);
      when(criteria.add(any(org.hibernate.criterion.Criterion.class))).thenReturn(criteria);

      when(criteria.uniqueResult()).thenReturn(table);

      assertSame(table, PrinterUtils.requireTableByName(entityName));
    }
  }

  /**
   * Ensures requireTableByName throws OBException when no Table is found for the given name.
   * Verifies the formatted message includes the entity name.
   */
  @Test
  void requireTableByNameWhenNotFoundThrowsOBException() {
    String entityName = "NonExistingTable";

    obMsgStatic.when(() -> OBMessageUtils.getI18NMessage("ETPP_TableNotFound")).thenReturn("Table not found: %s");

    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
      obDalStatic.when(OBDal::getInstance).thenReturn(obDal);

      @SuppressWarnings("unchecked") OBCriteria<Table> criteria = mock(OBCriteria.class);
      when(obDal.createCriteria(Table.class)).thenReturn(criteria);
      when(criteria.add(any(org.hibernate.criterion.Criterion.class))).thenReturn(criteria);
      when(criteria.uniqueResult()).thenReturn(null);

      OBException ex = assertThrows(OBException.class, () -> PrinterUtils.requireTableByName(entityName));

      assertTrue(ex.getMessage().contains(entityName));
    }
  }

  /**
   * Ensures resolveTemplateLineFor throws OBException when no Template exists for the given table.
   * Verifies the message contains the table name.
   */
  @Test
  void resolveTemplateLineForWhenTemplateNotFoundThrowsOBException() {
    obMsgStatic.when(() -> OBMessageUtils.getI18NMessage("ETPP_PrintLocationNotFound")).thenReturn(
        "Print location not found for table: %s");

    when(table.getName()).thenReturn("Order");

    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
      obDalStatic.when(OBDal::getInstance).thenReturn(obDal);

      @SuppressWarnings("unchecked") OBCriteria<Template> templateCrit = mock(OBCriteria.class);
      when(obDal.createCriteria(Template.class)).thenReturn(templateCrit);

      when(templateCrit.add(any(Criterion.class))).thenReturn(templateCrit);
      when(templateCrit.uniqueResult()).thenReturn(null);

      OBException ex = assertThrows(OBException.class, () -> PrinterUtils.resolveTemplateLineFor(table));
      assertTrue(ex.getMessage().contains("Order"));
    }
  }

  /**
   * Ensures resolveTemplateLineFor returns the first TemplateLine when Template exists.
   * Mocks both criteria: Template and TemplateLine.
   */
  @Test
  void resolveTemplateLineForWhenFoundReturnsBestTemplateLine() {
    try (MockedStatic<OBDal> obDalStatic = mockResolveTemplateCriteria(template, templateLine)) {
      assertSame(templateLine, PrinterUtils.resolveTemplateLineFor(table));
    }
  }

  /**
   * Ensures resolveTemplateLineFor returns null when Template exists, but no TemplateLine is found.
   */
  @Test
  void resolveTemplateLineForWhenNoTemplateLineReturnsNull() {
    try (MockedStatic<OBDal> obDalStatic = mockResolveTemplateCriteria(template, null)) {
      assertNull(PrinterUtils.resolveTemplateLineFor(table));
    }
  }

  /**
   * Ensures getRequiredParam throws OBException when provider is null.
   */
  @Test
  void getRequiredParamWhenProviderNullThrowsOBException() {
    obMsgStatic.when(() -> OBMessageUtils.getI18NMessage(anyString())).thenReturn(PROVIDER_NOT_FOUND);

    OBException ex = assertThrows(OBException.class, () -> PrinterUtils.getRequiredParam(null, API_KEY));
    assertTrue(ex.getMessage().contains(PROVIDER_NOT_FOUND));
  }

  /**
   * Ensures getRequiredParam throws OBException when paramKey is blank.
   * Verifies the message is formatted with the literal "paramKey".
   */
  @Test
  void getRequiredParamWhenParamKeyBlankThrowsOBException() {
    obMsgStatic.when(() -> OBMessageUtils.getI18NMessage(anyString())).thenReturn(MISSING_PARAMETER);

    OBException ex = assertThrows(OBException.class, () -> PrinterUtils.getRequiredParam(provider, ""));
    assertTrue(ex.getMessage().contains("paramKey"));
  }

  /**
   * Ensures getRequiredParam returns the ProviderParam when it exists in the DB.
   * Mocks OBDal.createCriteria(...) -> uniqueResult() returning the param.
   */
  @Test
  void getRequiredParamWhenFoundReturnsParam() {
    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
      obDalStatic.when(OBDal::getInstance).thenReturn(obDal);

      @SuppressWarnings("unchecked") OBCriteria<ProviderParam> criteria = mock(OBCriteria.class);
      when(obDal.createCriteria(ProviderParam.class)).thenReturn(criteria);

      when(criteria.add(any(Criterion.class))).thenReturn(criteria);
      when(criteria.uniqueResult()).thenReturn(providerParam);

      ProviderParam result = PrinterUtils.getRequiredParam(provider, API_KEY);
      assertSame(providerParam, result);
    }
  }

  /**
   * Ensures getRequiredParam throws OBException when the parameter is not found.
   * Verifies the message contains the searched key.
   */
  @Test
  void getRequiredParamWhenNotFoundThrowsOBException() {
    obMsgStatic.when(() -> OBMessageUtils.getI18NMessage("ETPP_ProviderParamNotFound")).thenReturn(
        "Provider param not found: %s");

    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
      obDalStatic.when(OBDal::getInstance).thenReturn(obDal);

      @SuppressWarnings("unchecked") OBCriteria<ProviderParam> criteria = mock(OBCriteria.class);
      when(obDal.createCriteria(ProviderParam.class)).thenReturn(criteria);

      when(criteria.add(any(Criterion.class))).thenReturn(criteria);
      when(criteria.uniqueResult()).thenReturn(null);

      OBException ex = assertThrows(OBException.class, () -> PrinterUtils.getRequiredParam(provider, PRINTERS_URL));

      assertTrue(ex.getMessage().contains(PRINTERS_URL));
    }
  }

  /**
   * Ensures requireParam throws OBException when the key is missing in the JSON.
   */
  @Test
  void requireParamWhenKeyMissingThrowsOBException() {
    JSONObject json = new JSONObject();
    String key = PARAM;

    obMsgStatic.when(() -> OBMessageUtils.getI18NMessage(org.mockito.ArgumentMatchers.anyString())).thenReturn(
        MISSING_PARAMETER);

    OBException ex = assertThrows(OBException.class, () -> PrinterUtils.requireParam(json, key));
    assertTrue(ex.getMessage().contains(key), MESSAGE_SHOULD_INCLUDE_KEY);
  }

  /**
   * Ensures requireParam throws OBException when the value is an empty string.
   */
  @Test
  void requireParamWhenValueEmptyThrowsOBException() throws Exception {
    JSONObject json = new JSONObject().put(PARAM, "");
    String key = PARAM;

    obMsgStatic.when(() -> OBMessageUtils.getI18NMessage(org.mockito.ArgumentMatchers.anyString())).thenReturn(
        MISSING_PARAMETER);

    OBException ex = assertThrows(OBException.class, () -> PrinterUtils.requireParam(json, key));
    assertTrue(ex.getMessage().contains(key));
  }

  /**
   * Ensures requireParam returns the non-blank value when present.
   */
  @Test
  void requireParamWhenValuePresentReturnsIt() throws Exception {
    JSONObject json = new JSONObject().put(PARAM, "value-123");

    assertEquals("value-123", PrinterUtils.requireParam(json, PARAM));
  }

  /**
   * Ensures requireJSONArray throws OBException when the key is missing in the JSON.
   */
  @Test
  void requireJSONArrayWhenKeyMissingThrowsOBException() {
    JSONObject json = new JSONObject();
    String key = ITEMS;

    obMsgStatic.when(() -> OBMessageUtils.getI18NMessage(org.mockito.ArgumentMatchers.anyString())).thenReturn(
        MISSING_PARAMETER);

    OBException ex = assertThrows(OBException.class, () -> PrinterUtils.requireJSONArray(json, key));
    assertTrue(ex.getMessage().contains(key), MESSAGE_SHOULD_INCLUDE_KEY);
  }

  /**
   * Ensures requireJSONArray throws OBException when the key exists but is not an array.
   */
  @Test
  void requireJSONArrayWhenValueIsNotArrayThrowsOBException() throws Exception {
    JSONObject json = new JSONObject().put(ITEMS, "not-an-array");
    String key = ITEMS;

    obMsgStatic.when(() -> OBMessageUtils.getI18NMessage(org.mockito.ArgumentMatchers.anyString())).thenReturn(
        MISSING_PARAMETER);

    OBException ex = assertThrows(OBException.class, () -> PrinterUtils.requireJSONArray(json, key));
    assertTrue(ex.getMessage().contains(key));
  }

  /**
   * Ensures requireJSONArray throws OBException when the array is empty.
   */
  @Test
  void requireJSONArrayWhenEmptyArrayThrowsOBException() throws Exception {
    JSONObject json = new JSONObject().put(ITEMS, new JSONArray());
    String key = ITEMS;

    obMsgStatic.when(() -> OBMessageUtils.getI18NMessage(org.mockito.ArgumentMatchers.anyString())).thenReturn(
        MISSING_PARAMETER);

    OBException ex = assertThrows(OBException.class, () -> PrinterUtils.requireJSONArray(json, key));
    assertTrue(ex.getMessage().contains(key));
  }

  /**
   * Ensures requireJSONArray returns the non-empty array when present.
   */
  @Test
  void requireJSONArrayWhenNonEmptyReturnsArray() throws Exception {
    JSONArray arr = new JSONArray().put(1).put(2);
    JSONObject json = new JSONObject().put(ITEMS, arr);

    JSONArray result = PrinterUtils.requireJSONArray(json, ITEMS);
    assertSame(arr, result, "Should return the same JSONArray instance stored in the JSON");
    assertEquals(2, result.length());
  }

  /**
   * Ensures requirePositiveInt throws OBException when the key is missing in the JSON.
   * Verifies the formatted message includes the missing key.
   */
  @Test
  void requirePositiveIntWhenKeyMissingThrowsOBException() {
    JSONObject json = new JSONObject();
    String key = COPIES;

    obMsgStatic.when(() -> OBMessageUtils.getI18NMessage(org.mockito.ArgumentMatchers.anyString())).thenReturn(
        MISSING_PARAMETER);

    OBException ex = assertThrows(OBException.class, () -> PrinterUtils.requirePositiveInt(json, key));
    assertTrue(ex.getMessage().contains(key), MESSAGE_SHOULD_INCLUDE_KEY);
  }

  /**
   * Ensures requirePositiveInt throws OBException when the value is zero.
   * Verifies the formatted message includes the invalid number.
   */
  @Test
  void requirePositiveIntWhenValueIsZeroThrowsOBException() throws Exception {
    JSONObject json = new JSONObject().put(COPIES, 0);

    obMsgStatic.when(() -> OBMessageUtils.getI18NMessage("ETPP_NumberOfCopiesInvalid")).thenReturn(
        "Invalid copies: %s");

    OBException ex = assertThrows(OBException.class, () -> PrinterUtils.requirePositiveInt(json, COPIES));
    assertTrue(ex.getMessage().contains("0"), "Message should include the invalid number");
  }

  /**
   * Ensures requirePositiveInt throws OBException when the value is negative.
   * Verifies the formatted message includes the invalid number.
   */
  @Test
  void requirePositiveIntWhenValueIsNegativeThrowsOBException() throws Exception {
    JSONObject json = new JSONObject().put(COPIES, -5);

    obMsgStatic.when(() -> OBMessageUtils.getI18NMessage("ETPP_NumberOfCopiesInvalid")).thenReturn(
        "Invalid copies: %s");

    OBException ex = assertThrows(OBException.class, () -> PrinterUtils.requirePositiveInt(json, COPIES));
    assertTrue(ex.getMessage().contains("-5"), "Message should include the invalid number");
  }

  /**
   * Ensures requirePositiveInt returns the positive integer when present and valid.
   */
  @Test
  void requirePositiveIntWhenValueIsPositiveReturnsIt() throws Exception {
    JSONObject json = new JSONObject().put(COPIES, 3);

    assertEquals(3, PrinterUtils.requirePositiveInt(json, COPIES));
  }

  /**
   * Ensures fail sets the result type to ERROR, assigns the given detail as a message,
   * and returns the same ActionResult instance.
   */
  @Test
  void failSetsErrorTypeAndMessageReturnsSameInstance() {
    assertSame(actionResult, PrinterUtils.fail(actionResult, "Something went wrong"),
        "fail should return the same ActionResult instance");
    verify(actionResult).setType(Result.Type.ERROR);
    verify(actionResult).setMessage("Something went wrong");
    verifyNoMoreInteractions(actionResult);
  }

  /**
   * Ensures fail accepts a null detail and still sets the ERROR type
   * while passing null to setMessage.
   */
  @Test
  void failWhenDetailIsNullSetsErrorAndNullMessage() {
    assertSame(actionResult, PrinterUtils.fail(actionResult, null),
        "fail should return the same ActionResult instance");
    verify(actionResult).setType(Result.Type.ERROR);
    verify(actionResult).setMessage(null);
    verifyNoMoreInteractions(actionResult);
  }

  /**
   * Ensures warning sets the result type to WARNING, assigns the given detail as a message,
   * and returns the same ActionResult instance.
   */
  @Test
  void warningSetsWarningTypeAndMessageReturnsSameInstance() {
    assertSame(actionResult, PrinterUtils.warning(actionResult, "Be careful"),
        "warning should return the same ActionResult instance");
    verify(actionResult).setType(Result.Type.WARNING);
    verify(actionResult).setMessage("Be careful");
    verifyNoMoreInteractions(actionResult);
  }

  /**
   * Ensures warning accepts a null detail and still sets the WARNING type
   * while passing null to setMessage.
   */
  @Test
  void warningWhenDetailIsNullSetsWarningAndNullMessage() {
    assertSame(actionResult, PrinterUtils.warning(actionResult, null),
        "warning should return the same ActionResult instance");
    verify(actionResult).setType(Result.Type.WARNING);
    verify(actionResult).setMessage(null);
    verifyNoMoreInteractions(actionResult);
  }

  /**
   * Ensures resolveTemplateFile throws OBException when TemplateLine is null.
   */
  @Test
  void resolveTemplateFileWhenTemplateLineNullThrowsOBException() {
    obMsgStatic.when(() -> OBMessageUtils.getI18NMessage("ETPP_EmptyTemplateLocation")).thenReturn(
        EMPTY_TEMPLATE_LOCATION);

    OBException ex = assertThrows(OBException.class, () -> PrinterUtils.resolveTemplateFile(null));
    assertTrue(ex.getMessage().contains(EMPTY_TEMPLATE_LOCATION));
  }

  /**
   * Ensures resolveTemplateFile throws OBException when template location is blank.
   */
  @Test
  void resolveTemplateFileWhenLocationBlankThrowsOBException() {
    obMsgStatic.when(() -> OBMessageUtils.getI18NMessage("ETPP_EmptyTemplateLocation")).thenReturn(
        EMPTY_TEMPLATE_LOCATION);

    when(templateLine.getTemplateLocation()).thenReturn("");

    OBException ex = assertThrows(OBException.class, () -> PrinterUtils.resolveTemplateFile(templateLine));
    assertTrue(ex.getMessage().contains(EMPTY_TEMPLATE_LOCATION));
  }

  /**
   * Ensures resolveTemplateFile handles the @basedesign@ token (case-insensitive)
   * and delegates to trySrcLocThenWeb with the stripped relative path.
   */
  @Test
  void resolveTemplateFileWhenBasedesignTokenResolvesViaSrcOrWeb() throws Exception {
    when(templateLine.getTemplateLocation()).thenReturn("@BASEDESIGN@/reports/my-label.jasper");

    File returned = File.createTempFile("tpl", JASPER_EXT);
    returned.deleteOnExit();

    try (MockedStatic<DalContextListener> dl = mockStatic(
        DalContextListener.class); MockedStatic<PrinterUtils> pu = mockStatic(PrinterUtils.class)) {

      dl.when(DalContextListener::getServletContext).thenReturn(servletContext);

      pu.when(() -> PrinterUtils.resolveTemplateFile(any(TemplateLine.class))).thenCallRealMethod();
      pu.when(() -> PrinterUtils.stripLeadingSlash(anyString())).thenCallRealMethod();
      pu.when(() -> PrinterUtils.trySrcLocThenWeb(eq(servletContext), eq(REPORTS_MY_LABEL_JASPER))).thenReturn(
          returned);

      File out = PrinterUtils.resolveTemplateFile(templateLine);

      assertNotNull(out);
      assertEquals(returned.getAbsolutePath(), out.getAbsolutePath());

      pu.verify(() -> PrinterUtils.trySrcLocThenWeb(servletContext, REPORTS_MY_LABEL_JASPER));
    }
  }

  /**
   * Ensures resolveTemplateFile handles the @<module>@/path form and delegates properly.
   */
  @Test
  void resolveTemplateFileWhenModuleTokenResolvesViaSrcOrWeb() throws Exception {
    when(templateLine.getTemplateLocation()).thenReturn("@my.module@/sub/path/file.jasper");

    File returned = File.createTempFile("tpl", JASPER_EXT);
    returned.deleteOnExit();

    try (MockedStatic<DalContextListener> dl = mockStatic(
        DalContextListener.class); MockedStatic<PrinterUtils> pu = mockStatic(PrinterUtils.class)) {

      dl.when(DalContextListener::getServletContext).thenReturn(servletContext);

      pu.when(() -> PrinterUtils.resolveTemplateFile(any(TemplateLine.class))).thenCallRealMethod();
      pu.when(() -> PrinterUtils.stripLeadingSlash(anyString())).thenCallRealMethod();
      pu.when(() -> PrinterUtils.trySrcLocThenWeb(eq(servletContext), eq("my.module/sub/path/file.jasper"))).thenReturn(
          returned);

      File out = PrinterUtils.resolveTemplateFile(templateLine);

      assertNotNull(out);
      assertEquals(returned.getAbsolutePath(), out.getAbsolutePath());

      pu.verify(() -> PrinterUtils.trySrcLocThenWeb(servletContext, "my.module/sub/path/file.jasper"));
    }
  }

  /**
   * Ensures resolveTemplateFile resolves plain paths (without tokens) by delegating
   * to trySrcLocThenWeb with a stripped relative path.
   */
  @Test
  void resolveTemplateFileWhenPlainPathResolvesViaSrcOrWeb() throws Exception {
    when(templateLine.getTemplateLocation()).thenReturn("/plain/reports/out.jasper");

    File returned = File.createTempFile("tpl", JASPER_EXT);
    returned.deleteOnExit();

    try (MockedStatic<DalContextListener> dl = mockStatic(
        DalContextListener.class); MockedStatic<PrinterUtils> pu = mockStatic(PrinterUtils.class)) {

      dl.when(DalContextListener::getServletContext).thenReturn(servletContext);

      pu.when(() -> PrinterUtils.resolveTemplateFile(any(TemplateLine.class))).thenCallRealMethod();
      pu.when(() -> PrinterUtils.stripLeadingSlash(anyString())).thenCallRealMethod();
      pu.when(() -> PrinterUtils.trySrcLocThenWeb(eq(servletContext), eq("plain/reports/out.jasper"))).thenReturn(
          returned);

      File out = PrinterUtils.resolveTemplateFile(templateLine);

      assertNotNull(out);
      assertEquals(returned.getAbsolutePath(), out.getAbsolutePath());

      pu.verify(() -> PrinterUtils.trySrcLocThenWeb(servletContext, "plain/reports/out.jasper"));
    }
  }

  /**
   * Ensures trySrcLocThenWeb returns the file from 'src-loc/design/<rel>' when it exists.
   */
  @Test
  void trySrcLocThenWebWhenSrcLocExistsReturnsSrcLocFile() throws Exception {
    String rel = REPORTS_MY_LABEL_JASPER;
    File srcLocFile = File.createTempFile("srcLoc-", ".tmp");
    srcLocFile.deleteOnExit();

    when(servletContext.getRealPath(SRC_LOC_DESIGN + rel)).thenReturn(srcLocFile.getAbsolutePath());

    File result = PrinterUtils.trySrcLocThenWeb(servletContext, rel);

    assertNotNull(result);
    assertEquals(srcLocFile.getAbsolutePath(), result.getAbsolutePath());
  }

  /**
   * Ensures trySrcLocThenWeb falls back to 'web/<rel>' when src-loc does not exist but web does.
   */
  @Test
  void trySrcLocThenWebWhenSrcLocMissingAndWebExistsReturnsWebFile() throws Exception {
    String rel = "reports/another-label.jasper";

    File missing = new File("definitely-missing-" + System.nanoTime() + ".tmp");
    when(servletContext.getRealPath(SRC_LOC_DESIGN + rel)).thenReturn(missing.getAbsolutePath());

    File webFile = File.createTempFile("web-", ".tmp");
    webFile.deleteOnExit();
    when(servletContext.getRealPath("web/" + rel)).thenReturn(webFile.getAbsolutePath());

    File result = PrinterUtils.trySrcLocThenWeb(servletContext, rel);

    assertNotNull(result);
    assertEquals(webFile.getAbsolutePath(), result.getAbsolutePath());
  }

  /**
   * Ensures trySrcLocThenWeb throws PrintProviderException when neither src-loc nor web contains the file.
   * Verifies that the formatted message includes both candidate paths.
   */
  @Test
  void trySrcLocThenWebWhenNeitherExistsThrowsPrintProviderException() {
    String rel = "reports/missing.jasper";

    when(servletContext.getRealPath(SRC_LOC_DESIGN + rel)).thenReturn(null);
    when(servletContext.getRealPath("web/" + rel)).thenReturn(null);

    obMsgStatic.when(() -> OBMessageUtils.getI18NMessage("ETPP_TemplateNotFound")).thenReturn(
        "Template not found: %s | %s");

    PrintProviderException ex = assertThrows(PrintProviderException.class,
        () -> PrinterUtils.trySrcLocThenWeb(servletContext, rel));

    String msg = ex.getMessage();
    assertNotNull(msg);
    assertTrue(msg.contains(SRC_LOC_DESIGN + rel), "Message should include src-loc candidate path");
    assertTrue(msg.contains("web/" + rel), "Message should include web candidate path");
  }

  /**
   * Ensures stripLeadingSlash returns null when the input path is null.
   */
  @Test
  void stripLeadingSlashWhenNullReturnsNull() {
    assertNull(PrinterUtils.stripLeadingSlash(null));
  }

  /**
   * Ensures stripLeadingSlash removes a single leading slash.
   */
  @Test
  void stripLeadingSlashWhenStartsWithSlashStripsOne() {
    assertEquals(FOO_BAR, PrinterUtils.stripLeadingSlash("/foo/bar"));
  }

  /**
   * Ensures stripLeadingSlash returns the same string when there is no leading slash.
   */
  @Test
  void stripLeadingSlashWhenNoLeadingSlashReturnsSame() {
    assertEquals(FOO_BAR, PrinterUtils.stripLeadingSlash(FOO_BAR));
  }

  /**
   * Ensures stripLeadingSlash returns empty string when input is empty.
   */
  @Test
  void stripLeadingSlashWhenEmptyReturnsEmpty() {
    assertEquals("", PrinterUtils.stripLeadingSlash(""));
  }

  /**
   * Ensures stripLeadingSlash only removes the first slash when multiple is present.
   */
  @Test
  void stripLeadingSlashWhenMultipleLeadingSlashesStripsOnlyFirst() {
    assertEquals("/foo", PrinterUtils.stripLeadingSlash("//foo"));
  }

  /**
   * Ensures a .jrxml path compiles the report via JasperCompileManager and returns the JasperReport.
   */
  @Test
  void loadOrCompileJasperReportWhenJrxmlCompilesReport() {
    File jrFile = new File(SAMPLE_JRXML_PATH);

    try (MockedStatic<JasperCompileManager> jcm = mockStatic(JasperCompileManager.class)) {
      jcm.when(() -> JasperCompileManager.compileReport(SAMPLE_JRXML_PATH)).thenReturn(jasperReport);

      JasperReport result = PrinterUtils.loadOrCompileJasperReport(SAMPLE_JRXML_PATH, jrFile);
      assertSame(jasperReport, result);
    }
  }

  /**
   * Ensures a .jasper path loads the report via JRLoader.loadObject and returns the JasperReport.
   */
  @Test
  void loadOrCompileJasperReportWhenJasperLoadsReport() throws Exception {
    String absPath = "/tmp/sample.jasper";
    File jrFile = File.createTempFile("tpl", JASPER_EXT);
    jrFile.deleteOnExit();

    try (MockedStatic<JRLoader> jrl = mockStatic(JRLoader.class)) {
      jrl.when(() -> JRLoader.loadObject(jrFile)).thenReturn(jasperReport);

      JasperReport result = PrinterUtils.loadOrCompileJasperReport(absPath, jrFile);
      assertSame(jasperReport, result);
    }
  }

  /**
   * Ensures an unsupported extension throws PrintProviderException with a formatted message.
   */
  @Test
  void loadOrCompileJasperReportWhenUnsupportedExtThrowsPrintProviderException() {
    String absPath = "/tmp/sample.txt";
    File jrFile = new File(absPath);

    obMsgStatic.when(() -> OBMessageUtils.getI18NMessage("ETPP_UnsupportedTemplateExtension")).thenReturn(
        "Unsupported: %s");

    PrintProviderException ex = assertThrows(PrintProviderException.class,
        () -> PrinterUtils.loadOrCompileJasperReport(absPath, jrFile));

    assertTrue(ex.getMessage().contains("sample.txt"));
  }

  /**
   * Ensures a JRException during compilation (.jrxml) is wrapped into PrintProviderException
   * with a message formatted by OBMessageUtils.getI18NMessage("ETPP_ErrorLoadingOrCompilingJasper").
   */
  @Test
  void loadOrCompileJasperReportWhenCompilationFailsWrapsIntoPrintProviderException() {
    File jrFile = new File(SAMPLE_JRXML_PATH);

    obMsgStatic.when(() -> OBMessageUtils.getI18NMessage("ETPP_ErrorLoadingOrCompilingJasper")).thenReturn("Error: %s");

    try (MockedStatic<JasperCompileManager> jcm = mockStatic(JasperCompileManager.class)) {
      jcm.when(() -> JasperCompileManager.compileReport(SAMPLE_JRXML_PATH)).thenThrow(new JRException("boom"));

      PrintProviderException ex = assertThrows(PrintProviderException.class,
          () -> PrinterUtils.loadOrCompileJasperReport(SAMPLE_JRXML_PATH, jrFile));

      assertTrue(ex.getMessage().contains(SAMPLE_JRXML_PATH));
    }
  }

  /**
   * Ensures a JRException during load (.jasper) is wrapped into PrintProviderException
   * with a message formatted by OBMessageUtils.getI18NMessage("ETPP_ErrorLoadingOrCompilingJasper").
   */
  @Test
  void loadOrCompileJasperReportWhenLoadFailsWrapsIntoPrintProviderException() throws Exception {
    String absPath = "/tmp/sample.jasper";
    File jrFile = File.createTempFile("tpl", JASPER_EXT);
    jrFile.deleteOnExit();

    obMsgStatic.when(() -> OBMessageUtils.getI18NMessage("ETPP_ErrorLoadingOrCompilingJasper")).thenReturn("Error: %s");

    try (MockedStatic<JRLoader> jrl = mockStatic(JRLoader.class)) {
      jrl.when(() -> JRLoader.loadObject(jrFile)).thenThrow(new JRException("boom"));

      PrintProviderException ex = assertThrows(PrintProviderException.class,
          () -> PrinterUtils.loadOrCompileJasperReport(absPath, jrFile));

      assertTrue(ex.getMessage().contains(absPath));
    }
  }

  /**
   * Ensures providerParamContentCheck throws OBException when param content is null.
   */
  @Test
  void providerParamContentCheckWhenContentNullThrowsOBException() {
    OBException ex = assertThrows(OBException.class,
        () -> PrinterUtils.providerParamContentCheck(providerParam, API_KEY));
    assertTrue(ex.getMessage().contains(API_KEY));
  }

  /**
   * Ensures providerParamContentCheck throws OBException when param content is empty.
   */
  @Test
  void providerParamContentCheckWhenContentEmptyThrowsOBException() {
    when(providerParam.getParamContent()).thenReturn("");

    OBException ex = assertThrows(OBException.class,
        () -> PrinterUtils.providerParamContentCheck(providerParam, PRINTERS_URL));
    assertTrue(ex.getMessage().contains(PRINTERS_URL));
  }

  /**
   * Ensures providerParamContentCheck throws OBException when param content is blank spaces.
   */
  @Test
  void providerParamContentCheckWhenContentBlankThrowsOBException() {
    when(providerParam.getParamContent()).thenReturn("");

    OBException ex = assertThrows(OBException.class,
        () -> PrinterUtils.providerParamContentCheck(providerParam, "PRINTJOB_URL"));
    assertTrue(ex.getMessage().contains("PRINTJOB_URL"));
  }

  /**
   * Ensures providerParamContentCheck does not throw when param content is non-blank.
   */
  @Test
  void providerParamContentCheckWhenContentPresentDoesNotThrow() {
    when(providerParam.getParamContent()).thenReturn("some-value");

    assertDoesNotThrow(() -> PrinterUtils.providerParamContentCheck(providerParam, "ANY_KEY"));
  }

  /**
   * Sets up minimal OBDal/criteria mocks used by {@code resolveTemplateLineFor(table)}.
   *
   * @param template
   *     Template to return from Template criteria (or {@code null})
   * @param lineToReturn
   *     TemplateLine to return from TemplateLine criteria (or {@code null})
   * @return active {@link MockedStatic} for {@link OBDal} that the caller must close
   */
  private MockedStatic<OBDal> mockResolveTemplateCriteria(Template template, TemplateLine lineToReturn) {
    MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class);
    obDalStatic.when(OBDal::getInstance).thenReturn(obDal);

    @SuppressWarnings("unchecked") OBCriteria<Template> templateCrit = mock(OBCriteria.class);
    when(obDal.createCriteria(Template.class)).thenReturn(templateCrit);
    when(templateCrit.add(any(org.hibernate.criterion.Criterion.class))).thenReturn(templateCrit);
    when(templateCrit.setMaxResults(anyInt())).thenReturn(templateCrit);
    when(templateCrit.uniqueResult()).thenReturn(template);

    @SuppressWarnings("unchecked") OBCriteria<TemplateLine> lineCrit = mock(OBCriteria.class);
    when(obDal.createCriteria(TemplateLine.class)).thenReturn(lineCrit);
    when(lineCrit.add(any(org.hibernate.criterion.Criterion.class))).thenReturn(lineCrit);
    when(lineCrit.addOrder(any(org.hibernate.criterion.Order.class))).thenReturn(lineCrit);
    when(lineCrit.setMaxResults(anyInt())).thenReturn(lineCrit);
    when(lineCrit.uniqueResult()).thenReturn(lineToReturn);

    return obDalStatic;
  }
}
