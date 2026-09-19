package com.agentengine.util.common;

import com.agentengine.util.context.Context;
import io.quarkus.arc.Unremovable;
import io.quarkus.runtime.StartupEvent;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Singleton;

/**
 * Runs every task RxJava schedules in the {@link Context} bound where it was scheduled. RxJava's
 * schedule handler is global to the JVM and holds one handler, so this replaces any other.
 */
@Singleton
@Unremovable
public class RxJavaPlugin {

  void onStart(@Observes final StartupEvent event) {
    RxJavaPlugins.setScheduleHandler(Context::bindCurrent);
  }
}
