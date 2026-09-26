package com.agentengine.util.common;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Accumulates items one at a time into a buffer, flushing whatever's buffered (via the {@code
 * onFlush} consumer given at construction) as soon as {@link #shouldFlush} says the buffer — which
 * already includes the item just added — is ready to go out. Not thread-safe: a caller must not
 * call {@link #add} or {@link #flush} concurrently with themselves or each other.
 *
 * @param <T> the type of item being batched
 */
public abstract class Batcher<T> {

  protected final List<T> buffer = new ArrayList<>();
  protected final Consumer<List<T>> onFlush;

  protected Batcher(final Consumer<List<T>> onFlush) {
    this.onFlush = onFlush;
  }

  public boolean add(final T item) {
    buffer.add(item);
    final boolean needsFlush = shouldFlush();
    if (needsFlush) {
      flush();
    }
    return needsFlush;
  }

  /**
   * Flushes whatever's currently buffered, if anything, to {@code onFlush} and clears the buffer.
   * Lets a caller force out what's left once no further item is coming (e.g. at the end of a
   * stream), and is what {@link #add} calls internally when {@link #shouldFlush} says to.
   */
  public void flush() {
    if (!buffer.isEmpty()) {
      final List<T> batch = List.copyOf(buffer);
      buffer.clear();
      onFlush.accept(batch);
    }
  }

  protected abstract boolean shouldFlush();
}
