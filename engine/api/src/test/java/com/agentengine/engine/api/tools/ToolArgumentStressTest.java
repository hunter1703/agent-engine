package com.agentengine.engine.api.tools;

import static org.junit.jupiter.api.Assertions.*;

import com.agentengine.engine.api.tools.annotations.ToolSchema;
import com.google.adk.tools.ToolContext;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.ArrayList;
import java.util.HashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ToolArgumentStressTest {

    private ToolContext toolContext;

    @BeforeEach
    void setUp() {
        // buildArguments doesn't call methods on toolContext, just passes it through.
        toolContext = null; 
    }

    // --- Helper Beans for Stress Testing ---

    public static class NestedBean {
        @ToolSchema(name = "renamed_field")
        private String field;
        private int value;

        public NestedBean() {}

        public String getField() { return field; }
        public void setField(String field) { this.field = field; }
        public int getValue() { return value; }
        public void setValue(int value) { this.value = value; }
    }

    public static class RootBean {
        private String name;
        private NestedBean nested;
        private List<NestedBean> items;

        public RootBean() {}

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public NestedBean getNested() { return nested; }
        public void setNested(NestedBean nested) { this.nested = nested; }
        public List<NestedBean> getItems() { return items; }
        public void setItems(List<NestedBean> items) { this.items = items; }
    }

    public enum TestEnum {
        UNKNOWN,
        ALPHA,
        BETA;

        public static TestEnum valueOfOrDefault(final String value) {
            if (value == null || value.isBlank()) {
                return UNKNOWN;
            }
            try {
                return TestEnum.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return UNKNOWN;
            }
        }
    }

    // --- The Stress Test Tool Implementation ---

    public static class StressTool extends Tool {
        public StressTool() {
            super(new ToolDescriptor("stress", "Stress testing tool", List.of("ALL"), Map.of()));
        }

        public Map<String, Object> execute(
                @ToolSchema(name = "p_string") String s,
                @ToolSchema(name = "p_int") int i,
                @ToolSchema(name = "p_bool") boolean b,
                @ToolSchema(name = "p_enum") TestEnum e,
                @ToolSchema(name = "p_bean") RootBean bean,
                @ToolSchema(name = "p_list_strings") List<String> listStrings,
                @ToolSchema(name = "p_list_beans") List<NestedBean> listBeans,
                @ToolSchema(name = "p_map") Map<String, Object> map,
                @ToolSchema(name = "p_optional", optional = true) String opt,
                ToolContext context // Named 'context' but should work by type
        ) {
            return Map.of("status", "success");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void testBuildArgumentsStress() throws Exception {
        StressTool tool = new StressTool();
        
        // Use HashMap to be safe and avoid Map.of limitations/order issues
        Map<String, Object> rawArgs = new HashMap<>();
        rawArgs.put("p_string", "test");
        rawArgs.put("p_int", 42);
        rawArgs.put("p_bool", true);
        rawArgs.put("p_enum", "ALPHA");
        
        Map<String, Object> nestedBeanMap = new HashMap<>();
        nestedBeanMap.put("renamed_field", "nested_val");
        nestedBeanMap.put("value", 100);
        
        Map<String, Object> item1Map = new HashMap<>();
        item1Map.put("renamed_field", "item1");
        item1Map.put("value", 1);
        
        Map<String, Object> rootBeanMap = new HashMap<>();
        rootBeanMap.put("name", "root");
        rootBeanMap.put("nested", nestedBeanMap);
        rootBeanMap.put("items", List.of(item1Map));
        
        rawArgs.put("p_bean", rootBeanMap);
        rawArgs.put("p_list_strings", List.of("a", "b"));
        
        Map<String, Object> listItemMap = new HashMap<>();
        listItemMap.put("renamed_field", "list_item");
        listItemMap.put("value", 2);
        
        rawArgs.put("p_list_beans", List.of(listItemMap));
        rawArgs.put("p_map", Map.of("key", "val"));

        // Access private buildArguments via reflection for direct testing
        java.lang.reflect.Method buildArgsMethod = Tool.class.getDeclaredMethod("buildArguments", Map.class, ToolContext.class);
        buildArgsMethod.setAccessible(true);
        
        Object[] args = (Object[]) buildArgsMethod.invoke(tool, rawArgs, toolContext);

        // Assertions
        assertEquals("test", args[0]);
        assertEquals(42, args[1]);
        assertEquals(true, args[2]);
        assertEquals(TestEnum.ALPHA, args[3]);
        
        // RootBean assertions
        RootBean bean = (RootBean) args[4];
        assertNotNull(bean);
        assertEquals("root", bean.getName());
        assertNotNull(bean.getNested());
        assertEquals("nested_val", bean.getNested().getField());
        assertEquals(100, bean.getNested().getValue());
        assertNotNull(bean.getItems());
        assertEquals(1, bean.getItems().size());
        assertEquals("item1", bean.getItems().get(0).getField());

        // Lists
        assertEquals(List.of("a", "b"), args[5]);
        List<NestedBean> lb = (List<NestedBean>) args[6];
        assertEquals(1, lb.size());
        assertEquals("list_item", lb.get(0).getField());

        // Map
        assertEquals("val", ((Map<String, Object>)args[7]).get("key"));

        // Optional
        assertNull(args[8]);

        // ToolContext
        assertEquals(toolContext, args[9]);
    }

    @Test
    void testMissingRequiredThrows() {
        StressTool tool = new StressTool();
        Map<String, Object> incompleteArgs = Map.of("p_string", "missing_others");

        // The tool's runAsync catches the IllegalArgumentException from buildArguments
        // and returns a Map with status="error".
        Map<String, Object> result = tool.runAsync(incompleteArgs, toolContext).blockingGet();
        assertEquals("error", result.get("status"));
        assertTrue(result.get("message").toString().contains("internal error") || result.get("message").toString().contains("not found"));
    }

    @Test
    void testNormalizationWithStandardJavaNames() throws Exception {
        StressTool tool = new StressTool();
        
        // Test that it still works when using standard Java names instead of @ToolSchema renamed fields
        Map<String, Object> rawArgs = Map.of(
            "p_string", "s", "p_int", 1, "p_bool", true, "p_enum", "BETA",
            "p_bean", Map.of("name", "n", "nested", Map.of("field", "standard", "value", 5)),
            "p_list_strings", List.of(), "p_list_beans", List.of(), "p_map", Map.of()
        );

        java.lang.reflect.Method buildArgsMethod = Tool.class.getDeclaredMethod("buildArguments", Map.class, ToolContext.class);
        buildArgsMethod.setAccessible(true);
        Object[] args = (Object[]) buildArgsMethod.invoke(tool, rawArgs, toolContext);

        RootBean bean = (RootBean) args[4];
        assertNotNull(bean);
        assertNotNull(bean.getNested());
        assertEquals("standard", bean.getNested().getField()); // Should bind even if renamed via @ToolSchema if Java name matches
    }
}
