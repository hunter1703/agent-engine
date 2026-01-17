# Deployment

## Build REST Service
```bash
./gradlew :interfaces:rest:build
```

This produces the Quarkus runnable distribution in `interfaces/rest/build/quarkus-app/`.

## Systemd
1. Copy the Quarkus build output and plugins to `/opt/agent-engine`:
   ```bash
   sudo mkdir -p /opt/agent-engine
   sudo cp -R interfaces/rest/build/quarkus-app/* /opt/agent-engine/
   sudo cp -R plugins /opt/agent-engine/
   ```
2. Install the unit file:
   ```bash
   sudo cp deploy/agent-engine.service /etc/systemd/system/agent-engine.service
   sudo systemctl daemon-reload
   sudo systemctl enable --now agent-engine
   ```

## Runtime Settings
- `PLUGIN_DIR` (default: `./plugins`) to locate plugin JARs.
- `MONGODB_CONNECTION_STRING` is read from a JVM system property (set in `ExecStart`).
