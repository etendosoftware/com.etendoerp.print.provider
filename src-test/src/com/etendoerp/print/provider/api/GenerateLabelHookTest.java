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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openbravo.model.ad.datamodel.Table;

import com.etendoerp.print.provider.api.GenerateLabelHook.GenerateLabelContext;
import com.etendoerp.print.provider.data.Provider;
import com.etendoerp.print.provider.data.TemplateLine;

/**
 * Unit tests for GenerateLabelHook interface and GenerateLabelContext class.
 * Validates the context object behavior, parameter management, and hook contract.
 */
@ExtendWith(MockitoExtension.class)
class GenerateLabelHookTest {

  @Mock
  private Provider mockProvider;

  @Mock
  private Table mockTable;

  @Mock
  private TemplateLine mockTemplateLine;

  private Map<String, Object> jrParams;
  private GenerateLabelContext context;

  private static final String RECORD_ID = "test-record-123";
  private static final String PARAM_KEY = "testParam";
  private static final String PARAM_VALUE = "testValue";
  private static final String EXISTING_PARAM_KEY = "existingParam";
  private static final String TEST_TABLE_ID = "testTableId";
  private static final String CUSTOM_VALUE = "customValue";
  private static final String CUSTOM_PARAM = "customParam";

  /**
   * Sets up the test environment by creating mock objects and initializing the context.
   *
   * @throws JSONException
   *     if there is an error creating the JSON parameters.
   */
  @BeforeEach
  void setUp() throws JSONException {
    JSONObject jsonParameters = new JSONObject();
    jsonParameters.put(CUSTOM_PARAM, CUSTOM_VALUE);
    jrParams = new HashMap<>();
    jrParams.put(EXISTING_PARAM_KEY, EXISTING_PARAM_KEY);

    context = new GenerateLabelContext(
        mockProvider,
        mockTable,
        RECORD_ID,
        mockTemplateLine,
        jsonParameters,
        jrParams
    );
  }

  /**
   * Tests the context creation by verifying the context object properties.
   */
  @Test
  void testContextCreation() {
    assertThat("Context should not be null", context, is(notNullValue()));
    assertThat("Provider should not be null", context.getProvider(), is(notNullValue()));
    assertThat("Table should match", context.getTable(), is(mockTable));
    assertThat("Record ID should match", context.getRecordId(), is(RECORD_ID));
    assertThat("Template line should not be null", context.getTemplateLine(), is(notNullValue()));
    assertThat("JSON parameters should not be null", context.getJsonParameters(), is(notNullValue()));
    assertThat("JR parameters should match", context.getParameters(), is(jrParams));
  }

  /**
   * Tests the addParameter method by verifying the parameter is added to the JR parameters map and retrievable via the context.
   */
  @Test
  void testAddParameter() {
    context.addParameter(PARAM_KEY, PARAM_VALUE);

    assertThat("Parameter should be added to JR params map",
        jrParams.get(PARAM_KEY), is(PARAM_VALUE));
    assertThat("Parameter should be retrievable via context",
        context.getParameter(PARAM_KEY), is(PARAM_VALUE));
  }

  /**
   * Tests the addParameter method by verifying the parameter is overwritten in the JR parameters map and retrievable via the context.
   */
  @Test
  void testAddParameterOverwritesExisting() {
    final String newValue = "newValue";
    context.addParameter(EXISTING_PARAM_KEY, newValue);

    assertThat("Parameter should be overwritten",
        context.getParameter(EXISTING_PARAM_KEY), is(newValue));
  }

  /**
   * Tests the addParameter method by verifying the parameter is added to the JR parameters map and retrievable via the context.
   */
  @Test
  void testAddParameterWithNullValue() {
    context.addParameter(PARAM_KEY, null);

    assertThat("Null value should be stored",
        context.getParameter(PARAM_KEY), is(nullValue()));
    assertThat("Parameter key should exist in map",
        jrParams.containsKey(PARAM_KEY), is(true));
  }

  /**
   * Tests the getParameter method by verifying the parameter is retrievable via the context.
   */
  @Test
  void testGetParameterReturnsNull() {
    final Object result = context.getParameter("nonExistentParam");

    assertThat("Non-existent parameter should return null", result, is(nullValue()));
  }

  /**
   * Tests the getParameters method by verifying the parameters map is returned.
   */
  @Test
  void testGetParametersReturnsSameMap() {
    final Map<String, Object> retrievedParams = context.getParameters();

    assertThat("Should return the same map instance", retrievedParams, is(jrParams));
    assertThat("Map should contain existing parameter",
        retrievedParams.get(EXISTING_PARAM_KEY), is(EXISTING_PARAM_KEY));
  }

  /**
   * Tests the getProvider method by verifying the provider is retrievable via the context.
   */
  @Test
  void testGetProvider() {
    assertThat("Provider should be accessible", context.getProvider(), is(notNullValue()));
  }

  /**
   * Tests the getTable method by verifying the table is retrievable via the context.
   */
  @Test
  void testGetTable() {
    assertThat("Table should be accessible", context.getTable(), is(mockTable));
  }

  /**
   * Tests the getRecordId method by verifying the record ID is retrievable via the context.
   */
  @Test
  void testGetRecordId() {
    assertThat("Record ID should be accessible", context.getRecordId(), is(RECORD_ID));
  }

  /**
   * Tests the getTemplateLine method by verifying the template line is retrievable via the context.
   */
  @Test
  void testGetTemplateLine() {
    assertThat("Template line should be accessible",
        context.getTemplateLine(), is(notNullValue()));
  }

