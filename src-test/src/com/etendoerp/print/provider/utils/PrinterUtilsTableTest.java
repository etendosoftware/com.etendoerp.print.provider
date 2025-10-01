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

import static org.junit.jupiter.api.Assertions.assertNull;
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

import com.etendoerp.print.provider.data.Template;
import com.etendoerp.print.provider.data.TemplateLine;

/**
 * Unit tests for table and template resolution utilities in {@link PrinterUtils}.
 * <p>
 * These tests cover:
 * <ul>
 *   <li>{@code requireTableByName}: Ensures correct retrieval or exception when searching for a table by name.</li>
 *   <li>{@code resolveTemplateLineFor}: Ensures correct retrieval of a template line for a table, or proper exception/null when not found.</li>
 * </ul>
 * The tests use Mockito to mock dependencies and static methods, and verify both positive and negative scenarios.
 */
@ExtendWith(MockitoExtension.class)
class PrinterUtilsTableTest {
  private MockedStatic<OBMessageUtils> obMsgStatic;
  @Mock
  private Table table;
  @Mock
  private Template template;
  @Mock
  private TemplateLine templateLine;
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
   * Verifies that requireTableByName returns the Table when it exists for the given entity name.
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
   * Verifies that requireTableByName throws OBException when no Table is found for the given name,
   * and that the exception message contains the entity name.
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
   * Verifies that resolveTemplateLineFor throws OBException when no Template exists for the given table,
   * and that the exception message contains the table name.
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
      when(templateCrit.add(any(org.hibernate.criterion.Criterion.class))).thenReturn(templateCrit);
      when(templateCrit.uniqueResult()).thenReturn(null);
      OBException ex = assertThrows(OBException.class, () -> PrinterUtils.resolveTemplateLineFor(table));
      assertTrue(ex.getMessage().contains("Order"));
    }
  }

  /**
   * Verifies that resolveTemplateLineFor returns the TemplateLine when found for the table.
   */
  @Test
  void resolveTemplateLineForWhenFoundReturnsBestTemplateLine() {
    try (MockedStatic<OBDal> obDalStatic = mockResolveTemplateCriteria(template, templateLine)) {
      assertSame(templateLine, PrinterUtils.resolveTemplateLineFor(table));
    }
  }

  /**
   * Verifies that resolveTemplateLineFor returns null when a Template exists but no TemplateLine is found.
   */
  @Test
  void resolveTemplateLineForWhenNoTemplateLineReturnsNull() {
    try (MockedStatic<OBDal> obDalStatic = mockResolveTemplateCriteria(template, null)) {
      assertNull(PrinterUtils.resolveTemplateLineFor(table));
    }
  }

  /**
   * Utility to mock OBDal/criteria for template and template line resolution.
   *
   * @param template
   *     Template to return from Template criteria (or null)
   * @param lineToReturn
   *     TemplateLine to return from TemplateLine criteria (or null)
   * @return active MockedStatic for OBDal that the caller must close
   */
  private MockedStatic<OBDal> mockResolveTemplateCriteria(Template template, TemplateLine lineToReturn) {
    MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class);
    obDalStatic.when(OBDal::getInstance).thenReturn(obDal);
    @SuppressWarnings("unchecked") OBCriteria<Template> templateCrit = mock(OBCriteria.class);
    when(obDal.createCriteria(Template.class)).thenReturn(templateCrit);
    when(templateCrit.add(any(org.hibernate.criterion.Criterion.class))).thenReturn(templateCrit);
    when(templateCrit.setMaxResults(org.mockito.ArgumentMatchers.anyInt())).thenReturn(templateCrit);
    when(templateCrit.uniqueResult()).thenReturn(template);
    @SuppressWarnings("unchecked") OBCriteria<TemplateLine> lineCrit = mock(OBCriteria.class);
    when(obDal.createCriteria(TemplateLine.class)).thenReturn(lineCrit);
    when(lineCrit.add(any(org.hibernate.criterion.Criterion.class))).thenReturn(lineCrit);
    when(lineCrit.addOrder(any(org.hibernate.criterion.Order.class))).thenReturn(lineCrit);
    when(lineCrit.setMaxResults(org.mockito.ArgumentMatchers.anyInt())).thenReturn(lineCrit);
    when(lineCrit.uniqueResult()).thenReturn(lineToReturn);
    return obDalStatic;
  }
}
