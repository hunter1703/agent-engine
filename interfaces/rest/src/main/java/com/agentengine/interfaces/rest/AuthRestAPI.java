package com.agentengine.interfaces.rest;

import com.agentengine.identity.IdentityProvider;
import com.agentengine.identity.LoginRequest;
import com.agentengine.identity.LoginResult;
import com.agentengine.interfaces.rest.dto.responses.MeResponse;
import com.agentengine.tenancy.AccessControlService;
import com.agentengine.tenancy.UserService;
import com.agentengine.tenancy.beans.User;
import com.agentengine.util.common.config.ApplicationConfig;
import com.agentengine.util.common.exception.UnauthorizedException;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.context.Context;
import com.agentengine.util.context.ContextAware;
import com.agentengine.util.context.RequestContextProvider;
import com.agentengine.util.context.UserCaller;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Locale;

@Path("/v1/auth")
@ContextAware
public class AuthRestAPI {
  private static final int SESSION_COOKIE_MAX_AGE_SECONDS =
      (int) IdentityProvider.SESSION_LIFETIME.toSeconds();
  private static final String SESSION_COOKIE_SAME_SITE_KEY =
      "agent-engine.auth.session-cookie-same-site";

  private final IdentityProvider identityProvider;
  private final NewCookie.SameSite sessionCookieSameSite;
  private final RequestContextProvider requestContextProvider;
  private final UserService userService;
  private final AccessControlService accessControlService;

  @Inject
  public AuthRestAPI(
      final IdentityProvider identityProvider,
      final ApplicationConfig applicationConfig,
      final RequestContextProvider requestContextProvider,
      final UserService userService,
      final AccessControlService accessControlService) {
    this.identityProvider = identityProvider;
    this.sessionCookieSameSite =
        sameSite(applicationConfig.getString(SESSION_COOKIE_SAME_SITE_KEY));
    this.requestContextProvider = requestContextProvider;
    this.userService = userService;
    this.accessControlService = accessControlService;
  }

  @GET
  @Path("/me")
  @Produces(MediaType.APPLICATION_JSON)
  public Response me() {
    final Context context = requestContextProvider.get();
    final User user =
        context.userCaller().map(UserCaller::userId).map(userService::get).orElse(null);
    final List<String> roleIds = user == null ? List.of() : accessControlService.getRolesForUser(user.getId());
    return Response.ok(new MeResponse(context.customerId(), user, roleIds))
        .build();
  }

  @POST
  @Path("/login")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response login(final LoginRequest request) {
    try {
      final LoginResult result = identityProvider.login(request);
      return Response.ok()
          .header(
              "Set-Cookie",
              sessionCookie(result.token(), SESSION_COOKIE_MAX_AGE_SECONDS).toString()
                  + "; Partitioned")
          .build();
    } catch (final UnauthorizedException exception) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
  }

  @POST
  @Path("/logout")
  public Response logout(@CookieParam(AuthFilter.SESSION_COOKIE) final String token) {
    identityProvider.logout(token);
    return Response.ok()
        .header("Set-Cookie", sessionCookie("", 0).toString() + "; Partitioned")
        .build();
  }

  private NewCookie sessionCookie(final String value, final int maxAgeSeconds) {
    return new NewCookie.Builder(AuthFilter.SESSION_COOKIE)
        .value(value)
        .path("/")
        .maxAge(maxAgeSeconds)
        .secure(true)
        .httpOnly(true)
        .sameSite(sessionCookieSameSite)
        .build();
  }

  /** The configured SameSite policy of the session cookie; STRICT when unset or unrecognised. */
  private static NewCookie.SameSite sameSite(final String value) {
    if (StringUtils.isBlank(value)) {
      return NewCookie.SameSite.STRICT;
    }
    try {
      return NewCookie.SameSite.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (final IllegalArgumentException exception) {
      return NewCookie.SameSite.STRICT;
    }
  }
}
