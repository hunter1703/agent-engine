package com.agentengine.util.common;

import java.io.Writer;

public class StringBuilderWriter extends Writer {

  private final StringBuilder builder;

  public StringBuilderWriter() {
    this.builder = new StringBuilder();
  }

  @Override
  public void write(final char[] cbuf, final int off, final int len) {
    builder.append(cbuf, off, len);
  }

  @Override
  public void write(final String str) {
    builder.append(str);
  }

  @Override
  public void write(final int c) {
    builder.append((char) c);
  }

  @Override
  public Writer append(final CharSequence csq) {
    builder.append(csq);
    return this;
  }

  @Override
  public Writer append(final CharSequence csq, final int start, final int end) {
    builder.append(csq, start, end);
    return this;
  }

  @Override
  public Writer append(final char c) {
    builder.append(c);
    return this;
  }

  @Override
  public void flush() {
    // No-op
  }

  @Override
  public void close() {
    // No-op
  }

  @Override
  public String toString() {
    return builder.toString();
  }

  public StringBuilder getBuilder() {
    return builder;
  }
}
