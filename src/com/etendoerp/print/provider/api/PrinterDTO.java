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
package com.etendoerp.print.provider.api;

/**
 * Data transfer object for a printer returned by a print provider.
 */
public final class PrinterDTO {
  private final String id;
  private final String name;
  private final boolean isDefault;

  /**
   * Constructor.
   *
   * @param id
   *     the unique identifier of this printer (never {@code null})
   * @param name
   *     the name of this printer (never {@code null})
   * @param isDefault
   *     true if this printer is the default printer for the provider
   */
  public PrinterDTO(String id, String name, boolean isDefault) {
    this.id = id;
    this.name = name;
    this.isDefault = isDefault;
  }

  /**
   * Returns the provider-specific identifier of this printer, which is unique
   * within the provider.
   *
   * @return the unique identifier of this printer (never {@code null})
   */
  public String getId() {
    return id;
  }

  /**
   * Returns the human-readable name of this printer.
   *
   * @return the name of this printer (never {@code null})
   */
  public String getName() {
    return name;
  }

  /**
   * Indicates whether this printer is the default printer for the provider.
   *
   * @return true if this printer is the default printer (false otherwise)
   */
  public boolean isDefault() {
    return isDefault;
  }

}