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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.enterprise.inject.Instance;

import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openbravo.base.exception.OBException;
import org.openbravo.model.ad.datamodel.Table;

import com.etendoerp.print.provider.api.GenerateLabelHook;
import com.etendoerp.print.provider.api.GenerateLabelHook.GenerateLabelContext;
import com.etendoerp.print.provider.api.PrintProviderException;
import com.etendoerp.print.provider.data.Provider;
import com.etendoerp.print.provider.data.TemplateLine;

/**
 * Unit tests for GenerateLabelHookManager class.
 * Validates hook discovery, execution order, error handling, and applicability filtering.
 */
class GenerateLabelHookManagerTest {

  private static final String TABLE_ID_ORDER = "order-table-id";
  private static final String TABLE_ID_PRODUCT = "product-table-id";
  private static final String TABLE_DB_NAME = "C_Order";
  private static final String RECORD_ID = "test-record-456";
  private static final String EXECUTION_ORDER = "executionOrder";
  private static final String EXECUTION_COUNTER = "executionCounter";

  private GenerateLabelHookManager manager;
  private Instance<GenerateLabelHook> mockHooksInstance;
  private Table mockTable;
  private Map<String, Object> jrParams;
  private GenerateLabelContext context;

  /**
   * Sets up the test environment by creating mock objects and initializing the context.
   */
  @BeforeEach
  void setUp() {
    Provider mockProvider = mock(Provider.class);
    mockTable = mock(Table.class);
    TemplateLine mockTemplateLine = mock(TemplateLine.class);
    JSONObject jsonParameters = new JSONObject();
    jrParams = new HashMap<>();

    when(mockTable.getId()).thenReturn(TABLE_ID_ORDER);
    when(mockTable.getDBTableName()).thenReturn(TABLE_DB_NAME);

    context = new GenerateLabelContext(
        mockProvider,
        mockTable,
        RECORD_ID,
        mockTemplateLine,
        jsonParameters,
        jrParams
    );

    mockHooksInstance = createMockInstance(new ArrayList<>());
    manager = new GenerateLabelHookManager(mockHooksInstance);
  }

  /**
   * Tests the executeHooks method with no applicable hooks.
   *
   * @throws PrintProviderException
   *     if there is an error executing the hooks.
   */
  @Test
  void testExecuteHooksWithNoApplicableHooks() throws PrintProviderException {
    // Create hooks that don't apply to the current table
    final List<GenerateLabelHook> hooks = new ArrayList<>();
    hooks.add(new TestHook(TABLE_ID_PRODUCT, 100));

    mockHooksInstance = createMockInstance(hooks);
    manager = new GenerateLabelHookManager(mockHooksInstance);

    manager.executeHooks(context);

    // Verify no parameters were added
    assertThat("Parameters map should be empty", jrParams.size(), is(0));
  }

  /**
   * Tests the executeHooks method with a single applicable hook.
   *
   * @throws PrintProviderException
   *     if there is an error executing the hooks.
   */
  @Test
  void testExecuteHooksWithSingleApplicableHook() throws PrintProviderException {
    final TestHook hook = new TestHook(TABLE_ID_ORDER, 100);
    final List<GenerateLabelHook> hooks = new ArrayList<>();
    hooks.add(hook);

    mockHooksInstance = createMockInstance(hooks);
    manager = new GenerateLabelHookManager(mockHooksInstance);

    manager.executeHooks(context);

    assertThat("Hook should have added parameter",
        jrParams.get(EXECUTION_ORDER), is(equalTo(100)));
  }

