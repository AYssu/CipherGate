import request from '../utils/request';

export interface SystemMessage {
  id: number;
  messageType: string;
  title: string;
  content: string;
  importanceLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';
  targetType: string;
  targetId?: number;
  emailSent: boolean;
  emailSentTime?: string;
  createdTime: string;
  expireTime?: string;
  isRead: boolean;
}

export const messageApi = {
  /**
   * 获取当前用户的系统消息
   */
  getMyMessages: (limit: number = 10) => {
    return request<SystemMessage[]>({
      url: '/messages/my',
      method: 'GET',
      params: { limit }
    });
  },

  /**
   * 标记消息为已读
   */
  markAsRead: (id: number) => {
    return request<void>({
      url: `/messages/${id}/read`,
      method: 'PUT'
    });
  }
};
