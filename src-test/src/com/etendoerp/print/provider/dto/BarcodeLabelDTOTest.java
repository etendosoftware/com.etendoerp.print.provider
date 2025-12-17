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
package com.etendoerp.print.provider.dto;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for BarcodeLabelDTO class.
 * Validates the DTO behavior, constructor, getters, and setters.
 */
class BarcodeLabelDTOTest {

  private static final String BARCODE_VALUE = "123456789012";
  private static final String BARCODE_WITH_SEPARATOR = "1234-5678-9012";

  private BarcodeLabelDTO dto;

  /**
   * Sets up the test environment.
   * Initializes the BarcodeLabelDTO with test values.
   */
  @BeforeEach
  void setUp() {
    dto = new BarcodeLabelDTO(BARCODE_VALUE, BARCODE_WITH_SEPARATOR);
  }

  /**
   * Tests the constructor of BarcodeLabelDTO.
   * Validates that the constructor initializes the object with the provided values.
   */
  @Test
  void testConstructor() {
    assertThat("DTO should not be null", dto, is(notNullValue()));
    assertThat("Barcode should match constructor value",
        dto.getBarcode(), is(equalTo(BARCODE_VALUE)));
    assertThat("Barcode with separator should match constructor value",
        dto.getBarcodeWithSeparator(), is(equalTo(BARCODE_WITH_SEPARATOR)));
  }

  /**
   * Tests the constructor of BarcodeLabelDTO with null values.
   * Validates that the constructor initializes the object with null values.
   */
  @Test
  void testConstructorWithNullValues() {
    final BarcodeLabelDTO nullDto = new BarcodeLabelDTO(null, null);

    assertThat("DTO should accept null barcode",
        nullDto.getBarcode(), is(nullValue()));
    assertThat("DTO should accept null barcode with separator",
        nullDto.getBarcodeWithSeparator(), is(nullValue()));
  }

  /**
   * Tests the constructor of BarcodeLabelDTO with empty strings.
   * Validates that the constructor initializes the object with empty strings.
   */
  @Test
  void testConstructorWithEmptyStrings() {
    final BarcodeLabelDTO emptyDto = new BarcodeLabelDTO("", "");

    assertThat("DTO should accept empty barcode",
        emptyDto.getBarcode(), is(equalTo("")));
    assertThat("DTO should accept empty barcode with separator",
        emptyDto.getBarcodeWithSeparator(), is(equalTo("")));
  }

  /**
   * Tests the getBarcode method of BarcodeLabelDTO.
   * Validates that the method returns the correct barcode value.
   */
  @Test
  void testGetBarcode() {
    assertThat("Get barcode should return correct value",
        dto.getBarcode(), is(equalTo(BARCODE_VALUE)));
  }

  /**
   * Tests the setBarcode method of BarcodeLabelDTO.
   * Validates that the method updates the barcode value.
   */
  @Test
  void testSetBarcode() {
    final String newBarcode = "987654321098";
    dto.setBarcode(newBarcode);

    assertThat("Barcode should be updated",
        dto.getBarcode(), is(equalTo(newBarcode)));
  }

  /**
   * Tests the setBarcode method of BarcodeLabelDTO with null values.
   * Validates that the method updates the barcode value to null.
   */
  @Test
  void testSetBarcodeToNull() {
    dto.setBarcode(null);

    assertThat("Barcode should be set to null",
        dto.getBarcode(), is(nullValue()));
  }

  /**
   * Tests the setBarcode method of BarcodeLabelDTO with empty strings.
   * Validates that the method updates the barcode value to an empty string.
   */
  @Test
  void testSetBarcodeToEmpty() {
    dto.setBarcode("");

    assertThat("Barcode should be set to empty string",
        dto.getBarcode(), is(equalTo("")));
  }

  /**
   * Tests the getBarcodeWithSeparator method of BarcodeLabelDTO.
   * Validates that the method returns the correct barcode with separator value.
   */
  @Test
  void testGetBarcodeWithSeparator() {
    assertThat("Get barcode with separator should return correct value",
        dto.getBarcodeWithSeparator(), is(equalTo(BARCODE_WITH_SEPARATOR)));
  }

