import type {
  BackupPolicy,
  CodeRule,
  ComponentBaseline,
  ConfigurationAuditEvent,
  ConfigurationBaselineSummary,
  ConfigurationBaselineWorkspace,
  ConfigurationChangeRequest,
  ProcessTemplate,
  RolePermissionBaseline,
  SecurityCurrentContext,
  SystemParameter,
  VideoParameter,
  WarehouseState,
} from '../data/configurationBaselineData';
import { apiGet } from './apiClient';

const baselinePath = '/api/configuration/baseline';

export async function fetchConfigurationBaselineWorkspace(): Promise<ConfigurationBaselineWorkspace> {
  const [
    summary,
    systemParameters,
    codeRules,
    rolePermissions,
    processTemplates,
    warehouseStates,
    videoParameters,
    components,
    backupPolicies,
    changeRequests,
    auditEvents,
    securityContext,
  ] = await Promise.all([
    apiGet<ConfigurationBaselineSummary>(`${baselinePath}/summary`),
    apiGet<SystemParameter[]>(`${baselinePath}/system-parameters`),
    apiGet<CodeRule[]>(`${baselinePath}/code-rules`),
    apiGet<RolePermissionBaseline[]>(`${baselinePath}/role-permissions`),
    apiGet<ProcessTemplate[]>(`${baselinePath}/process-templates`),
    apiGet<WarehouseState[]>(`${baselinePath}/warehouse-states`),
    apiGet<VideoParameter[]>(`${baselinePath}/video-parameters`),
    apiGet<ComponentBaseline[]>(`${baselinePath}/components`),
    apiGet<BackupPolicy[]>(`${baselinePath}/backup-policies`),
    apiGet<ConfigurationChangeRequest[]>(`${baselinePath}/change-requests`),
    apiGet<ConfigurationAuditEvent[]>(`${baselinePath}/audit-events`),
    apiGet<SecurityCurrentContext>('/api/security/current-context'),
  ]);

  return {
    summary,
    systemParameters,
    codeRules,
    rolePermissions,
    processTemplates,
    warehouseStates,
    videoParameters,
    components,
    backupPolicies,
    changeRequests,
    auditEvents,
    securityContext,
  };
}
