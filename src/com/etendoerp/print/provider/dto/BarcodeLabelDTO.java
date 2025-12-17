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

/**
 * DTO for barcode label data.
 * This class holds the information needed to print a barcode label.
 */
public class BarcodeLabelDTO {

  private String barcode;
  private String barcodeWithSeparator;

  /**
   * Creates a new BarcodeLabelDTO.
   *
   * @param barcode
   *     the barcode to use
   * @param barcodeWithSeparator
   *     the barcode with separator to use
   */
  public BarcodeLabelDTO(String barcode, String barcodeWithSeparator) {
    this.barcode = barcode;
    this.barcodeWithSeparator = barcodeWithSeparator;
  }

  /**
   * Gets the barcode.
   *
   * @return the barcode
   */
  public String getBarcode() {
    return barcode;
  }

  /**
   * Sets the barcode.
   *
   * @param barcode
   *     the barcode to set
   */
  public void setBarcode(String barcode) {
    this.barcode = barcode;
  }

  /**
   * Gets the barcode with separator.
   *
   * @return the barcode with separator
   */
  public String getBarcodeWithSeparator() {
    return barcodeWithSeparator;
  }

  /**
   * Sets the barcode with separator.
   *
   * @param barcodeWithSeparator
   *     the barcode with separator to set
   */
  public void setBarcodeWithSeparator(String barcodeWithSeparator) {
    this.barcodeWithSeparator = barcodeWithSeparator;
  }
}
