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