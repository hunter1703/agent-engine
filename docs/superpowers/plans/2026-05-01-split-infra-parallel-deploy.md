# Split Infra + Maximally Parallel Deployment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the monolithic `k8s/infra/` Helm chart with four standalone charts (mongodb, postgres, localstack, qdrant), split postgres schema init out of `seed-infra-configs.sh`, and wire everything into a maximally parallel deployment DAG in `deploy.sh`.

**Architecture:** Each infra workload becomes its own Helm chart so it can be tracked individually in the flag-file job framework. `deploy.sh` declares both `INFRA_SERVICES` and `APP_SERVICES` as data tables; two parallel loops launch all jobs at T=0. Seeds fire as soon as their single dependency is ready.

**Tech Stack:** POSIX sh, Helm 3, kubectl, MongoDB 7, PostgreSQL 15, LocalStack 3, Qdrant v1.13.6.

---

## File Map

**Created:**
- `k8s/mongodb/Chart.yaml`, `values.yaml`, `templates/_helpers.tpl`, `templates/statefulset.yaml`, `templates/service.yaml`, `templates/headless-service.yaml`, `templates/auth-secret.yaml`, `templates/connection-secret.yaml`
- `k8s/postgres/Chart.yaml`, `values.yaml`, `templates/_helpers.tpl`, `templates/statefulset.yaml`, `templates/service.yaml`, `templates/headless-service.yaml`, `templates/auth-secret.yaml`
- `k8s/localstack/Chart.yaml`, `values.yaml`, `templates/_helpers.tpl`, `templates/deployment.yaml`, `templates/service.yaml`
- `k8s/qdrant/Chart.yaml`, `values.yaml`, `templates/_helpers.tpl`, `templates/deployment.yaml`, `templates/service.yaml`
- `k8s/environments/local/mongodb.yaml`
- `k8s/environments/local/postgres.yaml`
- `k8s/scripts/init-postgres-schema.sh`

**Modified:**
- `k8s/scripts/lib.sh` — replace `infra` with 4 new charts in `ALL_CHARTS` and `chart_release_name()`
- `k8s/scripts/apply-charts.sh` — remove infra `--atomic` block; add rollout-wait cases for 4 new infra charts
- `k8s/scripts/seed-infra-configs.sh` — remove psql block; update secret name defaults
- `k8s/scripts/deploy.sh` — add `INFRA_SERVICES`, `job_infra_svc`, `job_init_postgres`; update `job_seed_infra`, `APP_SERVICES`, `CATALOG_DEPS`; update `usage()`
- `k8s/scripts/cleanup.sh` — replace `infra` uninstall with 4 individual uninstalls; fix localstack cleanup guard

**Deleted:**
- `k8s/infra/` (entire directory)
- `k8s/scripts/deploy-infra.sh`
- `k8s/environments/local/infra.yaml`

---

## Task 1: Create `k8s/mongodb/` chart

**Files:**
- Create: `k8s/mongodb/Chart.yaml`
- Create: `k8s/mongodb/values.yaml`
- Create: `k8s/mongodb/templates/_helpers.tpl`
- Create: `k8s/mongodb/templates/auth-secret.yaml`
- Create: `k8s/mongodb/templates/connection-secret.yaml`
- Create: `k8s/mongodb/templates/statefulset.yaml`
- Create: `k8s/mongodb/templates/service.yaml`
- Create: `k8s/mongodb/templates/headless-service.yaml`

- [ ] **Step 1: Create chart scaffold**

```bash
mkdir -p k8s/mongodb/templates
```

- [ ] **Step 2: Write `k8s/mongodb/Chart.yaml`**

```yaml
apiVersion: v2
name: agent-engine-mongodb
description: MongoDB for Agent Engine
type: application
version: 0.1.0
appVersion: "7.0"
```

- [ ] **Step 3: Write `k8s/mongodb/values.yaml`**

```yaml
namespace: agent-engine

image:
  repository: mongo
  tag: "7.0"
  digest: ""
  pullPolicy: IfNotPresent

serviceName: mongodb
port: 27017
connectionString: ""

auth:
  enabled: true
  username: agentengine
  password: agentengine
  secretName: "mongodb-auth"
  existingSecret: ""
  usernameKey: username
  passwordKey: password

connection:
  secretName: "mongodb-connection"

requireExistingSecret: false

persistence:
  enabled: true
  size: 10Gi
  storageClassName: ""

service:
  annotations: {}

podAnnotations: {}
podLabels: {}

podSecurityContext:
  seccompProfile:
    type: RuntimeDefault

containerSecurityContext:
  allowPrivilegeEscalation: false
  readOnlyRootFilesystem: false

resources:
  requests:
    cpu: 500m
    memory: 512Mi
  limits:
    cpu: 500m
    memory: 1Gi

probes:
  startup:
    periodSeconds: 5
    failureThreshold: 30
    timeoutSeconds: 5
  readiness:
    periodSeconds: 10
    failureThreshold: 6
    timeoutSeconds: 5
  liveness:
    periodSeconds: 20
    failureThreshold: 6
    timeoutSeconds: 5

imagePullSecrets: []
commonLabels: {}
nodeSelector: {}
tolerations: []
affinity: {}
```

- [ ] **Step 4: Write `k8s/mongodb/templates/_helpers.tpl`**

```
{{- define "agent-engine-mongodb.namespace" -}}
{{- default .Release.Namespace .Values.namespace -}}
{{- end -}}

{{- define "agent-engine-mongodb.labels" -}}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: agent-engine
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | quote }}
{{- with .Values.commonLabels }}
{{ toYaml . }}
{{- end }}
{{- end -}}

{{- define "agent-engine-mongodb.authSecretName" -}}
{{- if .Values.auth.existingSecret -}}
{{- .Values.auth.existingSecret -}}
{{- else if .Values.requireExistingSecret -}}
{{- required "auth.existingSecret must be set when requireExistingSecret is true" .Values.auth.existingSecret -}}
{{- else -}}
{{- .Values.auth.secretName | default "mongodb-auth" -}}
{{- end -}}
{{- end -}}

{{- define "agent-engine-mongodb.connectionSecretName" -}}
{{- .Values.connection.secretName | default "mongodb-connection" -}}
{{- end -}}

{{- define "agent-engine-mongodb.connectionString" -}}
{{- if .Values.connectionString -}}
{{- .Values.connectionString -}}
{{- else if .Values.auth.enabled -}}
{{- printf "mongodb://%s:%s@%s:%v/?authSource=admin" .Values.auth.username .Values.auth.password .Values.serviceName .Values.port -}}
{{- else -}}
{{- printf "mongodb://%s:%v" .Values.serviceName .Values.port -}}
{{- end -}}
{{- end -}}

{{- define "agent-engine-mongodb.image" -}}
{{- if .Values.image.digest -}}
{{ printf "%s@%s" .Values.image.repository .Values.image.digest }}
{{- else -}}
{{ printf "%s:%s" .Values.image.repository .Values.image.tag }}
{{- end -}}
{{- end -}}
```

