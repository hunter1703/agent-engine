package com.agentengine.util.ms.client;

import io.grpc.ManagedChannel;

import java.util.concurrent.TimeUnit;

public record Channel(ManagedChannel channel) implements AutoCloseable {

    @Override
    public void close() throws InterruptedException {
        channel.shutdown();
        if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
            channel.shutdownNow();
        }
    }
}
