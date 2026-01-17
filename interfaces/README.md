# Interfaces Module

This module groups interface implementations that expose the engine over different transports.

## Submodules
- `cli`: stdio CLI interface
- `rest`: REST + SSE interface

## Commands
Run CLI:
```bash
./gradlew :interfaces:cli:run --args="server"
```

Run REST in dev mode:
```bash
./gradlew :interfaces:rest:quarkusDev
```
