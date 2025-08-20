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
   * Plain message (already composed).
   */
  public PrintProviderException(String message) {
    super(message);
  }

  /**
   * Plain message with cause.
   */
  public PrintProviderException(String message, Throwable cause) {
    super(message, cause);
  }
}