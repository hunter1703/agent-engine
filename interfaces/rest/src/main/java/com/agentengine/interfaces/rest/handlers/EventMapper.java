package com.agentengine.interfaces.rest.handlers;

import com.agui.core.event.BaseEvent;
import io.reactivex.rxjava3.core.Flowable;

public interface EventMapper<S, T> {

  Flowable<T> map(final S event);

  Flowable<T> onComplete();

  Flowable<T> onError(final Throwable throwable);
}
