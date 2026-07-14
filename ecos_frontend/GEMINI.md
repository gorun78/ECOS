# Databridge (C2EOS-COSMOS) UI System Specification & Code Blueprint
> **For AI Agents (Claude-code, Gemini, Antigravity) & Human Engineers**
> This document defines the exact visual design system, typography standard, component blueprints, and design principles of the C2EOS metadata platform to ensure all future features are developed with complete consistency.

---

## 1. Design Philosophy: High-Density Cognitive Clarity (高密认知的严谨主义)
C2EOS (Cognitive Enterprise Operating System) is inspired by Palantir AIP and Foundry. The user interface prioritizes **craftsmanship, structural integrity, and physical density**. 
* **Zero Aesthetic Fluff (拒绝无用装饰)**: Do not decorate the screen with unnecessary tech-larping console logs, fake network green dots, or unrequested telemetry logs unless requested by users.
* **Density & Contrast**: Maintain clean typography hierarchy with high functional contrast. Keep the layouts tight, structured, and informative.
* **Integrated Themes**: Every screen must gracefully support 4 cohesive color themes (`slate-light`, `deep-space`, `cyber-terminal`, `royal-purple`) by binding components directly to theme styles (`styles.cardBg`, `styles.cardBorder`, etc.) from `useTheme()`.

---

## 2. Color Mapping & Theme Presets (主题与色彩矩阵)
Never hardcode raw Tailwind color styles (e.g., `bg-white` or `bg-slate-900`) for structural components. Instead, import `useTheme()` and fetch classes dynamically:

```tsx
import { useTheme } from "../components/ThemeContext";
const { styles } = useTheme();

<div className={`border ${styles.cardBorder} ${styles.cardBg} ${styles.cardText}`}>
```

### Theme Variable Tokens & Intended Purpose

| Token Property | `slate-light` (明亮石板) | `deep-space` (智蓝星空) | `cyber-terminal` (极客终端) | `royal-purple` (皇家幽紫) | Common Mapping Use Case |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `appBg` | `bg-slate-50/60` | `bg-[#0B0F19]` | `bg-[#020202]` | `bg-[#0F0C1B]` | Overall application backdrop |
| `appText` | `text-slate-800` | `text-slate-100` | `text-emerald-500` | `text-purple-100` | Global descriptive pages labels |
| `cardBg` | `bg-white` | `bg-[#141924]` | `bg-[#080808]` | `bg-[#1C1530]` | Structural cards / grid list items |
| `cardBorder` | `border-[#E2E8F0]` | `border-[#1E293B]` | `border-emerald-500/30` | `border-purple-900/40` | Container outlines & dividing rules |
| `cardText` | `text-slate-800` | `text-slate-100` | `text-emerald-400` | `text-purple-100` | Regular readable words & parameters |
| `cardTextMuted` | `text-slate-500` | `text-slate-400` | `text-emerald-650` | `text-purple-400` | Labels, timestamps, and annotations |
| `inputBg` | `bg-white` | `bg-[#1E2533]` | `bg-[#050505]` | `bg-[#251D3E]` | Interactive selectors & text areas |
| `inputBorder` | `border-[#E2E8F0]` | `border-[#2D3748]` | `border-emerald-610/40` | `border-purple-800/40` | Form outline state bounding box |

### Semantic Color Scale (语义化颜色表达)

To display statuses or critical feedback consistently:
* **Success (正常运行 / 成功)**: 
  * *Light*: `bg-emerald-50 text-emerald-700 border-emerald-200`
  * *Dark & Cyber/Purple*: `bg-emerald-500/10 text-emerald-500 border-emerald-500/20`
* **Warning (越限警告 / 待处理)**:
  * *Light*: `bg-amber-50 text-amber-700 border-amber-200`
  * *Dark & Cyber/Purple*: `bg-amber-500/10 text-amber-500 border-amber-500/20`
* **Danger (高危干预 / 错误)**:
  * *Light*: `bg-rose-50 text-rose-700 border-rose-200`
  * *Dark & Cyber/Purple*: `bg-rose-500/10 text-rose-500 border-rose-500/20`
* **Info / Blue (元数据 / 信息)**:
  * *Light*: `bg-indigo-50 text-indigo-700 border-indigo-200`
  * *Dark & Cyber/Purple*: `bg-indigo-500/10 text-indigo-400 border-indigo-500/20`

---

## 3. Typography Hierarchy Guide (温润严饰排版规范)
Fonts are mapped at `@theme` level in Tailwind (`src/index.css`) to prevent pixelated font scaling:
* **Sans-serif (UI Core Elements)**: `"Inter"`, ui-sans-serif (high legibility, neutral).
* **Monospace (Telemetry, Indices, SQL, Code Blocks)**: `"JetBrains Mono"`, ui-monospace (even rhythm, tech-logical code vibes).

### Text Sizes & Display Classes

```markdown
# UI Layer Text Hierarchy
├── 💎 System Large Page Title -> `font-bold text-xl tracking-tight text-slate-900 dark:text-white`
├── 📦 Card/Group Subsections  -> `font-semibold text-sm`
├── 📰 General Body Text       -> `text-xs leading-normal opacity-85`
├── ⏱️ Small Annotations/Logs  -> `font-mono text-[10px] tracking-wider uppercase opacity-60`
```

