package com.etendoerp.print.provider.utils;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.print.provider.data.Printer;

/**
 * Unit tests for the PrinterUtils class related to Printer entity operations.
 * <p>
 * This test class verifies the correct behavior of methods in PrinterUtils that interact with the Printer entity.
 * It uses Mockito for mocking dependencies and static methods, and JUnit 5 for test structure and assertions.
 * <p>
 * Key aspects tested:
 * <ul>
 *   <li>Correct retrieval of a Printer by its ID.</li>
 *   <li>Exception handling when a Printer is not found.</li>
 *   <li>Proper static mocking and resource management for OBMessageUtils and OBDal.</li>
 * </ul>
 * <p>
 * Each test ensures that static mocks are closed after execution to avoid conflicts between tests.
 */
@ExtendWith(MockitoExtension.class)
class PrinterUtilsPrinterTest {
  private static final String PRINTER_NOT_FOUND = "Printer not found: %s";
  @Mock
  private Printer printer;
  @Mock
  private OBDal obDal;
  private MockedStatic<OBMessageUtils> obMsgStatic = null;

  @BeforeEach
  void setUp() {
    obMsgStatic = mockStatic(OBMessageUtils.class);
  }

  @AfterEach
  void tearDown() {
    if (obMsgStatic != null) {
      obMsgStatic.close();
    }
  }

  /**
   * Ensures requirePrinter returns the Printer when it exists for the given id.
   */
  @Test
  void requirePrinterWhenFoundReturnsPrinter() {
    String printerId = "PRN-123";
    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
      obDalStatic.when(OBDal::getInstance).thenReturn(obDal);
      when(obDal.get(Printer.class, printerId)).thenReturn(printer);
      assertSame(printer, PrinterUtils.requirePrinter(printerId));
    }
  }

  /**
   * Ensures requirePrinter throws OBException when no Printer exists for the given id.
   */
  @Test
  void requirePrinterWhenNotFoundThrowsOBException() {
    String printerId = "MISSING-999";
    obMsgStatic.when(() -> OBMessageUtils.getI18NMessage("ETPP_PrinterNotFoundById")).thenReturn(PRINTER_NOT_FOUND);
    try (MockedStatic<OBDal> obDalStatic = mockStatic(OBDal.class)) {
      obDalStatic.when(OBDal::getInstance).thenReturn(obDal);
      when(obDal.get(Printer.class, printerId)).thenReturn(null);
      OBException ex = assertThrows(OBException.class, () -> PrinterUtils.requirePrinter(printerId));
      assertTrue(ex.getMessage().contains(printerId));
    }
  }
}
