import request from '../utils/request';

export interface AppAgentDTO {
  id?: number;
  appId?: number;
  agentCode?: string;
  userId?: number;
  scopeMode?: 'ALL_IN_APP' | 'OWN_ONLY';
  enabled?: boolean;
  remark?: string;
  permissions?: string[];
  quotas?: Record<string, number>;
}

export interface AgentBindUserDTO {
  id: number;
  githubId: string;
  login?: string;
  name?: string;
  status?: number;
}

export const listAppAgents = (appId: number) =>
  request.get(`/applications/${appId}/agents`);

export const createAppAgent = (appId: number, data: AppAgentDTO) =>
  request.post(`/applications/${appId}/agents`, data);

export const updateAppAgent = (appId: number, agentId: number, data: AppAgentDTO) =>
  request.put(`/applications/${appId}/agents/${agentId}`, data);

export const updateAppAgentPermissions = (appId: number, agentId: number, permissions: string[]) =>
  request.put(`/applications/${appId}/agents/${agentId}/permissions`, permissions);

export const updateAppAgentQuotas = (appId: number, agentId: number, quotas: Record<string, number>) =>
  request.put(`/applications/${appId}/agents/${agentId}/quotas`, quotas);

export const lookupAppAgentBindUser = (appId: number, githubId: string) =>
  request.get(`/applications/${appId}/agents/lookup-user`, { params: { githubId } });

