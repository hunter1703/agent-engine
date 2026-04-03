package com.agentengine.util.pekko;

import com.agentengine.util.mongodb.infra.InfraConfig;
import java.util.List;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator(value = "PEKKO")
public class PekkoConfig extends InfraConfig {
    public static final String TYPE = "PEKKO";
    public static final String CATEGORY = "PEKKO";
    public static final String CONFIG_ID = "default";

    private List<String> seedNodes;
    private String clusterName;
    private int snapshotThreshold = 100;

    public PekkoConfig() {
        super(TYPE);
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

    public int getSnapshotThreshold() {
        return snapshotThreshold;
    }

    public void setSnapshotThreshold(final int snapshotThreshold) {
        this.snapshotThreshold = snapshotThreshold;
    }
}
