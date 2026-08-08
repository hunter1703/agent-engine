package com.agentengine.util.scripts.templated;

import java.util.Map;

public interface Template<T> {

    T getValue(Map<String, Object> parameters);

    static <S> Template<S> constant(S value) {
        return _ -> value;
    }
}
