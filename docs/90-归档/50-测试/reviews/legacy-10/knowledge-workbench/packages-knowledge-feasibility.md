# packages/knowledge 独立 npm 包 ROI 评估

> 来源：肖国荣 | 日期：2026-09-12 | 批次 E-C
> 结论：**DON'T** — 当前单消费方 + i18n 紧绑，拆包负收益。

## 一、核心证据（4 条）

1. **复用场景 = 0（决定性反证）**
   - `knowledgeApi.` 在 `src/` 非 knowledge 路径下 **0 命中**（Grep 全量扫描）
   - `import ... from '.../knowledge/services/knowledgeApi'` 全仓 **0 命中**
   - 15 个 Tab 全部位于 `src/pages/knowledge/tabs/` 域内部；跨域工作台（data / ontology / cognitive / agent）via `knowledge` 关键字命中 49 文件，但均为**路由/标题字符串**、非 API 消费

2. **i18n 与 SPA 紧绑（拆包必破不变式）**
   - `src/components/LanguageContext.tsx` 直接 `import` 7 个 domain JSON 字面量展开进同一 `TRANSLATIONS` 对象，`knowledgeZh/knowledgeEn` 是硬编码静态 import
   - 拆包若迁出 `locales/knowledge/*`，需改 LanguageContext + 引入运行时注册机制，直接破 **i18n 1:1 不变式**

3. **Vite alias 已覆盖跨页面使用**
   - `vite.config.ts:13` `'@': path.resolve(__dirname, '.')` + `tsconfig.json:18-21` `"@/*": ["./*"]`
   - 即便未来第 2 个消费方在 monorepo 内，`@/pages/knowledge/...` 已可直接 import，无需包化

4. **非 monorepo 现状（拆包需另建 workspace）**
   - `pnpm-workspace.yaml` 仅有 `allowBuilds:`（pnpm v10 build script 白名单），**无 `packages:` 字段**
   - 拆包需新增 workspace root + `packages/knowledge/{package.json,vite.config.ts,tsconfig.json}` + 改 `ecos_frontend/package.json` + script 联动，影响 `npm run lint/test/dev/build` 全部入口

## 二、成本估算

| 路径 | 人天 | 说明 |
|:--|:--:|:--|
| **DON'T（推荐）** | ~0 | 无代码变更 |
| 纯 lib 包（Vite 内 ESBuild 解析） | ~3 | 新建包（src/index.ts + @ecos/knowledge + vite lib 模式 + tsconfig）+ 15 Tab 改 import + **i18n 重组**（迁出 knowledgeZh/knowledgeEn，LanguageContext 改运行时注册）+ 测试迁移 + 回归 |
| pnpm monorepo 化 | ~5+ | 上项 + workspace root + 8 个 npm script 联动 + Gate 脚本 / CI 适配 |

## 三、若未来需要（触发条件 + 微路径）

- **触发条件**：出现 **2 个独立 consumer**（分仓 admin portal / 移动端 / 跨 monorepo 管理台），或单一包需独立发版
- **推荐微路径**：① 先抽 `src/lib/knowledgeApi/` + `src/types/knowledge/` 为纯函数+类型（不依赖 `apiFetchData`，注入 fetch 端点）；② 当第 2 个 consumer 出现时升级为 `packages/knowledge` pnpm workspace，i18n key 同步迁出并引入 `registerLocale(domain, zh, en)` 运行时注册，LanguageContext 改订阅

## 四、交付声明

**无代码变更，仅 1 个 md 交付。** 不动 KnowledgeView / 15 Tab / knowledgeApi.ts / LanguageContext.tsx / i18n / vite.config.ts / tsconfig.json / pnpm-workspace.yaml。
