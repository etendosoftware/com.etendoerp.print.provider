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
package com.etendoerp.print.provider.filterexpression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.hibernate.criterion.Order;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.print.provider.data.Printer;
import com.etendoerp.print.provider.data.Provider;
import com.etendoerp.print.provider.utils.PrinterUtils;

/**
 * Unit tests for {@link PrintProviderExpressions}.
 * Uses MockitoExtension to enable Mockito mocks for dependencies and static methods.
 */
@ExtendWith(MockitoExtension.class)
class PrintProviderExpressionsTest {

  private static final String PROV_1 = "PROV-1";
  private static final String PRN_1 = "PRN-1";
  private static final String CURRENT_PARAM = "currentParam";
  private PrintProviderExpressions hook;
  private MockedStatic<OBDal> obdalStatic;
  private MockedStatic<OBContext> obContextStatic;

  @Mock
  private OBDal obDal;

  @Mock
  private Provider provider;

  @Mock
  private Printer printer;

  /**
   * Initializes the PrintProviderExpressions and sets up the necessary mocks before each test.
   */
  @BeforeEach
  void setUp() {
    hook = spy(new PrintProviderExpressions());

    obdalStatic = mockStatic(OBDal.class);
    obdalStatic.when(OBDal::getInstance).thenReturn(obDal);

    obContextStatic = mockStatic(OBContext.class);
  }

  /**
   * Releases static mocks for OBDal and OBContext after each test.
   * Ensures that resources are properly closed to avoid memory leaks.
   */
  @AfterEach
  void tearDown() {
    if (obdalStatic != null) obdalStatic.close();
    if (obContextStatic != null) obContextStatic.close();
  }

  /**
   * Tests that when the 'currentParam' is PROVIDER,
   * the getExpression method returns the default provider ID.
   */
  @Test
  void getExpressionWhenProviderParamReturnsProviderId() {
    doReturn(PROV_1).when(hook).getDefaultProviderForPrintJob();

    Map<String, String> req = new HashMap<>();
    req.put(CURRENT_PARAM, PrinterUtils.PROVIDER);

    assertEquals(PROV_1, hook.getExpression(req));

    obContextStatic.verify(() -> OBContext.setAdminMode(true));
    obContextStatic.verify(OBContext::restorePreviousMode);
  }

  /**
   * Tests that when the 'currentParam' is PRINTERS,
   * the getExpression method returns the default printer ID.
   */
  @Test
  void getExpressionWhenPrintersParamReturnsPrinterId() {
    doReturn(PRN_1).when(hook).getDefaultPrinterForPrintJob();

    Map<String, String> req = new HashMap<>();
    req.put(CURRENT_PARAM, PrinterUtils.PRINTERS);

    assertEquals(PRN_1, hook.getExpression(req));

    obContextStatic.verify(() -> OBContext.setAdminMode(true));
    obContextStatic.verify(OBContext::restorePreviousMode);
  }

  /**
   * Tests that when the 'currentParam' is unknown,
   * the getExpression method returns an empty string.
   */
  @Test
  void getExpressionWhenUnknownParamReturnsEmptyString() {
    Map<String, String> req = new HashMap<>();
    req.put(CURRENT_PARAM, "OTHER_PARAM");

    String result = hook.getExpression(req);
    assertEquals("", result);

    obContextStatic.verify(() -> OBContext.setAdminMode(true));
    obContextStatic.verify(OBContext::restorePreviousMode);
  }

  /**
   * Tests that when an exception is thrown during evaluation,
   * the getExpression method returns null.
   */
  @Test
  void getExpressionWhenExceptionThrownReturnsNull() {
    doThrow(new RuntimeException("boom")).when(hook).getDefaultProviderForPrintJob();

    Map<String, String> req = new HashMap<>();
    req.put(CURRENT_PARAM, PrinterUtils.PROVIDER);

    assertNull(hook.getExpression(req));

    obContextStatic.verify(() -> OBContext.setAdminMode(true));
    obContextStatic.verify(OBContext::restorePreviousMode);
  }

