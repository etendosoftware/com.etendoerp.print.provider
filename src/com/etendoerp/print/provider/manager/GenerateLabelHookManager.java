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
package com.etendoerp.print.provider.manager;

import java.util.ArrayList;
import java.util.List;

import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import org.openbravo.model.ad.datamodel.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.etendoerp.print.provider.api.GenerateLabelHook;
import com.etendoerp.print.provider.api.GenerateLabelHook.GenerateLabelContext;
import com.etendoerp.print.provider.api.PrintProviderException;

/**
 * Manager class responsible for coordinating the execution of label generation hooks.
 * <p>
 * This manager uses CDI (Contexts and Dependency Injection) to discover and manage all registered
 * implementations of {@link GenerateLabelHook}. When invoked, it orchestrates the execution of
 * applicable hooks in priority order, allowing different modules to customize JasperReports
 * parameters for specific tables.
 * </p>
 *
 * <p>
 * Hooks are executed sequentially based on their priority (lower values execute first). Each hook
 * can add or modify parameters in the shared context, and these modifications are accumulated
 * before the actual report generation.
 * </p>
 *
 * <h3>Thread Safety</h3>
 * This class is thread-safe as it does not maintain any mutable state and delegates to CDI
 * for dependency management.
 *
 * @see GenerateLabelHook
 */
@Dependent
public class GenerateLabelHookManager {

  private static final Logger log = LoggerFactory.getLogger(GenerateLabelHookManager.class);

  private Instance<GenerateLabelHook> hooks;

  /**
   * Constructor for CDI injection.
   *
   * @param hooks
   *     CDI Instance providing all discovered GenerateLabelHook implementations
   */
  @Inject
  public GenerateLabelHookManager(@Any Instance<GenerateLabelHook> hooks) {
    this.hooks = hooks;
  }

  /**
   * Executes all applicable hooks for the given table in priority order.
   * <p>
   * This method filters hooks by applicability for the specified table, sorts them by priority,
   * and executes them sequentially. Each hook receives the shared context and can add or modify
   * parameters that will be used for JasperReports generation.
   * </p>
   *
   * <p>
   * If any hook throws a {@link PrintProviderException}, the exception is propagated to the caller
   * and remaining hooks are not executed. This ensures that critical parameter setup failures are
   * properly handled.
   * </p>
   *
   * @param context
   *     the generation context containing parameters and metadata
   * @throws PrintProviderException
   *     if any hook fails during execution
   */
  public void executeHooks(GenerateLabelContext context) throws PrintProviderException {
    final Table table = context.getTable();
    if (table == null) {
      log.warn("The context does not contain a table, skipping hook execution.");
      return;
    }
    final List<GenerateLabelHook> applicableHooks = getApplicableHooks(table);

    if (applicableHooks.isEmpty()) {
      log.debug("No applicable hooks found for table: {}", table.getDBTableName());
      return;
    }

    log.debug("Executing {} hooks for table: {}", applicableHooks.size(), table.getDBTableName());

    for (GenerateLabelHook hook : applicableHooks) {
      try {
        log.trace("Executing hook: {} (priority: {})",
            hook.getClass().getSimpleName(), hook.getPriority());
        hook.execute(context);
      } catch (PrintProviderException e) {
        throw e;
      } catch (Exception e) {
        throw new PrintProviderException(
            String.format("Hook %s failed: %s", hook.getClass().getSimpleName(), e.getMessage()), e);
      }
    }

    log.debug("All hooks executed successfully for table: {}", table.getDBTableName());
  }

  /**
   * Gets the list of applicable hooks for the given table.
   *
   * @param table
   *     the table to get applicable hooks for
   * @return the list of applicable hooks
   */
  protected List<GenerateLabelHook> getApplicableHooks(Table table) {
    final List<GenerateLabelHook> applicableHooks = new ArrayList<>();

    for (GenerateLabelHook hook : hooks) {
      try {
        if (hook.tablesToWhichItApplies().contains(table.getId())) {
          applicableHooks.add(hook);
          log.trace("Hook {} is applicable for table {}",
              hook.getClass().getSimpleName(), table.getDBTableName());
        }
      } catch (Exception e) {
        log.warn("Error checking applicability of hook {} for table {}: {}",
            hook.getClass().getSimpleName(), table.getDBTableName(), e.getMessage());
      }
    }

    applicableHooks.sort((h1, h2) -> Integer.compare(h1.getPriority(), h2.getPriority()));
    return applicableHooks;
  }
}
