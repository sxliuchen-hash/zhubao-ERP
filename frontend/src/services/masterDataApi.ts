import { apiGet, apiPost } from './apiClient';

export interface CustomerProfile {
  customerId: number;
  customerCode: string;
  name: string;
  aliases: string;
  contact: string;
  phone: string;
  remark: string;
  enabled: boolean;
}

export interface MaterialProfile {
  materialCode: string;
  name: string;
  aliases: string;
  remark: string;
  enabled: boolean;
}

export interface RequirementShortcut {
  shortcutId: number;
  phrase: string;
  sortOrder: number;
}

export const fetchCustomers = () => apiGet<CustomerProfile[]>('/api/business/master-data/customers');
export const fetchMaterials = () => apiGet<MaterialProfile[]>('/api/business/master-data/materials');
export const saveCustomer = (body: CustomerProfile) =>
  apiPost<CustomerProfile, CustomerProfile>('/api/business/master-data/customers', body);
export const saveMaterial = (body: MaterialProfile) =>
  apiPost<MaterialProfile, MaterialProfile>('/api/business/master-data/materials', body);
export const fetchShortcuts = () => apiGet<RequirementShortcut[]>('/api/business/master-data/requirement-shortcuts');
export const saveShortcut = (phrase: string) =>
  apiPost<RequirementShortcut, RequirementShortcut>('/api/business/master-data/requirement-shortcuts', {
    shortcutId: 0,
    phrase,
    sortOrder: 0,
  });
export const removeShortcut = (shortcutId: number) =>
  apiPost<{ shortcutId: number }, RequirementShortcut>('/api/business/master-data/requirement-shortcuts/removals', { shortcutId });