- [ ] **Step 5: Write `k8s/mongodb/templates/auth-secret.yaml`**

```yaml
{{- if and .Values.auth.enabled (not .Values.auth.existingSecret) }}
apiVersion: v1
kind: Secret
metadata:
  name: {{ include "agent-engine-mongodb.authSecretName" . }}
  namespace: {{ include "agent-engine-mongodb.namespace" . }}
  labels:
    app.kubernetes.io/name: mongodb
    {{- include "agent-engine-mongodb.labels" . | nindent 4 }}
type: Opaque
stringData:
  {{ .Values.auth.usernameKey }}: {{ .Values.auth.username | quote }}
  {{ .Values.auth.passwordKey }}: {{ .Values.auth.password | quote }}
{{- end }}
```

- [ ] **Step 6: Write `k8s/mongodb/templates/connection-secret.yaml`**

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: {{ include "agent-engine-mongodb.connectionSecretName" . }}
  namespace: {{ include "agent-engine-mongodb.namespace" . }}
  labels:
    app.kubernetes.io/name: mongodb
    {{- include "agent-engine-mongodb.labels" . | nindent 4 }}
type: Opaque
stringData:
  connection-string: {{ include "agent-engine-mongodb.connectionString" . | quote }}
```

- [ ] **Step 7: Write `k8s/mongodb/templates/statefulset.yaml`**

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: mongodb
  namespace: {{ include "agent-engine-mongodb.namespace" . }}
  labels:
    app.kubernetes.io/name: mongodb
    {{- include "agent-engine-mongodb.labels" . | nindent 4 }}
spec:
  serviceName: mongodb-headless
  replicas: 1
  selector:
    matchLabels:
      app.kubernetes.io/name: mongodb
      app.kubernetes.io/instance: {{ .Release.Name }}
  template:
    metadata:
      labels:
        app.kubernetes.io/name: mongodb
        app.kubernetes.io/instance: {{ .Release.Name }}
        {{- with .Values.podLabels }}
        {{- toYaml . | nindent 8 }}
        {{- end }}
      {{- with .Values.podAnnotations }}
      annotations:
        {{- toYaml . | nindent 8 }}
      {{- end }}
    spec:
      {{- with .Values.imagePullSecrets }}
      imagePullSecrets:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      securityContext:
        {{- toYaml .Values.podSecurityContext | nindent 8 }}
      {{- with .Values.nodeSelector }}
      nodeSelector:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      {{- with .Values.affinity }}
      affinity:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      {{- with .Values.tolerations }}
      tolerations:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      terminationGracePeriodSeconds: 30
      containers:
        - name: mongodb
          image: {{ include "agent-engine-mongodb.image" . | quote }}
          imagePullPolicy: {{ .Values.image.pullPolicy }}
          securityContext:
            {{- toYaml .Values.containerSecurityContext | nindent 12 }}
          {{- if .Values.auth.enabled }}
          env:
            - name: MONGO_INITDB_ROOT_USERNAME
              valueFrom:
                secretKeyRef:
                  name: {{ include "agent-engine-mongodb.authSecretName" . }}
                  key: {{ .Values.auth.usernameKey }}
            - name: MONGO_INITDB_ROOT_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: {{ include "agent-engine-mongodb.authSecretName" . }}
                  key: {{ .Values.auth.passwordKey }}
          {{- end }}
          ports:
            - name: mongo
              containerPort: {{ .Values.port }}
          startupProbe:
            tcpSocket:
              port: mongo
            periodSeconds: {{ .Values.probes.startup.periodSeconds }}
            failureThreshold: {{ .Values.probes.startup.failureThreshold }}
            timeoutSeconds: {{ .Values.probes.startup.timeoutSeconds }}
          readinessProbe:
            exec:
              command:
                {{- if .Values.auth.enabled }}
                - sh
                - -c
                - 'mongosh --quiet --eval "db.adminCommand(\"ping\").ok" --username "$MONGO_INITDB_ROOT_USERNAME" --password "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin'
                {{- else }}
                - mongosh
                - --quiet
                - --eval
                - "db.adminCommand('ping').ok"
                {{- end }}
            periodSeconds: {{ .Values.probes.readiness.periodSeconds }}
            failureThreshold: {{ .Values.probes.readiness.failureThreshold }}
            timeoutSeconds: {{ .Values.probes.readiness.timeoutSeconds }}
          livenessProbe:
            tcpSocket:
              port: mongo
            periodSeconds: {{ .Values.probes.liveness.periodSeconds }}
            failureThreshold: {{ .Values.probes.liveness.failureThreshold }}
            timeoutSeconds: {{ .Values.probes.liveness.timeoutSeconds }}
          resources:
            {{- toYaml .Values.resources | nindent 12 }}
          volumeMounts:
            - name: data
              mountPath: /data/db
      {{- if not .Values.persistence.enabled }}
      volumes:
        - name: data
          emptyDir: {}
      {{- end }}
  {{- if .Values.persistence.enabled }}
  volumeClaimTemplates:
    - metadata:
        name: data
      spec:
        accessModes:
          - ReadWriteOnce
        {{- if .Values.persistence.storageClassName }}
        storageClassName: {{ .Values.persistence.storageClassName | quote }}
        {{- end }}
        resources:
          requests:
            storage: {{ .Values.persistence.size }}
  {{- end }}
```

- [ ] **Step 8: Write `k8s/mongodb/templates/service.yaml`**

```yaml
apiVersion: v1
kind: Service
metadata:
  name: {{ .Values.serviceName }}
  namespace: {{ include "agent-engine-mongodb.namespace" . }}
  labels:
    app.kubernetes.io/name: mongodb
    {{- include "agent-engine-mongodb.labels" . | nindent 4 }}
  {{- with .Values.service.annotations }}
  annotations:
    {{- toYaml . | nindent 4 }}
  {{- end }}
spec:
  selector:
    app.kubernetes.io/name: mongodb
    app.kubernetes.io/instance: {{ .Release.Name }}
  ports:
    - name: mongo
      port: {{ .Values.port }}
      targetPort: mongo
```

- [ ] **Step 9: Write `k8s/mongodb/templates/headless-service.yaml`**

```yaml
# Headless service required by the MongoDB StatefulSet for pod DNS identity.
# Application pods connect to MongoDB via the regular ClusterIP service (mongodb).
apiVersion: v1
kind: Service
metadata:
  name: mongodb-headless
  namespace: {{ include "agent-engine-mongodb.namespace" . }}
  labels:
    app.kubernetes.io/name: mongodb
    {{- include "agent-engine-mongodb.labels" . | nindent 4 }}
spec:
  clusterIP: None
  selector:
    app.kubernetes.io/name: mongodb
    app.kubernetes.io/instance: {{ .Release.Name }}
  ports:
    - name: mongo
      port: {{ .Values.port }}
      targetPort: mongo
```

