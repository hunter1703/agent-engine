package com.agentengine.interfaces.rest.handlers;

import com.agui.core.event.BaseEvent;
import com.google.adk.events.Event;
import io.reactivex.rxjava3.core.Flowable;

public interface EventMapper<S, T> {

    Flowable<T> map(final S event);
}
