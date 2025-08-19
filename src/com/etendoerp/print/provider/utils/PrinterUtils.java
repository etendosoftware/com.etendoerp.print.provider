package com.etendoerp.print.provider.utils;

/**
 * Utility holder for well-known parameter names and message keys used by
 * Print Provider processes/actions (e.g. {@code UpdatePrinters} and
 * {@code SendGeneratedLabelToPrinter}).
 *
 * <p>This class is a namespaced, type-safe alternative to scattering string
 * literals across the codebase. All members are {@code public static final}
 * and the class is non-instantiable.</p>
 *
 * <p><strong>Thread-safety:</strong> immutable constants; safe for concurrent use.</p>
 *
 * @see com.etendoerp.print.provider.action.UpdatePrinters
 * @see com.etendoerp.print.provider.action.SendGeneratedLabelToPrinter
 */
public class PrinterUtils {

  /**
   * Private constructor to prevent instantiation of the utility class.
   */
  protected PrinterUtils() {
    throw new UnsupportedOperationException("Utility class");
  }

  public static final String PARAMS = "_params";
  public static final String PROVIDER = "Provider";
  public static final String ENTITY_NAME = "_entityName";
  public static final String RECORDS = "recordIds";
  public static final String PRINTERS = "Printers";
  public static final String MISSING_PARAMETER_MSG = "ETPP_MissingParameter";
}
