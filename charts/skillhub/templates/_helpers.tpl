{{- /*
SkillHub Helm Chart 模板辅助函数
*/}}

{{- /* 名称 */}}
{{- define "skillhub.name" -}}
{{- default "skillhub" .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- /* 完整名称 */}}
{{- define "skillhub.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default "skillhub" .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{- /* Chart 标签 */}}
{{- define "skillhub.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- /* 通用标签 */}}
{{- define "skillhub.labels" -}}
helm.sh/chart: {{ include "skillhub.chart" . }}
{{ include "skillhub.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: skillhub
{{- end }}

{{- /* 选择器标签 */}}
{{- define "skillhub.selectorLabels" -}}
app.kubernetes.io/name: {{ include "skillhub.name" . }}
{{- end }}

{{- /* 组件标签 */}}
{{- define "skillhub.server.labels" -}}
{{ include "skillhub.labels" . }}
app.kubernetes.io/component: server
{{- end }}
{{- define "skillhub.server.selectorLabels" -}}
{{ include "skillhub.selectorLabels" . }}
app.kubernetes.io/component: server
{{- end }}

{{- define "skillhub.web.labels" -}}
{{ include "skillhub.labels" . }}
app.kubernetes.io/component: web
{{- end }}
{{- define "skillhub.web.selectorLabels" -}}
{{ include "skillhub.selectorLabels" . }}
app.kubernetes.io/component: web
{{- end }}

{{- define "skillhub.scanner.labels" -}}
{{ include "skillhub.labels" . }}
app.kubernetes.io/component: scanner
{{- end }}
{{- define "skillhub.scanner.selectorLabels" -}}
{{ include "skillhub.selectorLabels" . }}
app.kubernetes.io/component: scanner
{{- end }}

{{- /* 镜像地址 */}}
{{- define "skillhub.image" -}}
{{- $registry := .registry | default .global.registry }}
{{- printf "%s/%s:%s" $registry .name .tag }}
{{- end }}

{{- /* JDBC Host */}}
{{- define "skillhub.jdbcHost" -}}
{{- if eq .Values.database.mode "internal" -}}
{{ include "skillhub.fullname" . }}-postgres
{{- else -}}
{{ .Values.database.external.host }}
{{- end -}}
{{- end }}

{{- /* JDBC Port */}}
{{- define "skillhub.jdbcPort" -}}
{{- if eq .Values.database.mode "internal" -}}5432{{- else -}}
{{ .Values.database.external.port | default "5432" }}
{{- end -}}
{{- end }}

{{- /* Secret 名称 */}}
{{- define "skillhub.secretName" -}}
{{- .Values.existingSecret | default (printf "%s-secret" (include "skillhub.fullname" .)) }}
{{- end }}

{{- /* Redis Host */}}
{{- define "skillhub.redisHost" -}}
{{- if eq .Values.redis.mode "internal" -}}
{{ include "skillhub.fullname" . }}-redis
{{- else -}}
{{ .Values.redis.external.host }}
{{- end -}}
{{- end }}

{{- /* Redis Port */}}
{{- define "skillhub.redisPort" -}}
{{- if eq .Values.redis.mode "internal" -}}6379{{- else -}}
{{ .Values.redis.external.port | default "6379" }}
{{- end -}}
{{- end }}

{{- /* 数据库 JDBC URL */}}
{{- define "skillhub.jdbcUrl" -}}
{{- if eq .Values.database.mode "internal" -}}
jdbc:postgresql://{{ include "skillhub.fullname" . }}-postgres:5432/skillhub
{{- else -}}
{{- if .Values.database.external.jdbcUrl -}}
{{ .Values.database.external.jdbcUrl }}
{{- else -}}
jdbc:postgresql://{{ .Values.database.external.host }}:{{ .Values.database.external.port }}/{{ .Values.database.external.database }}{{ if .Values.database.external.parameters }}?{{ .Values.database.external.parameters }}{{ end }}
{{- end }}
{{- end }}
{{- end }}
