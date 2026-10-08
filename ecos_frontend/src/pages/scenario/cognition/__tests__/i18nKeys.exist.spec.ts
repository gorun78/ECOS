// @vitest-environment node
/**
 * i18n 键存在性门禁（design-05 B.9-3 / W138）：
 * 认知域页面（scenario/cognition + aiworkbench/cognition + CognitionPanel）
 * 里每个 **字面量** `t("ns.key")` 调用使用的键，必须在合并后的 locale 中存在，
 * 且不得再携带内联中文 fallback 第二参（`t("k","中文")`）。
 *
 * 动态/模板插值键（含 `${}`）无法静态解析，跳过。
 */
import { describe, it, expect } from "vitest";
import fs from "fs";
import path from "path";

// src 根：__dirname = src/pages/scenario/cognition/__tests__
const SRC = path.resolve(__dirname, "..", "..", "..", "..");
const LOCALES_DIR = path.join(SRC, "locales");
const PAGE_DIRS = [
  path.join(SRC, "pages", "scenario", "cognition"),
  path.join(SRC, "pages", "aiworkbench", "cognition"),
];
const EXTRA_FILES = [path.join(SRC, "pages", "scenario", "CognitionPanel.tsx")];

/** 合并全部 locale 文件得到扁平键集合（与 LanguageContext 的合并语义一致）。 */
function mergedKeys(): Set<string> {
  const keys = new Set<string>();
  for (const domain of fs.readdirSync(LOCALES_DIR)) {
    const dir = path.join(LOCALES_DIR, domain);
    if (!fs.statSync(dir).isDirectory()) continue;
    for (const f of fs.readdirSync(dir)) {
      if (!f.endsWith(".json")) continue;
      const obj = JSON.parse(fs.readFileSync(path.join(dir, f), "utf8")) as Record<string, unknown>;
      for (const k of Object.keys(obj)) keys.add(k);
    }
  }
  return keys;
}

/** 递归收集页面源文件（排除测试与 __tests__）。 */
function listSourceFiles(dir: string, out: string[] = []): string[] {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) {
      if (e.name === "__tests__") continue;
      listSourceFiles(p, out);
    } else if (/\.tsx?$/.test(e.name) && !e.name.includes(".test.")) {
      out.push(p);
    }
  }
  return out;
}

// 字面量 t("key" ...) ：key 仅点分字母数字；后随 , 或 )
const literalKeyRe = /t\(\s*(['"])([a-zA-Z0-9_.]+)\1\s*[,)]/g;
// 内联 fallback：t("key", "literal" | `literal`) —— 第二参为字符串字面量
const inlineFallbackRe = /t\(\s*(['"])([a-zA-Z0-9_.]+)\1\s*,\s*(?:"[^"]*"|'[^']*'|`[^`]*`)\s*\)/g;

describe("认知域 i18n 键存在性（W138 / B.9-3）", () => {
  const existing = mergedKeys();
  const files = [
    ...PAGE_DIRS.flatMap((d) => listSourceFiles(d)),
    ...EXTRA_FILES.filter((f) => fs.existsSync(f)),
  ];

  it("locale 合并键集合非空", () => {
    expect(existing.size).toBeGreaterThan(0);
  });

  it("每个字面量 t() 键都存在于 locale", () => {
    const missing: string[] = [];
    for (const file of files) {
      const txt = fs.readFileSync(file, "utf8");
      let m: RegExpExecArray | null;
      literalKeyRe.lastIndex = 0;
      while ((m = literalKeyRe.exec(txt))) {
        const key = m[2];
        if (!existing.has(key)) missing.push(`${key} @ ${path.relative(SRC, file)}`);
      }
    }
    expect(missing, `缺失的 i18n 键：\n${missing.join("\n")}`).toEqual([]);
  });

  it("认知域页面不再携带内联中文 fallback 第二参", () => {
    const offenders: string[] = [];
    for (const file of files) {
      const txt = fs.readFileSync(file, "utf8");
      let m: RegExpExecArray | null;
      inlineFallbackRe.lastIndex = 0;
      while ((m = inlineFallbackRe.exec(txt))) {
        offenders.push(`${m[2]} @ ${path.relative(SRC, file)}`);
      }
    }
    expect(offenders, `仍含内联 fallback：\n${offenders.join("\n")}`).toEqual([]);
  });
});
