# AG UI Protocol Server

The AG UI Protocol Server provides a WebSocket-based interface for the Agent Engine UI to communicate with the backend services.

## Features

- WebSocket-based real-time communication
- Support for AG UI protocol messages
- Agent lifecycle management (create, start, stop, delete)
- Real-time agent status updates
- Message passing between UI and agents

## Protocol Messages

The server supports the following message types:

- `initialize` - Initialize the AG server
- `get_agents` - Retrieve list of available agents
- `create_agent` - Create a new agent
- `delete_agent` - Delete an existing agent
- `start_agent` - Start an agent
- `stop_agent` - Stop an agent
- `get_agent_status` - Get status of an agent
- `send_message` - Send a message to an agent
- `ping` - Health check message

## Configuration

The server can be configured using the following properties:

- `agui.protocol.enabled` - Enable/disable the protocol server (default: true)
- `agui.protocol.port` - Port for the WebSocket endpoint (default: 8082)
- `agui.protocol.path` - Path for the WebSocket endpoint (default: /agui/protocol)
- `agui.protocol.max.connections` - Maximum concurrent connections (default: 100)

## Building and Running

To build the AG UI protocol server:

```bash
./gradlew :interfaces:ag-ui:build
```

To run in development mode:

```bash
./gradlew :interfaces:ag-ui:quarkusDev
```

## WebSocket Endpoint

The WebSocket endpoint is available at:
`ws://localhost:8082/agui/protocol`

## Health Checks

Health status is available at:
`GET http://localhost:8082/health`
