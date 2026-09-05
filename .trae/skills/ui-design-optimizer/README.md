# UI Design Optimizer Skill

## 简介

通过系统化分析、优先级优化和证据驱动验证，将前端实现与 UI 设计规格对齐。本技能基于实际项目经验，解决设计与实现之间常见的视觉不一致问题，确保高保真还原。

## 使用场景

当遇到以下情况时，应调用此技能：
- 需要将前端代码与设计文件对齐
- 需要分析设计规格用于指导实现
- 需要验证设计还原的准确性
- 需要按优先级进行 UI 优化（P0/P1/P2）
- 需要修复 CSS 相关的构建或渲染问题

## 核心原则

基于真实项目经验，以下原则确保有效的设计优化：

### 原则 1：证据驱动分析（40% 失败原因）
- 为每个设计规格收集可验证的证据
- 记录所有颜色/值引用的文件路径和行号
- 生成带有可追溯来源的结构化分析报告
- **CRITICAL**：同时分析主题变量和组件实际使用，检测颜色冲突

### 原则 2：优先级驱动优化（30% 失败原因）
- 按影响和工作量分类调整项（P0/P1/P2）
- 先修复关键问题，再处理细节
- 记录非平凡决策的权衡
- 优化前解决颜色系统冲突

### 原则 3：全面验证（20% 失败原因）
- 在视觉对比前验证页面可访问性
- 验证 CSS 变量、计算样式和组件状态
- 使用自动化工具交叉检查证据准确性
- 跨多个设计文件交叉验证规格

### 原则 4：配置一致性（10% 失败原因）
- 保持 CSS 变量、Tailwind 配置和组件样式同步
- 使用 `:deep(:root)` 处理 Vue scoped 样式覆盖
- 记录设计与实现之间的命名差异
- 维护设计令牌与实现之间的颜色映射表

## 工具

### Python 脚本

**validate_report.py** - 自动化报告验证工具：

```bash
python .trae/skills/ui-design-optimizer/validate_report.py <report-file>
```

**功能**：
- 验证设计稿文件存在性和指定行的颜色值
- 验证代码文件存在性和指定行的颜色值
- 验证代码片段在附近上下文的匹配
- 支持简单和详细两种报告格式
- 生成验证统计和错误报告

**输出**：
- 每条分析记录的通过/失败状态
- 失败验证的详细错误列表
- 证据缺失的警告列表
- 成功返回退出码 0，失败返回非零

### 手动工具
- **设计分析**：Read 工具用于设计文件提取
- **代码搜索**：Grep 工具用于颜色值追踪
- **视觉对比**：浏览器 DevTools 检查计算样式
- **构建验证**：`npm run build` 检查编译错误

## 工作流程

### Phase 0：页面可访问性检查（必需）

**目标**：确保优化前页面可以正常访问和渲染

**步骤**：
1. 运行 `npm run build` 验证编译
2. 启动开发服务器并验证 HTTP 响应
3. 检查浏览器控制台的 JavaScript 错误
4. 确认所有资源加载成功

**输出**：
- 可访问性检查清单
- 构建验证状态

**必须验证**：
- ✅ 构建成功无错误
- ✅ 页面 HTTP 200 加载
- ✅ 无控制台错误
- ✅ 所有资源加载完成

### Phase 1：设计规格分析

**目标**：提取并记录所有设计规格，包括颜色冲突检测

**步骤**：
1. 读取设计主题文件（CSS、JSON、导出规格）- 提取主题变量
2. 读取设计页面组件（index.tsx、index.vue）- 提取实际颜色使用
3. **CRITICAL**：对比主题变量与组件使用，检测颜色冲突
4. 提取配色方案、排版、间距和组件规格
5. 与当前前端实现对比
6. **保存分析报告**到设计目录：`UI设计规格分析-{YYYYMMDD}.md`
7. 运行验证脚本：`python validate_report.py <report-file>`

**需要提取的关键规格**：
- 配色方案（主色、辅助色、成功、警告、错误）
- 排版系统（字体族、字号、行高）
- 间距系统（内边距、外边距、间隙）
- 组件尺寸（高度、宽度、圆角）
- 阴影规格
- 渐变定义
- **主题与组件之间的颜色冲突**

