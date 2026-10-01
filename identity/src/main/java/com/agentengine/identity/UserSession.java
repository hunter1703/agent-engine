package com.agentengine.identity;

import com.agentengine.util.common.annotations.Index;
import com.agentengine.util.common.beans.BaseEntity;
import java.util.Date;

@Index(name = "expires_at_ttl", def = "{'expiresAt': 1}", expireAfterSeconds = 0)
public class UserSession extends BaseEntity {

  private String customerId;
  private String userId;
  private Date expiresAt;

  public String getCustomerId() {
    return customerId;
  }

  public void setCustomerId(final String customerId) {
    this.customerId = customerId;
  }

  public Date getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(final Date expiresAt) {
    this.expiresAt = expiresAt;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(final String userId) {
    this.userId = userId;
  }
}
