package com.agentengine.util.ms;

/** Host and port for a gRPC microservice endpoint. */
public record MicroServiceEndpoint(String host, int port) {}
