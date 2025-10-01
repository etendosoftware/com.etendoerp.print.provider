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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.print.provider.data.Provider;
import com.etendoerp.print.provider.data.ProviderParam;

/**
 * Unit tests for the PrinterUtils class related to Provider entity operations.
 * <p>
 * This test class verifies the correct behavior of methods in PrinterUtils that interact with the Provider and ProviderParam entities.
 * It uses Mockito for mocking dependencies and static methods, and JUnit 5 for test structure and assertions.
 * <p>
 * Key aspects tested:
 * <ul>
 *   <li>Correct retrieval of a Provider by its ID.</li>
 *   <li>Exception handling when a Provider is not found.</li>
 *   <li>Retrieval and validation of ProviderParam entities.</li>
 *   <li>Exception handling for missing or invalid ProviderParam.</li>
 *   <li>Proper static mocking and resource management for OBMessageUtils and OBDal.</li>
 * </ul>
 * <p>
 * Each test ensures that static mocks are closed after execution to avoid conflicts between tests.
 */
@ExtendWith(MockitoExtension.class)
class PrinterUtilsProviderTest {
  private static final String PROVIDER_NOT_FOUND = "Provider not found";
  private static final String API_KEY = "API_KEY";
  private static final String PRINTERS_URL = "PRINTERS_URL";
  private MockedStatic<OBMessageUtils> obMsgStatic = null;

  @Mock
  private Provider provider;
  @Mock
  private ProviderParam providerParam;
  @Mock
  private OBDal obDal;

  @BeforeEach
  void setUp() {
    obMsgStatic = mockStatic(OBMessageUtils.class);
    lenient().when(OBMessageUtils.messageBD(anyString())).thenReturn("Provider parameter without content: %s");
  }

  @AfterEach
  void tearDown() {
    if (obMsgStatic != null) {
      obMsgStatic.close();
    }
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
   */
  @Test
  void requireProviderWhenNotFoundThrowsOBException() {
    String providerId = "MISSING-999";
    obMsgStatic.when(() -> OBMessageUtils.getI18NMessage(anyString())).thenReturn(PROVIDER_NOT_FOUND);
    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
      obDalStatic.when(OBDal::getInstance).thenReturn(obDal);
      when(obDal.get(Provider.class, providerId)).thenReturn(null);
      OBException ex = assertThrows(OBException.class, () -> PrinterUtils.requireProvider(providerId));
      assertTrue(ex.getMessage().contains(PROVIDER_NOT_FOUND));
    }
  }

  /**
   * Ensures getRequiredParam throws OBException when the Provider is null.
   */
  @Test
  void getRequiredParamWhenProviderNullThrowsOBException() {
    obMsgStatic.when(() -> OBMessageUtils.getI18NMessage(anyString())).thenReturn(PROVIDER_NOT_FOUND);
    OBException ex = assertThrows(OBException.class, () -> PrinterUtils.getRequiredParam(null, API_KEY));
    assertTrue(ex.getMessage().contains(PROVIDER_NOT_FOUND));
  }

  /**
   * Ensures getRequiredParam returns the ProviderParam when it exists for the given provider and key.
   */
  @Test
  void getRequiredParamWhenFoundReturnsParam() {
    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
      obDalStatic.when(OBDal::getInstance).thenReturn(obDal);
      @SuppressWarnings("unchecked") OBCriteria<ProviderParam> criteria = mock(OBCriteria.class);
      when(obDal.createCriteria(ProviderParam.class)).thenReturn(criteria);
      when(criteria.add(any(org.hibernate.criterion.Criterion.class))).thenReturn(criteria);
      when(criteria.uniqueResult()).thenReturn(providerParam);
      ProviderParam result = PrinterUtils.getRequiredParam(provider, API_KEY);
      assertSame(providerParam, result);
    }
  }

  /**
   * Ensures getRequiredParam throws OBException when the ProviderParam is not found for the given provider and key.
   */
  @Test
  void getRequiredParamWhenNotFoundThrowsOBException() {
    obMsgStatic.when(() -> OBMessageUtils.getI18NMessage("ETPP_ProviderParamNotFound")).thenReturn(
        "Provider param not found: %s");
    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
      obDalStatic.when(OBDal::getInstance).thenReturn(obDal);
      @SuppressWarnings("unchecked") OBCriteria<ProviderParam> criteria = mock(OBCriteria.class);
      when(obDal.createCriteria(ProviderParam.class)).thenReturn(criteria);
      when(criteria.add(any(org.hibernate.criterion.Criterion.class))).thenReturn(criteria);
      when(criteria.uniqueResult()).thenReturn(null);
      OBException ex = assertThrows(OBException.class, () -> PrinterUtils.getRequiredParam(provider, PRINTERS_URL));
      assertTrue(ex.getMessage().contains(PRINTERS_URL));
    }
  }
}
