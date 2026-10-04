// @vitest-environment node
import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { readdirSync, statSync } from "node:fs";
import path from "node:path";

// C181/W199 (M1·B)：Git/版本 UI 全量走 services/gitService.ts 单通道（ADR-12 / 铁律 #6）。
// 违规点判定：任何 .ts/.tsx 文件（除 gitService.ts 本身）出现 `apiFetchData` 或 `fetch(` 直接
// 拉 `/api/v1/ecos/git/*` 端点即视为绕过单通道。
const SRC = path.resolve(__dirname, "..");

function walk(dir: string, out: string[] = []): string[] {
  for (const entry of readdirSync(dir)) {
    if (entry === "node_modules" || entry.startsWith(".")) continue;
    const full = path.join(dir, entry);
    const st = statSync(full);
    if (st.isDirectory()) walk(full, out);
    else if (/\.tsx?$/.test(entry)) out.push(full);
  }
  return out;
}

const files = walk(SRC);

describe("gitService 单通道 (C181/W199)：/api/v1/ecos/git/* 端点必须在 gitService.ts 内单源", () => {
  it("仅 gitService.ts 直接引用 /api/v1/ecos/git/* 端点（其余文件通过 import 走）", () => {
    const violates: string[] = [];
    const re = /["'`]\/api\/v1\/ecos\/git\/[A-Za-z0-9_\-]+/;
    for (const f of files) {
      const name = path.basename(f);
      if (name === "gitService.ts") continue;
      const src = readFileSync(f, "utf8");
      // 过滤行内以注释字首 `*` 或 `//` 开头的文档说明（允许栏外引用端点作说明）
      src.split(/\r?\n/).forEach((line, i) => {
        const trimmed = line.trim();
        if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) return;
        if (re.test(line)) violates.push(`${path.relative(SRC, f)}:${i + 1}`);
      });
    }
    expect(violates).toEqual([]);
  });

  it("gitService.ts 导出 push/pull/postAction 三个补充入口（C181 补齐 GitPanel 曾用 raw fetch 的动作集）", () => {
    const src = readFileSync(path.join(SRC, "services/gitService.ts"), "utf8");
    // 用 new RegExp(str) 避免 TS 解析器把字面 `/export\s+function\s+push\s*</` 里的 `<` 误判成类型断言起符
    expect(src).toMatch(new RegExp("export\\s+function\\s+push\\s*<"));
    expect(src).toMatch(new RegExp("export\\s+function\\s+pull\\s*<"));
    expect(src).toMatch(new RegExp("export\\s+function\\s+postAction\\s*<"));
  });

  it("postAction 有 action 白名单校验（防绕过单通道注入任意路径）", () => {
    const src = readFileSync(path.join(SRC, "services/gitService.ts"), "utf8");
    const m = src.match(/export\s+function\s+postAction[\s\S]*?\n\}/);
    expect(m).not.toBeNull();
    const body = m![0];
    expect(body).toMatch(/\/\^\[a-z\]|\[\^a-z\]/i);
    expect(body).toMatch(/throw\s+new\s+Error/);
  });

  it("GitPanel.tsx 的 Git 动作 switch 已改走 gitService 函数（无 raw 'push'/'pull' 直 fetch 残留）", () => {
    const p = path.join(SRC, "components/data-workbench/GitPanel.tsx");
    const src = readFileSync(p, "utf8");
    expect(src).toMatch(/from\s+['"]\.\.\/\.\.\/services\/gitService['"]/);
    const directFetchGit = /apiFetchData\(\s*[`'"][^`'"]*\/api\/v1\/ecos\/git\//;
    expect(src).not.toMatch(directFetchGit);
  });
});
