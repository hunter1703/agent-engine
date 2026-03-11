package com.agentengine.util.ms;

/** Resolves the gRPC endpoint for a given microservice class. */
public interface MicroServiceEndpointResolver {

  MicroServiceEndpoint resolve(Class<?> serviceClass);
}
