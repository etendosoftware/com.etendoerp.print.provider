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
package com.etendoerp.print.provider.action;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.datamodel.Table;

import com.etendoerp.print.provider.api.PrintProviderException;
import com.etendoerp.print.provider.data.Printer;
import com.etendoerp.print.provider.data.Provider;
import com.etendoerp.print.provider.data.TemplateLine;
import com.etendoerp.print.provider.strategy.PrintProviderStrategy;
import com.etendoerp.print.provider.utils.PrinterUtils;
import com.etendoerp.print.provider.utils.ProviderStrategyResolver;
import com.smf.jobs.Action;
import com.smf.jobs.ActionResult;
import com.smf.jobs.Result;

/**
 * Action to generate labels for a given table and send them to a printer.
 */
public class SendGeneratedLabelToPrinter extends Action {

  private static final Logger log = LogManager.getLogger();

  /**
   * Processes the given {@code parameters} by generating labels for the given
   * {@code entityName} records and sending them to the selected printer. The
   * following parameters are expected:
   * <ul>
   *   <li>{@code provider}: the ID of the provider to use (e.g. a PrintNode
   *       provider)</li>
   *   <li>{@code entityName}: the name of the table to generate labels for
   *       (e.g. {@code C_Order}</li>
   *   <li>{@code records}: a JSON array of record IDs to generate labels for</li>
   *   <li>{@code printers}: the ID of the printer to send the labels to</li>
   *   <li>{@code numberOfCopies}: the number of copies to print</li>
   * </ul>
   *
   * <p>This action will log errors if there are any issues generating or sending
   * the labels but will not throw an exception. Instead, it will return an
   * {@link ActionResult} with the result of the action.</p>
   **/
  @Override
  protected ActionResult action(JSONObject parameters, MutableBoolean isStopped) {
    final ActionResult res = new ActionResult();
    res.setType(Result.Type.SUCCESS);
    log.debug("SendGeneratedLabelToPrinter process started: {}", parameters);
    try {
      OBContext.setAdminMode(true);

      // --- Validate & read params (via PrinterUtils) ---
      final String providerId = PrinterUtils.requireParam(parameters, PrinterUtils.PROVIDER);
      final String entityName = PrinterUtils.requireParam(parameters, PrinterUtils.ENTITY_NAME);
      final JSONArray records = PrinterUtils.requireJSONArray(parameters, PrinterUtils.RECORDS);
      final String printerId = PrinterUtils.requireParam(parameters, PrinterUtils.PRINTERS);
      final int numberOfCopies = PrinterUtils.requirePositiveInt(parameters, PrinterUtils.NUMBER_OF_COPIES);

      // --- Resolve DAL entities / strategy ---
      final Provider provider = PrinterUtils.requireProvider(providerId);
      final PrintProviderStrategy strategy = ProviderStrategyResolver.resolveForProvider(provider);
      final Printer targetPrinter = PrinterUtils.requirePrinter(printerId);
      final Table table = PrinterUtils.requireTableByName(entityName);
      final TemplateLine templateLine = PrinterUtils.resolveTemplateLineFor(table);

      // --- Iterate over records: generate label + send to printer ---
      final List<String> jobIds = new ArrayList<>(records.length());
      int failureCount = 0;

      for (int i = 0; i < records.length(); i++) {
        final String recordId = records.getString(i);
        File label = null;
        try {
          label = strategy.generateLabel(provider, table, recordId, templateLine, parameters);
          final String jobId = strategy.sendToPrinter(provider, targetPrinter, numberOfCopies, label);
          jobIds.add(StringUtils.defaultIfBlank(jobId, "-"));
        } catch (Exception e) {
          log.error("Error printing record {} on printer {}: {}", recordId, printerId, e.getMessage(), e);
          failureCount++;
        } finally {
          safeDelete(label, recordId);
        }
      }

      // --- Build result message ---
      if (failureCount > 0 && failureCount == records.length()) {
        return PrinterUtils.fail(res, OBMessageUtils.getI18NMessage("ETPP_AllPrintJobsFailed"));
      } else if (failureCount > 0) {
        return PrinterUtils.warning(res,
            String.format(OBMessageUtils.getI18NMessage("ETPP_SomePrintJobsFailed"), failureCount));
      }

      final StringJoiner sj = new StringJoiner(", ");
      jobIds.forEach(sj::add);
      final String joinedIds = sj.toString();

      res.setMessage(String.format(OBMessageUtils.getI18NMessage("ETPP_PrintJobSent"), joinedIds));

      log.debug("SendGeneratedLabelToPrinter process finished: {}", res.getMessage());
      return res;

    } catch (PrintProviderException e) {
      log.error(e.getMessage(), e);
      OBDal.getInstance().rollbackAndClose();
      return PrinterUtils.fail(res, String.format(OBMessageUtils.getI18NMessage("ETPP_ProviderError"), e.getMessage()));
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      OBDal.getInstance().rollbackAndClose();
      return PrinterUtils.fail(res, e.getMessage());
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  /**
   * Deletes the temp file using NIO, logging the outcome with context.
   */
  private void safeDelete(File file, String recordId) {
    if (file == null) return;

    final Path p = file.toPath();
    try {
      final boolean deleted = Files.deleteIfExists(p);
      if (!deleted) {
        // File was already removed or never existed
        log.debug("Temp label file for record {} was not present: {}", recordId, p);
      }
    } catch (IOException ex) {
      log.warn("Could not delete temp label file for record {} ({}): {}", recordId, p, ex.getMessage());
    }
  }

  /**
   * This method returns the input class for this action.
   *
   * @return the input class for this action, which is {@code BaseOBObject}
   */
  @Override
  protected Class<BaseOBObject> getInputClass() {
    return BaseOBObject.class;
  }
}