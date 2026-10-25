import type { ApiResponse } from './apiResponse';
import request from '../utils/request';

// 本地类型定义，避免循环导入
export interface Menu {
  id: number;
  menuName: string;
  menuCode: string;
  parentId: number;
  menuType: number;
  path?: string;
  component?: string;
  icon?: string;
  sortOrder: number;
  visible: number;
  status: number;
  createdAt?: string;
  updatedAt?: string;
  children?: Menu[];
}

export class MenuService {
  /**
   * 获取所有菜单树
   */
  static async getAllMenus() {
    return request.get<unknown, ApiResponse<Menu[]>>('/menus/all');
  }

  /**
   * 获取用户菜单树
   */
  static async getUserMenus() {
    return request.get<unknown, ApiResponse<Menu[]>>('/menus/user');
  }

  /**
   * 获取父菜单选项
   */
  static async getParentMenuOptions() {
    return request.get<unknown, ApiResponse<Menu[]>>('/menus/parent-options');
  }

  /**
   * 根据ID获取菜单详情
   */
  static async getMenuById(id: number) {
    return request.get<unknown, ApiResponse<Menu>>(`/menus/${id}`);
  }

  /**
   * 创建菜单
   */
  static async createMenu(menu: Partial<Menu>) {
    return request.post<unknown, ApiResponse<string>>('/menus', menu);
  }

  /**
   * 更新菜单
   */
  static async updateMenu(id: number, menu: Partial<Menu>) {
    return request.put<unknown, ApiResponse<string>>(`/menus/${id}`, menu);
  }

  /**
   * 删除菜单
   */
  static async deleteMenu(id: number) {
    return request.delete<unknown, ApiResponse<string>>(`/menus/${id}`);
  }

  /**
   * 批量删除菜单
   */
  static async batchDeleteMenus(ids: number[]) {
    return request.delete<unknown, ApiResponse<string>>('/menus/batch', { data: ids });
  }
}