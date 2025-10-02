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
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.print.provider.data.Provider;

/**
 * Unit tests for the {@link PrinterUtils#requireProvider(String)} utility method.
 * <p>
 * These tests verify the behavior of requireProvider when searching for a provider by its ID:
 * <ul>
 *   <li>When a provider with the given ID exists, it is returned.</li>
 *   <li>When no provider with the given ID exists, an {@link OBException} is thrown with a message containing the ID.</li>
 * </ul>
 * The tests use Mockito to mock dependencies and static methods.
 */
@ExtendWith(MockitoExtension.class)
class PrinterUtilsProviderTest {
  private MockedStatic<OBMessageUtils> obMsgStatic;
  @Mock
  private Provider provider;
  @Mock
  private OBDal obDal;

  @BeforeEach
  void setUp() {
    obMsgStatic = mockStatic(OBMessageUtils.class);
  }

  @AfterEach
  void tearDown() {
    if (obMsgStatic != null) obMsgStatic.close();
  }

  /**
   * Verifies that requireProvider returns the Provider when it exists for the given ID.
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
   * Verifies that requireProvider throws OBException when no Provider exists for the given ID,
   * and that the exception message contains the requested provider ID.
   */
  @Test
  void requireProviderWhenNotFoundThrowsOBException() {
    String providerId = "MISSING-999";
    obMsgStatic.when(() -> OBMessageUtils.getI18NMessage(org.mockito.ArgumentMatchers.anyString())).thenReturn(
        "Provider not found");
    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
      obDalStatic.when(OBDal::getInstance).thenReturn(obDal);
      when(obDal.get(Provider.class, providerId)).thenReturn(null);
      assertThrows(OBException.class, () -> PrinterUtils.requireProvider(providerId));
    }
  }
}
