package com.agentengine.util.ms.client;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MicroServiceMethod {
  int DEFAULT_STREAMING_BATCH_SIZE = 100;
  long DEFAULT_STREAMING_BATCH_FLUSH_INTERVAL_MS = 50;

  int streamingBatchSize() default DEFAULT_STREAMING_BATCH_SIZE;

  long streamingBatchFlushIntervalMs() default DEFAULT_STREAMING_BATCH_FLUSH_INTERVAL_MS;
}
