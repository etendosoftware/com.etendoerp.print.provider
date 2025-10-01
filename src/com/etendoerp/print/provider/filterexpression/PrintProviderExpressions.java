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
package com.etendoerp.print.provider.filterexpression;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;
import org.openbravo.client.application.FilterExpression;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.print.provider.data.Printer;
import com.etendoerp.print.provider.data.Provider;
import com.etendoerp.print.provider.utils.PrinterUtils;

/**
 * Filter expression for the Print Provider module.
 *
 * <p>This class implements the {@link FilterExpression} interface and provides
 * default values for the {@link PrinterUtils#PROVIDER} and
 * {@link PrinterUtils#PRINTERS} parameters.</p>
 */
public class PrintProviderExpressions implements FilterExpression {
  protected static final Logger log = LogManager.getLogger();

  /**
   * @return a default value for the parameter given in the request map.
   *     The parameter name is given in the "currentParam" key of the map.
   *
   *     <p>
   *     The default values are:
   *     <ul>
   *       <li>for {@link PrinterUtils#PROVIDER}: the first provider in the list
   *           of providers.</li>
   *       <li>for {@link PrinterUtils#PRINTERS}: the first printer of the first
   *           provider in the list of providers.</li>
   *     </ul>
   *     </p>
   *
   *     <p>
   *     If any exception occurs during the computation of the default value, the
   *     method returns an empty string.
   *     </p>
   */
  @Override
  public String getExpression(Map<String, String> requestMap) {
    String strCurrentParam = StringUtils.EMPTY;
    try {
      OBContext.setAdminMode(true);
      strCurrentParam = requestMap.get("currentParam");
      switch (strCurrentParam) {
        case PrinterUtils.PROVIDER:
          return getDefaultProviderForPrintJob();
        case PrinterUtils.PRINTERS:
          return getDefaultPrinterForPrintJob();
        default:
          return StringUtils.EMPTY;

      }
    } catch (Exception e) {
      log.error("Error trying to get default value of {}: {}", strCurrentParam, e.getMessage(), e);
    } finally {
      OBContext.restorePreviousMode();
    }
    log.warn("No default value found for param: {}", strCurrentParam);
    return null;
  }

  /**
   * Returns the ID of the default provider to use for print jobs.
   *
   * <p>This method returns the ID of the first provider found in the database,
   * ordered alphabetically by name. If no providers are present, it returns an
   * empty string.</p>
   *
   * <p>This method is used by the {@link FilterExpression} to set a default value
   * for the {@code Provider} parameter in the {@code PrintJob} window.</p>
   *
   * @return the ID of the default provider to use for print jobs, or an empty
   *     string if no provider is present
   */
  protected String getDefaultProviderForPrintJob() {
    OBCriteria<Provider> providerOBCriteria = OBDal.getInstance().createCriteria(Provider.class);
    providerOBCriteria.addOrder(Order.asc(Provider.PROPERTY_NAME));
    providerOBCriteria.setMaxResults(1);

    Provider provider = (Provider) providerOBCriteria.uniqueResult();
    return provider == null ? StringUtils.EMPTY : provider.getId();
  }

  /**
   * Finds the default printer to use for print jobs.
   * <p>
   * The default printer is determined as follows:
   * <ol>
   * <li>The default provider is queried using {@link #getDefaultProviderForPrintJob()}.</li>
   * <li>A query is performed to find the printer with the highest priority
   *     (i.e. the highest value of {@link Printer#isDefault()}).</li>
   * <li>If no printers are found, an empty string is returned.</li>
   * <li>If a single printer is found, its ID is returned.</li>
   * </ol>
   *
   * @return the ID of the default printer to use for print jobs, an empty string if none is found.
   */
  protected String getDefaultPrinterForPrintJob() {
    Provider provider = OBDal.getInstance().get(Provider.class, getDefaultProviderForPrintJob());
    if (provider == null) {
      return StringUtils.EMPTY;
    }

    OBCriteria<Printer> printerOBCriteria = OBDal.getInstance().createCriteria(Printer.class);
    printerOBCriteria.add(Restrictions.eq(Printer.PROPERTY_PROVIDER, provider));
    printerOBCriteria.addOrder(Order.desc(Printer.PROPERTY_DEFAULT));
    printerOBCriteria.addOrder(Order.asc(Provider.PROPERTY_NAME));
    printerOBCriteria.setMaxResults(1);

    Printer printerResult = (Printer) printerOBCriteria.uniqueResult();

    return printerResult == null ? StringUtils.EMPTY : printerResult.getId();
  }
}