package com.agentengine.util.agents.beans.tools;

import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.builder.annotations.UiField;
import com.agentengine.util.common.builder.annotations.UiLookup;
import com.agentengine.util.common.builder.annotations.UiLookupOption;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record ConnectorToolConfig(
    @UiField(label = "App", order = 10) @UiLookup(assetType = AssetClass.CONNECTOR_APP) @NotBlank
        String app,
    @UiField(label = "Connectors", order = 20)
        @UiLookup(
            assetType = AssetClass.CONNECTOR,
            options = {@UiLookupOption(key = "appName", expr = "$item.app")})
        List<String> connectors) {}
