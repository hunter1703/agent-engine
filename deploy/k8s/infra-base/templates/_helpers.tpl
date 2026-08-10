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