* **Always use `leading-relaxed` or `leading-normal`** for blocks of prose to keep text readable.
* **Always use `tracking-tight`** for large main titles to produce an elegant display card layout.
* **Always pair values dynamically**: If using a muted secondary descriptor, pair it with `font-mono text-[10px] uppercase font-bold text-slate-400`.

---

## 4. Layout Spacing & Grid System (布局空间与网格骨架)

To ensure cohesive layouts on different modules, we enforce a strict nested structure and layout rules:

### Standard Screen Envelope (通用屏幕页面包装)
All primary top-level views inside `src/pages/` must follow this nesting format to maximize viewport usability:

```tsx
export default function StandardPage() {
  const { styles } = useTheme();
  return (
    <div className="flex-grow overflow-y-auto p-6 font-sans">
      <div className="max-w-7xl mx-auto space-y-6">
        {/* Module Title Section */}
        {/* Bento Cards / Active Core KPI Grids */}
        {/* Dynamic Details: Columns or Charts */}
      </div>
    </div>
  );
}
```

### Gap Token Presets (间距档位定义)
* **Main Section Stack (`space-y-6`)**: Separates header banner, main grid row, and secondary table outputs.
* **Metrics Bento Cards Row (`grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4`)**: Consistently layouts 4 core KPIs.
* **Sub-Container inner elements (`space-y-4` / `gap-3`)**: Defines margin rules for form controls or vertical list items inside a card.

---

## 5. Icon System Specifications (图标一致性约束)
All interactive or informational symbols **MUST** be imported from `lucide-react`. Custom SVG code blocks are strictly forbidden.

### Size Mapping Context
* **Table Column Header / Actions Buttons Sub-elements**: `w-3 h-3` or `w-3.5 h-3.5` (keeps headers clean without overloading margins).
* **Standard Button Prefixes / Card Metadata Icons**: `w-4 h-4` (the gold-standard dimension).
* **Large Hero Indicators / Left Rail App Icons**: `w-5 h-5` (used for section landmarks).

```tsx
// Example layout inside buttons
<button className="flex items-center gap-1.5 ...">
  <RotateCw className="w-3.5 h-3.5" />
  <span>{t("wb.run.all")}</span>
</button>
```

---

## 6. Atomic Components Blueprint (典型原子级组件代码模型)

These standard styling frameworks can be safely copy-pasted and adapted by agents or humans during implementations:

### A. Primary Adaptive Card (主体层级容器卡片)
```tsx
<div className={`border ${styles.cardBorder} ${styles.cardBg} rounded-xl p-6 shadow-2xs space-y-4`}>
  <div className="flex items-center justify-between border-b border-dashed border-slate-200 dark:border-slate-800 pb-3">
    <h3 className="font-bold text-sm tracking-tight flex items-center gap-2">
      <LucideIcon className="w-4 h-4 text-indigo-500" />
      <span>区块标题</span>
    </h3>
  </div>
  <p className={`text-xs ${styles.cardTextMuted}`}>{description}</p>
</div>
```

### B. Input Elements (交互文本输入、选择框)
```tsx
<input 
  type="text" 
  value={textVal}
  onChange={onChange}
  className={`w-full text-xs p-1.5 px-3 rounded-lg border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} focus:outline-none focus:ring-1 focus:ring-indigo-500/50 transition`} 
  placeholder="输入占位文字..."
/>
```

### C. Standard Action Buttons (状态反馈完备的控制键)
Support both English and Chinese with standard disabled style adjustments.

```tsx
// 1. Primary Action Button (高亮行动)
<button 
  onClick={onClick}
  className="px-3 py-1.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg text-xs font-bold leading-none cursor-pointer transition disabled:opacity-50 h-8"
>
  确认提交 Action
</button>

// 2. Outline Neutral Button (空心灰选)
<button 
  onClick={onClick}
  className={`px-3 py-1.5 border ${styles.cardBorder} ${styles.cardText} hover:bg-black/5 dark:hover:bg-white/5 rounded-lg text-xs font-semibold leading-none cursor-pointer transition h-8`}
>
  取消/返回
</button>
```

---

## 7. Localization Protocol (全业务国际化协议)
Every user-facing label **MUST** support bilingual switching. Avoid injecting static language words directly into components. Always route them through `LanguageContext` `t()` mechanism.

* **Key Declarations**: Add keys to both `zh` and `en` matrices in `src/components/LanguageContext.tsx`.
* **Rendering Pattern**: Use `t("namespace.key")` for strings, or use `locale === "zh"` for situational layout variations (such as datetime formats, etc.).

```tsx
import { useLanguage } from "../components/LanguageContext";
const { t, locale } = useLanguage();

<h1>{t("wb.title")}</h1>
```

---

## 8. Anti-AI-Slop Code Directives (大模型编写安全边界)
When generating or editing app files, you must adhere to these mechanical principles to preserve code safety:
1. **Always Read-Modify-Write**: Always fetch existing code structure with `view_file` before making any assumptions or replacements.
2. **Never break existing imports**: Keep current relative routes (e.g. `../components/LanguageContext`) intact.
3. **Never write incomplete React structures**: Always close tags securely. Never leave messy code trails or dangling placeholders.
4. **Use `lucide-react` Only**: Never invent SVG code structures.
5. **Durable Persistence Strategy**: For user profiles or data assets, write full API integration calls. Use safe UI-fallback structures when server parameters are completing.

---
*Created and approved by Databridge Design Committee for C2EOS. Code well, architect wisely!*