  /**
   * Tests the setBarcodeWithSeparator method of BarcodeLabelDTO.
   * Validates that the method updates the barcode with separator value.
   */
  @Test
  void testSetBarcodeWithSeparator() {
    final String newBarcodeWithSeparator = "9876-5432-1098";
    dto.setBarcodeWithSeparator(newBarcodeWithSeparator);

    assertThat("Barcode with separator should be updated",
        dto.getBarcodeWithSeparator(), is(equalTo(newBarcodeWithSeparator)));
  }

  /**
   * Tests the setBarcodeWithSeparator method of BarcodeLabelDTO with null values.
   * Validates that the method updates the barcode with separator value to null.
   */
  @Test
  void testSetBarcodeWithSeparatorToNull() {
    dto.setBarcodeWithSeparator(null);

    assertThat("Barcode with separator should be set to null",
        dto.getBarcodeWithSeparator(), is(nullValue()));
  }

  /**
   * Tests the setBarcodeWithSeparator method of BarcodeLabelDTO with empty strings.
   * Validates that the method updates the barcode with separator value to an empty string.
   */
  @Test
  void testSetBarcodeWithSeparatorToEmpty() {
    dto.setBarcodeWithSeparator("");

    assertThat("Barcode with separator should be set to empty string",
        dto.getBarcodeWithSeparator(), is(equalTo("")));
  }

  /**
   * Tests multiple set operations on BarcodeLabelDTO.
   * Validates that the method updates the barcode and barcode with separator values.
   */
  @Test
  void testMultipleSetOperations() {
    dto.setBarcode("111111111111");
    dto.setBarcodeWithSeparator("1111-1111-1111");

    assertThat("First barcode update should persist",
        dto.getBarcode(), is(equalTo("111111111111")));
    assertThat("First separator update should persist",
        dto.getBarcodeWithSeparator(), is(equalTo("1111-1111-1111")));

    dto.setBarcode("222222222222");
    dto.setBarcodeWithSeparator("2222-2222-2222");

    assertThat("Second barcode update should overwrite",
        dto.getBarcode(), is(equalTo("222222222222")));
    assertThat("Second separator update should overwrite",
        dto.getBarcodeWithSeparator(), is(equalTo("2222-2222-2222")));
  }

  /**
   * Tests independent field updates on BarcodeLabelDTO.
   * Validates that the method updates the barcode and barcode with separator values.
   */
  @Test
  void testIndependentFieldUpdates() {
    final String originalBarcode = dto.getBarcode();
    dto.setBarcodeWithSeparator("NEW-SEPARATOR");

    assertThat("Updating separator should not affect barcode",
        dto.getBarcode(), is(equalTo(originalBarcode)));
    assertThat("Separator should be updated",
        dto.getBarcodeWithSeparator(), is(equalTo("NEW-SEPARATOR")));

    final String currentSeparator = dto.getBarcodeWithSeparator();
    dto.setBarcode("NEWBARCODE");

    assertThat("Updating barcode should not affect separator",
        dto.getBarcodeWithSeparator(), is(equalTo(currentSeparator)));
    assertThat("Barcode should be updated",
        dto.getBarcode(), is(equalTo("NEWBARCODE")));
  }

  /**
   * Tests with special characters on BarcodeLabelDTO.
   * Validates that the method updates the barcode and barcode with separator values.
   */
  @Test
  void testWithSpecialCharacters() {
    final String specialBarcode = "ABC-123!@#$%";
    final String specialSeparator = "ABC|123|!@#|$%";

    dto.setBarcode(specialBarcode);
    dto.setBarcodeWithSeparator(specialSeparator);

    assertThat("Should handle special characters in barcode",
        dto.getBarcode(), is(equalTo(specialBarcode)));
    assertThat("Should handle special characters in separator",
        dto.getBarcodeWithSeparator(), is(equalTo(specialSeparator)));
  }

  /**
   * Tests with long strings on BarcodeLabelDTO.
   * Validates that the method updates the barcode and barcode with separator values.
   */
  @Test
  void testWithLongStrings() {
    final String longBarcode = "1".repeat(1000);
    final String longSeparator = "2-".repeat(500);

    dto.setBarcode(longBarcode);
    dto.setBarcodeWithSeparator(longSeparator);

    assertThat("Should handle long barcode strings",
        dto.getBarcode(), is(equalTo(longBarcode)));
    assertThat("Should handle long separator strings",
        dto.getBarcodeWithSeparator(), is(equalTo(longSeparator)));
  }
}
