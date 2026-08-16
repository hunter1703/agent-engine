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
Pekko clustering is a single switch: `service.pekkoEnabled` turns on the runtime property, the
pod-discovery RBAC that cluster bootstrap needs, the service account token it reads the API with,
and the remoting and management ports. Keeping these together stops a service from being half
configured — enabled but unable to discover peers, or granted pod read access it never uses.
*/}}
{{- define "agent-engine.app-base.pekkoEnabled" -}}
{{- if .Values.service.pekkoEnabled }}true{{ end -}}
{{- end -}}

{{- define "agent-engine.app-base.rbacRules" -}}
{{- $rules := .Values.rbac.rules | default list -}}
{{- if .Values.service.pekkoEnabled -}}
{{- $discovery := dict "apiGroups" (list "") "resources" (list "pods") "verbs" (list "get" "watch" "list") -}}
{{- $rules = concat $rules (list $discovery) -}}
{{- end -}}
{{- toYaml $rules -}}
{{- end -}}

{{- define "agent-engine.app-base.needsRbac" -}}
{{- if or .Values.rbac.rules .Values.service.pekkoEnabled }}true{{ end -}}
{{- end -}}

{{- define "agent-engine.app-base.automountToken" -}}
{{- or .Values.serviceAccount.automountToken .Values.service.pekkoEnabled -}}
{{- end -}}
