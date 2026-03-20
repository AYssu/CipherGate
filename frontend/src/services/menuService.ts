// 本地类型定义，避免循环导入
interface Menu {
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

interface ApiResponse<T = any> {
  code: number;
  message: string;
  data: T;
  timestamp: string;
  success: boolean;
}

const BASE_URL = 'http://localhost:8080/api/menus';

export class MenuService {
  /**
   * 获取所有菜单树
   */
  static async getAllMenus(): Promise<ApiResponse<Menu[]>> {
    const response = await fetch(`${BASE_URL}/all`, {
      credentials: 'include'
    });
    return response.json();
  }

  /**
   * 获取用户菜单树
   */
  static async getUserMenus(): Promise<ApiResponse<Menu[]>> {
    const response = await fetch(`${BASE_URL}/user`, {
      credentials: 'include'
    });
    return response.json();
  }

  /**
   * 获取父菜单选项
   */
  static async getParentMenuOptions(): Promise<ApiResponse<Menu[]>> {
    const response = await fetch(`${BASE_URL}/parent-options`, {
      credentials: 'include'
    });
    return response.json();
  }

  /**
   * 根据ID获取菜单详情
   */
  static async getMenuById(id: number): Promise<ApiResponse<Menu>> {
    const response = await fetch(`${BASE_URL}/${id}`, {
      credentials: 'include'
    });
    return response.json();
  }

  /**
   * 创建菜单
   */
  static async createMenu(menu: Partial<Menu>): Promise<ApiResponse<string>> {
    const response = await fetch(BASE_URL, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      credentials: 'include',
      body: JSON.stringify(menu)
    });
    return response.json();
  }

  /**
   * 更新菜单
   */
  static async updateMenu(id: number, menu: Partial<Menu>): Promise<ApiResponse<string>> {
    const response = await fetch(`${BASE_URL}/${id}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json'
      },
      credentials: 'include',
      body: JSON.stringify(menu)
    });
    return response.json();
  }

  /**
   * 删除菜单
   */
  static async deleteMenu(id: number): Promise<ApiResponse<string>> {
    const response = await fetch(`${BASE_URL}/${id}`, {
      method: 'DELETE',
      credentials: 'include'
    });
    return response.json();
  }

  /**
   * 批量删除菜单
   */
  static async batchDeleteMenus(ids: number[]): Promise<ApiResponse<string>> {
    const response = await fetch(`${BASE_URL}/batch`, {
      method: 'DELETE',
      headers: {
        'Content-Type': 'application/json'
      },
      credentials: 'include',
      body: JSON.stringify(ids)
    });
    return response.json();
  }
}