  /**
   * Tests that if no providers are found,
   * the getDefaultProviderForPrintJob method returns an empty string.
   */
  @Test
  void getDefaultProviderForPrintJobWhenNoProvidersReturnsEmptyString() {
    @SuppressWarnings("unchecked") OBCriteria<Provider> providerCriteria = mock(OBCriteria.class);

    obdalStatic.when(OBDal::getInstance).thenReturn(obDal);
    when(obDal.createCriteria(Provider.class)).thenReturn(providerCriteria);
    when(providerCriteria.uniqueResult()).thenReturn(null);

    assertEquals("", hook.getDefaultProviderForPrintJob());

    verify(providerCriteria).setMaxResults(1);
    verify(providerCriteria, atLeastOnce()).addOrder(any(Order.class));
  }

  /**
   * Tests that if a provider is found,
   * the getDefaultProviderForPrintJob method returns its ID.
   */
  @Test
  void getDefaultProviderForPrintJobWhenProviderExistsReturnsItsId() {
    @SuppressWarnings("unchecked") OBCriteria<Provider> providerCriteria = mock(OBCriteria.class);

    when(provider.getId()).thenReturn(PROV_1);

    when(obDal.createCriteria(Provider.class)).thenReturn(providerCriteria);
    when(providerCriteria.uniqueResult()).thenReturn(provider);

    String result = hook.getDefaultProviderForPrintJob();
    assertEquals(PROV_1, result);

    verify(providerCriteria).setMaxResults(1);
    verify(providerCriteria, atLeastOnce()).addOrder(any(Order.class));
  }

  /**
   * Tests that if the provider returned is null,
   * the getDefaultPrinterForPrintJob method returns an empty string.
   */
  @Test
  void getDefaultPrinterForPrintJobWhenProviderIsNullReturnsEmptyString() {
    @SuppressWarnings("unchecked") OBCriteria<Provider> providerCriteria = mock(OBCriteria.class);

    when(obDal.createCriteria(Provider.class)).thenReturn(providerCriteria);
    when(obDal.get(eq(Provider.class), any())).thenReturn(null);

    assertEquals("", hook.getDefaultPrinterForPrintJob());
  }

  /**
   * Tests that if no printers are found,
   * the getDefaultPrinterForPrintJob method returns an empty string.
   */
  @Test
  void getDefaultPrinterForPrintJobWhenNoPrintersFoundReturnsEmptyString() {
    doReturn(PROV_1).when(hook).getDefaultProviderForPrintJob();

    when(obDal.get(Provider.class, PROV_1)).thenReturn(provider);

    @SuppressWarnings("unchecked") OBCriteria<Printer> printerCriteria = mock(OBCriteria.class);
    when(obDal.createCriteria(Printer.class)).thenReturn(printerCriteria);
    when(printerCriteria.uniqueResult()).thenReturn(null);

    assertEquals("", hook.getDefaultPrinterForPrintJob());

    verify(printerCriteria).setMaxResults(1);
    verify(printerCriteria, atLeast(1)).add(any());
    verify(printerCriteria, atLeast(1)).addOrder(any(Order.class));
  }

  /**
   * Tests that if a printer is found,
   * the getDefaultPrinterForPrintJob method returns its ID.
   */
  @Test
  void getDefaultPrinterForPrintJobWhenPrinterExistsReturnsItsId() {
    doReturn(PROV_1).when(hook).getDefaultProviderForPrintJob();

    when(obDal.get(Provider.class, PROV_1)).thenReturn(provider);

    @SuppressWarnings("unchecked") OBCriteria<Printer> printerCriteria = mock(OBCriteria.class);
    when(obDal.createCriteria(Printer.class)).thenReturn(printerCriteria);

    when(printer.getId()).thenReturn(PRN_1);
    when(printerCriteria.uniqueResult()).thenReturn(printer);

    assertEquals(PRN_1, hook.getDefaultPrinterForPrintJob());

    verify(printerCriteria).setMaxResults(1);
    verify(printerCriteria, atLeast(1)).add(any());
    verify(printerCriteria, atLeast(1)).addOrder(any(Order.class));
  }
}
