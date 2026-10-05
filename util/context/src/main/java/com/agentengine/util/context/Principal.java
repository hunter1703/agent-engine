package com.agentengine.util.context;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.*;

/**
 * A user acting in the context of a chain of assets, each acting in the context of the one before
 * it: {@code User/5} is user 5 acting directly, {@code User/5:Agent/A:AgentSession/S} user 5 acting
 * in session S, itself acting in agent A. The first segment is always the user; any segment's id
 * may be {@value Segment#ANY_ID}, so a principal named in a grant can stand for many — {@code
 * User/*:Agent/A} is agent A, whichever user it acts for.
 *
 * <p>A principal covers every principal it is a prefix of, with any of its ids widened to {@value
 * Segment#ANY_ID}, and a grant to it reaches every principal it covers. So what is granted to user
 * 5 reaches everything acting for user 5, and what is granted to agent A everything acting within
 * agent A.
 */
public record Principal(List<Segment> segments) {

  public static final String USER = "User";
  static final String SEGMENT_SEPARATOR = ":";

  public Principal {
    segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
    if (segments.isEmpty() || !USER.equals(segments.getFirst().assetClass())) {
      throw new IllegalArgumentException("A principal starts with its user: " + segments);
    }
    for (final Segment segment : segments.subList(1, segments.size())) {
      if (USER.equals(segment.assetClass())) {
        throw new IllegalArgumentException("A principal has one user: " + segments);
      }
    }
  }

  public static Principal ofUser(final String userId) {
    return new Principal(List.of(new Segment(USER, userId)));
  }

  public static Principal ofAnyUser() {
    return new Principal(List.of(Segment.anyOf(USER)));
  }

  @JsonCreator
  public static Principal parse(final String value) {
    return new Principal(
        Arrays.stream(Objects.requireNonNull(value, "value").strip().split(SEGMENT_SEPARATOR, -1))
            .map(
                str -> {
                  if (str.isEmpty()) {
                    throw new IllegalArgumentException(
                        "Empty segment in principal: '" + value + "'");
                  }
                  return Segment.parse(str);
                })
            .toList());
  }

  /** The user acting; null for a principal that stands for any user. */
  public String userId() {
    final Segment user = segments.getFirst();
    return user.isAnyId() ? null : user.assetId();
  }

  public boolean isUserActingDirectly() {
    return segments.size() == 1;
  }

  /** The id of the asset of {@code assetClass} in this principal, if present. */
  public Optional<String> assetId(final String assetClass) {
    for (final Segment segment : segments) {
      if (segment.assetClass().equals(assetClass)) {
        return Optional.of(segment.assetId());
      }
    }
    return Optional.empty();
  }

  /** This principal, acting in the context of asset {@code assetClass}/{@code assetId}. */
  public Principal within(final String assetClass, final String assetId) {
    final List<Segment> extended = new ArrayList<>(segments);
    extended.add(new Segment(assetClass, assetId));
    return new Principal(extended);
  }

  /** This principal, for user {@code userId} instead of its own. */
  public Principal forUser(final String userId) {
    return withUser(new Segment(USER, userId));
  }

  /** This principal, for any user instead of its own. */
  public Principal forAnyUser() {
    return withUser(Segment.anyOf(USER));
  }

  /** Whether this principal acts in the asset {@code assetClass}/{@code assetId}. */
  public boolean actsIn(final String assetClass, final String assetId) {
    return segments.subList(1, segments.size()).contains(new Segment(assetClass, assetId));
  }

  /**
   * This principal's user acting in its asset of {@code assetClass}: the principal cut off after
   * that segment, e.g. {@code User/5:Agent/A} for {@code User/5:Agent/A:AgentSession/S} acting in
   * its agent.
   *
   * @throws IllegalArgumentException when it acts in no asset of {@code assetClass}
   */
  public Principal actingIn(final String assetClass) {
    for (int index = 1; index < segments.size(); index++) {
      if (segments.get(index).assetClass().equals(assetClass)) {
        return new Principal(segments.subList(0, index + 1));
      }
    }
    throw new IllegalArgumentException(this + " acts in no " + assetClass);
  }

  /**
   * Every principal that covers this one, so a grant to it reaches this one: each of its prefixes,
   * with each segment's id kept or widened to {@value Segment#ANY_ID} — this one included.
   */
  public Set<Principal> coveringPrincipals() {
    final Set<Principal> coveringPrincipals = new LinkedHashSet<>();
    List<List<Segment>> prefixes = List.of(List.of());
    for (final Segment segment : segments) {
      final List<List<Segment>> longer = new ArrayList<>();
      for (final List<Segment> prefix : prefixes) {
        longer.add(append(prefix, segment));
        if (!segment.isAnyId()) {
          longer.add(append(prefix, segment.withAnyId()));
        }
      }
      prefixes = longer;
      for (final List<Segment> prefix : prefixes) {
        coveringPrincipals.add(new Principal(prefix));
      }
    }
    return coveringPrincipals;
  }

  @JsonValue
  @Override
  public String toString() {
    return String.join(SEGMENT_SEPARATOR, segments.stream().map(Segment::toString).toList());
  }

  private Principal withUser(final Segment user) {
    final List<Segment> replaced = new ArrayList<>(segments);
    replaced.set(0, user);
    return new Principal(replaced);
  }

  private static List<Segment> append(final List<Segment> prefix, final Segment segment) {
    final List<Segment> appended = new ArrayList<>(prefix);
    appended.add(segment);
    return appended;
  }

  /**
   * One step of a {@link Principal}: an asset, written {@code Class/id}, or any asset of its class,
   * written {@code Class/*}.
   */
  private record Segment(String assetClass, String assetId) {

    public static final String ANY_ID = "*";
    static final String ID_SEPARATOR = "/";

    public Segment {
      if (!isValidPart(assetClass) || ANY_ID.equals(assetClass) || !isValidPart(assetId)) {
        throw new IllegalArgumentException(
            "Invalid segment '" + assetClass + ID_SEPARATOR + assetId + "'");
      }
    }

    /** Any asset of {@code assetClass}. */
    public static Segment anyOf(final String assetClass) {
      return new Segment(assetClass, ANY_ID);
    }

    public boolean isAnyId() {
      return ANY_ID.equals(assetId);
    }

    /** This segment widened to any asset of its class. */
    public Segment withAnyId() {
      return anyOf(assetClass);
    }

    static Segment parse(final String value) {
      final String[] parts = value.split(ID_SEPARATOR, -1);
      if (parts.length != 2) {
        throw new IllegalArgumentException("Invalid segment '" + value + "'");
      }
      return new Segment(parts[0], parts[1]);
    }

    @Override
    public String toString() {
      return assetClass + ID_SEPARATOR + assetId;
    }

    /**
     * Whether {@code part} can stand in a segment: not blank, and free of every separator a
     * principal, a caller or a grant is written with.
     */
    private static boolean isValidPart(final String part) {
      return part != null
          && !part.isBlank()
          && !part.contains(ID_SEPARATOR)
          && !part.contains(SEGMENT_SEPARATOR)
          && !part.contains(UserCaller.PRINCIPAL_SEPARATOR)
          && !part.contains("#");
    }
  }
}
