package com.agentengine.interfaces.rest;

import com.agentengine.identity.IdentityProvider;
import com.agentengine.identity.UserSession;
import com.agentengine.tenancy.CustomerService;
import com.agentengine.tenancy.UserService;
import com.agentengine.tenancy.beans.Customer;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.context.Caller;
import com.agentengine.util.context.Context;
import com.agentengine.util.context.Principal;
import com.agentengine.util.context.RequestContextProvider;
import com.agentengine.util.context.UserCaller;
import com.agentengine.util.distributed.CacheScope;
import com.agentengine.util.distributed.DistributedCache;
import com.agentengine.util.distributed.DistributedCacheManager;
import com.google.common.cache.CacheBuilder;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;
import org.jboss.resteasy.reactive.server.ServerResponseFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Binds every request to a context: the customer its origin belongs to, and the user its session
 * cookie is logged in as. Only logging in, logging out and preflight requests go without a user. A
 * logged-in request that names an origin must come from its session's customer: a browser names the
 * origin of every cross-site request, so a page on any other site cannot use the session cookie it
 * sends along.
 */
@Singleton
public class AuthFilter {
  private static final Logger LOG = LoggerFactory.getLogger(AuthFilter.class);
  static final String SESSION_COOKIE = "session";
  private static final String REQUEST_ID_HEADER = "X-Request-Id";
  private static final String ORIGIN_HEADER = "Origin";
  private static final String LOGIN_PATH = "v1/auth/login";
  private static final String LOGOUT_PATH = "v1/auth/logout";

  private final RequestContextProvider requestContextProvider;
  private final IdentityProvider identityProvider;
  private final CustomerService customerService;
  private final UserService userService;
  private final DistributedCache<Optional<Customer>> domainVsCustomer;
  private final DistributedCache<Boolean> activeUsersCache;

  @Inject
  public AuthFilter(
      final RequestContextProvider requestContextProvider,
      final IdentityProvider identityProvider,
      final CustomerService customerService,
      final UserService userService,
      final DistributedCacheManager cacheManager) {
    this.requestContextProvider = requestContextProvider;
    this.identityProvider = identityProvider;
    this.customerService = customerService;
    this.userService = userService;
    this.domainVsCustomer =
        new DistributedCache.Builder<Optional<Customer>>(
                CustomerService.CUSTOMER_BY_DOMAIN_CACHE, cacheManager)
            .scope(CacheScope.GLOBAL)
            .localCache(
                CacheBuilder.newBuilder()
                    .maximumSize(10_000)
                    .expireAfterWrite(300, TimeUnit.SECONDS))
            .loader(this::getByDomain)
            .build();
    this.activeUsersCache =
        new DistributedCache.Builder<Boolean>("ACTIVE_USERS", cacheManager)
            .scope(CacheScope.CUSTOMER)
            .localCache(CacheBuilder.newBuilder().expireAfterWrite(300, TimeUnit.SECONDS))
            .loader(
                userId ->
                    Context.require().asSystemCaller().get(() -> this.userService.isActive(userId)))
            .build();
  }

  /**
   * A pre-matching filter runs on the event loop, which forbids the blocking customer lookup
   * {@code customerIdOf} can do on a cold cache; running the body on the default worker executor
   * keeps that blocking work off the event loop regardless.
   */
  @ServerRequestFilter(preMatching = true, priority = Priorities.AUTHENTICATION)
  public Uni<Response> filter(final ContainerRequestContext requestContext) {
    return Uni.createFrom()
        .item(() -> filterBlocking(requestContext))
        .runSubscriptionOn(Infrastructure.getDefaultExecutor());
  }

  private Response filterBlocking(final ContainerRequestContext requestContext) {
    final String requestId = UUID.randomUUID().toString();
    final String path = path(requestContext);
    final String origin = requestContext.getHeaderString(ORIGIN_HEADER);
    final String customerId = customerIdOf(origin);
    final Cookie sessionCookie = requestContext.getCookies().get(SESSION_COOKIE);
    final Optional<UserSession> loggedIn =
        sessionCookie == null
            ? Optional.empty()
            : identityProvider.authenticate(sessionCookie.getValue());
    LOG.info(
        "Processing request with cookie {} for customer {} and origin {}",
        sessionCookie == null ? null : sessionCookie.getValue(),
        customerId,
        origin);
    final boolean isSystemRequest =
        Objects.equals(sessionCookie == null ? null : sessionCookie.getValue(), "systemSession");
    requestContextProvider.set(
        loggedIn
            .map(
                session ->
                    new Context(
                        requestId,
                        session.getCustomerId(),
                        UserCaller.of(Principal.ofUser(session.getUserId()))))
            .orElseGet(
                () -> {
                  if (isSystemRequest) {
                    return new Context(requestId, customerId, Caller.SYSTEM);
                  }
                  return new Context(requestId, customerId, Caller.ANONYMOUS);
                }));
    LOG.info(
        "[After] Processing request with cookie {} for customer {} and origin {}",
        sessionCookie == null ? null : sessionCookie.getValue(),
        customerId,
        origin);
    if (LOGOUT_PATH.equals(path)) {
      // Clearing the cookie needs no identity, so an expired or foreign session can still log out.
      return null;
    }
    if (loggedIn.isPresent()) {
      final UserSession session = loggedIn.get();
      final boolean active =
          Context.asSystemUser(session.getCustomerId())
              .get(() -> activeUsersCache.get(session.getUserId()));
      if (!active) {
        identityProvider.logout(sessionCookie.getValue());
        return Response.status(Response.Status.UNAUTHORIZED).build();
      }
      if (origin != null && !session.getCustomerId().equals(customerId)) {
        return Response.status(Response.Status.FORBIDDEN).build();
      }
      return null;
    }
    if (isSystemRequest) {
      return null;
    }
    if (customerId == null) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }
    if (!HttpMethod.OPTIONS.equals(requestContext.getMethod()) && !LOGIN_PATH.equals(path)) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    return null;
  }

  @ServerResponseFilter
  public void filter(final ContainerResponseContext responseContext) {
    final Context context = requestContextProvider.get();
    if (context != null) {
      responseContext.getHeaders().add(REQUEST_ID_HEADER, context.requestId());
    }
  }

  /** The customer whose domain {@code origin} is on, if any. */
  private String customerIdOf(final String origin) {
    final String domain = domainOf(origin);
    if (domain == null) {
      return null;
    }
    return domainVsCustomer.get(domain).map(Customer::getId).orElse(null);
  }

  /** Empty for a domain no customer has, so an unknown origin is cached too. */
  private Optional<Customer> getByDomain(final String domain) {
    return Optional.ofNullable(
        Context.asSystemCustomer().get(() -> customerService.getByDomain(domain)));
  }

  private static String path(final ContainerRequestContext requestContext) {
    return requestContext.getUriInfo().getPath().replaceFirst("^/", "");
  }

  private static String domainOf(final String origin) {
    if (StringUtils.isBlank(origin)) {
      return null;
    }
    try {
      final String host = new URI(origin).getHost();
      return host == null ? null : host.toLowerCase(Locale.ROOT);
    } catch (final URISyntaxException exception) {
      return null;
    }
  }
}
