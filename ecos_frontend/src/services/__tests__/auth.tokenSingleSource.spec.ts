// @vitest-environment node
/**
 * auth token 单源门禁（design-05 B.9-2 / W138 X-51）：
 * 认知/知识域等前端源码里禁止直读 `localStorage.getItem('token')` 之类的
 * token 键；一律经 `services/auth.ts#getAuthToken()`。本用例扫描全量页面/
 * 组件/服务源文件，剔除注释与 `auth.ts` 自身后断言零直读。
 */
import { describe, it, expect } from "vitest";
import fs from "fs";
import path from "path";

const SRC = path.resolve(__dirname, "..", "..");

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

/** 粗略去注释：删块注释 /* ... *\/ 与行注释（// 起始、以及 docblock 的 * 行）。 */
function stripComments(code: string): string {
  return code
    .replace(/\/\*[\s\S]*?\*\//g, "") // 块注释（含 JSDoc）
    .split("\n")
    .filter((line) => !/^\s*\*/.test(line)) // 残留的 docblock 续行
    .map((line) => line.replace(/\/\/.*$/g, "")) // 行尾 // 注释
    .join("\n");
}

const TOKEN_DIRECT_READ = /localStorage\s*\.\s*getItem\s*\(\s*['"](?:token|accessToken|jwt)['"]\s*\)/g;

describe("auth token 单源（X-51 / B.9-2）", () => {
  const authSelf = path.join(SRC, "services", "auth.ts");
  const files = listSourceFiles(SRC).filter((f) => path.resolve(f) !== authSelf);

  it("至少扫描到源文件", () => {
    expect(files.length).toBeGreaterThan(0);
  });

  // 全 src 同步扫描在并行 worker + 本机负载下会超 vitest 默认 5s（实测 6.2s 被误判 fail），显式放宽。
  it("非注释代码中不存在 localStorage token 键直读", () => {
    const offenders: string[] = [];
    for (const file of files) {
      const code = stripComments(fs.readFileSync(file, "utf8"));
      let m: RegExpExecArray | null;
      TOKEN_DIRECT_READ.lastIndex = 0;
      while ((m = TOKEN_DIRECT_READ.exec(code))) {
        offenders.push(`${m[0]} @ ${path.relative(SRC, file)}`);
      }
    }
    expect(offenders, `存在直读：\n${offenders.join("\n")}`).toEqual([]);
  }, 30_000);
});

/**
 * B.9-2 收口强化：本次治理范围内的目录（认知域页面 + services + api）不仅禁止
 * getItem 直读，任何对 token|accessToken|jwt 键的 getItem/setItem/removeItem
 * （含 sessionStorage）都必须经由 services/auth.ts（getAuthToken/clearAuthToken）。
 * auth.ts 自身、__tests__、*.test.* 除外。
 */
const SCOPE_DIRS = [
  path.join(SRC, "services"),
  path.join(SRC, "api"),
  path.join(SRC, "pages", "scenario", "cognition"),
];

const TOKEN_STORAGE_TOUCH =
  /(localStorage|sessionStorage)\s*\.\s*(getItem|setItem|removeItem)\s*\(\s*['"](?:token|accessToken|jwt)['"]/g;

describe("auth token 存储操作单源（B.9-2 范围收口）", () => {
  const authSelf = path.join(SRC, "services", "auth.ts");
  const scoped: string[] = [];
  for (const dir of SCOPE_DIRS) {
    if (fs.existsSync(dir)) listSourceFiles(dir, scoped);
  }
  const files = scoped.filter((f) => path.resolve(f) !== authSelf);

  it("范围内至少扫描到源文件", () => {
    expect(files.length).toBeGreaterThan(0);
  });

  it("范围内不存在 token 键的直接存储读写", () => {
    const offenders: string[] = [];
    for (const file of files) {
      const code = stripComments(fs.readFileSync(file, "utf8"));
      let m: RegExpExecArray | null;
      TOKEN_STORAGE_TOUCH.lastIndex = 0;
      while ((m = TOKEN_STORAGE_TOUCH.exec(code))) {
        offenders.push(`${m[0]} @ ${path.relative(SRC, file)}`);
      }
    }
    expect(offenders, `存在直接存储操作：\n${offenders.join("\n")}`).toEqual([]);
  }, 30_000);
});
