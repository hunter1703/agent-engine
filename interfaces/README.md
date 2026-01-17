# Interfaces Module

This module groups interface implementations that expose the engine over different transports.

## Protocol
Interface implementations share the same logical protocol:
- Requests are expressed with the `AgentRequest` JSON shape (agent identity, optional session,
  and optional message content).
- Responses may be single payloads or streamed JSON events depending on transport.
- Engine lifecycle/tool events are exposed verbatim over both transports.

See each transport submodule README for details and examples.
