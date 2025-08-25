package com.etendoerp.print.provider.utils;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.datamodel.Table;

import com.etendoerp.print.provider.data.Printer;
import com.etendoerp.print.provider.data.Provider;
import com.etendoerp.print.provider.data.ProviderParam;
import com.etendoerp.print.provider.data.Template;
import com.etendoerp.print.provider.data.TemplateLine;
import com.smf.jobs.ActionResult;
import com.smf.jobs.Result;

/**
 * Utility holder for well-known parameter names, i18n helpers and common DAL lookups
 * used by Print Provider processes/actions (e.g. UpdatePrinters and SendGeneratedLabelToPrinter).
 *
 * <p>Thread-safety: immutable statics; safe for concurrent use.</p>
 */
public class PrinterUtils {

  private PrinterUtils() {
    throw new UnsupportedOperationException("Utility class");
  }

  // ---------------------------------------------------------------------------
  // Public constants used in process params
  // ---------------------------------------------------------------------------
  public static final String PARAMS = "_params";
  public static final String PROVIDER = "Provider";
  public static final String ENTITY_NAME = "_entityName";
  public static final String RECORDS = "recordIds";
  public static final String PRINTERS = "Printers";
  public static final String NUMBER_OF_COPIES = "numberofcopies";

  // Message keys
  public static final String MISSING_PARAMETER_MSG = "ETPP_MissingParameter";
  public static final String PROVIDER_NOT_FOUND_MSG = "ETPP_ProviderNotFound";

  // ---------------------------------------------------------------------------
  // DAL lookups (throwing OBException with i18n when not found)
  // ---------------------------------------------------------------------------

  /**
   * Loads a Provider by id or throws OBException(i18n).
   */
  public static Provider requireProvider(final String providerId) {
    final Provider provider = OBDal.getInstance().get(Provider.class, providerId);
    if (provider == null) {
      throw new OBException(OBMessageUtils.getI18NMessage(PROVIDER_NOT_FOUND_MSG));
    }
    return provider;
  }

  /**
   * Loads a Printer by id or throws OBException(i18n with id).
   */
  public static Printer requirePrinter(final String printerId) {
    final Printer printer = OBDal.getInstance().get(Printer.class, printerId);
    if (printer == null) {
      throw new OBException(String.format(OBMessageUtils.getI18NMessage("ETPP_PrinterNotFound"), printerId));
    }
    return printer;
  }

  /**
   * Loads a Table by name (AD_Table.name) or throws OBException(i18n with name).
   */
  public static Table requireTableByName(final String entityName) {
    OBCriteria<Table> tableOBCriteria = OBDal.getInstance().createCriteria(Table.class);
    tableOBCriteria.add(Restrictions.eq(Table.PROPERTY_NAME, entityName));
    tableOBCriteria.setMaxResults(1);
    final Table table = (Table) tableOBCriteria.uniqueResult();
    if (table == null) {
      throw new OBException(String.format(OBMessageUtils.getI18NMessage("ETPP_TableNotFound"), entityName));
    }
    return table;
  }

  /**
   * Resolves the TemplateLine for a given table, preferring defaults and ordering by lineNo.
   * Throws OBException(i18n) if there is no Template for that table.
   */
  public static TemplateLine resolveTemplateLineFor(final Table table) {
    // Get Template by table
    OBCriteria<Template> templateOBCriteria = OBDal.getInstance().createCriteria(Template.class);
    templateOBCriteria.add(Restrictions.eq(Template.PROPERTY_TABLE, table));
    templateOBCriteria.setMaxResults(1);
    final Template template = (Template) templateOBCriteria.uniqueResult();
    if (template == null) {
      throw new OBException(
          String.format(OBMessageUtils.getI18NMessage("ETPP_PrintLocationNotFound"), table.getName()));
    }

    // Pick best TemplateLine (default desc, lineNo asc)
    OBCriteria<TemplateLine> templateLineOBCriteria = OBDal.getInstance().createCriteria(TemplateLine.class);
    templateLineOBCriteria.add(Restrictions.eq(TemplateLine.PROPERTY_TEMPLATE, template));
    templateLineOBCriteria.addOrder(Order.desc(TemplateLine.PROPERTY_DEFAULT));
    templateLineOBCriteria.addOrder(Order.asc(TemplateLine.PROPERTY_LINENO));
    templateLineOBCriteria.setMaxResults(1);
    return (TemplateLine) templateLineOBCriteria.uniqueResult();
  }

