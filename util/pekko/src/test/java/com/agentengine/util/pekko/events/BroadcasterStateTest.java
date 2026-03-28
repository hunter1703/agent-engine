package com.agentengine.util.pekko.events;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BroadcasterStateTest {

  @Test
  void shouldAddSubscriptionToEmptyState() {
    final BroadcasterState state = BroadcasterState.empty();

    state.withSubscription("sub-1");

    assertThat(state.subscriptions()).containsExactly("sub-1");
  }

  @Test
  void shouldRemoveSubscriptionFromState() {
    final BroadcasterState state = BroadcasterState.empty().withSubscription("sub-1");

    state.withoutSubscription("sub-1");

    assertThat(state.subscriptions()).isEmpty();
  }
}
