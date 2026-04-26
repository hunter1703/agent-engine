package com.agentengine.runtime.tools.image;

import com.agentengine.runtime.annotations.DiscoverableTool;
import com.agentengine.runtime.tools.BinaryDataTool;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolRiskLevel;
import com.agentengine.util.common.annotations.ToolSchema;
import com.agentengine.util.common.beans.FileDetails;
import com.agentengine.util.common.service.CloudStorageService;

import java.util.List;
import java.util.Map;

/**
 * Agent tool that opens a file and returns its content for inspection.
 */
@DiscoverableTool
public final class OpenFileTool extends BinaryDataTool {

    public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            "open_file",
            "Opens a file and returns its content for inspection. "
                    + "Use this whenever you need to examine content of a file.",
            Map.of(),
            ToolRiskLevel.LOW);

    public OpenFileTool(final CloudStorageService cloudStorageService) {
        super(DESCRIPTOR, false, cloudStorageService);
    }

    public FileResponse execute(
            @ToolSchema(name = "fileDetails", description = "A nested file details map containing complete file details.") FileDetails fileDetails) {
        return new FileResponse(List.of(fileDetails));
    }
}
