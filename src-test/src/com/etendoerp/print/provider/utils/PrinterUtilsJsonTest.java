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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.base.exception.OBException;
import org.openbravo.erpCommon.utility.OBMessageUtils;

/**
 * Unit tests for JSON parameter validation utilities in {@link PrinterUtils}.
 * <p>
 * These tests cover the following methods:
 * <ul>
 *   <li>{@code requireParam}: Ensures a required key exists and is non-blank in a {@link JSONObject}.</li>
 *   <li>{@code requireJSONArray}: Ensures a required key exists and is a non-empty {@link JSONArray}.</li>
 *   <li>{@code requirePositiveInt}: Ensures a required key exists and is a positive integer.</li>
 * </ul>
 * The tests verify correct exception handling and value extraction for valid and invalid cases.
 */
@ExtendWith(MockitoExtension.class)
class PrinterUtilsJsonTest {
  private MockedStatic<OBMessageUtils> obMsgStatic;
  private static final String MISSING_PARAMETER = "Missing parameter: %s";
  private static final String MESSAGE_SHOULD_INCLUDE_KEY = "Message should include the missing key";
  private static final String PARAM = "param";
  private static final String ITEMS = "items";
  private static final String COPIES = "copies";

  @BeforeEach
  void setUp() {
    obMsgStatic = mockStatic(OBMessageUtils.class);
  }

  @AfterEach
  void tearDown() {
    if (obMsgStatic != null) obMsgStatic.close();
  }

  /**
   * Verifies that requireParam throws OBException when the key is missing in the JSON object.
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
   * Verifies that requireParam throws OBException when the value is an empty string.
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
   * Verifies that requireParam returns the value when present and non-blank.
   */
  @Test
  void requireParamWhenValuePresentReturnsIt() throws Exception {
    JSONObject json = new JSONObject().put(PARAM, "value-123");
    assertEquals("value-123", PrinterUtils.requireParam(json, PARAM));
  }

  /**
   * Verifies that requireJSONArray throws OBException when the key is missing in the JSON object.
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
   * Verifies that requireJSONArray throws OBException when the value is not a JSONArray.
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
   * Verifies that requireJSONArray throws OBException when the array is empty.
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
   * Verifies that requireJSONArray returns the array when present and non-empty.
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
   * Verifies that requirePositiveInt throws OBException when the key is missing in the JSON object.
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
   * Verifies that requirePositiveInt throws OBException when the value is zero.
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
   * Verifies that requirePositiveInt throws OBException when the value is negative.
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
   * Verifies that requirePositiveInt returns the value when it is positive.
   */
  @Test
  void requirePositiveIntWhenValueIsPositiveReturnsIt() throws Exception {
    JSONObject json = new JSONObject().put(COPIES, 3);
    assertEquals(3, PrinterUtils.requirePositiveInt(json, COPIES));
  }
}