**输出**：
- 包含证据列的分析报告
- **颜色冲突检测章节**
- `validate_report.py` 验证报告

**必须验证**：
- ✅ 所有颜色值已记录来源
- ✅ 排版规格已提取
- ✅ 组件尺寸已记录
- ✅ **设计组件已分析实际颜色使用**
- ✅ **颜色冲突已检测和记录**
- ✅ 报告已保存到设计目录
- ✅ 验证通过无关键错误

### Phase 2：优先级优化

**目标**：按优先级实现变更

**P0 - 关键（高影响，低工作量）**：
- 主色调
- 背景色
- 核心颜色变量
- 基础字体大小

**P1 - 重要（中影响，中工作量）**：
- 按钮样式（高度、渐变、圆角）
- 卡片样式（阴影、圆角）
- 输入框样式
- 组件尺寸

**P2 - 精细（低影响，高工作量）**：
- 标签页样式（选中状态、悬停效果）
- 专用组件（扫描区域等）
- 动画细节
- 边界情况和响应式调整

**输出**：
- 更新后的 CSS 变量
- 修改后的组件样式
- 同步的 Tailwind 配置

**必须验证**：
- ✅ P0 变更已完成并验证
- ✅ P1 变更已完成并验证
- ✅ P2 变更已完成（如适用）

### Phase 3：验证

**目标**：验证实现与设计规格的一致性

**步骤**：
1. 运行浏览器验证脚本检查 CSS 变量
2. 检查关键组件的计算样式
3. 生成还原度报告
4. 运行 `validate_report.py` 确认证据有效性

**输出**：
- 验证 JSON 输出
- 还原度评分
- 更新的验证报告

**必须验证**：
- ✅ CSS 变量匹配设计规格
- ✅ 计算样式匹配预期值
- ✅ 所有组件渲染正确
- ✅ 验证脚本通过

## 分析报告模板

```markdown
## UI Design Specs Analysis

### Color System Conflict Detection (CRITICAL)

| Component | Color Used in Component | Theme Variable | Match | Resolution |
|-----------|------------------------|---------------|-------|------------|
| Button | #3370FF | --primary: #030213 | ❌ Mismatch | Use component color (#3370FF) |
| Tab Active | #3370FF | --primary: #030213 | ❌ Mismatch | Use component color (#3370FF) |
| Border | rgba(51,112,255,0.1) | --border: rgba(0,0,0,0.1) | ❌ Mismatch | Use component color |

### Color Palette Comparison

| Priority | Adjustment | Design Spec | Current | Difference | Design Source | Code Source | Code Snippet |
|----------|------------|-------------|---------|------------|--------------|-------------|--------------|
| P0 | Primary color | #3370FF | #030213 | 颜色不匹配 | index.tsx#L100 | style.css#L8 | `--primary: #030213` |
| P0 | Primary light | #4E83FD | #030213 | 颜色不匹配 | index.tsx#L105 | style.css#L9 | `--primary-light: #030213` |
| P0 | Background | #ffffff | #ffffff | ✅ 一致 | theme.css#L5 | style.css#L25 | `--bg-page: #ffffff` |

### Typography Comparison

| Priority | Adjustment | Design Spec | Current | Difference | Design Source | Code Source | Code Snippet |
|----------|------------|-------------|---------|------------|--------------|-------------|--------------|
| P1 | Body font size | 14px | 14px | ✅ 一致 | theme.css | style.css | `font-size: 14px` |
```

### 证据列定义

| 列名 | 描述 | 格式 |
|------|------|------|
| Design Source | 设计稿文件路径和行号 | `filename.css#Lxx` 或 `filename.tsx#Lxx` |
| Code Source | 前端代码路径和行号 | `filename.css#Lxx` |
| Code Snippet | 关键代码片段 | `--variable: value` 或 `color: #xxx` |

## 证据收集工作流

