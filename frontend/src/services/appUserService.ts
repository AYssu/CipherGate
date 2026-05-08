import request from '../utils/request';

// 应用终端用户类型定义
export interface AppUser {
  id: number;
  appId: number;
  username: string;
  email?: string;
  phone?: string;
  nickname?: string;
  avatarUrl?: string;
  signature?: string;
  loginCount: number;
  lastLoginAt?: string;
  lastLoginIp?: string;
  lastLoginIpRegion?: string;
  /** 最后 WS 登录上报的 deviceId */
  lastDeviceId?: string;
  createdAt: string;
  updatedAt: string;
  appName?: string;
  creatorType?: 'SELF' | 'AGENT';
  agentDisplayName?: string;
  bindingCount?: number;
  /** 当前是否有第三方 WS 会话（服务端内存，多机未聚合） */
  wsOnline?: boolean;
  wsSessionCount?: number;
  wsEarliestConnectedAtEpochMs?: number;
  /** 相对最早会话的在线秒数 */
  wsOnlineSeconds?: number;
  /** 当天累计在线秒数（含当前在线片段） */
  wsTodayOnlineSeconds?: number;
  /** 会员到期时间 */
  memberExpiresAt?: string;
  /** 当前是否在会员有效期内 */
  memberActive?: boolean;
  /** 是否已申请过试用 */
  trialApplied?: boolean;
  /** 试用到期时间 */
  trialExpiresAt?: string;
  /** 当前试用是否有效 */
  trialActive?: boolean;
  /** 是否存在已封禁的设备绑定（任一条） */
  isBanned?: boolean;
}

export interface AppUserDTO {
  id?: number;
  appId: number;
  username: string;
  email?: string;
  phone?: string;
  password?: string;
  nickname?: string;
  avatarUrl?: string;
  signature?: string;
  memberExpiresAt?: string;
}

export interface AppUserQueryDTO {
  appId?: number;
  username?: string;
  keyword?: string;
  email?: string;
  phone?: string;
  nickname?: string;
  /** true=已封禁，false=正常 */
  banned?: boolean;
  /** ACTIVE=未到期，EXPIRED=已到期，NONE=未开通 */
  memberStatus?: 'ACTIVE' | 'EXPIRED' | 'NONE';
  /** true=在线，false=离线（单机内存） */
  wsOnline?: boolean;
  current?: number;
  size?: number;
}

// 获取终端用户列表
export const getAppUserList = (params: AppUserQueryDTO) => {
  return request.get('/app-users', { params });
};

// 获取终端用户详情
export const getAppUserById = (id: number) => {
  return request.get(`/app-users/${id}`);
};

// 创建终端用户
export const createAppUser = (data: AppUserDTO) => {
  return request.post('/app-users', data);
};

// 更新终端用户
export const updateAppUser = (id: number, data: AppUserDTO) => {
  return request.put(`/app-users/${id}`, data);
};

// 删除终端用户
export const deleteAppUser = (id: number) => {
  return request.delete(`/app-users/${id}`);
};

// 重置用户密码
export const resetPassword = (id: number, newPassword: string) => {
  return request.post(`/app-users/${id}/reset-password`, { newPassword });
};

// 封禁/解封用户
export const banUser = (id: number, ban: boolean, reason?: string, bindingId?: number) => {
  return request.post(`/app-users/${id}/ban`, { ban, reason, bindingId });
};

// 强制下线终端用户 WS（CloseStatus=MEMBER_EXPIRED）
export const kickAppUserWs = (id: number) => {
  return request.post(`/app-users/${id}/kick-ws`);
};

export interface BatchIdsDTO {
  ids: number[];
}

export interface BatchBanDTO extends BatchIdsDTO {
  ban: boolean;
  reason?: string;
}

export interface AppUserAppNotExpiredDurationDTO {
  appId: number;
  amount: number;
  unit: Exclude<MemberExtendUnit, 'PERMANENT'>;
}

