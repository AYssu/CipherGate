import request from '../utils/request';

export interface DocCategory {
  id: number;
  name: string;
  sortOrder: number;
  status: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface DocMenuItem {
  id: number;
  title: string;
  categoryId: number;
  categoryName?: string;
  sortOrder: number;
}

export interface DocMenuCategory {
  id: number;
  name: string;
  items: DocMenuItem[];
}

export interface DocDetail {
  id: number;
  title: string;
  categoryId: number;
  categoryName?: string;
  content: string;
  sortOrder: number;
  status: number;
  attachments?: DocAttachment[];
  createdAt?: string;
  updatedAt?: string;
}

export interface DocAttachment {
  id: number;
  docId: number;
  fileName: string;
  fileUrl: string;
  fileSize?: number;
  fileType?: string;
  downloadCount: number;
  createdAt?: string;
}

export interface DocItem {
  id: number;
  title: string;
  categoryId: number;
  content: string;
  sortOrder: number;
  status: number;
}

export const docApi = {
  getCategories: () =>
    request.get<{ data: DocCategory[] }>('/doc/categories'),

  createCategory: (data: Partial<DocCategory>) =>
    request.post('/doc/categories', data),

  updateCategory: (id: number, data: Partial<DocCategory>) =>
    request.put(`/doc/categories/${id}`, data),

  deleteCategory: (id: number) =>
    request.delete(`/doc/categories/${id}`),

  getDocMenu: () =>
    request.get<{ data: DocMenuCategory[] }>('/doc/menu'),

  getDocDetail: (id: number) =>
    request.get<{ data: DocDetail }>(`/doc/items/${id}`),

  createDoc: (data: Partial<DocItem>) =>
    request.post('/doc/items', data),

  updateDoc: (id: number, data: Partial<DocItem>) =>
    request.put(`/doc/items/${id}`, data),

  deleteDoc: (id: number) =>
    request.delete(`/doc/items/${id}`),

  getDocsByCategory: (categoryId: number) =>
    request.get<{ data: DocItem[] }>(`/doc/items/category/${categoryId}`),

  addAttachment: (
    docId: number,
    fileName: string,
    fileUrl: string,
    fileSize?: number,
    fileType?: string
  ) =>
    request.post(`/doc/items/${docId}/attachments`, {
      fileName,
      fileUrl,
      fileSize,
      fileType,
    }),

  deleteAttachment: (id: number) =>
    request.delete(`/doc/attachments/${id}`),

  recordDownload: (id: number) =>
    request.post(`/doc/attachments/${id}/download`),
};