1. **提取设计主题规格**：
   ```
   使用 Read 工具读取设计主题文件：
   - file_path: <设计主题文件>（如 theme.css）
   - Extract: CSS 变量、颜色值、排版设置
   - Record: 每个规格的文件路径和行号
   ```

2. **提取设计组件颜色（CRITICAL）**：
   ```
   使用 Read 工具读取设计页面组件：
   - file_path: <设计组件文件>（如 index.tsx、index.vue）
   - Extract: 组件中实际使用的硬编码颜色值
   - Record: 每个颜色使用的文件路径和行号
   ```

3. **搜索设计组件中的颜色**：
   ```
   使用 Grep 工具搜索设计组件中的硬编码颜色：
   - pattern: "#[0-9A-Fa-f]{6}\|rgba\([0-9]+,\s*[0-9]+,\s*[0-9]+"
   - path: <设计目录>/src
   - output_mode: "content"
   - Record: 组件中实际使用的所有颜色值
   ```

4. **检测颜色冲突**：
   ```
   对比主题变量与组件颜色使用：
   - 主题变量：--primary: #030213
   - 组件使用：background: #3370FF
   - 如果不同：记录为冲突，优先使用组件颜色
   ```

5. **搜索前端代码**：
   ```
   使用 Grep 工具搜索匹配的颜色值：
   - pattern: <颜色值>（如 "#3370FF"）
   - path: <前端源代码目录>
   - output_mode: "content"
   - Record: 每个匹配的文件路径和行号
   ```

6. **读取代码上下文**：
   ```
   使用 Read 工具读取特定代码段：
   - file_path: <代码文件路径>
   - offset: <行号 - 2>
   - limit: 5
   - Extract: 显示当前实现的代码片段
   ```

7. **验证证据**：
   ```
   运行 validate_report.py 验证证据准确性：
   - python validate_report.py <报告文件> --design-dir <设计目录>
   - 修复识别的错误
   - 必要时重新生成报告
   ```

## 验证脚本

```javascript
const verify = () => {
  const results = {};
  
  // 检查 CSS 变量
  const root = getComputedStyle(document.documentElement);
  results.cssVariables = {
    '--primary': root.getPropertyValue('--primary').trim(),
    '--bg-page': root.getPropertyValue('--bg-page').trim(),
    '--border': root.getPropertyValue('--border').trim(),
    '--success': root.getPropertyValue('--success').trim(),
    '--warning': root.getPropertyValue('--warning').trim(),
    '--error': root.getPropertyValue('--error').trim()
  };
  
  // 检查元素样式
  const buttons = document.querySelectorAll('button');
  if (buttons.length > 0) {
    results.buttonStyles = {
      backgroundColor: getComputedStyle(buttons[0]).backgroundColor,
      borderRadius: getComputedStyle(buttons[0]).borderRadius,
      height: getComputedStyle(buttons[0]).height
    };
  }
  
  console.log(results);
};
verify();
```

## 设计还原检查清单

| 项目 | 设计稿规格 | 实际值 | 状态 | 验证证据 |
|------|-----------|--------|------|----------|
| 主色 | #3370FF | | ✅/❌ | `getComputedStyle(root).getPropertyValue('--primary')` |
| 主色浅色 | #4E83FD | | ✅/❌ | `getComputedStyle(root).getPropertyValue('--primary-light')` |
| 背景色 | #ffffff | | ✅/❌ | `getComputedStyle(body).backgroundColor` |
| 正文字号 | 14px | | ✅/❌ | `getComputedStyle(body).fontSize` |
| 按钮高度 | 38px | | ✅/❌ | `getComputedStyle(btn).height` |
| 卡片圆角 | 10px | | ✅/❌ | `getComputedStyle(card).borderRadius` |

## 还原度评分标准

| 得分 | 描述 |
|------|------|
| 90-100% | 优秀 - 所有关键元素与设计稿一致 |
| 70-89% | 良好 - 非关键元素存在轻微差异 |
| 50-69% | 一般 - 部分关键元素需要调整 |
| < 50% | 较差 - 需要大幅重新设计 |

## 常见问题及解决方案

