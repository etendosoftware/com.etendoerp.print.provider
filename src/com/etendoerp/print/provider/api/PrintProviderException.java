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

import org.openbravo.base.exception.OBException;

/**
 * Domain exception for Print Provider connectors.
 * <p>
 * Extends {@link OBException} to integrate with Etendo/OB standard error handling,
 * translation (AD_MESSAGES) and UI-friendly messages.
 */
public class PrintProviderException extends OBException {

  /**
   * Constructs a new PrintProviderException with the specified detail message.
   *
   * @param message
   *     the detail message to be shown to the user or logged
   */
  public PrintProviderException(String message) {
    super(message);
  }

  /**
   * Constructs a new PrintProviderException with the specified detail message and cause.
   *
   * @param message
   *     the detail message to be shown to the user or logged
   * @param cause
   *     the cause of the exception (can be retrieved later by the {@link Throwable#getCause()} method)
   */
  public PrintProviderException(String message, Throwable cause) {
    super(message, cause);
  }
}