// @vitest-environment node
/**
 * 主题 token + 可访问性门禁（design-05 B.9-4 / B.7，前端规范 §七.7）：
 *  1) 认知域范围（scenario/cognition、aiworkbench/cognition、CausalGraphView.tsx）
 *     生产源码禁止硬编码 Tailwind 颜色字面量，颜色一律经 useTheme() token；
 *     （注：ThemeContext.tsx 自身是 token 定义地，不在扫描范围。）
 *  2) scenario/cognition 生产源码：JSX <button>/<select>/<input> 开标签必须携带
 *     aria-label（或 aria-labelledby）；Tab 壳必须具备 tablist/tab/tabpanel 语义
 *     与方向键 onKeyDown 键盘可达。
 *
 * 约定：JSX 开标签的收尾 `>`/`/>` 独占一行（本仓库现有格式），据此切分标签段。
 */
import { describe, it, expect } from "vitest";
import fs from "fs";
import path from "path";

// src 根：__dirname = src/pages/scenario/cognition/__tests__
const SRC = path.resolve(__dirname, "..", "..", "..", "..");

function listSourceFiles(dir: string, out: string[] = []): string[] {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) {
      if (e.name === "__tests__" || e.name === "node_modules") continue;
      listSourceFiles(p, out);
    } else if (/\.tsx?$/.test(e.name) && !e.name.includes(".test.")) {
      out.push(p);
    }
  }
  return out;
}

/** 粗略去注释：块注释 + 行注释 + docblock 续行（与 auth 守卫同款实现）。 */
function stripComments(code: string): string {
  return code
    .replace(/\/\*[\s\S]*?\*\//g, "")
    .split("\n")
    .filter((line) => !/^\s*\*/.test(line))
    .map((line) => line.replace(/\/\/.*$/g, ""))
    .join("\n");
}

const COLOR_SCOPE = [
  path.join(SRC, "pages", "scenario", "cognition"),
  path.join(SRC, "pages", "aiworkbench", "cognition"),
];
const COLOR_EXTRA = [path.join(SRC, "components", "CausalGraphView.tsx")];

const TAILWIND_COLOR =
  /\b(?:bg|text|border|ring|accent|from|to|via|fill|stroke|shadow)-(?:white|black|slate|gray|zinc|neutral|stone|red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose)(?:-[0-9]{1,3})?(?:\/[0-9]{1,3})?\b/g;

const ARIA_SCOPE_DIR = path.join(SRC, "pages", "scenario", "cognition");

describe("认知域主题 token 单源（B.9-4）", () => {
  const files = [
    ...COLOR_SCOPE.flatMap((d) => listSourceFiles(d)),
    ...COLOR_EXTRA.filter((f) => fs.existsSync(f)),
  ];

  it("扫描到范围内源文件", () => {
    expect(files.length).toBeGreaterThan(0);
  });

  it("非注释代码中不存在硬编码 Tailwind 颜色字面量", () => {
    const offenders: string[] = [];
    for (const file of files) {
      const code = stripComments(fs.readFileSync(file, "utf8"));
      let m: RegExpExecArray | null;
      TAILWIND_COLOR.lastIndex = 0;
      while ((m = TAILWIND_COLOR.exec(code))) {
        offenders.push(`${m[0]} @ ${path.relative(SRC, file)}`);
      }
    }
    expect(offenders, `存在硬编码颜色：\n${offenders.join("\n")}`).toEqual([]);
  });
});

describe("场景认知面板可访问性（B.7）", () => {
  const files = listSourceFiles(ARIA_SCOPE_DIR);

  it("扫描到 scenario/cognition 生产源文件", () => {
    expect(files.length).toBeGreaterThan(0);
  });

  it("每个 <button>/<select>/<input> 开标签携带 aria-label(ledby)", () => {
    const offenders: string[] = [];
    for (const file of files) {
      const code = stripComments(fs.readFileSync(file, "utf8"));
      for (const tag of ["button", "select", "input"] as const) {
        // 开标签：`<tag` 起，到独占一行的 `>` / `/>` 止
        const tagRe = new RegExp(`<${tag}\\b[\\s\\S]*?\\n\\s*/?>`, "g");
        let m: RegExpExecArray | null;
        while ((m = tagRe.exec(code))) {
          const openTag = m[0];
          if (!/aria-label(ledby)?=/.test(openTag)) {
            offenders.push(`<${tag}> 缺 aria-label @ ${path.relative(SRC, file)}`);
          }
        }
        // 防切分失效：<tag 出现次数应与可解析开标签数一致
        const rawCount = (code.match(new RegExp(`<${tag}\\b`, "g")) || []).length;
        const parsedCount = (code.match(new RegExp(`<${tag}\\b[\\s\\S]*?\\n\\s*/?>`, "g")) || []).length;
        expect(parsedCount, `切分异常：<${tag}> @ ${path.relative(SRC, file)}`).toBe(rawCount);
      }
    }
    expect(offenders, `缺可访问名：\n${offenders.join("\n")}`).toEqual([]);
  });

  it("Tab 壳具备 tablist/tab/tabpanel 语义与键盘可达", () => {
    const panel = stripComments(fs.readFileSync(path.join(ARIA_SCOPE_DIR, "CognitiveRightPanel.tsx"), "utf8"));
    expect(panel).toContain('role="tablist"');
    expect(panel).toContain('role="tab"');
    expect(panel).toContain('role="tabpanel"');
    expect(panel).toContain("onKeyDown");
    expect(panel).toContain("ArrowLeft");
    expect(panel).toContain("ArrowRight");
    expect(panel).toMatch(/tabIndex=\{isActive \? 0 : -1\}/);
  });

  it("置信度三色不依赖颜色单通道（含文字档位 i18n 键）", () => {
    const hyp = stripComments(fs.readFileSync(path.join(ARIA_SCOPE_DIR, "HypothesesTab.tsx"), "utf8"));
    expect(hyp).toContain("scenario.cognition.hyp.confidence.");
  });

  it("移动端（<768px）右栏折叠为抽屉（复用 useMobileSidebar）", () => {
    const panel = fs.readFileSync(path.join(ARIA_SCOPE_DIR, "CognitiveRightPanel.tsx"), "utf8");
    expect(panel).toContain("useMobileSidebar");
    expect(panel).toContain('role="dialog"');
    expect(panel).toContain('aria-modal="true"');
    expect(panel).toContain("scenario.cognition.openPanel");
    expect(panel).toContain("scenario.cognition.closePanel");
  });
});
