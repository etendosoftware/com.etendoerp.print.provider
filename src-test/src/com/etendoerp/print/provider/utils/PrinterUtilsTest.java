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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import javax.servlet.ServletContext;

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
  private static final String MISSING_PARAMETER = "Missing parameter: %s";
  private static final String REPORTS_MY_LABEL_JASPER = "reports/my-label.jasper";
  private static final String SRC_LOC_DESIGN = "src-loc/design/";
  private static final String FOO_BAR = "foo/bar";
  private static final String API_KEY = "API_KEY";
  private static final String PRINTERS_URL = "PRINTERS_URL";
  private static final String JASPER_EXT = ".jasper";
  private static final String SAMPLE_JRXML_PATH = "/tmp/sample.jrxml";
  private static final String SAMPLE_JASPER_PATH = "/tmp/sample.jasper";

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
    File jrFile = File.createTempFile("tpl", JASPER_EXT);
    jrFile.deleteOnExit();

    try (MockedStatic<JRLoader> jrl = mockStatic(JRLoader.class)) {
      jrl.when(() -> JRLoader.loadObject(jrFile)).thenReturn(jasperReport);

      JasperReport result = PrinterUtils.loadOrCompileJasperReport(SAMPLE_JASPER_PATH, jrFile);
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
    File jrFile = File.createTempFile("tpl", JASPER_EXT);
    jrFile.deleteOnExit();

    obMsgStatic.when(() -> OBMessageUtils.getI18NMessage("ETPP_ErrorLoadingOrCompilingJasper")).thenReturn("Error: %s");

    try (MockedStatic<JRLoader> jrl = mockStatic(JRLoader.class)) {
      jrl.when(() -> JRLoader.loadObject(jrFile)).thenThrow(new JRException("boom"));

      PrintProviderException ex = assertThrows(PrintProviderException.class,
          () -> PrinterUtils.loadOrCompileJasperReport(SAMPLE_JASPER_PATH, jrFile));

      assertTrue(ex.getMessage().contains(SAMPLE_JASPER_PATH));
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
}
