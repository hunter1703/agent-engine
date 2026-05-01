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
