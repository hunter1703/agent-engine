package com.agentengine.knowledge.api.beans;

import com.agentengine.util.agents.beans.config.KnowledgeSettings;
import com.agentengine.util.common.beans.FileDetails;
import java.util.List;

public class IndexRequest {

    private String agentId;
    private List<String> grants;
    private FileDetails fileDetails;
    private String title;
    private String description;
    private KnowledgeSettings settings;
    private boolean waitForCompletion;

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(final String agentId) {
        this.agentId = agentId;
    }

    public List<String> getGrants() {
        return grants;
    }

    public void setGrants(final List<String> grants) {
        this.grants = grants;
    }

    public FileDetails getFileDetails() {
        return fileDetails;
    }

    public void setFileDetails(final FileDetails fileDetails) {
        this.fileDetails = fileDetails;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(final String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public KnowledgeSettings getSettings() {
        return settings;
    }

    public void setSettings(final KnowledgeSettings settings) {
        this.settings = settings;
    }

    public boolean isWaitForCompletion() {
        return waitForCompletion;
    }

    public void setWaitForCompletion(final boolean waitForCompletion) {
        this.waitForCompletion = waitForCompletion;
    }
}
