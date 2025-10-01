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
package com.etendoerp.print.provider.utils;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.smf.jobs.ActionResult;
import com.smf.jobs.Result;

/**
 * Unit tests for the fail and warning utility methods in {@link PrinterUtils}.
 * <p>
 * These tests verify that the fail and warning methods correctly set the type and message
 * on the provided {@link ActionResult} instance, and that they return the same instance for chaining.
 * <ul>
 *   <li>{@code fail}: Sets the result type to ERROR and assigns the provided detail as the message.</li>
 *   <li>{@code warning}: Sets the result type to WARNING and assigns the provided detail as the message.</li>
 * </ul>
 * The tests also check the behavior when the detail/message is null.
 */
@ExtendWith(MockitoExtension.class)
class PrinterUtilsActionResultTest {
  @Mock
  private ActionResult actionResult;

  /**
   * Verifies that fail sets the result type to ERROR, assigns the given detail as the message,
   * and returns the same ActionResult instance.
   */
  @Test
  void failSetsErrorTypeAndMessageReturnsSameInstance() {
    assertSame(actionResult, PrinterUtils.fail(actionResult, "Something went wrong"),
        "fail should return the same ActionResult instance");
    verify(actionResult).setType(Result.Type.ERROR);
    verify(actionResult).setMessage("Something went wrong");
    verifyNoMoreInteractions(actionResult);
  }

  /**
   * Verifies that fail accepts a null detail, sets the result type to ERROR,
   * and assigns null as the message.
   */
  @Test
  void failWhenDetailIsNullSetsErrorAndNullMessage() {
    assertSame(actionResult, PrinterUtils.fail(actionResult, null),
        "fail should return the same ActionResult instance");
    verify(actionResult).setType(Result.Type.ERROR);
    verify(actionResult).setMessage(null);
    verifyNoMoreInteractions(actionResult);
  }

  /**
   * Verifies that warning sets the result type to WARNING, assigns the given detail as the message,
   * and returns the same ActionResult instance.
   */
  @Test
  void warningSetsWarningTypeAndMessageReturnsSameInstance() {
    assertSame(actionResult, PrinterUtils.warning(actionResult, "Be careful"),
        "warning should return the same ActionResult instance");
    verify(actionResult).setType(Result.Type.WARNING);
    verify(actionResult).setMessage("Be careful");
    verifyNoMoreInteractions(actionResult);
  }

  /**
   * Verifies that warning accepts a null detail, sets the result type to WARNING,
   * and assigns null as the message.
   */
  @Test
  void warningWhenDetailIsNullSetsWarningAndNullMessage() {
    assertSame(actionResult, PrinterUtils.warning(actionResult, null),
        "warning should return the same ActionResult instance");
    verify(actionResult).setType(Result.Type.WARNING);
    verify(actionResult).setMessage(null);
    verifyNoMoreInteractions(actionResult);
  }
}
