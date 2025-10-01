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
import org.openbravo.model.ad.datamodel.Table;

/**
 * Unit tests for the PrinterUtils class related to Table entity operations.
 * <p>
 * This test class verifies the correct behavior of methods in PrinterUtils that interact with the Table entity.
 * It uses Mockito for mocking dependencies and static methods, and JUnit 5 for test structure and assertions.
 * <p>
 * Key aspects tested:
 * <ul>
 *   <li>Correct retrieval of a Table by its name.</li>
 *   <li>Exception handling when a Table is not found.</li>
 *   <li>Proper static mocking and resource management for OBMessageUtils and OBDal.</li>
 * </ul>
 * <p>
 * Each test ensures that static mocks are closed after execution to avoid conflicts between tests.
 */
@ExtendWith(MockitoExtension.class)
class PrinterUtilsTableTest {
  private static final String TABLE_NOT_FOUND = "Table not found: %s";
  @Mock
  private Table table;
  @Mock
  private OBDal obDal;
  private MockedStatic<OBMessageUtils> obMsgStatic = null;

  @BeforeEach
  void setUp() {
    obMsgStatic = mockStatic(OBMessageUtils.class);
  }

  @AfterEach
  void tearDown() {
    if (obMsgStatic != null) {
      obMsgStatic.close();
    }
  }

  /**
   * Ensures requireTableByName returns the Table when it exists for the given name.
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
   */
  @Test
  void requireTableByNameWhenNotFoundThrowsOBException() {
    String entityName = "NonExistingTable";
    obMsgStatic.when(() -> OBMessageUtils.getI18NMessage("ETPP_TableNotFound")).thenReturn(TABLE_NOT_FOUND);
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
}
