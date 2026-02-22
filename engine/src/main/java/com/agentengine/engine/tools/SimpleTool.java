package com.agentengine.engine.tools;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.tools.Tool;
import com.agentengine.engine.tools.planning.UpdateTaskTool;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.FunctionTool;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

/**
 * All simple tools must have a no-arg constructor
 */
public abstract class SimpleTool implements Tool {

    public final BaseTool create() {
        try {
            final Constructor<? extends SimpleTool> declaredConstructor = this.getClass().getDeclaredConstructor();
            final SimpleTool tool = declaredConstructor.newInstance();
            return FunctionTool.create(tool, "execute");
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