- [ ] **Step 10: Lint the chart**

```bash
helm lint k8s/mongodb --set namespace=agent-engine
```

Expected: `1 chart(s) linted, 0 chart(s) failed`

- [ ] **Step 11: Commit**

```bash
git add k8s/mongodb/
git commit -m "feat(k8s): add standalone mongodb Helm chart"
```

---

## Task 2: Create `k8s/postgres/` chart

**Files:**
- Create: `k8s/postgres/Chart.yaml`
- Create: `k8s/postgres/values.yaml`
- Create: `k8s/postgres/templates/_helpers.tpl`
- Create: `k8s/postgres/templates/auth-secret.yaml`
- Create: `k8s/postgres/templates/statefulset.yaml`
- Create: `k8s/postgres/templates/service.yaml`
- Create: `k8s/postgres/templates/headless-service.yaml`

- [ ] **Step 1: Create chart scaffold**

```bash
mkdir -p k8s/postgres/templates
```

- [ ] **Step 2: Write `k8s/postgres/Chart.yaml`**

```yaml
apiVersion: v2
name: agent-engine-postgres
description: PostgreSQL for Agent Engine
type: application
version: 0.1.0
appVersion: "15"
```

- [ ] **Step 3: Write `k8s/postgres/values.yaml`**

```yaml
namespace: agent-engine

image:
  repository: postgres
  tag: "15"
  digest: ""
  pullPolicy: IfNotPresent

serviceName: postgres
port: 5432
database: agent_engine_events

auth:
  username: agentengine
  password: agentengine
  secretName: "postgres-auth"
  existingSecret: ""
  usernameKey: username
  passwordKey: password

requireExistingSecret: false

persistence:
  enabled: true
  size: 10Gi
  storageClassName: ""

service:
  annotations: {}

podAnnotations: {}
podLabels: {}

podSecurityContext:
  seccompProfile:
    type: RuntimeDefault

containerSecurityContext:
  allowPrivilegeEscalation: false
  readOnlyRootFilesystem: false

resources:
  requests:
    cpu: 500m
    memory: 512Mi
  limits:
    cpu: 500m
    memory: 1Gi

probes:
  startup:
    periodSeconds: 5
    failureThreshold: 30
    timeoutSeconds: 5
  readiness:
    periodSeconds: 10
    failureThreshold: 6
    timeoutSeconds: 5
  liveness:
    periodSeconds: 20
    failureThreshold: 6
    timeoutSeconds: 5

imagePullSecrets: []
commonLabels: {}
nodeSelector: {}
tolerations: []
affinity: {}
```

- [ ] **Step 4: Write `k8s/postgres/templates/_helpers.tpl`**

```
{{- define "agent-engine-postgres.namespace" -}}
{{- default .Release.Namespace .Values.namespace -}}
{{- end -}}

{{- define "agent-engine-postgres.labels" -}}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: agent-engine
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | quote }}
{{- with .Values.commonLabels }}
{{ toYaml . }}
{{- end }}
{{- end -}}

{{- define "agent-engine-postgres.authSecretName" -}}
{{- if .Values.auth.existingSecret -}}
{{- .Values.auth.existingSecret -}}
{{- else if .Values.requireExistingSecret -}}
{{- required "auth.existingSecret must be set when requireExistingSecret is true" .Values.auth.existingSecret -}}
{{- else -}}
{{- .Values.auth.secretName | default "postgres-auth" -}}
{{- end -}}
{{- end -}}

{{- define "agent-engine-postgres.image" -}}
{{- if .Values.image.digest -}}
{{ printf "%s@%s" .Values.image.repository .Values.image.digest }}
{{- else -}}
{{ printf "%s:%s" .Values.image.repository .Values.image.tag }}
{{- end -}}
{{- end -}}
```

- [ ] **Step 5: Write `k8s/postgres/templates/auth-secret.yaml`**

```yaml
{{- if not .Values.auth.existingSecret }}
apiVersion: v1
kind: Secret
metadata:
  name: {{ include "agent-engine-postgres.authSecretName" . }}
  namespace: {{ include "agent-engine-postgres.namespace" . }}
  labels:
    app.kubernetes.io/name: postgres
    {{- include "agent-engine-postgres.labels" . | nindent 4 }}
type: Opaque
stringData:
  {{ .Values.auth.usernameKey }}: {{ .Values.auth.username | quote }}
  {{ .Values.auth.passwordKey }}: {{ .Values.auth.password | quote }}
{{- end }}
```

- [ ] **Step 6: Write `k8s/postgres/templates/statefulset.yaml`**

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: postgres
  namespace: {{ include "agent-engine-postgres.namespace" . }}
  labels:
    app.kubernetes.io/name: postgres
    {{- include "agent-engine-postgres.labels" . | nindent 4 }}
spec:
  serviceName: postgres-headless
  replicas: 1
  selector:
    matchLabels:
      app.kubernetes.io/name: postgres
      app.kubernetes.io/instance: {{ .Release.Name }}
  template:
    metadata:
      labels:
        app.kubernetes.io/name: postgres
        app.kubernetes.io/instance: {{ .Release.Name }}
        {{- with .Values.podLabels }}
        {{- toYaml . | nindent 8 }}
        {{- end }}
      {{- with .Values.podAnnotations }}
      annotations:
        {{- toYaml . | nindent 8 }}
      {{- end }}
    spec:
      {{- with .Values.imagePullSecrets }}
      imagePullSecrets:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      securityContext:
        {{- toYaml .Values.podSecurityContext | nindent 8 }}
      {{- with .Values.nodeSelector }}
      nodeSelector:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      {{- with .Values.affinity }}
      affinity:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      {{- with .Values.tolerations }}
      tolerations:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      terminationGracePeriodSeconds: 30
      containers:
        - name: postgres
          image: {{ include "agent-engine-postgres.image" . | quote }}
          imagePullPolicy: {{ .Values.image.pullPolicy }}
          securityContext:
            {{- toYaml .Values.containerSecurityContext | nindent 12 }}
          env:
            - name: POSTGRES_DB
              value: {{ .Values.database | quote }}
            - name: POSTGRES_USER
              valueFrom:
                secretKeyRef:
                  name: {{ include "agent-engine-postgres.authSecretName" . }}
                  key: {{ .Values.auth.usernameKey }}
            - name: POSTGRES_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: {{ include "agent-engine-postgres.authSecretName" . }}
                  key: {{ .Values.auth.passwordKey }}
          ports:
            - name: postgres
              containerPort: {{ .Values.port }}
          startupProbe:
            exec:
              command: ["pg_isready"]
            periodSeconds: {{ .Values.probes.startup.periodSeconds }}
            failureThreshold: {{ .Values.probes.startup.failureThreshold }}
            timeoutSeconds: {{ .Values.probes.startup.timeoutSeconds }}
          readinessProbe:
            exec:
              command: ["pg_isready"]
            periodSeconds: {{ .Values.probes.readiness.periodSeconds }}
            failureThreshold: {{ .Values.probes.readiness.failureThreshold }}
            timeoutSeconds: {{ .Values.probes.readiness.timeoutSeconds }}
          livenessProbe:
            tcpSocket:
              port: postgres
            periodSeconds: {{ .Values.probes.liveness.periodSeconds }}
            failureThreshold: {{ .Values.probes.liveness.failureThreshold }}
            timeoutSeconds: {{ .Values.probes.liveness.timeoutSeconds }}
          resources:
            {{- toYaml .Values.resources | nindent 12 }}
          volumeMounts:
            - name: data
              mountPath: /var/lib/postgresql/data
              subPath: pgdata
      {{- if not .Values.persistence.enabled }}
      volumes:
        - name: data
          emptyDir: {}
      {{- end }}
  {{- if .Values.persistence.enabled }}
  volumeClaimTemplates:
    - metadata:
        name: data
      spec:
        accessModes:
          - ReadWriteOnce
        {{- if .Values.persistence.storageClassName }}
        storageClassName: {{ .Values.persistence.storageClassName | quote }}
        {{- end }}
        resources:
          requests:
            storage: {{ .Values.persistence.size }}
  {{- end }}
