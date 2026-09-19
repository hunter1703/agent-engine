package com.agentengine.util.pekko.actor;

import com.agentengine.util.context.Context;
import com.agentengine.util.pekko.PekkoSerializable;

public class ContextualCommand implements PekkoSerializable {

    private Context context = Context.current().orElse(null);

    public Context getContext() {
        return context;
    }

    public void setContext(final Context context) {
        this.context = context;
    }
}
