package com.localagent.engine.beans.config;

import com.alibaba.fastjson2.annotation.JSONField;

public class LastNContextConfig extends ContextConfig {
    @JSONField(name = "keep_last")
    private int keepLast = 24;

    public LastNContextConfig() {
        super(ContextType.LAST_N);
    }

    public int getKeepLast() {
        return keepLast;
    }

    public void setKeepLast(final int keepLast) {
        this.keepLast = keepLast;
    }
}