```

- [ ] **Step 7: Write `k8s/postgres/templates/service.yaml`**

```yaml
apiVersion: v1
kind: Service
metadata:
  name: {{ .Values.serviceName }}
  namespace: {{ include "agent-engine-postgres.namespace" . }}
  labels:
    app.kubernetes.io/name: postgres
    {{- include "agent-engine-postgres.labels" . | nindent 4 }}
  {{- with .Values.service.annotations }}
  annotations:
    {{- toYaml . | nindent 4 }}
  {{- end }}
spec:
  selector:
    app.kubernetes.io/name: postgres
    app.kubernetes.io/instance: {{ .Release.Name }}
  ports:
    - name: postgres
      port: {{ .Values.port }}
      targetPort: postgres
```

- [ ] **Step 8: Write `k8s/postgres/templates/headless-service.yaml`**

```yaml
# Headless service required by the Postgres StatefulSet for pod DNS identity.
# Application pods connect to Postgres via the regular ClusterIP service (postgres).
apiVersion: v1
kind: Service
metadata:
  name: postgres-headless
  namespace: {{ include "agent-engine-postgres.namespace" . }}
  labels:
    app.kubernetes.io/name: postgres
    {{- include "agent-engine-postgres.labels" . | nindent 4 }}
spec:
  clusterIP: None
  selector:
    app.kubernetes.io/name: postgres
    app.kubernetes.io/instance: {{ .Release.Name }}
  ports:
    - name: postgres
      port: {{ .Values.port }}
      targetPort: postgres
```

- [ ] **Step 9: Lint the chart**

```bash
helm lint k8s/postgres --set namespace=agent-engine
```

Expected: `1 chart(s) linted, 0 chart(s) failed`

- [ ] **Step 10: Commit**

```bash
git add k8s/postgres/
git commit -m "feat(k8s): add standalone postgres Helm chart"
```

---

## Task 3: Create `k8s/localstack/` chart

**Files:**
- Create: `k8s/localstack/Chart.yaml`
- Create: `k8s/localstack/values.yaml`
- Create: `k8s/localstack/templates/_helpers.tpl`
- Create: `k8s/localstack/templates/deployment.yaml`
- Create: `k8s/localstack/templates/service.yaml`

- [ ] **Step 1: Create chart scaffold**

```bash
mkdir -p k8s/localstack/templates
```

- [ ] **Step 2: Write `k8s/localstack/Chart.yaml`**

```yaml
apiVersion: v2
name: agent-engine-localstack
description: LocalStack for Agent Engine
type: application
version: 0.1.0
appVersion: "3"
```

- [ ] **Step 3: Write `k8s/localstack/values.yaml`**

```yaml
namespace: agent-engine

image:
  repository: localstack/localstack
  tag: "3"
  pullPolicy: IfNotPresent

serviceName: localstack
port: 4566
services: s3
region: us-east-1

resources:
  requests:
    cpu: 100m
    memory: 512Mi
  limits:
    cpu: 100m
    memory: 512Mi

imagePullSecrets: []
commonLabels: {}
```

- [ ] **Step 4: Write `k8s/localstack/templates/_helpers.tpl`**

```
{{- define "agent-engine-localstack.namespace" -}}
{{- default .Release.Namespace .Values.namespace -}}
{{- end -}}

{{- define "agent-engine-localstack.labels" -}}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: agent-engine
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | quote }}
{{- with .Values.commonLabels }}
{{ toYaml . }}
{{- end }}
{{- end -}}
```

- [ ] **Step 5: Write `k8s/localstack/templates/deployment.yaml`**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: localstack
  namespace: {{ include "agent-engine-localstack.namespace" . }}
  labels:
    app.kubernetes.io/name: localstack
    {{- include "agent-engine-localstack.labels" . | nindent 4 }}
spec:
  replicas: 1
  selector:
    matchLabels:
      app.kubernetes.io/name: localstack
      app.kubernetes.io/instance: {{ .Release.Name }}
  template:
    metadata:
      labels:
        app.kubernetes.io/name: localstack
        app.kubernetes.io/instance: {{ .Release.Name }}
    spec:
      {{- with .Values.imagePullSecrets }}
      imagePullSecrets:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      containers:
        - name: localstack
          image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
          imagePullPolicy: {{ .Values.image.pullPolicy }}
          env:
            - name: SERVICES
              value: {{ .Values.services | quote }}
            - name: DEFAULT_REGION
              value: {{ .Values.region | quote }}
          ports:
            - name: gateway
              containerPort: 4566
          startupProbe:
            httpGet:
              path: /_localstack/health
              port: gateway
            periodSeconds: 5
            failureThreshold: 24
            timeoutSeconds: 3
          readinessProbe:
            httpGet:
              path: /_localstack/health
              port: gateway
            periodSeconds: 10
            failureThreshold: 3
            timeoutSeconds: 3
          livenessProbe:
            httpGet:
              path: /_localstack/health
              port: gateway
            periodSeconds: 20
            failureThreshold: 3
            timeoutSeconds: 3
          resources:
            {{- toYaml .Values.resources | nindent 12 }}
```

- [ ] **Step 6: Write `k8s/localstack/templates/service.yaml`**

```yaml
apiVersion: v1
kind: Service
metadata:
  name: {{ .Values.serviceName }}
  namespace: {{ include "agent-engine-localstack.namespace" . }}
  labels:
    app.kubernetes.io/name: localstack
    {{- include "agent-engine-localstack.labels" . | nindent 4 }}
spec:
  selector:
    app.kubernetes.io/name: localstack
    app.kubernetes.io/instance: {{ .Release.Name }}
  ports:
    - name: gateway
      port: {{ .Values.port }}
      targetPort: gateway
```

