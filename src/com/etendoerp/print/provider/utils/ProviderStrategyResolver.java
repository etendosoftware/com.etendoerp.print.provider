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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.util.OBClassLoader;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.print.provider.api.PrintProviderException;
import com.etendoerp.print.provider.data.Provider;
import com.etendoerp.print.provider.data.ProvidersImplementation;
import com.etendoerp.print.provider.strategy.PrintProviderStrategy;

/**
 * Resolves and instantiates the concrete {@link com.etendoerp.print.provider.strategy.PrintProviderStrategy}
 * associated with a given {@link com.etendoerp.print.provider.data.Provider}.
 *
 * <p>The resolver reads the linked {@link com.etendoerp.print.provider.data.ProvidersImplementation}
 * from the provider (field {@code providerImplementation}), obtains the fully-qualified class name
 * from {@code javaImplementation}, loads it via {@link org.openbravo.base.util.OBClassLoader}, verifies
 * that it implements {@code PrintProviderStrategy}, and returns a new instance created through the
 * public no-args constructor.</p>
 *
 * <h3>Contract & Assumptions</h3>
 * <ul>
 *   <li>The implementation class must be present on the classpath.</li>
 *   <li>The class must implement {@code PrintProviderStrategy}.</li>
 *   <li>The class must expose a public no-argument constructor.</li>
 *   <li>The provider must have a non-null {@code providerImplementation} row with a non-blank
 *       {@code javaImplementation} value.</li>
 * </ul>
 *
 * <h3>Error Handling</h3>
 * <ul>
 *   <li>Missing implementation row ⇒ {@link org.openbravo.base.exception.OBException}
 *       with i18n key {@code ETPP_ProviderImplMissing}.</li>
 *   <li>Blank class name ⇒ {@code OBException} with {@code ETPP_ProviderImplClassEmpty}.</li>
 *   <li>Loaded class does not implement the strategy interface ⇒ {@code OBException} with
 *       {@code ETPP_NotImplementPrintProviderStrategy} (formatted with the class name).</li>
 *   <li>Any ClassLoader/reflective failure is wrapped and rethrown as {@code OBException}.</li>
 * </ul>
 *
 * <h3>Thread-safety</h3>
 * <p>Stateless and thread-safe. All methods are pure {@code static} utilities.</p>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * Provider provider = OBDal.getInstance().get(Provider.class, providerId);
 * PrintProviderStrategy strategy = ProviderStrategyResolver.resolveForProvider(provider);
 * // Now delegate provider-specific logic:
 * strategy.fetchPrinters(provider);
 * }</pre>
 *
 * @see com.etendoerp.print.provider.strategy.PrintProviderStrategy
 * @see com.etendoerp.print.provider.data.Provider
 * @see com.etendoerp.print.provider.data.ProvidersImplementation
 */
public class ProviderStrategyResolver {

  private static final Logger log = LogManager.getLogger(ProviderStrategyResolver.class);

  /**
   * Private constructor to prevent instantiation of the utility class.
   */
  private ProviderStrategyResolver() {
    throw new IllegalStateException("Utility class");
  }

  /**
   * Resolves the appropriate PrintProviderStrategy implementation for the given provider.
   *
   * @param provider
   *     the Provider instance for which the strategy needs to be resolved
   * @return the resolved PrintProviderStrategy implementation
   */
  @SuppressWarnings("java:S112")
  public static PrintProviderStrategy resolveForProvider(Provider provider) {
    try {
      ProvidersImplementation impl = provider.getProviderImplementation();
      if (impl == null) {
        throw new OBException(OBMessageUtils.messageBD("ETPP_ProviderImplMissing"));
      }
      String javaImpl = impl.getJavaImplementation();
      if (javaImpl == null || javaImpl.isBlank()) {
        throw new OBException(OBMessageUtils.messageBD("ETPP_ProviderImplClassEmpty"));
      }

      Class<?> clazz = OBClassLoader.getInstance().loadClass(javaImpl);
      if (!PrintProviderStrategy.class.isAssignableFrom(clazz)) {
        throw new OBException(String.format(
            OBMessageUtils.messageBD("ETPP_NotImplementPrintProviderStrategy"), javaImpl));
      }
      return (PrintProviderStrategy) clazz.getDeclaredConstructor().newInstance();

    } catch (Exception e) {
      log.error(e.getMessage());
      throw new PrintProviderException(OBMessageUtils.messageBD("ETPP_ProviderResolveError"), e);
    }
  }

}