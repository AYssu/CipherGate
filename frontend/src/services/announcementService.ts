import request from '../utils/request';

export interface SystemAnnouncement {
  id: number;
  title: string;
  content: string;
  status: number;
  createdBy: number;
  createdAt: string;
  updatedAt: string;
}

export const announcementApi = {
  getAnnouncements: () => {
    return request.get('/announcements');
  },

  getActiveAnnouncements: () => {
    return request.get('/announcements/active');
  },

  getAnnouncementById: (id: number) => {
    return request.get(`/announcements/${id}`);
  },

  createAnnouncement: (data: { title: string; content: string; status: number }) => {
    return request.post('/announcements', data);
  },

  updateAnnouncement: (id: number, data: { title: string; content: string; status: number }) => {
    return request.put(`/announcements/${id}`, data);
  },

  deleteAnnouncement: (id: number) => {
    return request.delete(`/announcements/${id}`);
  },
};
