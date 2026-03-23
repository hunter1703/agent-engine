package com.agentengine.core.agui;

import io.reactivex.rxjava3.core.Flowable;

public interface EventMapper<S, T> {

  default Flowable<T> map(final Flowable<S> source) {
    return source.concatMap(event -> {
      try {
        return map(event);
      } catch (Exception e) {
        return Flowable.error(e);
      }
    }).concatWith(Flowable.defer(this::onComplete)).onErrorResumeNext(this::onError);
  }

  Flowable<T> map(final S event);

  Flowable<T> onComplete();

  Flowable<T> onError(final Throwable throwable);
}
