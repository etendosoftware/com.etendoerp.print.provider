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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.print.provider.api.PrintProviderException;
import com.etendoerp.print.provider.api.PrinterDTO;
import com.etendoerp.print.provider.data.Printer;
import com.etendoerp.print.provider.data.Provider;
import com.etendoerp.print.provider.strategy.PrintProviderStrategy;
import com.etendoerp.print.provider.utils.PrinterUtils;
import com.etendoerp.print.provider.utils.ProviderStrategyResolver;
import com.smf.jobs.Action;
import com.smf.jobs.ActionResult;
import com.smf.jobs.Result;

/**
 * Action to update the list of printers for a given provider.
 */
public class UpdatePrinters extends Action {

  private static final Logger log = LogManager.getLogger();

  /**
   * Entry point for the action. Expects a single parameter:
   * <ul>
   *   <li>{@link PrinterUtils#PARAMS}: a JSON object containing the ID of the provider
   *       to update the printers for ({@link PrinterUtils#PROVIDER}).</li>
   * </ul>
   *
   * <p>
   * The action will resolve the provider instance and the target strategy using the
   * utility methods in {@link PrinterUtils} and {@link ProviderStrategyResolver}.
   * Then it will fetch the list of printers from the provider, upsert them into the
   * database, and inactivate any printers that were not received from the provider.
   * </p>
   *
   * <p>
   * The result message will contain the number of printers created, updated and inactivated.
   * </p>
   *
   * <p>
   * If any of the printers fail to print, the action will return an error message.
   * </p>
   */
  @Override
  protected ActionResult action(JSONObject parameters, MutableBoolean isStopped) {
    final ActionResult result = new ActionResult();
    log.debug("Updating printers process started: {}", parameters);
    try {
      OBContext.setAdminMode(true);
      result.setType(Result.Type.SUCCESS);

      // --- Validate and read nested params (_params.Provider) ---
      final JSONObject paramsObj = parameters.optJSONObject(PrinterUtils.PARAMS);
      if (paramsObj == null) {
        throw new OBException(String.format(OBMessageUtils.getI18NMessage(
            PrinterUtils.MISSING_PARAMETER_MSG), PrinterUtils.PARAMS));
      }
      final String providerId = paramsObj.optString(PrinterUtils.PROVIDER, null);
      if (StringUtils.isBlank(providerId)) {
        throw new OBException(String.format(OBMessageUtils.getI18NMessage(
            PrinterUtils.MISSING_PARAMETER_MSG), PrinterUtils.PROVIDER));
      }

      // --- Resolve provider & strategy using the util ---
      final Provider provider = PrinterUtils.requireProvider(providerId);
      final PrintProviderStrategy strategy = ProviderStrategyResolver.resolveForProvider(provider);

      // --- Fetch from provider ---
      final List<PrinterDTO> remote = strategy.fetchPrinters(provider);

      // --- Upsert & inactivate ---
      final UpsertCounters counters = upsertPrinters(provider, remote);

      OBDal.getInstance().flush();

      result.setMessage(String.format(OBMessageUtils.getI18NMessage(
              "ETPP_PrintersUpdated"),
          counters.created, counters.updated, counters.inactivated));

      log.debug("Updating printers process finished: {}", result.getMessage());

      return result;

    } catch (PrintProviderException e) {
      log.error(e.getMessage(), e);
      OBDal.getInstance().rollbackAndClose();
      return PrinterUtils.fail(result,
          String.format(OBMessageUtils.getI18NMessage("ETPP_ProviderError"), e.getMessage()));
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      OBDal.getInstance().rollbackAndClose();
      return PrinterUtils.fail(result, e.getMessage());
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  /**
   * Updates the local printer catalog for the given provider.
   *
   * <p>This method:</p>
   * <ul>
   *   <li>Iterates over the given list of remote printers and:</li>
   *   <ul>
   *     <li>Creates a new local printer if it doesn't exist yet.</li>
   *     <li>Updates the local printer if it does exist.</li>
   *   </ul>
   *   <li>Inactivates local printers not returned by the provider.</li>
   * </ul>
   * <p>Returns a {@link UpsertCounters} instance holding the number of created,
   * updated and inactivated printers.</p>
   *
   * @param provider
   *     the provider of the printers to be updated.
   * @param printers
   *     the list of remote printers to be processed.
   * @return a {@link UpsertCounters} instance.
   */
  private static UpsertCounters upsertPrinters(Provider provider, List<PrinterDTO> printers) {

    final Set<String> seen = new HashSet<>();
    int created = 0;
    int updated = 0;
    int inactivated = 0;

    for (PrinterDTO dto : printers) {
      seen.add(dto.getId());

      // Find existing by (provider, value = externalId)
      final OBCriteria<Printer> criteria = OBDal.getInstance().createCriteria(Printer.class);
      criteria.add(Restrictions.eq(Printer.PROPERTY_PROVIDER, provider));
      criteria.add(Restrictions.eq(Printer.PROPERTY_VALUE, dto.getId()));
      criteria.setMaxResults(1);
      final Printer existing = (Printer) criteria.uniqueResult();

      if (existing == null) {
        // Create
        final Printer p = OBProvider.getInstance().get(Printer.class);
        p.setValue(dto.getId());
        p.setName(dto.getName());
        p.setDefault(dto.isDefault());
        p.setProvider(provider);
        OBDal.getInstance().save(p);
        created++;
      } else {
        // Update
        existing.setName(dto.getName());
        existing.setDefault(dto.isDefault());
        OBDal.getInstance().save(existing);
        updated++;
      }
    }

    // Inactivate those not returned by provider
    final OBCriteria<Printer> toInactivate = OBDal.getInstance().createCriteria(Printer.class);
    toInactivate.add(Restrictions.eq(Printer.PROPERTY_PROVIDER, provider));
    if (!seen.isEmpty()) {
      toInactivate.add(Restrictions.not(Restrictions.in(Printer.PROPERTY_VALUE, seen)));
    }
    for (Printer printer : toInactivate.list()) {
      if (Boolean.TRUE.equals(printer.isActive())) {
        printer.setActive(false);
        OBDal.getInstance().save(printer);
        inactivated++;
      }
    }

    return new UpsertCounters(created, updated, inactivated);
  }

  /**
   * Immutable container for upsert counts.
   */
  private static final class UpsertCounters {

    /** Number of printers created. */
    public final int created;

    /** Number of printers updated. */
    public final int updated;

    /** Number of printers inactivated. */
    public final int inactivated;

    /**
     * Creates an immutable container for upsert results.
     *
     * @param created number of printers created
     * @param updated number of printers updated
     * @param inactivated number of printers inactivated
     */
    UpsertCounters(int created, int updated, int inactivated) {
      this.created = created;
      this.updated = updated;
      this.inactivated = inactivated;
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>This implementation returns the entity class of {@link Printer}.</p>
   */
  @Override
  protected Class<Printer> getInputClass() {
    return Printer.class;
  }
}