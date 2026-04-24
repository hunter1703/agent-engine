package com.agentengine.runtime.tools.image;

import com.agentengine.runtime.annotations.DiscoverableTool;
import com.agentengine.util.common.annotations.ToolSchema;
import com.agentengine.runtime.tools.Tool;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolRiskLevel;
import com.agentengine.util.common.beans.FileDetails;
import com.agentengine.util.common.service.CloudStorageService;

import java.util.Map;

/**
 * Agent tool that downloads an image and returns it as base64 for visual inspection.
 */
@DiscoverableTool
public final class ViewImageTool extends Tool {

    public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "view_image",
            "Downloads an image and returns it as a base64-encoded string so you can inspect its visual "
                    + "content. Use this whenever you need to examine what an image looks like. "
                    + "Returns: { base64: \"<data>\", mimeType: \"<type>\" }.",
            Map.of(),
            ToolRiskLevel.LOW);

    public ViewImageTool() {
        super(DESCRIPTOR);
    }

    public Map<String, Object> execute(CloudStorageService cloudStorageService,
            @ToolSchema(name = "file", description = "File details of the image to view.") FileDetails file) {
        try {
            if (file == null) {
                return Map.of("error", "file is required");
            }
            final FileDetails resolved = file.resolved(cloudStorageService);
            return Map.of("base64", resolved.base64Content(), "mimeType", resolved.mimeType());
        } catch (Exception e) {
            return Map.of("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }
}
