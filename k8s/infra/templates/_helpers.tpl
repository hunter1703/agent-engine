{{- define "agent-engine-infra.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}

{{- define "agent-engine-infra.namespace" -}}
{{- default .Release.Namespace .Values.namespace -}}
{{- end -}}

{{- define "agent-engine-infra.labels" -}}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: agent-engine
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | quote }}
{{- with .Values.commonLabels }}
{{ toYaml . }}
{{- end }}
{{- end -}}

{{- define "agent-engine-infra.mongodbAuthSecretName" -}}
{{- if .Values.mongodb.auth.existingSecret -}}
{{- .Values.mongodb.auth.existingSecret -}}
{{- else -}}
{{- printf "%s-mongodb-auth" (include "agent-engine-infra.fullname" .) -}}
{{- end -}}
{{- end -}}

{{- define "agent-engine-infra.postgresSecretName" -}}
{{- if .Values.postgres.existingSecret -}}
{{- .Values.postgres.existingSecret -}}
{{- else -}}
{{- printf "%s-postgres-auth" (include "agent-engine-infra.fullname" .) -}}
{{- end -}}
{{- end -}}

{{- define "agent-engine-infra.mongodbConnectionString" -}}
{{- if .Values.mongodb.connectionString -}}
{{- .Values.mongodb.connectionString -}}
{{- else if .Values.mongodb.auth.enabled -}}
{{- printf "mongodb://%s:%s@%s:%v/?authSource=admin" .Values.mongodb.auth.username .Values.mongodb.auth.password .Values.mongodb.serviceName .Values.mongodb.port -}}
{{- else -}}
{{- printf "mongodb://%s:%v" .Values.mongodb.serviceName .Values.mongodb.port -}}
{{- end -}}
{{- end -}}

{{- define "agent-engine-infra.postgresJdbcUrl" -}}
{{- if .Values.postgres.jdbcUrl -}}
{{- .Values.postgres.jdbcUrl -}}
{{- else -}}
{{- printf "jdbc:postgresql://%s:%v/%s" .Values.postgres.serviceName .Values.postgres.port .Values.postgres.database -}}
{{- end -}}
{{- end -}}

{{- define "agent-engine-infra.mongodbImage" -}}
{{- if .Values.mongodb.image.digest -}}
{{ printf "%s@%s" .Values.mongodb.image.repository .Values.mongodb.image.digest }}
{{- else -}}
{{ printf "%s:%s" .Values.mongodb.image.repository .Values.mongodb.image.tag }}
{{- end -}}
{{- end -}}

{{- define "agent-engine-infra.postgresImage" -}}
{{- if .Values.postgres.image.digest -}}
{{ printf "%s@%s" .Values.postgres.image.repository .Values.postgres.image.digest }}
{{- else -}}
{{ printf "%s:%s" .Values.postgres.image.repository .Values.postgres.image.tag }}
{{- end -}}
{{- end -}}
