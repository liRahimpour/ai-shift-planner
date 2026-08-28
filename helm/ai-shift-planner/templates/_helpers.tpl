{{/*
Expand the name of the chart.
*/}}
{{- define "ai-shift-planner.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Fully qualified app name, truncated to 63 chars (the DNS label limit Kubernetes enforces on
names; exceeding it fails at apply time with a message that does not mention the length).
*/}}
{{- define "ai-shift-planner.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{- define "ai-shift-planner.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "ai-shift-planner.labels" -}}
helm.sh/chart: {{ include "ai-shift-planner.chart" . }}
{{ include "ai-shift-planner.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels must never include anything that changes between releases (version, chart
version): a Deployment's selector is immutable, so a changing selector makes every upgrade
fail with a message that does not explain why.
*/}}
{{- define "ai-shift-planner.selectorLabels" -}}
app.kubernetes.io/name: {{ include "ai-shift-planner.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{- define "ai-shift-planner.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "ai-shift-planner.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{- define "ai-shift-planner.image" -}}
{{- $tag := .Values.image.tag | default .Chart.AppVersion -}}
{{- printf "%s:%s" .Values.image.repository $tag -}}
{{- end }}

{{/*
JDBC URL assembled from the individual value fields, so an environment only overrides the
host or the database name rather than restating a whole connection string.
*/}}
{{- define "ai-shift-planner.databaseUrl" -}}
{{- printf "jdbc:postgresql://%s:%v/%s%s" .Values.database.host .Values.database.port .Values.database.name .Values.database.options -}}
{{- end }}

{{/*
The environment shared by the application Deployment and the migration Job, so the two can
never drift into connecting to different databases - which would be a spectacularly
confusing failure.
*/}}
{{- define "ai-shift-planner.commonEnv" -}}
- name: SPRING_PROFILES_ACTIVE
  value: {{ .Values.config.springProfilesActive | quote }}
- name: DATABASE_URL
  value: {{ include "ai-shift-planner.databaseUrl" . | quote }}
- name: DATABASE_USERNAME
  valueFrom:
    secretKeyRef:
      name: {{ .Values.secrets.existingSecret | quote }}
      key: {{ .Values.secrets.keys.databaseUsername | quote }}
- name: DATABASE_PASSWORD
  valueFrom:
    secretKeyRef:
      name: {{ .Values.secrets.existingSecret | quote }}
      key: {{ .Values.secrets.keys.databasePassword | quote }}
- name: LOG_LEVEL
  value: {{ .Values.config.logLevel | quote }}
{{- end }}
