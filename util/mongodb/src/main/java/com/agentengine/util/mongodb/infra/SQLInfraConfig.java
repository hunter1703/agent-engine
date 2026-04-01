package com.agentengine.util.mongodb.infra;

import com.agentengine.util.common.Secure;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator(value = "sql")
public class SQLInfraConfig extends InfraConfig {
    public static final String TYPE = "sql";

    private String jdbcUrl;
    private String jdbcUser;

    @Secure
    private String jdbcPassword;

    public SQLInfraConfig() {
        super(TYPE);
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
}
