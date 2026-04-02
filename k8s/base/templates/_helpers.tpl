{{- define "agent-engine.base.name" -}}
{{- default .root.Chart.Name .root.Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "agent-engine.base.fullname" -}}
{{- if .root.Values.fullnameOverride -}}
{{- .root.Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- .root.Release.Name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}

{{- define "agent-engine.base.namespace" -}}
{{- default .root.Release.Namespace .root.Values.namespace -}}
{{- end -}}

{{- define "agent-engine.base.labels" -}}
app.kubernetes.io/name: {{ include "agent-engine.base.fullname" . }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
app.kubernetes.io/component: {{ .component }}
app.kubernetes.io/managed-by: {{ .root.Release.Service }}
app.kubernetes.io/part-of: agent-engine
helm.sh/chart: {{ printf "%s-%s" .root.Chart.Name .root.Chart.Version | quote }}
{{- with .root.Values.commonLabels }}
{{ toYaml . }}
{{- end }}
{{- end -}}

{{- define "agent-engine.base.selectorLabels" -}}
app.kubernetes.io/name: {{ include "agent-engine.base.fullname" . }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
{{- end -}}

{{- define "agent-engine.base.serviceAccountName" -}}
{{- if .root.Values.serviceAccount.create -}}
{{- default (include "agent-engine.base.fullname" .) .root.Values.serviceAccount.name -}}
{{- else -}}
{{- default "default" .root.Values.serviceAccount.name -}}
{{- end -}}
{{- end -}}

{{- define "agent-engine.base.applicationPropertiesConfigMapName" -}}
{{- if .root.Values.config.existingConfigMap -}}
{{- .root.Values.config.existingConfigMap -}}
{{- else -}}
{{- printf "%s-application-properties-configmap" (include "agent-engine.base.fullname" .) -}}
{{- end -}}
{{- end -}}

{{- define "agent-engine.base.localPropertiesConfigMapName" -}}
{{- printf "%s-local-properties-configmap" (include "agent-engine.base.fullname" .) -}}
{{- end -}}

{{- define "agent-engine.base.image" -}}
{{- $repository := .root.Values.image.repository -}}
{{- $tag := default .root.Chart.AppVersion .root.Values.image.tag -}}
{{- if .root.Values.image.digest -}}
{{ printf "%s@%s" $repository .root.Values.image.digest }}
{{- else -}}
{{ printf "%s:%s" $repository $tag }}
{{- end -}}
{{- end -}}
