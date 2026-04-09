import request from '../utils/request';

export interface PluginModule {
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
  createdAt?: string;
  updatedAt?: string;
}

export const listPlugins = () => {
  return request.get('/plugins');
};

export const uploadPlugin = (formData: FormData) => {
  return request.post('/plugins/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
};

export const enablePlugin = (id: number) => {
  return request.post(`/plugins/${id}/enable`);
};

export const disablePlugin = (id: number) => {
  return request.post(`/plugins/${id}/disable`);
};

export const deletePlugin = (id: number) => {
  return request.delete(`/plugins/${id}`);
};

export const getPluginConfigSchema = (id: number) => {
  return request.get(`/plugins/${id}/config-schema`);
};

export const getPluginConfig = (id: number) => {
  return request.get(`/plugins/${id}/config`);
};

export const updatePluginConfig = (id: number, configValues: Record<string, any>) => {
  return request.put(`/plugins/${id}/config`, configValues);
};