### 问题 1：Vite/Rollup 构建错误

**错误信息**："Source phase import must be external"

**解决方案**：在 package.json 中锁定版本：
```json
{
  "vite": "5.2.10",
  "rollup": "4.6.1",
  "overrides": { "rollup": "4.6.1" }
}
```

### 问题 2：CSS 变量在 Scoped 样式中不生效

**问题**：Vue scoped 样式影响 CSS 变量作用域

**解决方案**：使用 `:deep(:root)` 进行组件级覆盖：
```css
:deep(:root) {
  --primary: #030213;
}
```

### 问题 3：标签页样式与设计稿不符

**问题**：实现使用底部边框，而设计稿要求白色背景+阴影

**解决方案**：
```css
.tab-item.active {
  background: #fff;
  box-shadow: 0px 0px 10px 0px rgba(3, 2, 19, 0.1);
  color: #030213;
}
```

### 问题 4：缺失原生依赖导致构建失败

**错误信息**："Cannot find module @rollup/rollup-win32-x64-msvc"

**解决方案**：
```bash
npm install @rollup/rollup-win32-x64-msvc --save-optional
```

## 最佳实践

### 1. 设计驱动开发
- 实现前提取所有设计规格
- 将规格记录在结构化报告中
- 按影响和工作量排序
- 每个阶段完成后验证

### 2. CSS 变量管理
- 在 `:root` 中集中定义变量
- 使用 `:deep(:root)` 处理 scoped 组件
- 保持 CSS 变量与 Tailwind 配置一致
- 使用 `@layer components` 封装可复用样式

### 3. 构建配置
- 锁定 Vite 和 Rollup 版本
- 使用 `overrides` 确保依赖版本一致
- 包含平台特定的原生模块

### 4. 跨平台兼容性
- 在 Windows 上注意 Node.js 原生模块依赖
- 使用标准 CSS 属性确保浏览器兼容性

## 参考资料

- [分析报告](file:///m:/code/dec/gov-window-assistant/docs/01需求分析/01-政务窗口助手/01-UI设计/UI设计规格分析-20260724.md)
- [Tailwind 配置](file:///m:/code/dec/gov-window-assistant/frontend/tailwind.config.js)
- [全局样式](file:///m:/code/dec/gov-window-assistant/frontend/src/style.css)
- [验证脚本](file:///m:/code/dec/gov-window-assistant/.trae/skills/ui-design-optimizer/validate_report.py)

---

**技能创建时间**：2026-07-23  
**基于项目**：gov-window-assistant 项目经验  
**版本**：1.4.0

**变更记录**：
- **v1.4.0** (2026-07-24): 添加设计稿颜色冲突检测（CRITICAL）
  - Phase 1 增加设计稿页面组件分析步骤
  - 增加颜色冲突检测工作流（主题变量 vs 组件实际使用）
  - 更新分析报告模板，增加冲突检测章节
  - 更新 `validate_report.py`，增加设计稿组件颜色搜索和冲突检测功能
  - 更新报告模板示例为蓝色系（#3370FF）
  - 增加颜色映射表维护原则
- **v1.3.0** (2026-07-24): 重构为符合标准技能规范
  - 添加 frontmatter：version、category
  - 添加基于实际失败分析的核心原则
  - 重构工作流为 Phase 0-3，包含明确目标
  - 为每个阶段添加 Must-verify 检查清单
  - 添加 Tools 部分文档化 validate_report.py
  - 标准化术语和格式
- **v1.2.0** (2026-07-24): 添加报告验证脚本
  - 创建 `validate_report.py` 用于自动化证据验证
  - 支持简单和详细两种报告格式
  - 验证设计/代码文件存在性和颜色匹配
  - 提供详细的错误和警告输出
- **v1.1.0** (2026-07-24): 添加证据收集支持
  - 扩展分析报告模板，增加证据列
  - 添加证据收集工作流
  - 添加验证证据格式和 JSON 输出
- **v1.0.0** (2026-07-23): 初始版本
  - 基础设计优化工作流
  - 优先级调整（P0/P1/P2）
  - 分析报告生成和保存