- [ ] **Step 7: Lint the chart**

```bash
helm lint k8s/localstack --set namespace=agent-engine
```

Expected: `1 chart(s) linted, 0 chart(s) failed`

- [ ] **Step 8: Commit**

```bash
git add k8s/localstack/
git commit -m "feat(k8s): add standalone localstack Helm chart"
```

---

## Task 4: Create `k8s/qdrant/` chart

**Files:**
- Create: `k8s/qdrant/Chart.yaml`
- Create: `k8s/qdrant/values.yaml`
- Create: `k8s/qdrant/templates/_helpers.tpl`
- Create: `k8s/qdrant/templates/deployment.yaml`
- Create: `k8s/qdrant/templates/service.yaml`

- [ ] **Step 1: Create chart scaffold**

```bash
mkdir -p k8s/qdrant/templates
```

- [ ] **Step 2: Write `k8s/qdrant/Chart.yaml`**

```yaml
apiVersion: v2
name: agent-engine-qdrant
description: Qdrant vector database for Agent Engine
type: application
version: 0.1.0
appVersion: "v1.13.6"
```

- [ ] **Step 3: Write `k8s/qdrant/values.yaml`**

```yaml
namespace: agent-engine

image:
  repository: qdrant/qdrant
  tag: "v1.13.6"
  pullPolicy: IfNotPresent

serviceName: qdrant
httpPort: 6333
grpcPort: 6334

resources:
  requests:
    cpu: 250m
    memory: 512Mi
  limits:
    cpu: 500m
    memory: 1Gi

imagePullSecrets: []
commonLabels: {}
```

- [ ] **Step 4: Write `k8s/qdrant/templates/_helpers.tpl`**

```
{{- define "agent-engine-qdrant.namespace" -}}
{{- default .Release.Namespace .Values.namespace -}}
{{- end -}}

{{- define "agent-engine-qdrant.labels" -}}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: agent-engine
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | quote }}
{{- with .Values.commonLabels }}
{{ toYaml . }}
{{- end }}
{{- end -}}
```

- [ ] **Step 5: Write `k8s/qdrant/templates/deployment.yaml`**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: qdrant
  namespace: {{ include "agent-engine-qdrant.namespace" . }}
  labels:
    app.kubernetes.io/name: qdrant
    {{- include "agent-engine-qdrant.labels" . | nindent 4 }}
spec:
  replicas: 1
  selector:
    matchLabels:
      app.kubernetes.io/name: qdrant
      app.kubernetes.io/instance: {{ .Release.Name }}
  template:
    metadata:
      labels:
        app.kubernetes.io/name: qdrant
        app.kubernetes.io/instance: {{ .Release.Name }}
    spec:
      {{- with .Values.imagePullSecrets }}
      imagePullSecrets:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      containers:
        - name: qdrant
          image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
          imagePullPolicy: {{ .Values.image.pullPolicy }}
          ports:
            - name: http
              containerPort: {{ .Values.httpPort }}
            - name: grpc
              containerPort: {{ .Values.grpcPort }}
          startupProbe:
            httpGet:
              path: /healthz
              port: http
            periodSeconds: 5
            failureThreshold: 24
            timeoutSeconds: 3
          readinessProbe:
            httpGet:
              path: /healthz
              port: http
            periodSeconds: 10
            failureThreshold: 3
            timeoutSeconds: 3
          livenessProbe:
            httpGet:
              path: /healthz
              port: http
            periodSeconds: 20
            failureThreshold: 3
            timeoutSeconds: 3
          resources:
            {{- toYaml .Values.resources | nindent 12 }}
```

- [ ] **Step 6: Write `k8s/qdrant/templates/service.yaml`**

```yaml
apiVersion: v1
kind: Service
metadata:
  name: {{ .Values.serviceName }}
  namespace: {{ include "agent-engine-qdrant.namespace" . }}
  labels:
    app.kubernetes.io/name: qdrant
    {{- include "agent-engine-qdrant.labels" . | nindent 4 }}
spec:
  selector:
    app.kubernetes.io/name: qdrant
    app.kubernetes.io/instance: {{ .Release.Name }}
  ports:
    - name: http
      port: {{ .Values.httpPort }}
      targetPort: http
    - name: grpc
      port: {{ .Values.grpcPort }}
      targetPort: grpc
```

- [ ] **Step 7: Lint the chart**

```bash
helm lint k8s/qdrant --set namespace=agent-engine
```

Expected: `1 chart(s) linted, 0 chart(s) failed`

- [ ] **Step 8: Commit**

```bash
git add k8s/qdrant/
git commit -m "feat(k8s): add standalone qdrant Helm chart"
```

---

## Task 5: Create environment overlays and update `lib.sh`

**Files:**
- Create: `k8s/environments/local/mongodb.yaml`
- Create: `k8s/environments/local/postgres.yaml`
- Modify: `k8s/scripts/lib.sh`

- [ ] **Step 1: Write `k8s/environments/local/mongodb.yaml`**

```yaml
# Local Docker Desktop development overlay.
requireExistingSecret: false

auth:
  enabled: true
  username: agentengine
  password: agentengine

persistence:
  enabled: true
  size: 10Gi
  storageClassName: "hostpath"
```

- [ ] **Step 2: Write `k8s/environments/local/postgres.yaml`**

```yaml
# Local Docker Desktop development overlay.
requireExistingSecret: false

auth:
  username: agentengine
  password: agentengine

persistence:
  enabled: true
  size: 20Gi
  storageClassName: "hostpath"
```

- [ ] **Step 3: Update `ALL_CHARTS` in `k8s/scripts/lib.sh`**

Replace:
```sh
DEFAULT_CHARTS="global-properties agent catalog rest knowledge"
ALL_CHARTS="infra global-properties agent catalog rest knowledge"
```

With:
```sh
DEFAULT_CHARTS="global-properties agent catalog rest knowledge"
ALL_CHARTS="global-properties agent catalog rest knowledge mongodb postgres localstack qdrant"
```

- [ ] **Step 4: Update `chart_release_name()` in `k8s/scripts/lib.sh`**

Replace the entire function:
```sh
chart_release_name() {
  case "$1" in
    global-properties) echo "agent-engine-global-properties" ;;
    agent)      echo "agent-engine-agent"      ;;
    catalog)    echo "agent-engine-catalog"    ;;
    rest)       echo "agent-engine-rest"       ;;
    knowledge)  echo "agent-engine-knowledge"  ;;
    mongodb)    echo "agent-engine-mongodb"    ;;
    postgres)   echo "agent-engine-postgres"   ;;
    localstack) echo "agent-engine-localstack" ;;
    qdrant)     echo "agent-engine-qdrant"     ;;
    *)
      echo "Unknown chart: $1" >&2
      exit 1
      ;;
  esac
}
```

- [ ] **Step 5: Verify lib.sh is valid sh**

```bash
sh -n k8s/scripts/lib.sh
```

Expected: no output (no syntax errors)

- [ ] **Step 6: Commit**

```bash
git add k8s/environments/local/mongodb.yaml k8s/environments/local/postgres.yaml k8s/scripts/lib.sh
git commit -m "feat(k8s): add env overlays and register new charts in lib.sh"
```

---

## Task 6: Update `apply-charts.sh`

**Files:**
- Modify: `k8s/scripts/apply-charts.sh`

This task has two independent changes: (a) remove the infra `--atomic` special-case, and (b) add rollout-wait cases for the four new infra charts so `deploy_service` blocks until each pod is actually ready before touching its flag file.

- [ ] **Step 1: Remove the infra `--atomic` block from `build_helm_args()`**

In `apply-charts.sh`, find and remove these lines inside `build_helm_args()`:

```sh
    if [ "$ATOMIC" = "true" ] && [ "$chart" = "infra" ]; then
      set -- "$@" --atomic
    fi
