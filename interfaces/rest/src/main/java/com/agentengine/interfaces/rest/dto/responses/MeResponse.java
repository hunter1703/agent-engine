package com.agentengine.interfaces.rest.dto.responses;

import com.agentengine.tenancy.beans.User;
import java.util.List;

/**
 * The current request's logged-in user: its own details and the ids of every role mapped
 * directly to its principal.
 */
public record MeResponse(String customerId, User user, List<String> roleIds) {}