  /**
   * Tests the getJsonParameters method by verifying the JSON parameters are retrievable via the context.
   */
  @Test
  void testGetJsonParameters() throws JSONException {
    final JSONObject params = context.getJsonParameters();

    assertThat("JSON parameters should be accessible", params, is(notNullValue()));
    assertThat("JSON parameter value should match",
        params.getString(CUSTOM_PARAM), is(CUSTOM_VALUE));
  }

  /**
   * Tests the multiple parameter additions by verifying the parameters are retrievable via the context.
   */
  @Test
  void testMultipleParameterAdditions() {
    context.addParameter("param1", "value1");
    context.addParameter("param2", 123);
    context.addParameter("param3", true);
    context.addParameter("param4", 45.67);

    assertThat("String parameter should be added", context.getParameter("param1"), is("value1"));
    assertThat("Integer parameter should be added", context.getParameter("param2"), is(123));
    assertThat("Boolean parameter should be added", context.getParameter("param3"), is(true));
    assertThat("Double parameter should be added", context.getParameter("param4"), is(45.67));
  }

  /**
   * Tests the context with null JSON parameters by verifying the context accepts null JSON parameters.
   */
  @Test
  void testContextWithNullJsonParameters() {

    final GenerateLabelContext contextWithNull = new GenerateLabelContext(
        mockProvider,
        mockTable,
        RECORD_ID,
        mockTemplateLine,
        null,
        jrParams
    );

    assertThat("Context should accept null JSON parameters",
        contextWithNull.getJsonParameters(), is(nullValue()));
  }

  /**
   * Tests the context with empty JR parameters by verifying the context accepts empty JR parameters.
   */
  @Test
  void testContextWithEmptyJrParams() throws JSONException {
    JSONObject localJsonParameters = new JSONObject();
    localJsonParameters.put(CUSTOM_PARAM, CUSTOM_VALUE);

    final Map<String, Object> emptyParams = new HashMap<>();
    final GenerateLabelContext contextWithEmpty = new GenerateLabelContext(
        mockProvider,
        mockTable,
        RECORD_ID,
        mockTemplateLine,
        localJsonParameters,
        emptyParams
    );

    assertThat("Parameters map should be empty",
        contextWithEmpty.getParameters().size(), is(0));

    contextWithEmpty.addParameter("newParam", "newValue");

    assertThat("Should be able to add to empty map",
        contextWithEmpty.getParameters().size(), is(1));
  }

  /**
   * Tests the default priority by verifying the default priority is 100.
   */
  @Test
  void testDefaultPriority() {
    final TestHook hook = new TestHook();

    assertThat("Default priority should be 100", hook.getPriority(), is(100));
  }

  /**
   * Tests the custom priority by verifying the custom priority is 50.
   */
  @Test
  void testCustomPriority() {
    final TestHookWithCustomPriority hook = new TestHookWithCustomPriority();

    assertThat("Custom priority should be respected", hook.getPriority(), is(50));
  }

  /**
   * Tests the table to which it applies by verifying the tables list contains the expected table.
   */
  @Test
  void testTableToWhichItApplies() {
    final TestHook hook = new TestHook();
    final List<String> tables = hook.tablesToWhichItApplies();

    assertThat("Tables list should not be null", tables, is(notNullValue()));
    assertThat("Tables list should contain expected table", tables.contains(TEST_TABLE_ID), is(true));
    assertThat("Tables list size should be 1", tables.size(), is(1));
  }

  /**
   * Tests the execute method by verifying the hook throws a PrintProviderException.
   */
  @Test
  void testExecuteThrowsException() {
    final TestHookThatThrowsExc hook = new TestHookThatThrowsExc();

    assertThrows(PrintProviderException.class, () -> hook.execute(context),
        "Hook should throw PrintProviderException");
  }

  // Test hook implementations

  /**
   * Basic test hook implementation with default priority.
   */
  private static class TestHook implements GenerateLabelHook {
    /**
     * Executes the hook.
     */
    @Override
    public void execute(GenerateLabelContext context) {
      context.addParameter("hookExecuted", true);
    }

    /**
     * Returns the list of tables to which the hook applies.
     */
    @Override
    public List<String> tablesToWhichItApplies() {
      final List<String> tables = new ArrayList<>();
      tables.add(TEST_TABLE_ID);
      return tables;
    }
  }

  /**
   * Test hook with custom priority.
   */
  private static class TestHookWithCustomPriority implements GenerateLabelHook {
    /**
     * Executes the hook.
     */
    @Override
    public void execute(GenerateLabelContext context) {
      context.addParameter("customPriorityHookExecuted", true);
    }

    /**
     * Returns the priority of the hook.
     */
    @Override
    public int getPriority() {
      return 50;
    }

    /**
     * Returns the list of tables to which the hook applies.
     */
    @Override
    public List<String> tablesToWhichItApplies() {
      final List<String> tables = new ArrayList<>();
      tables.add(TEST_TABLE_ID);
      return tables;
    }
  }

  /**
   * Test hook that throws an exception.
   */
  private static class TestHookThatThrowsExc implements GenerateLabelHook {
    /**
     * Executes the hook.
     */
    @Override
    public void execute(GenerateLabelContext context) throws PrintProviderException {
      throw new PrintProviderException("Test exception");
    }

    /**
     * Returns the list of tables to which the hook applies.
     */
    @Override
    public List<String> tablesToWhichItApplies() {
      final List<String> tables = new ArrayList<>();
      tables.add(TEST_TABLE_ID);
      return tables;
    }
  }
}