  /**
   * Tests the executeHooks method with multiple applicable hooks in priority order.
   *
   * @throws PrintProviderException
   *     if there is an error executing the hooks.
   */
  @Test
  void testExecuteHooksInPriorityOrder() throws PrintProviderException {
    final List<GenerateLabelHook> hooks = new ArrayList<>();
    final TestHook highPriorityHook = new TestHook(TABLE_ID_ORDER, 50);
    final TestHook mediumPriorityHook = new TestHook(TABLE_ID_ORDER, 100);
    final TestHook lowPriorityHook = new TestHook(TABLE_ID_ORDER, 150);

    // Add in random order to test sorting
    hooks.add(lowPriorityHook);
    hooks.add(highPriorityHook);
    hooks.add(mediumPriorityHook);

    mockHooksInstance = createMockInstance(hooks);
    manager = new GenerateLabelHookManager(mockHooksInstance);

    manager.executeHooks(context);

    // Verify execution order by checking the final value (last hook wins)
    assertThat("Last hook should have priority 150",
        jrParams.get(EXECUTION_ORDER), is(equalTo(150)));

    // Verify all hooks were executed
    assertThat("Counter should be 3", jrParams.get(EXECUTION_COUNTER), is(equalTo(3)));
  }

  /**
   * Tests the executeHooks method with mixed applicability.
   *
   * @throws PrintProviderException
   *     if there is an error executing the hooks.
   */
  @Test
  void testExecuteHooksWithMixedApplicability() throws PrintProviderException {
    final List<GenerateLabelHook> hooks = new ArrayList<>();
    hooks.add(new TestHook(TABLE_ID_ORDER, 50));
    hooks.add(new TestHook(TABLE_ID_PRODUCT, 75)); // Not applicable
    hooks.add(new TestHook(TABLE_ID_ORDER, 100));

    mockHooksInstance = createMockInstance(hooks);
    manager = new GenerateLabelHookManager(mockHooksInstance);

    manager.executeHooks(context);

    // Only 2 hooks should have executed
    assertThat("Counter should be 2 (only applicable hooks)",
        jrParams.get(EXECUTION_COUNTER), is(equalTo(2)));
  }

  /**
   * Tests the executeHooks method with a hook that throws a PrintProviderException.
   */
  @Test
  void testExecuteHooksThrowsPrintProviderException() {
    final List<GenerateLabelHook> hooks = new ArrayList<>();
    hooks.add(new TestHookThatThrows(TABLE_ID_ORDER, true));

    mockHooksInstance = createMockInstance(hooks);
    manager = new GenerateLabelHookManager(mockHooksInstance);

    final PrintProviderException exception = assertThrows(
        PrintProviderException.class,
        () -> manager.executeHooks(context),
        "Should throw PrintProviderException"
    );

    assertThat("Exception message should match",
        exception.getMessage(), is(equalTo("Hook failed")));
  }

  /**
   * Tests the executeHooks method with a hook that throws a generic exception.
   */
  @Test
  void testExecuteHooksWrapsGenericException() {
    final List<GenerateLabelHook> hooks = new ArrayList<>();
    hooks.add(new TestHookThatThrows(TABLE_ID_ORDER, false));

    mockHooksInstance = createMockInstance(hooks);
    manager = new GenerateLabelHookManager(mockHooksInstance);

    final PrintProviderException exception = assertThrows(
        PrintProviderException.class,
        () -> manager.executeHooks(context),
        "Should wrap generic exception in PrintProviderException"
    );

    assertThat("Exception message should contain hook class name",
        exception.getMessage().contains("TestHookThatThrows"), is(true));
    assertThat("Exception message should contain original error",
        exception.getMessage().contains("Generic error"), is(true));
  }

  /**
   * Tests the executeHooks method with a hook that throws a PrintProviderException.
   */
  @Test
  void testExecuteHooksStopsOnFirstException() {
    final List<GenerateLabelHook> hooks = new ArrayList<>();
    final TestHook firstHook = new TestHook(TABLE_ID_ORDER, 50);
    final TestHookThatThrows failingHook = new TestHookThatThrows(TABLE_ID_ORDER, true);
    final TestHook lastHook = new TestHook(TABLE_ID_ORDER, 150);

    hooks.add(firstHook);
    hooks.add(failingHook);
    hooks.add(lastHook);

    mockHooksInstance = createMockInstance(hooks);
    manager = new GenerateLabelHookManager(mockHooksInstance);

    assertThrows(PrintProviderException.class, () -> manager.executeHooks(context));

    // Only first hook should have executed
    assertThat("Counter should be 1 (execution stopped after exception)",
        jrParams.get(EXECUTION_COUNTER), is(equalTo(1)));
  }

