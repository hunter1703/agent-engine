package com.agentengine.util.ms.client;

import com.agentengine.util.common.Defaults;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MicroServiceMethod {
  int streamingBatchSize() default Defaults.STREAMING_BATCH_SIZE;

  long streamingBatchFlushIntervalMs() default Defaults.STREAMING_BATCH_FLUSH_INTERVAL_MS;
}
