package com.etendoerp.print.provider.action;

import java.io.File;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.structure.BaseOBObject;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.datamodel.Table;

import com.etendoerp.print.provider.api.PrintProviderException;
import com.etendoerp.print.provider.data.Printer;
import com.etendoerp.print.provider.data.Provider;
import com.etendoerp.print.provider.data.Template;
import com.etendoerp.print.provider.data.TemplateLine;
import com.etendoerp.print.provider.strategy.PrintProviderStrategy;
import com.etendoerp.print.provider.utils.PrinterUtils;
import com.etendoerp.print.provider.utils.ProviderStrategyResolver;
import com.smf.jobs.Action;
import com.smf.jobs.ActionResult;
import com.smf.jobs.Result;

/**
 * Generates a label for a given ERP record and sends it to a selected printer using the
 * provider-specific strategy resolved at runtime.
 *
 * <p>This action is typically bound to a “Print Label” button in any window. It is
 * provider-agnostic: all backend details (file format, API calls, job creation) are
 * delegated to the {@link com.etendoerp.print.provider.strategy.PrintProviderStrategy}
 * obtained via {@link com.etendoerp.print.provider.utils.ProviderStrategyResolver}.</p>
 *
 * <h3>Expected parameters (Process Definition popup)</h3>
 * <ul>
 *   <li>{@code {@link com.etendoerp.print.provider.utils.PrinterUtils#PROVIDER} } –
 *       {@code ETPP_Provider_ID} (UUID).</li>
 *   <li>{@code {@link com.etendoerp.print.provider.utils.PrinterUtils#ENTITY_NAME} } –
 *       SmartClient entity/table name (e.g. {@code M_InOut}).</li>
 *   <li>{@code {@link com.etendoerp.print.provider.utils.PrinterUtils#RECORDS} } –
 *       array with the target record id; the action uses index {@code 0}.</li>
 *   <li>{@code {@link com.etendoerp.print.provider.utils.PrinterUtils#PRINTERS} } –
 *       {@code ETPP_Printer_ID} (UUID) chosen in the popup.</li>
 * </ul>
 *
 * @see com.etendoerp.print.provider.utils.PrinterUtils
 * @see com.etendoerp.print.provider.utils.ProviderStrategyResolver
 * @see com.etendoerp.print.provider.strategy.PrintProviderStrategy
 * @see com.etendoerp.print.provider.data.Provider
 * @see com.etendoerp.print.provider.data.Printer
 * @see com.etendoerp.print.provider.data.Template
 * @see com.etendoerp.print.provider.data.TemplateLine
 */
public class SendGeneratedLabelToPrinter extends Action {

