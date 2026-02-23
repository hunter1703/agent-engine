package com.agentengine.engine.tools.schema;

import static org.junit.jupiter.api.Assertions.*;

import com.agentengine.engine.api.tools.annotations.ToolSchema;
import com.agentengine.engine.api.utils.ToolUtils;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

class ToolUtilsTest {

    @Test
    void testResolveParameterName_WithAnnotation() throws NoSuchMethodException {
        Method method = MockTool.class.getMethod("methodWithAnnotation", String.class);
        Parameter parameter = method.getParameters()[0];
        
        String name = ToolUtils.resolveParameterName(parameter);
        
        assertEquals("custom_name", name);
    }

    @Test
    void testResolveParameterName_WithAnnotationEmptyName() throws NoSuchMethodException {
        Method method = MockTool.class.getMethod("methodWithEmptyAnnotation", String.class);
        Parameter parameter = method.getParameters()[0];
        
        String name = ToolUtils.resolveParameterName(parameter);
        
        // Should fall back to parameter name if annotation name is blank
        // Note: Actual parameter names are often arg0, arg1 etc. unless compiled with -parameters
        assertNotNull(name);
        assertFalse(name.isEmpty());
    }

    @Test
    void testResolveParameterName_WithoutAnnotation() throws NoSuchMethodException {
        Method method = MockTool.class.getMethod("methodWithoutAnnotation", String.class);
        Parameter parameter = method.getParameters()[0];
        
        String name = ToolUtils.resolveParameterName(parameter);
        
        assertNotNull(name);
        assertFalse(name.isEmpty());
    }

    @Test
    void testResolveParameterName_WithAnnotationBlankName() throws NoSuchMethodException {
        Method method = MockTool.class.getMethod("methodWithBlankAnnotation", String.class);
        Parameter parameter = method.getParameters()[0];
        
        String name = ToolUtils.resolveParameterName(parameter);
        
        assertNotNull(name);
        assertFalse(name.isEmpty());
    }

    static class MockTool {
        public void methodWithAnnotation(@ToolSchema(name = "custom_name") String param) {}
        public void methodWithEmptyAnnotation(@ToolSchema(name = "") String param) {}
        public void methodWithBlankAnnotation(@ToolSchema(name = "   ") String param) {}
        public void methodWithoutAnnotation(String param) {}
    }
}
