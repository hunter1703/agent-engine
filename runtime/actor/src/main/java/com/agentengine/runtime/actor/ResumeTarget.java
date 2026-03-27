package com.agentengine.runtime.actor;

import com.agentengine.util.pekko.PekkoSerializable;

/**
 * Declares where a ResumeRun command should be forwarded.
 * Self: this actor owns the confirmation.
 * Child: forward resume to the named child session.
 */
public sealed interface ResumeTarget extends PekkoSerializable
        permits ResumeTarget.Self, ResumeTarget.Child {

    record Self() implements ResumeTarget {}
    record Child(String childSessionId) implements ResumeTarget {}
}
