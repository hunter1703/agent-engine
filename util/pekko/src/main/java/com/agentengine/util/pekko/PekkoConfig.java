package com.agentengine.util.pekko;

import com.agentengine.util.mongodb.infra.InfraConfig;

import java.util.List;

public class PekkoConfig extends InfraConfig {
    private String hostname;
    private int port;

    private List<String> seedNodes;

    private String clusterName;

    private String jdbcUrl;

    private String jdbcUser;

    private String jdbcPassword;
    private int snapshotThreshold = 100;

    public PekkoConfig() {
        super("PEKKO");
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(final String hostname) {
        this.hostname = hostname;
    }

    public int getPort() {
        return port;
    }

    public void setPort(final int port) {
        this.port = port;
    }

    public List<String> getSeedNodes() {
        return seedNodes;
    }

    public void setSeedNodes(final List<String> seedNodes) {
        this.seedNodes = seedNodes;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(final String clusterName) {
        this.clusterName = clusterName;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(final String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public String getJdbcUser() {
        return jdbcUser;
    }

    public void setJdbcUser(final String jdbcUser) {
        this.jdbcUser = jdbcUser;
    }

    public String getJdbcPassword() {
        return jdbcPassword;
    }

    public void setJdbcPassword(final String jdbcPassword) {
        this.jdbcPassword = jdbcPassword;
    }

    public int getSnapshotThreshold() {
        return snapshotThreshold;
    }

    public void setSnapshotThreshold(final int snapshotThreshold) {
        this.snapshotThreshold = snapshotThreshold;
    }
}
