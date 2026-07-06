import type {
  BackupPolicy,
  CodeRule,
  ComponentBaseline,
  ConfigurationBaselineSummary,
  ConfigurationBaselineWorkspace,
  ProcessTemplate,
  RolePermissionBaseline,
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
  };
}
