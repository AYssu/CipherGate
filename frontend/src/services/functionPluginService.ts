import request from '../utils/request';

export interface FunctionPluginModule {
  id: number;
  pluginId: string;
  pluginName?: string;
  pluginVersion: string;
  bucketName: string;
  objectKey: string;
  sha256: string;
  status: number;
  loadedPluginId?: string;
  remark?: string;
  functions?: string;
  configSchema?: string;
  configDefaults?: string;
  configValues?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface FunctionInfo {
  name: string;
  pluginId: string;
  description?: string;
  exampleInput?: Record<string, unknown>;
  exampleOutput?: Record<string, unknown>;
  inputExample?: Record<string, unknown>;
  outputExample?: Record<string, unknown>;
  inputSchema?: Record<string, unknown>;
}

export interface TestFunctionRequest {
  pluginId: string;
  func: string;
  params: Record<string, unknown>;
}

export interface TestFunctionResponse {
  success: boolean;
  data?: Record<string, unknown>;
  code?: string;
  message?: string;
}

export const listFunctionPlugins = () => {
  return request.get('/function-plugins');
};

export const uploadFunctionPlugin = (formData: FormData) => {
  return request.post('/function-plugins/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
};

export const enableFunctionPlugin = (id: number) => {
  return request.post(`/function-plugins/${id}/enable`);
};

export const disableFunctionPlugin = (id: number) => {
  return request.post(`/function-plugins/${id}/disable`);
};

export const deleteFunctionPlugin = (id: number) => {
  return request.delete(`/function-plugins/${id}`);
};

export const getFunctionPluginConfigSchema = (id: number) => {
  return request.get(`/function-plugins/${id}/config-schema`);
};

export const getFunctionPluginConfig = (id: number) => {
  return request.get(`/function-plugins/${id}/config`);
};

export const updateFunctionPluginConfig = (id: number, configValues: Record<string, unknown>) => {
  return request.put(`/function-plugins/${id}/config`, configValues);
};

export const getFunctionPluginFunctions = (id: number) => {
  return request.get(`/function-plugins/${id}/functions`);
};

export const testFunction = (data: TestFunctionRequest) => {
  return request.post('/function-plugins/test', data);
};

export const getFunctionPluginAppAccess = (id: number) => {
  return request.get(`/function-plugins/${id}/app-access`);
};

export const replaceFunctionPluginAppAccess = (id: number, appIds: number[]) => {
  return request.put(`/function-plugins/${id}/app-access`, { appIds });
};
