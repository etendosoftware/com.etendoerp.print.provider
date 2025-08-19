package com.etendoerp.print.provider.api;

/**
 * Exception thrown when an error occurs in a print provider connector.
 */
public class PrintProviderException extends Exception {
  public PrintProviderException(String message) { super(message); }
  public PrintProviderException(String message, Throwable cause) { super(message, cause); }
}