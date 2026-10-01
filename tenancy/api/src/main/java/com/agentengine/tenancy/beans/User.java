package com.agentengine.tenancy.beans;

import com.agentengine.util.common.annotations.Index;
import com.agentengine.util.common.annotations.Permissioned;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.beans.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import java.util.Locale;

@Permissioned(assetClass = AssetClass.USER)
@Index(def = "{'username': 1}", name = "username_unique", unique = true)
public class User extends BaseEntity {

  public static final String FIELD_USERNAME = "username";
  public static final String FIELD_PASSWORD_HASH = "passwordHash";

  private String username;
  private String status = UserStatus.ACTIVE.name();

  /** The bcrypt hash of the user's password. Never read back, except to check a password. */
  @JsonIgnore @NotBlank private String passwordHash;

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

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(final String passwordHash) {
    this.passwordHash = passwordHash;
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
