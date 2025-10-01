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