```

The surrounding `upgrade --install` block stays unchanged.

- [ ] **Step 2: Add rollout-wait cases for infra charts**

In the rollout-wait section (after the existing `chart_selected knowledge` block), add:

```sh
  # shellcheck disable=SC2086
  if chart_selected mongodb $REQUESTED_CHARTS; then
    kubectl rollout status statefulset/mongodb \
      --namespace "$NAMESPACE" --timeout "$TIMEOUT" &
    rollout_pids="$rollout_pids $!"
  fi
  # shellcheck disable=SC2086
  if chart_selected postgres $REQUESTED_CHARTS; then
    kubectl rollout status statefulset/postgres \
      --namespace "$NAMESPACE" --timeout "$TIMEOUT" &
    rollout_pids="$rollout_pids $!"
  fi
  # shellcheck disable=SC2086
  if chart_selected localstack $REQUESTED_CHARTS; then
    kubectl rollout status deployment/localstack \
      --namespace "$NAMESPACE" --timeout "$TIMEOUT" &
    rollout_pids="$rollout_pids $!"
  fi
  # shellcheck disable=SC2086
  if chart_selected qdrant $REQUESTED_CHARTS; then
    kubectl rollout status deployment/qdrant \
      --namespace "$NAMESPACE" --timeout "$TIMEOUT" &
    rollout_pids="$rollout_pids $!"
  fi
```

- [ ] **Step 3: Verify syntax**

```bash
sh -n k8s/scripts/apply-charts.sh
```

Expected: no output

- [ ] **Step 4: Commit**

```bash
git add k8s/scripts/apply-charts.sh
git commit -m "feat(k8s): add rollout-wait cases for infra charts; remove infra --atomic"
```

---

## Task 7: Create `init-postgres-schema.sh`

**Files:**
- Create: `k8s/scripts/init-postgres-schema.sh`

- [ ] **Step 1: Write the script**

```sh
#!/usr/bin/env sh

set -eu

. "$(CDPATH= cd -- "$(dirname "$0")" && pwd)/lib.sh"

NAMESPACE=${NAMESPACE:-$DEFAULT_NAMESPACE}
POSTGRES_SECRET_NAME=${POSTGRES_SECRET_NAME:-postgres-auth}
POSTGRES_USERNAME_KEY=${POSTGRES_USERNAME_KEY:-username}
POSTGRES_PASSWORD_KEY=${POSTGRES_PASSWORD_KEY:-password}
POSTGRES_DATABASE=${POSTGRES_DATABASE:-agent_engine_events}

usage() {
  cat <<'EOF'
Usage:
  ./k8s/scripts/init-postgres-schema.sh
  ./k8s/scripts/init-postgres-schema.sh -n agent-engine

Behavior:
  - Creates the event_journal and snapshot tables required by Pekko Persistence JDBC.
  - Idempotent: uses CREATE TABLE IF NOT EXISTS.
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    -n|--namespace)
      NAMESPACE=$2
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

require_command kubectl

decode_base64() {
  if printf 'dGVzdA==' | base64 -d >/dev/null 2>&1; then
    base64 -d
  else
    base64 -D
  fi
}

json_secret_value() {
  secret_name=$1
  secret_key=$2
  kubectl get secret "$secret_name" --namespace "$NAMESPACE" -o "jsonpath={.data['$secret_key']}" | decode_base64
}

POSTGRES_POD=$(kubectl get pods --namespace "$NAMESPACE" -l app.kubernetes.io/name=postgres -o jsonpath='{.items[0].metadata.name}')
POSTGRES_USER=$(json_secret_value "$POSTGRES_SECRET_NAME" "$POSTGRES_USERNAME_KEY")
POSTGRES_PASSWORD=$(json_secret_value "$POSTGRES_SECRET_NAME" "$POSTGRES_PASSWORD_KEY")

kubectl exec --namespace "$NAMESPACE" -i "$POSTGRES_POD" -- \
  env PGPASSWORD="$POSTGRES_PASSWORD" psql -U "$POSTGRES_USER" -d "$POSTGRES_DATABASE" -q <<'EOSQL'
CREATE TABLE IF NOT EXISTS event_journal (
  ordering        BIGSERIAL,
  persistence_id  VARCHAR(255) NOT NULL,
  sequence_number BIGINT       NOT NULL,
  deleted         BOOLEAN      DEFAULT FALSE NOT NULL,
  writer          VARCHAR(255) NOT NULL,
  write_timestamp BIGINT       NOT NULL,
  adapter_manifest VARCHAR(255),
  event_ser_id    INTEGER      NOT NULL,
  event_ser_manifest VARCHAR(255) NOT NULL,
  event_payload   BYTEA        NOT NULL,
  meta_ser_id     INTEGER,
  meta_ser_manifest VARCHAR(255),
  meta_payload    BYTEA,
  PRIMARY KEY (persistence_id, sequence_number)
);
CREATE UNIQUE INDEX IF NOT EXISTS event_journal_ordering_idx ON event_journal (ordering);

