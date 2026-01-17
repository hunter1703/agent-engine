# Interfaces Module

This module groups interface implementations that expose the engine over different transports.
The current implementation is the REST transport, with shared utilities in `interfaces:common`.

## Protocol
Interface implementations share the same logical protocol:
- Requests are expressed with the `AgentRequest` JSON shape (agent identity, optional session,
  and optional message content).
- Responses may be single payloads or streamed JSON events depending on transport.
- Engine lifecycle/tool events are exposed over the REST transport.

See each transport submodule README for details and examples.
