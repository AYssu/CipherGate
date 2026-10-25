/** JSON-backed configuration remains extensible while callers must narrow individual values. */
export type JsonValue = null | boolean | number | string | JsonValue[] | { [key: string]: JsonValue };
export type JsonObject = { [key: string]: JsonValue };
export interface PageResult<T> { records: T[]; total: number; size: number; current: number; pages: number }
export interface MembershipLevel { id: number; level: number; levelName: string; price: number; appQuota: number; licenseQuota: number; userRegisterQuota: number; trafficQuota: number; durationDays: number; description: string; sortOrder: number; status: number }
export interface UserMembership {
 id: number; userId: number; levelId: number; appUsed: number; licenseUsed: number; userRegisterUsed: number; trafficUsed: number;
 extraAppQuota: number; extraLicenseQuota: number; extraUserRegisterQuota: number; extraTrafficQuota: number; balance: number;
 inviteCode: string; invitedBy: number | null; inviteCount: number; memberExpiresAt: string | null; lastCheckinDate: string | null;
 consecutiveCheckinDays: number; totalCheckinDays: number; createdAt: string; level?: MembershipLevel;
}
export interface UserMembershipSummary extends UserMembership { levelName: string; userName: string; appTotal: number; licenseTotal: number; userRegisterTotal: number; trafficTotal: number }
export interface QuotaProduct { id: number; productCode: string; productName: string; productType: string; quotaValue: number; price: number; description: string; sortOrder: number; status: number }
export interface PaymentOrder { id: number; orderNo: string; userId: number; productId: number; productType: string; productName: string; quantity: number; totalAmount: number; payAmount: number; paymentChannel: string; status: number; createdAt: string; paidAt: string | null }
export interface BalanceTransaction { id: number; transactionType: string; amount: number; balanceAfter: number; description: string; createdAt: string }
export interface Ticket { id: number; ticketNo: string; userId: number; title: string; category: string; priority: number; status: number; assignedTo: number | null; userName: string | null; assigneeName: string | null; createdAt: string; lastReplyAt: string | null }
export interface TicketMessage { id: number; ticketId: number; senderId: number; senderType: string; senderName: string | null; content: string; imageUrls: string | null; isRead: boolean; createdAt: string }
export interface CheckinStatus { checkedIn: boolean; consecutiveDays: number; totalDays: number }
export interface CheckinReward { type: string; amount: number; unit: string; amountMB?: number }
export interface CheckinResult extends CheckinStatus { reward: CheckinReward; rewardType: string }
export interface InviteStats { enabled?: boolean; inviteCode?: string; inviteCount?: number; invitedBy?: number | null; maxInviteCount?: number; rewardAmount?: number; totalReward?: number }
export interface InviteRecord { id: number; inviterId: number; inviteeId: number; rewardAmount: number; rewardGranted: boolean; createdAt: string }
