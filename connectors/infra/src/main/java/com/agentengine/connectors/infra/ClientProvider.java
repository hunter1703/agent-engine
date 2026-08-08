package com.agentengine.connectors.infra;

public interface ClientProvider<Options, Client> {
    Client getClient(Options options);
}
