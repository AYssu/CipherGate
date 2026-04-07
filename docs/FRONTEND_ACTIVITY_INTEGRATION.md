# 前端活动日志和消息功能集成说明

## 已完成的功能

### 1. 活动日志服务 (`frontend/src/services/activityService.ts`)

创建了活动日志 API 服务，包含以下接口：

```typescript
// 获取最近活动列表（用于首页展示）
activityApi.getRecentActivities(limit: number)

// 获取最近活动（分页）
activityApi.getRecentActivitiesPage(pageNum: number, pageSize: number)

// 获取用户最近活动
activityApi.getUserRecentActivities(userId: number, limit: number)
```

### 2. Dashboard 最近活动展示

在 `DashboardContent.tsx` 中集成了真实的活动日志数据：

- ✅ 自动获取最近10条活动记录
- ✅ 每30秒自动刷新
- ✅ 显示用户名、操作描述、时间、状态
- ✅ 相对时间显示（刚刚、X分钟前、X小时前等）
- ✅ 根据操作类型显示不同颜色的标签
- ✅ 加载状态提示

### 3. 消息通知图标

在 `MainLayout.tsx` 中添加了消息通知功能：

- ✅ 右上角显示消息图标（铃铛）
- ✅ 使用 Badge 组件显示未读消息数量
- ✅ 预留了获取未读消息数量的接口位置

## 数据展示效果

### 活动日志展示格式

```
● 管理员 执行了 登录操作
  2分钟前                    [成功]

● 用户001 执行了 更新用户信息
  15分钟前                   [成功]

● 系统管理员 执行了 删除角色
  1小时前                    [成功]
```

### 操作类型映射

| 后端类型 | 显示文本 | 标签颜色 |
|---------|---------|---------|
| LOGIN | 登录操作 | green |
| LOGOUT | 登出操作 | default |
| CREATE | 创建操作 | blue |
| UPDATE | 更新操作 | orange |
| DELETE | 删除操作 | red |
| VIEW | 查看操作 | cyan |

## 使用示例

### 在其他组件中使用活动日志 API

```typescript
import { activityApi, type ActivityLog } from '../services';

// 获取最近活动
const fetchActivities = async () => {
  try {
    const result = await activityApi.getRecentActivities(10);
    const activities = result.data;
    console.log(activities);
  } catch (error) {
    console.error('获取活动日志失败:', error);
  }
};

// 获取分页数据
const fetchActivitiesPage = async () => {
  try {
    const result = await activityApi.getRecentActivitiesPage(1, 20);
    const { records, total, current, pages } = result.data;
    console.log('总记录数:', total);
    console.log('当前页:', current);
    console.log('总页数:', pages);
  } catch (error) {
    console.error('获取活动日志失败:', error);
  }
};
```

## 测试步骤

1. **启动后端服务**
   ```bash
   cd CipherGate
   ./gradlew bootRun
   ```

2. **启动前端服务**
   ```bash
   cd frontend
   npm run dev
   ```

3. **测试活动日志**
   - 登录系统（会自动记录登录日志）
   - 访问 Dashboard 页面
   - 查看"最近活动"卡片，应该能看到登录记录
   - 执行一些操作（创建用户、更新角色等）
   - 刷新页面，查看新的活动记录

4. **测试消息图标**
   - 查看右上角的铃铛图标
   - 目前未读消息数为 0（因为还没有实现消息功能）

## 下一步计划

### 消息功能（待实现）

1. **后端**
   - 创建消息服务和 Controller
   - 实现发送消息、获取消息列表、标记已读等功能
   - 实现未读消息数量统计

2. **前端**
   - 创建消息服务 API
   - 实现消息列表弹窗
   - 点击铃铛图标显示消息列表
   - 实现消息已读/未读状态切换
   - 定时刷新未读消息数量

### 活动日志增强

1. 添加"查看全部"功能，跳转到完整的活动日志页面
2. 添加筛选功能（按用户、操作类型、时间范围）
3. 添加导出功能
4. 添加详细信息查看（IP地址、User-Agent等）

## 注意事项

1. 活动日志会自动记录，无需手动调用
2. 前端每30秒自动刷新一次活动列表
3. 时间显示为相对时间，更友好
4. 如果后端没有数据，会显示"暂无活动记录"
5. 消息功能的后端接口还需要实现

## API 响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "userId": 1,
      "username": "管理员",
      "actionType": "LOGIN",
      "actionTarget": "AUTHENTICATION",
      "actionDescription": "执行了登录操作",
      "ipAddress": "127.0.0.1",
      "userAgent": "Mozilla/5.0...",
      "status": "SUCCESS",
      "createdTime": "2026-04-07T14:10:22"
    }
  ]
}
```
