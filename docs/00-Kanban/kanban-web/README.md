# ECOS Kanban - 看板管理平台

基于 Vue 3 + TypeScript + Element Plus 的看板管理前端。

## 功能

- **登录页** — 用户认证（Mock模式，任意密码可登录）
- **看板列表** — 展示所有看板，支持新建看板（3列/4列/5列/自定义模板）
- **看板主页面** — 列+卡片展示，支持拖拽移动卡片、搜索筛选、添加卡片/列
- **卡片详情面板** — 右侧滑出抽屉，编辑标题/描述/负责人/截止日期/优先级/标签/子任务，评论，活动日志
- **看板设置页** — 基本信息编辑、列管理、成员权限管理（Admin/Member/Viewer）

## 技术栈

- Vue 3 + Composition API + `<script setup>`
- TypeScript（严格类型）
- Element Plus（UI组件库）
- Pinia（状态管理）
- Vue Router 4（路由）
- Vite（构建工具）
- Mock数据先行（USE_MOCK = true），后端就绪后切换

## 快速启动

```bash
cd kanban-web
npm install
npm run dev
```

访问 `http://localhost:5173`

## 构建

```bash
npm run build
```

输出在 `dist/` 目录。

## Mock账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| zhangsan | 任意 | 看板管理员 |
| lisi | 任意 | 成员 |
| wangwu | 任意 | 成员 |
| zhaoliu | 任意 | 观察者 |
| admin | 任意 | 系统管理员 |
| sunqi | 任意 | 成员（已禁用） |

## 项目结构

```
src/
├── api/                    # API请求层
│   ├── mock/data.ts        # Mock数据
│   ├── request.ts          # 请求工具
│   ├── boards.ts           # 看板API
│   ├── cards.ts            # 卡片API
│   ├── columns.ts          # 列API
│   └── users.ts            # 用户API
├── components/
│   └── board/
│       └── CardDetailDrawer.vue  # 卡片详情面板
├── layouts/
│   └── DefaultLayout.vue   # 主布局（侧边栏+内容区）
├── stores/
│   ├── auth.ts             # 认证状态
│   └── board.ts            # 看板状态
├── types/                  # TypeScript类型定义
│   ├── api.ts / board.ts / card.ts / column.ts / user.ts
├── views/
│   ├── login/LoginView.vue
│   ├── dashboard/DashboardView.vue
│   └── board/BoardView.vue, BoardSettingsView.vue
├── router/index.ts
├── App.vue
├── main.ts
└── style.css
```

## 接口对接

当前使用Mock数据模式（`USE_MOCK = true`）。后端就绪后：

1. 将 `src/api/*.ts` 中的 `USE_MOCK` 改为 `false`
2. 确保后端 API 路径与架构文档一致（`/api/v1/...`）
3. 配置 `vite.config.ts` 中的 proxy 指向后端服务
