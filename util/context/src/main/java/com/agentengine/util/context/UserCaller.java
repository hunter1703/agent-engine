package com.agentengine.util.context;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A user acting in one or more principals, all for that user. {@code principals} holds every
 * principal the user acts in, the primary one included; {@code primaryPrincipal} is the one the
 * work runs in and is attributed to — the others are further contexts it also acts in, such as an
 * agent transferred to within a session.
 *
 * <p>Written as its primary principal followed by the others, each after {@value
 * #PRINCIPAL_SEPARATOR}, in sorted order.
 */
public record UserCaller(Principal primaryPrincipal, Set<Principal> principals) implements Caller {

  static final String PRINCIPAL_SEPARATOR = "+";

  public UserCaller {
    Objects.requireNonNull(primaryPrincipal, "primaryPrincipal");
    final String userId = primaryPrincipal.userId();
    if (userId == null) {
      throw new IllegalArgumentException("A caller acts for one user: " + primaryPrincipal);
    }
    final Set<Principal> all = new LinkedHashSet<>();
    all.add(primaryPrincipal);
    if (principals != null) {
      all.addAll(principals);
    }
    for (final Principal principal : all) {
      if (!userId.equals(principal.userId())) {
        throw new IllegalArgumentException(principal + " is not for user " + userId);
      }
    }
    principals = Collections.unmodifiableSet(all);
  }

  public static UserCaller of(final Principal principal) {
    return new UserCaller(principal, Set.of(principal));
  }

  static UserCaller parse(final String value) {
    final String[] parts = value.split("\\" + PRINCIPAL_SEPARATOR, -1);
    return new UserCaller(
        Principal.parse(parts[0]),
        new LinkedHashSet<>(Arrays.stream(parts).map(Principal::parse).toList()));
  }

  public String userId() {
    return primaryPrincipal.userId();
  }

  /** This caller, also acting in {@code principal} for its user. */
  public UserCaller alsoIn(final Principal principal) {
    final Set<Principal> extended = new LinkedHashSet<>(principals);
    extended.add(principal.forUser(userId()));
    return new UserCaller(primaryPrincipal, extended);
  }

  /** This caller with {@code principal} as primary, added if it is not one of its principals. */
  public UserCaller withPrimary(final Principal principal) {
    final Set<Principal> extended = new LinkedHashSet<>(principals);
    extended.add(principal);
    return new UserCaller(principal, extended);
  }

  @JsonValue
  @Override
  public String toString() {
    return String.join(
        PRINCIPAL_SEPARATOR,
        Stream.concat(
                Stream.of(primaryPrincipal.toString()),
                principals.stream()
                    .filter(principal -> !principal.equals(primaryPrincipal))
                    .map(Principal::toString)
                    .sorted())
            .toList());
  }
}
