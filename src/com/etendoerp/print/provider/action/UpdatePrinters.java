package com.etendoerp.print.provider.action;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.structure.BaseOBObject;
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
 * Synchronizes the local printer catalog ({@code ETPP_Printer}) with the devices exposed
 * by a selected print provider.
 *
 * <p><strong>Purpose</strong><br>
 * Keeps Etendo’s printer list aligned with the remote provider. It creates or updates
 * printers returned by the provider and inactivates any local printers that are no longer
 * present upstream.</p>
 *
 * <p><strong>Expected parameters (popup JSON)</strong><br>
 * Inside {@code _params}:
 * <ul>
 *   <li>{@code Provider} – UUID of {@code ETPP_Provider} to synchronize.</li>
 * </ul>
 * See {@link com.etendoerp.print.provider.utils.PrinterUtils#PARAMS} and
 * {@link com.etendoerp.print.provider.utils.PrinterUtils#PROVIDER}.</p>
 *
 * @see com.etendoerp.print.provider.utils.PrinterUtils
 * @see com.etendoerp.print.provider.utils.ProviderStrategyResolver
 * @see com.etendoerp.print.provider.strategy.PrintProviderStrategy
 * @see com.etendoerp.print.provider.data.Provider
 * @see com.etendoerp.print.provider.data.Printer
 */
public class UpdatePrinters extends Action {

  /**
   * Job action to synchronize printers from a specific provider.
   *
   * <p>This action is meant to be scheduled periodically to keep the
   * <code>ETPP_Printer</code> table up-to-date with the printers exposed by
   * the configured printing provider.</p>
   *
   * <p>It fetches the list of printers from the provider, then upserts them
   * into <code>ETPP_Printer</code> and inactivates those present in the DB
   * but not returned by the provider.</p>
   *
   * <p>On success, the action logs a message with the number of created,
   * updated and inactivated printers.</p>
   *
   * <h3>Parameters</h3>
   * <ul>
   *   <li>{@code {@link PrinterUtils#PROVIDER}} (UUID) of the print provider to
   *       synchronize.</li>
   * </ul>
   *
   * @param parameters
   *     JSON object with the provider id
   * @param isStopped
   *     flag to indicate if the job was stopped externally
   * @return ActionResult indicating success or failure, with a message
   */
  @Override
  protected ActionResult action(JSONObject parameters, MutableBoolean isStopped) {
    ActionResult result = new ActionResult();

    try {
      OBContext.setAdminMode();
      result.setType(Result.Type.SUCCESS);

      checkMandatoryParameters(parameters);

      // 1) Resolve provider id from params
      JSONObject params = parameters.getJSONObject(PrinterUtils.PARAMS);
      final String providerId = params.getString(PrinterUtils.PROVIDER);

      // 2) Load provider & resolve strategy
      final Provider provider = OBDal.getInstance().get(Provider.class, providerId);
      if (provider == null) {
        throw new OBException(OBMessageUtils.messageBD("ETPP_ProviderNotFound"));
      }
      final PrintProviderStrategy strategy = ProviderStrategyResolver.resolveForProvider(provider);

      // 3) Fetch printers from provider
      final List<PrinterDTO> list = strategy.fetchPrinters(provider);

      // 4) Upsert into ETPP_PRINTER and inactivate missing ones
      UpsertCounters counters = upsertPrinters(provider, list);

      OBDal.getInstance().flush();

      String msg = OBMessageUtils.messageBD("ETPP_PrintersUpdated") + " " +
          "Created=" + counters.created + ", " +
          "Updated=" + counters.updated + ", " +
          "Inactivated=" + counters.inactivated;
      result.setMessage(msg);

      return result;

    } catch (PrintProviderException e) {
      OBDal.getInstance().rollbackAndClose();
      result.setType(Result.Type.ERROR);
      result.setMessage(String.format(
          OBMessageUtils.messageBD("ETPP_ProviderErrorPrefix"), e.getMessage()));
      return result;

    } catch (Exception e) {
      OBDal.getInstance().rollbackAndClose();
      result.setType(Result.Type.ERROR);
      result.setMessage(e.getMessage());
      return result;

    } finally {
      OBContext.restorePreviousMode();
    }
  }

  /**
   * Verifies that the required parameters for this action are present in the input JSON object.
   *
   * <p>The action requires the following parameters:</p>
   * <ul>
   *   <li>{@code Provider}: id of the print provider</li>
   * </ul>
   *
   * <p>If any of the required parameters are missing, an OBException is thrown.</p>
   *
   * @param parameters
   *     JSON object containing the action input parameters
   * @throws OBException
   *     if any required parameter is missing
   */
  private void checkMandatoryParameters(JSONObject parameters) {
    try {
      JSONObject params = parameters.getJSONObject(PrinterUtils.PARAMS);
      if (params == null) {
        throw new OBException(String.format(
            OBMessageUtils.messageBD("ETPP_MissingParameter"), PrinterUtils.PARAMS));
      }
      final String providerIdParam = params.getString(PrinterUtils.PROVIDER);

      if (StringUtils.isBlank(providerIdParam)) {
        throw new OBException(String.format(
            OBMessageUtils.messageBD("ETPP_MissingParameter"), PrinterUtils.PROVIDER));
      }
    } catch (JSONException e) {
      throw new OBException(e.getMessage());
    }
  }

  /**
   * Upserts printers returned by the print provider into the local database.
   *
   * <p>This method creates new records for printers not yet present in the local
   * database and updates existing ones if the name or default status have changed.
   * It also inactivates those printers that are no longer returned by the print
   * provider.</p>
   *
   * @param provider
   *     the print provider
   * @param printers
   *     the list of printers returned by the print provider
   * @return an object containing the number of created, updated, and inactivated
   *     printers
   */
  private static UpsertCounters upsertPrinters(Provider provider, List<PrinterDTO> printers) {

    Set<String> seen = new HashSet<>();
    int created = 0;
    int updated = 0;
    int inactivated = 0;

    for (PrinterDTO dto : printers) {
      seen.add(dto.getId());

      // Find existing by (provider, value = externalId)
      OBCriteria<Printer> criteria = OBDal.getInstance().createCriteria(Printer.class);
      criteria.add(Restrictions.eq(Printer.PROPERTY_PROVIDER, provider));
      criteria.add(Restrictions.eq(Printer.PROPERTY_VALUE, dto.getId()));
      criteria.setMaxResults(1);
      Printer existing = (Printer) criteria.uniqueResult();

      if (existing == null) {
        // Create
        Printer p = OBProvider.getInstance().get(Printer.class);
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
    OBCriteria<Printer> toInactivate = OBDal.getInstance().createCriteria(Printer.class);
    toInactivate.add(Restrictions.eq(Printer.PROPERTY_PROVIDER, provider));
    if (!seen.isEmpty()) {
      toInactivate.add(Restrictions.not(Restrictions.in(Printer.PROPERTY_VALUE, seen)));
    }
    for (Printer printer : toInactivate.list()) {
      if (printer.isActive()) {
        printer.setActive(false);
        OBDal.getInstance().save(printer);
        inactivated++;
      }
    }

    return new UpsertCounters(created, updated, inactivated);
  }

  /**
   * Immutable container for the result of the {@code UpdatePrinters} action.
   */
  private static final class UpsertCounters {
    // Number of printers created
    public final int created;
    // Number of printers updated
    public final int updated;
    // Number of printers inactivated
    public final int inactivated;

    /**
     * Constructor.
     *
     * @param created
     *     the number of created printers
     * @param updated
     *     the number of updated printers
     * @param inactivated
     *     the number of inactivated printers
     */
    UpsertCounters(int created, int updated, int inactivated) {
      this.created = created;
      this.updated = updated;
      this.inactivated = inactivated;
    }
  }

  /**
   * The input class for the {@code UpdatePrinters} action.
   * <p>
   * This action does not use any specific input class, so it falls back to the
   * base class of all Openbravo objects: {@code BaseOBObject}.
   *
   * @return the input class of the action
   */
  @Override
  protected Class<?> getInputClass() {
    return BaseOBObject.class;
  }
}