  /**
   * Tests the getApplicableHooks method with no applicable hooks.
   */
  @Test
  void testGetApplicableHooksReturnsEmptyList() {
    final List<GenerateLabelHook> hooks = new ArrayList<>();
    hooks.add(new TestHook(TABLE_ID_PRODUCT, 100));

    mockHooksInstance = createMockInstance(hooks);
    manager = new GenerateLabelHookManager(mockHooksInstance);

    final List<GenerateLabelHook> applicableHooks = manager.getApplicableHooks(mockTable);

    assertThat("Should return empty list", applicableHooks.size(), is(0));
  }

  /**
   * Tests the getApplicableHooks method with multiple applicable hooks.
   */
  @Test
  void testGetApplicableHooksSortsCorrectly() {
    final List<GenerateLabelHook> hooks = new ArrayList<>();
    final TestHook hook1 = new TestHook(TABLE_ID_ORDER, 100);
    final TestHook hook2 = new TestHook(TABLE_ID_ORDER, 50);
    final TestHook hook3 = new TestHook(TABLE_ID_ORDER, 75);

    hooks.add(hook1);
    hooks.add(hook2);
    hooks.add(hook3);

    mockHooksInstance = createMockInstance(hooks);
    manager = new GenerateLabelHookManager(mockHooksInstance);

    final List<GenerateLabelHook> applicableHooks = manager.getApplicableHooks(mockTable);

    assertThat("Should return 3 hooks", applicableHooks.size(), is(3));
    assertThat("First hook should have priority 50",
        applicableHooks.get(0).getPriority(), is(50));
    assertThat("Second hook should have priority 75",
        applicableHooks.get(1).getPriority(), is(75));
    assertThat("Third hook should have priority 100",
        applicableHooks.get(2).getPriority(), is(100));
  }

  /**
   * Tests the getApplicableHooks method with a hook that throws an exception in the applicability check.
   */
  @Test
  void testGetApplicableHooksHandlesExceptionInApplicabilityCheck() {
    final List<GenerateLabelHook> hooks = new ArrayList<>();
    hooks.add(new TestHookWithApplicabilityExc());
    hooks.add(new TestHook(TABLE_ID_ORDER, 100));

    mockHooksInstance = createMockInstance(hooks);
    manager = new GenerateLabelHookManager(mockHooksInstance);

    final List<GenerateLabelHook> applicableHooks = manager.getApplicableHooks(mockTable);

    // Only the valid hook should be returned
    assertThat("Should return 1 hook (exception in applicability check should be caught)",
        applicableHooks.size(), is(1));
  }

  /**
   * Tests the executeHooks method with a hook that applies to multiple tables.
   *
   * @throws PrintProviderException
   *     if there is an error executing the hooks.
   */
  @Test
  void testExecuteHooksWithMultipleTableIds() throws PrintProviderException {
    final List<GenerateLabelHook> hooks = new ArrayList<>();
    final TestHookMultipleTables multiTableHook = new TestHookMultipleTables();

    hooks.add(multiTableHook);

    mockHooksInstance = createMockInstance(hooks);
    manager = new GenerateLabelHookManager(mockHooksInstance);

    manager.executeHooks(context);

    assertThat("Hook should have executed",
        jrParams.get("multiTableHookExecuted"), is(equalTo(true)));
  }

  /**
   * Creates a mock CDI Instance with the given hooks.
   *
   * @param hooks
   *     the list of hooks to be returned by the mock instance.
   * @return a mock CDI Instance with the given hooks.
   */
  @SuppressWarnings("unchecked")
  private Instance<GenerateLabelHook> createMockInstance(List<GenerateLabelHook> hooks) {
    final Instance<GenerateLabelHook> instance = mock(Instance.class);
    when(instance.iterator()).thenReturn(hooks.iterator());
    return instance;
  }