  /**
   * Executes the action of generating and sending a label to the printer based on the
   * given parameters. It validates the input, resolves the appropriate strategy and template,
   * generates the label file, and sends it to the designated printer.
   *
   * @param parameters
   *     JSON object with required parameters:
   *     - "Provider": ID of the print provider
   *     - "_entityName": name of the entity/table
   *     - "recordIds": array with the record ID to print
   *     - "Printers": ID of the target printer
   * @param isStopped
   *     Flag to indicate if the job was stopped externally
   * @return ActionResult indicating success or failure, with a message
   */
  @Override
  protected ActionResult action(JSONObject parameters, MutableBoolean isStopped) {
    final ActionResult res = new ActionResult();
    res.setType(Result.Type.SUCCESS);
    try {
      OBContext.setAdminMode(true);
      checkMandatoryParameters(parameters);

      final String providerIdParam = parameters.getString(PrinterUtils.PROVIDER);
      final String entityName = parameters.getString(PrinterUtils.ENTITY_NAME);
      final JSONArray records = parameters.getJSONArray(PrinterUtils.RECORDS);
      final String printerId = parameters.getString(PrinterUtils.PRINTERS);
      //Handling multiple records is pending development.
      final String recordId = records.getString(0);

      Provider provider = OBDal.getInstance().get(Provider.class, providerIdParam);

      if (provider == null) {
        return fail(res, OBMessageUtils.getI18NMessage("ETPP_ProviderNotFound"));
      }

      // 2) Load strategy implementation for this provider
      final PrintProviderStrategy strategy = ProviderStrategyResolver.resolveForProvider(provider);

      // 3) Fetch printers and choose target
      Printer targetPrinter = OBDal.getInstance().get(Printer.class, printerId);
      if (targetPrinter == null) {
        return fail(res, String.format(
            OBMessageUtils.messageBD("ETPP_PrinterNotFound"), printerId));
      }

      OBCriteria<Table> criteria = OBDal.getInstance().createCriteria(Table.class);
      criteria.add(Restrictions.eq(Table.PROPERTY_NAME, entityName));
      criteria.setMaxResults(1);

      Table table = (Table) criteria.uniqueResult();

      if (table == null) {
        return fail(res, String.format(
            OBMessageUtils.messageBD("ETPP_TableNotFound"), entityName));
      }

      final TemplateLine templateLine = resolveTemplate(table);

      final File label = strategy.generateLabel(provider, table, recordId, templateLine, parameters);
      final String jobId = strategy.sendToPrinter(provider, targetPrinter, label);
      res.setMessage(String.format(
          OBMessageUtils.messageBD("ETPP_PrintJobSent"), (jobId == null ? "-" : jobId)));
      return res;

    } catch (PrintProviderException e) {
      OBDal.getInstance().rollbackAndClose();
      return fail(res, String.format(
          OBMessageUtils.messageBD("ETPP_ProviderErrorPrefix"), e.getMessage()));

    } catch (Exception e) {
      OBDal.getInstance().rollbackAndClose();
      return fail(res, e.getMessage());
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  /**
   * Checks the mandatory parameters in the input JSON object.
   *
   * @param parameters
   *     The JSON object containing the parameters:
   *     - "Provider": ID of the print provider
   *     - "EntityName": name of the entity
   *     - "Records": array of records
   *     - "Printers": ID of the printer
   * @throws OBException
   *     if any of the mandatory parameters are missing or invalid
   */
  private void checkMandatoryParameters(JSONObject parameters) {
    final String providerIdParam = parameters.optString(PrinterUtils.PROVIDER, null);
    final String entityName = parameters.optString(PrinterUtils.ENTITY_NAME, null);
    final JSONArray records = parameters.optJSONArray(PrinterUtils.RECORDS);
    final String printerId = parameters.optString(PrinterUtils.PRINTERS, null);

    if (StringUtils.isBlank(providerIdParam)) {
      throw new OBException(String.format(
          OBMessageUtils.messageBD(PrinterUtils.MISSING_PARAMETER_MSG), PrinterUtils.PROVIDER));
    }

    if (StringUtils.isBlank(entityName)) {
      throw new OBException(String.format(
          OBMessageUtils.messageBD(PrinterUtils.MISSING_PARAMETER_MSG), PrinterUtils.ENTITY_NAME));
    }

    if (records == null || records.length() == 0) {
      throw new OBException(String.format(
          OBMessageUtils.messageBD(PrinterUtils.MISSING_PARAMETER_MSG), PrinterUtils.RECORDS));
    }

    if (StringUtils.isBlank(printerId)) {
      throw new OBException(String.format(
          OBMessageUtils.messageBD(PrinterUtils.MISSING_PARAMETER_MSG), PrinterUtils.PRINTERS));
    }
  }


  /**
   * Resolves the appropriate TemplateLine to use for a given table.
   * Prefers default templates and orders by line number.
   *
   * @param table
   *     The entity table
   * @return The resolved TemplateLine or null if none found
   */
  private static TemplateLine resolveTemplate(Table table) {
    Template template = getTemplateByTable(table);
    if (template == null) {
      throw new OBException(String.format(
          OBMessageUtils.messageBD("ETPP_PrintLocationNotFound"), table.getName()));
    }

    OBCriteria<TemplateLine> lines = OBDal.getInstance().createCriteria(TemplateLine.class);
    lines.add(Restrictions.eq(TemplateLine.PROPERTY_TEMPLATE, template));
    lines.addOrder(Order.desc(TemplateLine.PROPERTY_DEFAULT));
    lines.addOrder(Order.asc(TemplateLine.PROPERTY_LINENO));
    lines.setMaxResults(1);

    return (TemplateLine) lines.uniqueResult();
  }

  /**
   * Fetches the Template entity associated with a given table.
   *
   * @param table
   *     The table to search the template for
   * @return The Template or null if not found
   */
  private static Template getTemplateByTable(Table table) {
    OBCriteria<Template> templateOBCriteria = OBDal.getInstance().createCriteria(Template.class);
    templateOBCriteria.add(Restrictions.eq(Template.PROPERTY_TABLE, table));
    templateOBCriteria.setMaxResults(1);
    return (Template) templateOBCriteria.uniqueResult();
  }

  /**
   * Helper method to set the ActionResult as failed with a message.
   *
   * @param res
   *     The ActionResult to update
   * @param detail
   *     The failure message
   * @return The updated ActionResult with error type and message
   */
  private static ActionResult fail(ActionResult res, String detail) {
    res.setType(Result.Type.ERROR);
    res.setMessage(detail);
    return res;
  }


  /**
   * Specifies the input class for the job action.
   *
   * @return The class representing input records
   */
  @Override
  protected Class<BaseOBObject> getInputClass() {
    return BaseOBObject.class;
  }
}