CREATE TABLE IF NOT EXISTS snapshot (
  persistence_id  VARCHAR(255) NOT NULL,
  sequence_number BIGINT       NOT NULL,
  created         BIGINT       NOT NULL,
  snapshot_ser_id INTEGER      NOT NULL,
  snapshot_ser_manifest VARCHAR(255) NOT NULL,
  snapshot_payload BYTEA       NOT NULL,
  meta_ser_id     INTEGER,
  meta_ser_manifest VARCHAR(255),
  meta_payload    BYTEA,
  PRIMARY KEY (persistence_id, sequence_number)
);
EOSQL
echo "PostgreSQL Pekko schema initialized"
```

- [ ] **Step 2: Make executable and verify syntax**

```bash
chmod +x k8s/scripts/init-postgres-schema.sh
sh -n k8s/scripts/init-postgres-schema.sh
```

Expected: no output

- [ ] **Step 3: Commit**

```bash
git add k8s/scripts/init-postgres-schema.sh
git commit -m "feat(k8s): extract postgres schema init into standalone script"
```

---

## Task 8: Update `seed-infra-configs.sh`

**Files:**
- Modify: `k8s/scripts/seed-infra-configs.sh`

Two changes: update the two secret name defaults, and remove the psql block (which is now in `init-postgres-schema.sh`).

- [ ] **Step 1: Update secret name defaults**

Find and replace:
```sh
MONGODB_CONNECTION_SECRET_NAME=${MONGODB_CONNECTION_SECRET_NAME:-agent-engine-infra-mongodb-connection}
```
with:
```sh
MONGODB_CONNECTION_SECRET_NAME=${MONGODB_CONNECTION_SECRET_NAME:-mongodb-connection}
```

Find and replace:
```sh
POSTGRES_SECRET_NAME=${POSTGRES_SECRET_NAME:-agent-engine-infra-postgres-auth}
```
with:
```sh
POSTGRES_SECRET_NAME=${POSTGRES_SECRET_NAME:-postgres-auth}
```

- [ ] **Step 2: Remove the psql block**

Remove these lines from the bottom of the script (everything from `POSTGRES_POD=` through `echo "PostgreSQL Pekko schema initialized"`):

```sh
POSTGRES_POD=$(kubectl get pods --namespace "$NAMESPACE" -l app.kubernetes.io/name=postgres -o jsonpath='{.items[0].metadata.name}')
POSTGRES_USER=$(json_secret_value "$POSTGRES_SECRET_NAME" "$POSTGRES_USERNAME_KEY")
POSTGRES_PASSWORD=$(json_secret_value "$POSTGRES_SECRET_NAME" "$POSTGRES_PASSWORD_KEY")

kubectl exec --namespace "$NAMESPACE" -i "$POSTGRES_POD" -- \
  env PGPASSWORD="$POSTGRES_PASSWORD" psql -U "$POSTGRES_USER" -d "$POSTGRES_DATABASE" -q <<'EOSQL'
CREATE TABLE IF NOT EXISTS event_journal (
  ...
);
...
EOSQL
echo "PostgreSQL Pekko schema initialized"
```

The script should now end right after the `kubectl exec ... mongosh` block and its closing line.

- [ ] **Step 3: Verify syntax**

```bash
sh -n k8s/scripts/seed-infra-configs.sh
```

Expected: no output

- [ ] **Step 4: Commit**

```bash
git add k8s/scripts/seed-infra-configs.sh
git commit -m "feat(k8s): remove postgres DDL from seed-infra-configs; update secret name defaults"
```

---

## Task 9: Update `deploy.sh`

**Files:**
- Modify: `k8s/scripts/deploy.sh`

This is the central orchestration change. Five targeted edits in order.

- [ ] **Step 1: Replace `job_infra` with `INFRA_SERVICES` table and `job_infra_svc`**

Remove the entire `job_infra()` function:
```sh
job_infra() {
  if [ "$SKIP_INFRA" = "true" ]; then
    touch "$STATE_DIR/infra-deployed"
    return 0
  fi
  print_phase "Deploying infrastructure workloads"
  # shellcheck disable=SC2046
  sh "$SCRIPT_DIR/deploy-infra.sh" $(helm_flags) || fail
  touch "$STATE_DIR/infra-deployed"
}
```

Replace it with the `INFRA_SERVICES` table and `job_infra_svc`:

```sh
# Declare infra services: "chart:comma-separated-wait-flags:ready-flag"
# To add a new infra service, append one line here. No other changes required.
INFRA_SERVICES="
mongodb::mongodb-ready
postgres::postgres-ready
localstack::localstack-ready
qdrant::qdrant-ready
"

# Deploys a single infra chart, skipping when --skip-infra is set.
# Accepts optional deps so infra services can depend on each other.
job_infra_svc() {
  chart=$1 deps=$2 ready_flag=$3
  if [ "$SKIP_INFRA" = "true" ] || [ "$DRY_RUN" = "true" ]; then
    touch "$STATE_DIR/$ready_flag"
    return 0
  fi
  deploy_service "$chart" "$deps" "$ready_flag"
}
```

- [ ] **Step 2: Add `job_init_postgres` after `job_seed_infra`**

Update the existing `job_seed_infra` to wait for `mongodb-ready` (not `infra-deployed`):

```sh
job_seed_infra() {
  if [ "$SKIP_INFRA" = "true" ] || [ "$DRY_RUN" = "true" ]; then
    touch "$STATE_DIR/infra-seeded"
    return 0
  fi
  wait_for mongodb-ready
  print_phase "Seeding infrastructure configuration"
  sh "$SCRIPT_DIR/seed-infra-configs.sh" -e "$ENVIRONMENT" -n "$NAMESPACE" || fail
  touch "$STATE_DIR/infra-seeded"
}
```

Add `job_init_postgres` immediately after it:

```sh
job_init_postgres() {
  if [ "$SKIP_INFRA" = "true" ] || [ "$DRY_RUN" = "true" ]; then
    touch "$STATE_DIR/postgres-schema-ready"
    return 0
  fi
  wait_for postgres-ready
  print_phase "Initializing PostgreSQL schema"
  sh "$SCRIPT_DIR/init-postgres-schema.sh" -n "$NAMESPACE" || fail
  touch "$STATE_DIR/postgres-schema-ready"
}
```

- [ ] **Step 3: Update `APP_SERVICES`**

Replace the existing `APP_SERVICES` block:

```sh
# Declare app services: "chart:comma-separated-wait-flags:ready-flag"
# To add a new service, append one line here. No other changes required.
APP_SERVICES="
global-properties::global-properties-ready
catalog:global-properties-ready,builds-done:catalog-ready
rest:global-properties-ready,builds-done:rest-ready
knowledge:global-properties-ready,builds-done:knowledge-ready
agent:global-properties-ready,catalog-ready,builds-done:agent-ready
"
```

- [ ] **Step 4: Update `CATALOG_DEPS`**

Replace:
```sh
CATALOG_DEPS="catalog-ready,rest-ready"
```
With:
```sh
# Ready-flags that must all be set before catalog seeding begins.
# Append a service's ready-flag here if seed-catalog calls through it.
CATALOG_DEPS="mongodb-ready,catalog-ready,rest-ready"
```

- [ ] **Step 5: Replace the main launch block**

Remove the old `job_build &`, `job_infra &`, `job_seed_infra &`, and `APP_SERVICES` loop. Replace the entire block between `pids=""` and the pid-collection loop with:

```sh
pids=""

job_build &
pids="$pids $!"

for entry in $INFRA_SERVICES; do
  [ -n "$entry" ] || continue
  chart=$(printf '%s' "$entry" | cut -d: -f1)
  deps=$(printf '%s' "$entry"  | cut -d: -f2)
  flag=$(printf '%s' "$entry"  | cut -d: -f3)
  job_infra_svc "$chart" "$deps" "$flag" &
  pids="$pids $!"
done

job_seed_infra &
pids="$pids $!"

