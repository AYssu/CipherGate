/** Portal response contracts mirror the server DTOs and deliberately exclude password hashes. */
export interface PortalAppInfo { appId: number; appName: string; memberActive?: boolean; memberExpiresAt?: string | null }
export interface PortalCaptcha { captchaId: string; image: string }
export interface PortalLoginInput { email: string; password: string; captchaCode: string; captchaId: string }
export interface PortalLoginResult { token: string; apps: PortalAppInfo[]; needSelectApp: boolean }
export interface PortalPasswordReset { email: string; verifyCode: string; appId: number; newPassword: string }
export interface PortalPasswordChange { oldPassword: string; newPassword: string }
export interface PortalEmailChange { currentPassword: string; newEmail: string; verifyCode: string }
export interface PortalProfile { id: number; email: string; username: string; nickname: string | null; avatarUrl: string | null; createdAt: string | null }
export interface PortalDashboardStats {
  totalLoginCount: number; portalLoginCount: number; appLoginCount: number; boundDeviceCount: number;
  lastLoginIp: string | null; lastLoginIpRegion: string | null; lastLoginAt: string | null;
  loginTrend: Array<{ date: string; count: number }>; ipDistribution: Array<{ name: string; value: number }>;
}
export interface PortalLoginLog { id: number; loginIp: string | null; ipRegion: string | null; status: string; createdAt: string }
export interface PortalDevice { id: number; deviceId: string; createdAt: string }
export interface PortalMembershipInfo {
  appId: number; memberExpiresAt: string | null; memberStatus: 'ACTIVE' | 'EXPIRED' | 'NONE' | 'TRIAL';
  memberActive: boolean; loginCount: number | null; lastLoginAt: string | null;
  trialActive: boolean; trialExpiresAt: string | null; trialApplied: boolean;
}
export interface PortalPricingPlan { id: number; planName: string; planType: string; durationDays: number | null; priceFen: number; sortOrder: number }
export interface PortalCreatedOrder { orderNo: string; payUrl: string; amount: number; planName: string }
export interface PortalOrder { id: number; orderNo: string; planName: string | null; amountFen: number; status: number; createdAt: string; paidAt: string | null }
export interface PortalEpayConfig { appId: number; epayUrl: string; epayPid: string; epayKey?: string; notifyUrl: string | null; returnUrl: string | null; enabled: boolean }
