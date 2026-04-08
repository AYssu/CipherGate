import request from '../utils/request';

export type VariableType = 'STRING' | 'NUMBER' | 'BOOLEAN' | 'JSON' | 'ARRAY';

export interface AppVariable {
  id: number;
  appId: number;
  appName?: string;
  variableName: string;
  displayName: string;
  description?: string;
  variableType: VariableType;
  variableValue?: string;
  required?: boolean;
  sortOrder?: number;
  validationRules?: string;
  options?: string;
  enabled?: boolean;
  version?: string;
  tags?: string;
  metadata?: Record<string, any>;
  createdBy?: number;
  updatedBy?: number;
  createdAt?: string;
  updatedAt?: string;
  deleted?: number;
}

export interface AppVariableQuery {
  appId?: number;
  variableName?: string;
  displayName?: string;
  variableType?: VariableType;
  enabled?: boolean;
  tag?: string;
  createdBy?: number;
  current?: number;
  size?: number;
}

export interface AppVariableDTO {
  id?: number;
  appId: number;
  variableName: string;
  displayName: string;
  description?: string;
  variableType: VariableType;
  variableValue?: string;
  required?: boolean;
  sortOrder?: number;
  validationRules?: string;
  options?: string;
  enabled?: boolean;
  version?: string;
  tags?: string;
  metadata?: Record<string, any>;
  changeReason?: string;
}

export interface AppVariableHistory {
  id: number;
  variableId: number;
  appId: number;
  variableName: string;
  operationType: 'CREATE' | 'UPDATE' | 'DELETE';
  oldValue?: string;
  newValue?: string;
  changeReason?: string;
  operatorId?: number;
  operatorIp?: string;
  operatedAt: string;
  version?: string;
}

/**
 * 分页查询变量
 */
export const getVariableList = (params: AppVariableQuery) => {
  return request.get('/app-variables', { params });
};

/**
 * 变量详情
 */
export const getVariableById = (id: number) => {
  return request.get(`/app-variables/${id}`);
};

/**
 * 根据名称查询变量
 */
export const getVariableByName = (appId: number, variableName: string) => {
  return request.get('/app-variables/by-name', { params: { appId, variableName } });
};

/**
 * 创建变量
 */
export const createVariable = (data: AppVariableDTO) => {
  return request.post('/app-variables', data);
};

/**
 * 更新变量
 */
export const updateVariable = (id: number, data: AppVariableDTO) => {
  return request.put(`/app-variables/${id}`, data);
};

/**
 * 删除变量
 */
export const deleteVariable = (id: number) => {
  return request.delete(`/app-variables/${id}`);
};

/**
 * 批量删除变量
 */
export const batchDeleteVariables = (ids: number[]) => {
  return request.delete('/app-variables/batch', { data: ids });
};

/**
 * 复制变量
 */
export const copyVariable = (id: number, newVariableName: string) => {
  return request.post(`/app-variables/${id}/copy`, { newVariableName });
};

/**
 * 获取变量历史记录
 */
export const getVariableHistory = (id: number, current = 1, size = 10) => {
  return request.get(`/app-variables/${id}/history`, { params: { current, size } });
};

/**
 * 导出应用变量配置
 */
export const exportAppVariables = (appId: number, params?: { format?: string }) => {
  return request.get(`/app-variables/app/${appId}/export`, { params });
};

/**
 * 导入应用变量配置
 */
export const importAppVariables = (
  appId: number,
  configData: string,
  params?: { format?: string }
) => {
  return request.post(`/app-variables/app/${appId}/import`, { configData }, { params });
};

/**
 * 验证变量值
 */
export const validateVariableValue = (id: number, value: string) => {
  return request.post(`/app-variables/${id}/validate`, { value });
};