  /**
   * Basic test hook that tracks execution order.
   */
  private static class TestHook implements GenerateLabelHook {
    private final String tableId;
    private final int priority;

    /**
     * Creates a new TestHook.
     *
     * @param tableId
     *     the table ID to which the hook applies.
     * @param priority
     *     the priority of the hook.
     */
    TestHook(String tableId, int priority) {
      this.tableId = tableId;
      this.priority = priority;
    }

    /**
     * Executes the hook.
     *
     * @param context
     *     the context in which the hook is executed.
     */
    @Override
    public void execute(GenerateLabelContext context) {
      context.addParameter(EXECUTION_ORDER, priority);
      final Integer counter = (Integer) context.getParameter(EXECUTION_COUNTER);
      context.addParameter(EXECUTION_COUNTER, counter == null ? 1 : counter + 1);
    }

    /**
     * Returns the priority of the hook.
     *
     * @return the priority of the hook.
     */
    @Override
    public int getPriority() {
      return priority;
    }

    /**
     * Returns the list of tables to which the hook applies.
     *
     * @return the list of tables to which the hook applies.
     */
    @Override
    public List<String> tablesToWhichItApplies() {
      final List<String> tables = new ArrayList<>();
      tables.add(tableId);
      return tables;
    }
  }

  /**
   * Test hook that throws exceptions.
   */
  private static class TestHookThatThrows implements GenerateLabelHook {
    private final String tableId;
    private final boolean throwPrintProviderException;

    /**
     * Creates a new TestHookThatThrows.
     *
     * @param tableId
     *     the table ID to which the hook applies.
     * @param throwPrintProviderException
     *     whether to throw a PrintProviderException.
     */
    TestHookThatThrows(String tableId, boolean throwPrintProviderException) {
      this.tableId = tableId;
      this.throwPrintProviderException = throwPrintProviderException;
    }

    /**
     * Executes the hook.
     *
     * @param context
     *     the context in which the hook is executed.
     * @throws PrintProviderException
     *     if a PrintProviderException is thrown.
     */
    @Override
    public void execute(GenerateLabelContext context) throws PrintProviderException {
      if (throwPrintProviderException) {
        throw new PrintProviderException("Hook failed");
      } else {
        throw new OBException("Generic error");
      }
    }

    /**
     * Returns the list of tables to which the hook applies.
     *
     * @return the list of tables to which the hook applies.
     */
    @Override
    public List<String> tablesToWhichItApplies() {
      final List<String> tables = new ArrayList<>();
      tables.add(tableId);
      return tables;
    }
  }

  /**
   * Test hook that throws exception during applicability check.
   */
  private static class TestHookWithApplicabilityExc implements GenerateLabelHook {

    /**
     * Executes the hook.
     *
     * @param context
     *     the context in which the hook is executed.
     */
    @Override
    public void execute(GenerateLabelContext context) {
      context.addParameter("shouldNotExecute", true);
    }

    /**
     * Returns the list of tables to which the hook applies.
     *
     * @return the list of tables to which the hook applies.
     */
    @Override
    public List<String> tablesToWhichItApplies() {
      throw new OBException("Error checking applicability");
    }
  }

  /**
   * Test hook that applies to multiple tables.
   */
  private static class TestHookMultipleTables implements GenerateLabelHook {
    /**
     * Executes the hook.
     *
     * @param context
     *     the context in which the hook is executed.
     */
    @Override
    public void execute(GenerateLabelContext context) {
      context.addParameter("multiTableHookExecuted", true);
    }

    /**
     * Returns the list of tables to which the hook applies.
     *
     * @return the list of tables to which the hook applies.
     */
    @Override
    public List<String> tablesToWhichItApplies() {
      final List<String> tables = new ArrayList<>();
      tables.add(TABLE_ID_ORDER);
      tables.add(TABLE_ID_PRODUCT);
      return tables;
    }
  }
}