job_init_postgres &
pids="$pids $!"

for entry in $APP_SERVICES; do
  [ -n "$entry" ] || continue
  chart=$(printf '%s' "$entry" | cut -d: -f1)
  deps=$(printf '%s' "$entry"  | cut -d: -f2)
  flag=$(printf '%s' "$entry"  | cut -d: -f3)
  deploy_service "$chart" "$deps" "$flag" &
  pids="$pids $!"
done

job_seed_catalog &
pids="$pids $!"
```

- [ ] **Step 6: Update `usage()` stages description**

Replace the stages comment in `usage()`:
```sh
Stages (run concurrently where dependencies allow):
  build + infra services   All start at T=0 in parallel.
  init postgres schema     Starts once postgres is ready.
  seed infra configs       Starts once mongodb is ready.
  global-properties        Starts at T=0 (static ConfigMap, no deps).
  catalog + rest           Start once build and global-properties are done.
  knowledge                Starts once build and global-properties are done.
  agent                    Starts once catalog is ready.
  seed application configs Starts once mongodb, catalog, and rest are ready.
```

- [ ] **Step 7: Verify syntax**

```bash
sh -n k8s/scripts/deploy.sh
```

Expected: no output

- [ ] **Step 8: Commit**

```bash
git add k8s/scripts/deploy.sh
git commit -m "feat(k8s): replace job_infra with INFRA_SERVICES table; wire parallel DAG"
```

---

## Task 10: Update `cleanup.sh`

**Files:**
- Modify: `k8s/scripts/cleanup.sh`

`cleanup.sh` currently uninstalls `infra` as one release and has a special localstack cleanup guard on `chart_selected "infra"`. Both need updating.

- [ ] **Step 1: Replace the Helm uninstall loop**

Find the loop:
```sh
for chart in rest catalog agent knowledge global-properties infra; do
  # shellcheck disable=SC2086
  if chart_selected "$chart" $REQUESTED_CHARTS; then
    release_name=$(chart_release_name "$chart")
    helm uninstall "$release_name" --namespace "$NAMESPACE" 2>/dev/null || true &
  fi
done
wait
```

Replace with (adds the 4 new charts, removes `infra`):
```sh
for chart in rest catalog agent knowledge global-properties mongodb postgres localstack qdrant; do
  # shellcheck disable=SC2086
  if chart_selected "$chart" $REQUESTED_CHARTS; then
    release_name=$(chart_release_name "$chart")
    helm uninstall "$release_name" --namespace "$NAMESPACE" 2>/dev/null || true &
  fi
done
wait
```

- [ ] **Step 2: Fix the localstack cleanup guard**

Find:
```sh
# Explicitly remove localstack resources that may persist outside of Helm tracking
# shellcheck disable=SC2086
if chart_selected "infra" $REQUESTED_CHARTS; then
  require_command kubectl
  kubectl delete all -l app.kubernetes.io/name=localstack --namespace "$NAMESPACE" --ignore-not-found >/dev/null
  echo "Removed localstack resources from namespace $NAMESPACE"
fi
```

Replace with:
```sh
# Explicitly remove localstack resources that may persist outside of Helm tracking
# shellcheck disable=SC2086
if chart_selected "localstack" $REQUESTED_CHARTS; then
  require_command kubectl
  kubectl delete all -l app.kubernetes.io/name=localstack --namespace "$NAMESPACE" --ignore-not-found >/dev/null
  echo "Removed localstack resources from namespace $NAMESPACE"
fi
```

- [ ] **Step 3: Verify syntax**

```bash
sh -n k8s/scripts/cleanup.sh
```

Expected: no output

- [ ] **Step 4: Commit**

```bash
git add k8s/scripts/cleanup.sh
git commit -m "feat(k8s): update cleanup.sh for split infra charts"
```

---

## Task 11: Delete old files

**Files:**
- Delete: `k8s/infra/` (entire directory)
- Delete: `k8s/scripts/deploy-infra.sh`
- Delete: `k8s/environments/local/infra.yaml`

- [ ] **Step 1: Delete the monolithic infra chart**

```bash
git rm -r k8s/infra/
```

- [ ] **Step 2: Delete the infra deploy wrapper script**

```bash
git rm k8s/scripts/deploy-infra.sh
```

- [ ] **Step 3: Delete the old environment overlay**

```bash
git rm k8s/environments/local/infra.yaml
```

- [ ] **Step 4: Commit**

```bash
git commit -m "chore(k8s): delete monolithic infra chart and deploy-infra.sh"
```

---

## Task 12: Validate

- [ ] **Step 1: Lint all four new charts together**

```bash
helm lint k8s/mongodb k8s/postgres k8s/localstack k8s/qdrant --set namespace=agent-engine
```

Expected: `4 chart(s) linted, 0 chart(s) failed`

- [ ] **Step 2: Template-render mongodb with local overlay**

```bash
helm template agent-engine-mongodb k8s/mongodb \
  --namespace agent-engine \
  --set namespace=agent-engine \
  -f k8s/environments/local/mongodb.yaml
```

Expected: valid YAML output containing `StatefulSet`, two `Service` resources, and two `Secret` resources. `storageClassName: hostpath` should appear in the VolumeClaimTemplate.

- [ ] **Step 3: Template-render postgres with local overlay**

```bash
helm template agent-engine-postgres k8s/postgres \
  --namespace agent-engine \
  --set namespace=agent-engine \
  -f k8s/environments/local/postgres.yaml
```

Expected: valid YAML with `StatefulSet`, two `Service` resources, one `Secret`. `storageClassName: hostpath` and `size: 20Gi` in VolumeClaimTemplate.

- [ ] **Step 4: Dry-run the full deployment orchestration**

```bash
sh k8s/scripts/deploy.sh --dry-run -e local
```

Expected: Helm renders each chart without error. No `failed` flag written. Script exits 0. Output should show phases for global-properties, catalog, rest, knowledge, agent (infra and seed jobs are no-ops in dry-run).

- [ ] **Step 5: Dry-run with `--skip-infra`**

```bash
sh k8s/scripts/deploy.sh --skip-infra --dry-run -e local
```

Expected: exits 0. All infra ready-flags pre-satisfied, seed jobs skipped. App service Helm renders proceed normally.

- [ ] **Step 6: Verify cleanup dry run lists correct releases**

```bash
sh k8s/scripts/cleanup.sh --help
```

Then manually verify the for-loop in `cleanup.sh` lists `mongodb postgres localstack qdrant` and not `infra`:

```bash
grep 'for chart in' k8s/scripts/cleanup.sh
```

Expected output:
```
for chart in rest catalog agent knowledge global-properties mongodb postgres localstack qdrant; do
```

- [ ] **Step 7: Final commit if any fixups were needed**

```bash
git add -A
git status  # confirm only intentional changes
git commit -m "fix(k8s): validation fixups for split infra parallel deploy" 2>/dev/null || true
```
