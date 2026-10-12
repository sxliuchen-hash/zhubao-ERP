export interface ConfigurationBaselineSummary {
  systemParameters: number;
  codeRules: number;
  roleBaselines: number;
  processTemplates: number;
  warehouseStates: number;
  videoParameters: number;
  approvalRequiredItems: number;
  changeRequests: number;
  auditEvents: number;
}

export interface SystemParameter {
  key: string;
  value: string;
  category: string;
  level: string;
  description: string;
  changeControl: string;
  effectiveScope: string;
}

export interface CodeRule {
  objectType: string;
  format: string;
  example: string;
  uniqueness: string;
  changePolicy: string;
}

export interface RolePermissionBaseline {
  role: string;
  dataScope: string;
  permissions: string[];
  sensitiveActions: string[];
}

export interface ProcessTemplate {
  code: string;
  name: string;
  materialScope: string;
  steps: string[];
  lossRule: string;
  wageRule: string;
  qualityRule: string;
  snapshotFields: string;
}

export interface WarehouseState {
  code: string;
  name: string;
  ownerBoundary: string;
  stockStatus: string;
  transitionRule: string;
  negativeAllowed: boolean;
}

export interface VideoParameter {
  key: string;
  value: string;
  scene: string;
  fallback: string;
  retention: string;
  changeControl: string;
}

export interface ComponentBaseline {
  component: string;
  version: string;
  license: string;
  purpose: string;
  source: string;
  risk: string;
}

export interface BackupPolicy {
  target: string;
  strategy: string;
  rpo: string;
  rto: string;
  owner: string;
}

export interface ConfigurationChangeRequest {
  code: string;
  targetKey: string;
  targetName: string;
  changeType: string;
  beforeValue: string;
  afterValue: string;
  reason: string;
  applicant: string;
  approver: string;
  status: string;
  riskLevel: string;
  submittedAt: string;
}

export interface ConfigurationAuditEvent {
  eventId: string;
  targetKey: string;
  operation: string;
  operator: string;
  occurredAt: string;
  result: string;
  evidence: string;
  traceId: string;
}

export interface PermissionGrant {
  code: string;
  label: string;
}

export interface SecurityHeaderContract {
  operatorHeader: string;
  roleHeader: string;
  dataScopeHeader: string;
  customerScopeHeader: string;
}

export interface NavigationMenuItem {
  code: string;
  label: string;
  module: string;
  route: string;
  buttonCodes: string[];
  children: NavigationMenuItem[];
}

export interface SecurityCurrentContext {
  operator: string;
  role: string;
  roleLabel: string;
  dataScope: string;
  dataScopeLabel: string;
  dataBoundary: string;
  customerScope: string[];
  headerBased: boolean;
  allowedPermissions: PermissionGrant[];
  sensitiveActionCodes: string[];
  menuCodes: string[];
  menuTree: NavigationMenuItem[];
  limitationNotes: string[];
  headerContract: SecurityHeaderContract;
}

export interface ConfigurationBaselineWorkspace {
  summary: ConfigurationBaselineSummary;
  systemParameters: SystemParameter[];
  codeRules: CodeRule[];
  rolePermissions: RolePermissionBaseline[];
  processTemplates: ProcessTemplate[];
  warehouseStates: WarehouseState[];
  videoParameters: VideoParameter[];
  components: ComponentBaseline[];
  backupPolicies: BackupPolicy[];
  changeRequests: ConfigurationChangeRequest[];
  auditEvents: ConfigurationAuditEvent[];
  securityContext: SecurityCurrentContext;
}
