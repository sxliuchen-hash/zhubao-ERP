/** 与服务端 MASTER_DATA_MANAGE 一致。业务管理必须单独勾选客户档案。 */
export function canManageMasterData(session: { role?: string; coverDuties?: string[] } | null | undefined): boolean {
  const role = session?.role ?? '';
  if (role === 'BUSINESS_MANAGER') return (session?.coverDuties ?? []).includes('MASTER');
  return ['ADMIN', 'GENERAL_MANAGER', 'RECEIVER', 'WAREHOUSE_KEEPER', 'WORKSHOP_LEADER'].includes(role);
}
