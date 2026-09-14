package com.agentengine.connectors.api.services;

import com.agentengine.connectors.api.beans.Connection;

public interface ConnectionRefresher {
  Connection refreshIfNeeded(Connection connection);
}
