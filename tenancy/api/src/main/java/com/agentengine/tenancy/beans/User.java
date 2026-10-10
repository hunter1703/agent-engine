package com.agentengine.tenancy.beans;

import com.agentengine.util.common.annotations.Index;
import com.agentengine.util.common.annotations.Permissioned;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.beans.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import java.util.Locale;
import org.bson.codecs.pojo.annotations.BsonIgnore;

@Permissioned(assetClass = AssetClass.USER)
@Index(def = "{'username': 1}", name = "username_unique", unique = true)
public class User extends BaseEntity {

  public static final String FIELD_USERNAME = "username";
  public static final String FIELD_PASSWORD_HASH = "passwordHash";
  public static final String FIELD_TIME_ZONE = "timeZone";
  public static final String DEFAULT_TIME_ZONE = "UTC";

  private String username;
  private String status = UserStatus.ACTIVE.name();

  /** An IANA time zone id, e.g. {@code Asia/Kolkata}: the zone times are shown to this user in. */
  private String timeZone = DEFAULT_TIME_ZONE;

  /** The bcrypt hash of the user's password. Never read back, except to check a password. */
  @JsonIgnore @NotBlank private String passwordHash;

  @BsonIgnore private String password;

  public String getUsername() {
    return username;
  }

  public void setUsername(final String username) {
    this.username = username;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(final String status) {
    this.status = status;
  }

  public String getTimeZone() {
    return timeZone;
  }

  public void setTimeZone(final String timeZone) {
    this.timeZone = timeZone;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(final String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public enum UserStatus {
    UNKNOWN,
    ACTIVE,
    DISABLED;

    public static UserStatus valueOfOrDefault(final String value) {
      if (value == null || value.isBlank()) {
        return UNKNOWN;
      }
      try {
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
      } catch (final IllegalArgumentException exception) {
        return UNKNOWN;
      }
    }
  }
}
