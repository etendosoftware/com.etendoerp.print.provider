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
package com.etendoerp.print.provider.api;

import java.util.List;
import java.util.Map;

import org.codehaus.jettison.json.JSONObject;
import org.openbravo.model.ad.datamodel.Table;

import com.etendoerp.print.provider.data.Provider;
import com.etendoerp.print.provider.data.TemplateLine;

/**
 * Hook interface for customizing JasperReports parameters during label generation.
 * <p>
 * Implementations can add or modify parameters that will be passed to JasperReports.
 * Each hook must specify which table(s) it applies to via {@link #tablesToWhichItApplies()}.
 * </p>
 */
public interface GenerateLabelHook {

  /**
   * Executes the hook to add or modify JasperReports parameters.
   *
   * @param context
   *     the generation context
   * @throws PrintProviderException
   *     if an error occurs
   */
  void execute(GenerateLabelContext context) throws PrintProviderException;

  /**
   * Returns the execution priority. Lower values execute first.
   *
   * @return the priority value (default: 100)
   */
  default int getPriority() {
    return 100;
  }

  /**
   * Returns the list of table IDs to which this hook applies.
   *
   * @return the list of table IDs
   */
  List<String> tablesToWhichItApplies();

  /**
   * Context object providing access to label generation state and parameters.
   */
  class GenerateLabelContext {
    private final Provider provider;
    private final Table table;
    private final String recordId;
    private final TemplateLine templateLine;
    private final JSONObject jsonParameters;
    private final Map<String, Object> jrParams;

    /**
     * Creates a new generation context.
     */
    public GenerateLabelContext(Provider provider, Table table, String recordId,
        TemplateLine templateLine, JSONObject jsonParameters, Map<String, Object> jrParams) {
      this.provider = provider;
      this.table = table;
      this.recordId = recordId;
      this.templateLine = templateLine;
      this.jsonParameters = jsonParameters;
      this.jrParams = jrParams;
    }

    /**
     * Adds or updates a parameter in the JasperReports parameters map.
     *
     * @param key
     *     the parameter key
     * @param value
     *     the parameter value
     */
    public void addParameter(String key, Object value) {
      jrParams.put(key, value);
    }

    /**
     * Gets the current value of a parameter.
     *
     * @param key
     *     the parameter key
     * @return the parameter value, or {@code null} if not present
     */
    public Object getParameter(String key) {
      return jrParams.get(key);
    }

    /**
     * Returns a read-only view of all current parameters.
     */
    public Map<String, Object> getParameters() {
      return jrParams;
    }

    /**
     * Gets the provider configuration.
     */
    public Provider getProvider() {
      return provider;
    }

    /**
     * Gets the target table.
     */
    public Table getTable() {
      return table;
    }

    /**
     * Gets the record ID.
     */
    public String getRecordId() {
      return recordId;
    }

    /**
     * Gets the template line reference.
     */
    public TemplateLine getTemplateLine() {
      return templateLine;
    }

    /**
     * Gets the JSON parameters from the request.
     */
    public JSONObject getJsonParameters() {
      return jsonParameters;
    }
  }
}