// 应用用户绑定类型定义
export interface AppUserBinding {
  id: number;
  appId: number;
  userId: number;
  bindType: string;
  licenseKeyId?: number;
  deviceId: string;
  deviceName?: string;
  deviceOs?: string;
  deviceIp?: string;
  expiresAt?: string;
  firstBindAt?: string;
  lastActiveAt?: string;
  useCount: number;
  unbindCount: number;
  isTrial?: boolean;
  trialExpiresAt?: string;
  allowUnbind?: boolean;
  isBanned?: boolean;
  banReason?: string;
  banAt?: string;
  remark?: string;
  status: number;
  createdAt: string;
  updatedAt: string;
  username?: string;
  licenseKeyCode?: string;
}

export interface AppUserBatchExtendMemberDTO {
  ids: number[];
  days: number;
}

export interface AppUserBatchExtendMemberFailItem {
  id: number;
  username?: string;
  reason: string;
}

export interface AppUserBatchExtendMemberResultDTO {
  successCount: number;
  failCount: number;
  failures: AppUserBatchExtendMemberFailItem[];
}

// 获取用户绑定设备列表
export const getUserBindings = (userId: number, current = 1, size = 10) => {
  return request.get(`/app-users/${userId}/bindings`, { 
    params: { current, size } 
  });
};

// 解绑用户设备
export const unbindDevice = (userId: number, bindingId: number, reason?: string) => {
  return request.delete(`/app-users/${userId}/bindings/${bindingId}`, {
    data: { reason }
  });
};

/** 延长会员天数（在「当前时间」与「原到期时间」中较晚者基础上累加） */
export const extendMemberDays = (id: number, days: number) => {
  return request.post(`/app-users/${id}/extend-member`, { days });
};

/** 批量延长会员天数（按天累加到到期时间） */
export const batchExtendMemberDays = (data: AppUserBatchExtendMemberDTO) => {
  return request.post('/app-users/batch-extend-member', data);
};

export type MemberExtendUnit = 'MINUTE' | 'HOUR' | 'DAY' | 'WEEK' | 'MONTH' | 'YEAR' | 'PERMANENT';

export interface ExtendMemberDurationDTO {
  amount?: number;
  unit: MemberExtendUnit;
}

export interface AppUserBatchExtendMemberDurationDTO {
  ids: number[];
  amount?: number;
  unit: MemberExtendUnit;
}

/** 按单位延长会员（分钟/小时/天/周/月/年/永久） */
export const extendMemberDuration = (id: number, body: ExtendMemberDurationDTO) => {
  return request.post(`/app-users/${id}/extend-member-duration`, body);
};

/** 批量按单位延长会员（分钟/小时/天/周/月/年/永久） */
export const batchExtendMemberDuration = (body: AppUserBatchExtendMemberDurationDTO) => {
  return request.post('/app-users/batch-extend-member-duration', body);
};

/** 批量按单位扣时（不支持永久） */
export const batchSubtractMemberDuration = (body: AppUserBatchExtendMemberDurationDTO) => {
  return request.post('/app-users/batch-subtract-member-duration', body);
};

/** 批量下线 WS（CloseStatus=MEMBER_EXPIRED） */
export const batchKickAppUserWs = (body: BatchIdsDTO) => {
  return request.post('/app-users/batch-kick-ws', body);
};

/** 批量封禁/解禁（封禁会踢线） */
export const batchBanAppUsers = (body: BatchBanDTO) => {
  return request.post('/app-users/batch-ban', body);
};

/** 批量删除 */
export const batchDeleteAppUsers = (body: BatchIdsDTO) => {
  return request.post('/app-users/batch-delete', body);
};

/** 选中应用：未到期会员批量加时 */
export const extendNotExpiredInApp = (body: AppUserAppNotExpiredDurationDTO) => {
  return request.post('/app-users/extend-not-expired-in-app', body);
};

/** 选中应用：未到期会员批量扣时 */
export const subtractNotExpiredInApp = (body: AppUserAppNotExpiredDurationDTO) => {
  return request.post('/app-users/subtract-not-expired-in-app', body);
};

/** 直接设置或清空会员到期；memberExpiresAt 为 null 表示清空 */
export const setMemberExpiresAt = (id: number, memberExpiresAt: string | null) => {
  return request.put(`/app-users/${id}/member-expires`, { memberExpiresAt });
};