  // ---------------------------------------------------------------------------
  // ProviderParam helpers
  // ---------------------------------------------------------------------------

  /**
   * Returns the ProviderParam for the given provider and key OR throws an OBException(i18n)
   * if it does not exist or the key is blank.
   */
  public static ProviderParam getRequiredParam(final Provider provider, final String paramKey) {
    if (provider == null) {
      throw new OBException(OBMessageUtils.getI18NMessage(PROVIDER_NOT_FOUND_MSG));
    }
    if (StringUtils.isBlank(paramKey)) {
      throw new OBException(String.format(OBMessageUtils.getI18NMessage(MISSING_PARAMETER_MSG), "paramKey"));
    }

    OBCriteria<ProviderParam> providerParamOBCriteria = OBDal.getInstance().createCriteria(ProviderParam.class);
    providerParamOBCriteria.add(Restrictions.eq(ProviderParam.PROPERTY_PROVIDER, provider));
    providerParamOBCriteria.add(Restrictions.eq(ProviderParam.PROPERTY_SEARCHKEY, paramKey).ignoreCase());
    providerParamOBCriteria.setMaxResults(1);

    ProviderParam providerParam = (ProviderParam) providerParamOBCriteria.uniqueResult();
    if (providerParam == null) {
      throw new OBException(String.format(OBMessageUtils.getI18NMessage("ETPP_ProviderParamNotFound"), paramKey));
    }
    return providerParam;
  }

  /**
   * Ensures a non-blank String is present at root level; throws OBException(i18n MissingParameter) otherwise.
   */
  public static String requireParam(final JSONObject json, final String key) {
    final String value = json.optString(key, null);
    if (StringUtils.isBlank(value)) {
      throw new OBException(String.format(OBMessageUtils.getI18NMessage(MISSING_PARAMETER_MSG), key));
    }
    return value;
  }

  /**
   * Ensures a non-empty JSONArray is present at the given key; throws OBException(i18n MissingParameter)
   * otherwise.
   */
  public static JSONArray requireJSONArray(final JSONObject json, final String key) {
    final JSONArray value = json.optJSONArray(key);
    if (value == null || value.length() == 0) {
      throw new OBException(String.format(OBMessageUtils.getI18NMessage(MISSING_PARAMETER_MSG), key));
    }
    return value;
  }

  /**
   * Ensures a positive integer is present; throws with MissingParameter or CopiesInvalid messages.
   */
  public static int requirePositiveInt(final JSONObject json, final String key) {
    if (!json.has(key)) {
      throw new OBException(String.format(OBMessageUtils.getI18NMessage(MISSING_PARAMETER_MSG), key));
    }
    final int n = json.optInt(key, -1);
    if (n <= 0) {
      throw new OBException(String.format(OBMessageUtils.getI18NMessage("ETPP_NumberOfCopiesInvalid"), n));
    }
    return n;
  }

  /**
   * Marks the given action result as a failure and sets its message to the
   * given detail.
   *
   * @param res
   *     the action result to mark as a failure
   * @param detail
   *     the message to use for the action result
   * @return the action result (marked as a failure)
   */
  public static ActionResult fail(ActionResult res, String detail) {
    res.setType(Result.Type.ERROR);
    res.setMessage(detail);
    return res;
  }

  /**
   * Marks the given action result as a warning and sets the message to the
   * given detail.
   *
   * @param res
   *     the action result to update
   * @param detail
   *     the message to set
   * @return the updated action result
   */
  public static ActionResult warning(ActionResult res, String detail) {
    res.setType(Result.Type.WARNING);
    res.setMessage(detail);
    return res;
  }
}