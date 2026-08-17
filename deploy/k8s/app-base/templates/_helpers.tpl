{{- define "agent-engine.app-base.namespace" -}}
{{- .Values.namespace -}}
{{- end -}}

{{- define "agent-engine.app-base.instance" -}}
{{- printf "%s-%s" .Values.service.name (required "tier must be set" .Values.tier) -}}
{{- end -}}

{{- define "agent-engine.app-base.labels" -}}
app.kubernetes.io/name: {{ .Values.service.name }}
app.kubernetes.io/instance: {{ include "agent-engine.app-base.instance" . }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | quote }}
{{- with .Values.labels }}
{{ toYaml . }}
{{- end }}
{{- end -}}

{{- define "agent-engine.app-base.selectorLabels" -}}
app.kubernetes.io/name: {{ .Values.service.name }}
app.kubernetes.io/instance: {{ include "agent-engine.app-base.instance" . }}
{{- end -}}

{{/*
rollingUpdate is only valid alongside type: RollingUpdate — Kubernetes rejects it set
together with Recreate. A tier overriding just the type (e.g. deploymentStrategy: {type:
Recreate}) would otherwise still carry rollingUpdate through from the base default, since
Helm's values merge is per-key, not a replace. Dropping it here means a tier override
never has to know that or null it out itself.
*/}}
{{- define "agent-engine.app-base.deploymentStrategy" -}}
type: {{ .Values.deploymentStrategy.type }}
{{- if eq .Values.deploymentStrategy.type "RollingUpdate" }}
rollingUpdate:
  {{- toYaml .Values.deploymentStrategy.rollingUpdate | nindent 2 }}
{{- end }}
{{- end -}}

{{- define "agent-engine.app-base.applicationPropertiesConfigMapName" -}}
{{- printf "%s-application-properties-configmap" (include "agent-engine.app-base.instance" .) -}}
{{- end -}}

{{- define "agent-engine.app-base.globalPropertiesConfigMapName" -}}
{{- $global := .Values.global | default dict -}}
{{- printf "global-properties-%s-configmap" (required "global.env must be set (-e/--environment)" $global.env) -}}
{{- end -}}

{{- define "agent-engine.app-base.image" -}}
{{- $img := .Values.image | default dict -}}
{{- $global := .Values.global | default dict -}}
{{- $tag := $global.imageTag | default $img.tag -}}
{{- printf "agent-engine/%s:%s" .Values.service.name $tag -}}
{{- end -}}

{{- define "agent-engine.app-base.probes" -}}
readinessProbe:
  httpGet:
    path: {{ .Values.probes.readiness.path }}
    port: http
  periodSeconds: {{ .Values.probes.readiness.periodSeconds }}
  failureThreshold: {{ .Values.probes.readiness.failureThreshold }}
  timeoutSeconds: {{ .Values.probes.readiness.timeoutSeconds }}
livenessProbe:
  httpGet:
    path: {{ .Values.probes.liveness.path }}
    port: http
  periodSeconds: {{ .Values.probes.liveness.periodSeconds }}
  failureThreshold: {{ .Values.probes.liveness.failureThreshold }}
  timeoutSeconds: {{ .Values.probes.liveness.timeoutSeconds }}
{{- if .Values.probes.startup.enabled }}
startupProbe:
  httpGet:
    path: {{ .Values.probes.startup.path }}
    port: http
  periodSeconds: {{ .Values.probes.startup.periodSeconds }}
  failureThreshold: {{ .Values.probes.startup.failureThreshold }}
  timeoutSeconds: {{ .Values.probes.startup.timeoutSeconds }}
{{- end }}
{{- end -}}

{{/*
Pekko clustering is a single switch: setting pekko.cluster turns on the runtime property, the
pod-discovery RBAC that cluster bootstrap needs, the service account token it reads the API with,
the remoting and management ports, and the pod label peers use to find each other. Keeping these
together stops a service from being half configured — enabled but unable to discover peers, or
granted pod read access it never uses. Every other template gates on this, not on pekko.cluster
directly, so the enablement check reads the same way everywhere.
*/}}
{{- define "agent-engine.app-base.pekkoEnabled" -}}
{{- if .Values.pekko.cluster }}true{{ end -}}
{{- end -}}

{{- define "agent-engine.app-base.rbacRules" -}}
{{- $rules := .Values.rbac.rules | default list -}}
{{- if include "agent-engine.app-base.pekkoEnabled" . -}}
{{- $discovery := dict "apiGroups" (list "") "resources" (list "pods") "verbs" (list "get" "watch" "list") -}}
{{- $rules = concat $rules (list $discovery) -}}
{{- end -}}
{{- toYaml $rules -}}
{{- end -}}

{{- define "agent-engine.app-base.needsRbac" -}}
{{- if or .Values.rbac.rules (include "agent-engine.app-base.pekkoEnabled" .) }}true{{ end -}}
{{- end -}}

{{- define "agent-engine.app-base.automountToken" -}}
{{- if or .Values.serviceAccount.automountToken (include "agent-engine.app-base.pekkoEnabled" .) }}true{{ else }}false{{ end -}}
{{- end -}}

{{/*
Which Pekko cluster this deployment joins. Distinct from service.name — that identifies the
Kubernetes Deployment/Service, this identifies cluster membership, so pod discovery only ever
finds peers meant to be in the same cluster instead of every pekko-enabled service in the
namespace.
*/}}
{{- define "agent-engine.app-base.pekkoClusterLabel" -}}
{{- if include "agent-engine.app-base.pekkoEnabled" . -}}
agent-engine.io/pekko-cluster: {{ .Values.pekko.cluster | quote }}
{{- end -}}
{{- end -}}
