import request from '../utils/request';

export interface ActivityLog {
  id: number;
  userId: number;
  username: string;
  actionType: string;
  actionTarget: string;
  actionDescription: string;
  ipAddress: string;
  userAgent: string;
  status: string;
  importanceLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';
  isRead: boolean;
  readTime?: string;
  createdTime: string;
}

export interface PageResult<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

export interface UnreadCount {
  total: number;
  medium: number;
  high: number;
  urgent: number;
  showBadge: boolean;
}

export const activityApi = {
  /**
   * 获取最近活动列表（用于首页展示）
   */
  getRecentActivities: (limit: number = 10) => {
    return request<ActivityLog[]>({
      url: '/activity/recent/list',
      method: 'GET',
      params: { limit }
    });
  },

  /**
   * 获取最近活动（分页）
   */
  getRecentActivitiesPage: (pageNum: number = 1, pageSize: number = 10) => {
    return request<PageResult<ActivityLog>>({
      url: '/activity/recent',
      method: 'GET',
      params: { pageNum, pageSize }
    });
  },

  /**
   * 获取用户最近活动
   */
  getUserRecentActivities: (userId: number, limit: number = 10) => {
    return request<ActivityLog[]>({
      url: `/activity/user/${userId}`,
      method: 'GET',
      params: { limit }
    });
  },

  /**
   * 获取未读消息统计
   */
  getUnreadCount: () => {
    return request<UnreadCount>({
      url: '/activity/unread/count',
      method: 'GET'
    });
  },

  /**
   * 标记活动为已读
   */
  markAsRead: (id: number) => {
    return request<void>({
      url: `/activity/${id}/read`,
      method: 'PUT'
    });
  },

  /**
   * 批量标记活动为已读
   */
  markBatchAsRead: (ids: number[]) => {
    return request<void>({
      url: '/activity/read/batch',
      method: 'PUT',
      data: ids
    });
  },

  /**
   * 标记所有活动为已读
   */
  markAllAsRead: () => {
    return request<void>({
      url: '/activity/read/all',
      method: 'PUT'
    });
  }
};
