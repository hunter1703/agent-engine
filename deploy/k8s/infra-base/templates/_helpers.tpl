{{/*
Shared across mongodb/postgres/localstack/qdrant. Each consuming chart's own .Values.<field>
(inclusive of whatever -f tiers/<tier>/values.yaml overlay it was rendered with) wins if set;
otherwise falls back to the default declared once in k8s/infra-base/values.yaml, reached via
.Subcharts.infra-base.Values — the one place a library chart's own values are actually reachable
from a consuming chart's templates.
*/}}

{{- define "agent-engine.infra-base.namespace" -}}
{{- default (index .Subcharts "infra-base").Values.namespace .Values.namespace -}}
{{- end -}}

{{- define "agent-engine.infra-base.appNamespace" -}}
{{- default (index .Subcharts "infra-base").Values.appNamespace .Values.appNamespace -}}
{{- end -}}

{{- define "agent-engine.infra-base.instance" -}}
{{- if .Values.tier -}}
{{- printf "%s-%s" .Values.service.name .Values.tier -}}
{{- else -}}
{{- .Values.service.name -}}
{{- end -}}
{{- end -}}

{{- define "agent-engine.infra-base.labels" -}}
app.kubernetes.io/name: {{ .Values.service.name }}
app.kubernetes.io/instance: {{ include "agent-engine.infra-base.instance" . }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | quote }}
{{- end -}}

{{- define "agent-engine.infra-base.selectorLabels" -}}
app.kubernetes.io/name: {{ .Values.service.name }}
app.kubernetes.io/instance: {{ include "agent-engine.infra-base.instance" . }}
{{- end -}}

{{/*
Returns the merged probes dict (startup/readiness/liveness periodSeconds/failureThreshold/
timeoutSeconds) as YAML — the chart's own .Values.probes (if set) overrides the shared default in
k8s/infra-base/values.yaml field by field. Callers pull it into a variable with `fromYaml` since
they need individual scalars (each protocol-specific probe block — tcpSocket, exec, httpGet — is
still written out per chart, only the timing numbers are shared):
  {{- $probes := include "agent-engine.infra-base.probes" . | fromYaml -}}
*/}}
{{- define "agent-engine.infra-base.probes" -}}
{{- mergeOverwrite (deepCopy (index .Subcharts "infra-base").Values.probes) (.Values.probes | default dict) | toYaml -}}
{{- end -}}

{{/* Same shape as .probes, defaulting from the infra-base httpProbes profile instead — for
httpGet-based checks (localstack/qdrant), which start up faster and tolerate fewer missed checks
than the tcpSocket/exec checks mongodb/postgres use. */}}
{{- define "agent-engine.infra-base.httpProbes" -}}
{{- mergeOverwrite (deepCopy (index .Subcharts "infra-base").Values.httpProbes) (.Values.probes | default dict) | toYaml -}}
{{- end -}}

{{- define "agent-engine.infra-base.podSecurityContext" -}}
{{- $merged := mergeOverwrite (deepCopy (index .Subcharts "infra-base").Values.podSecurityContext) (.Values.podSecurityContext | default dict) -}}
{{- toYaml $merged -}}
{{- end -}}

{{- define "agent-engine.infra-base.containerSecurityContext" -}}
{{- $merged := mergeOverwrite (deepCopy (index .Subcharts "infra-base").Values.containerSecurityContext) (.Values.containerSecurityContext | default dict) -}}
{{- toYaml $merged -}}
{{- end -}}

{{/*
Renders one post-install/post-upgrade Job per entry in .Values.postDeploymentScripts — a list of
{name, image, command} maps — for one-shot bootstrap logic (schema init, replica-set initiate,
seed data) that needs to run after a chart's pods exist. Each script gets its own Job, hook
weight following list order, so a chart with several scripts gets them run in the order declared
without hand-writing Job boilerplate per script. `image` and `command` are passed through `tpl`,
so values.yaml entries may use Helm template syntax against the calling chart's own context, e.g.
{{ .Values.replicaCount }} or {{ include "agent-engine.infra-base.instance" . }}. Call from a
chart's own templates/ file:
  {{- include "agent-engine.infra-base.postDeploymentScriptJobs" . }}
*/}}
{{- define "agent-engine.infra-base.postDeploymentScriptJobs" -}}
{{- $instance := include "agent-engine.infra-base.instance" . -}}
{{- $namespace := include "agent-engine.infra-base.namespace" . -}}
{{- range $index, $script := .Values.postDeploymentScripts }}
---
apiVersion: batch/v1
kind: Job
metadata:
  name: {{ $instance }}-{{ $script.name }}
  namespace: {{ $namespace }}
  labels:
    {{- include "agent-engine.infra-base.labels" $ | nindent 4 }}
  annotations:
    "helm.sh/hook": post-install,post-upgrade
    "helm.sh/hook-weight": {{ $index | quote }}
    # A Job's spec is immutable, so a name collision with the previous release's Job (still
    # around under ttlSecondsAfterFinished) would fail the upgrade outright without this.
    "helm.sh/hook-delete-policy": before-hook-creation
spec:
  backoffLimit: {{ $script.backoffLimit | default 6 }}
  activeDeadlineSeconds: {{ $script.activeDeadlineSeconds | default 600 }}
  ttlSecondsAfterFinished: 172800
  template:
    metadata:
      labels:
        {{- include "agent-engine.infra-base.selectorLabels" $ | nindent 8 }}
    spec:
      restartPolicy: OnFailure
      securityContext:
        {{- include "agent-engine.infra-base.podSecurityContext" $ | nindent 8 }}
      containers:
        - name: script-runner
          image: {{ tpl (required (printf "postDeploymentScripts[%d].image is required" $index) $script.image) $ }}
          imagePullPolicy: IfNotPresent
          securityContext:
            {{- include "agent-engine.infra-base.containerSecurityContext" $ | nindent 12 }}
          resources:
            {{- /* 256Mi default: a Node.js-based client (e.g. mongosh) needs ~150-250MB RSS just
                   to start, before it does anything - 128Mi silently OOM-kills it (exit 137) with
                   no visible OOMKilled event, since the container's PID 1 is the wrapping shell,
                   not the killed child process. */}}
            {{- toYaml ($script.resources | default (dict "requests" (dict "cpu" "100m" "memory" "256Mi") "limits" (dict "cpu" "100m" "memory" "256Mi"))) | nindent 12 }}
          command:
            - /bin/sh
            - -ec
            - |
              {{- tpl (required (printf "postDeploymentScripts[%d].command is required" $index) $script.command) $ | nindent 14 }}
{{- end }}
{{- end }}
