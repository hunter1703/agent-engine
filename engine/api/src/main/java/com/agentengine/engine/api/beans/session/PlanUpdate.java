package com.agentengine.engine.api.beans.session;

import java.util.List;

public record PlanUpdate(String explanation, List<PlanItem> plan) {
}
