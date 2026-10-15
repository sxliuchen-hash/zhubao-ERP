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

export const fetchCustomers = () => apiGet<CustomerProfile[]>('/api/business/master-data/customers');
export const fetchMaterials = () => apiGet<MaterialProfile[]>('/api/business/master-data/materials');
export const saveCustomer = (body: CustomerProfile) =>
  apiPost<CustomerProfile, CustomerProfile>('/api/business/master-data/customers', body);
export const saveMaterial = (body: MaterialProfile) =>
  apiPost<MaterialProfile, MaterialProfile>('/api/business/master-data/materials', body);
