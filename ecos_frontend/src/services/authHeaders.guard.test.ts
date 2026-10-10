// 详细设计-01 F01-10（M2）前端安全约束门禁：authHeaders 单实例防回归。
import { describe, it, expect } from "vitest";
import * as fs from "node:fs";
import * as path from "node:path";
import { authHeaders, authOnlyHeaders, getAuthToken } from "./auth";

const SRC_ROOT = path.resolve(__dirname, "..");
const AUTH_TS = path.resolve(__dirname, "auth.ts");

function walk(dir: string, out: string[] = []): string[] {
  for (const name of fs.readdirSync(dir)) {
    const p = path.join(dir, name);
    const st = fs.statSync(p);
    if (st.isDirectory()) walk(p, out);
    // 测试文件自身标题含 "function authHeaders" 字面量，扫描须排除 *.test/spec.*，否则自匹配
    else if (/\.(ts|tsx)$/.test(name) && !/\.(test|spec)\.(ts|tsx)$/.test(name)) out.push(p);
  }
  return out;
}

function countAuthHeaderDefinitions(): { file: string; count: number }[] {
  const files = walk(SRC_ROOT);
  const hits: { file: string; count: number }[] = [];
  // 只匹配"函数定义"，不匹配 import / 调用
  const defRe = /(?:function|const)\s+authHeaders\b/;
  for (const f of files) {
    const content = fs.readFileSync(f, "utf8");
    const m = content.match(defRe);
    if (m) hits.push({ file: path.relative(SRC_ROOT, f), count: 1 });
  }
  return hits;
}

describe("F01-10 authHeaders 单实例防回归", () => {
  it("全仓 `function authHeaders` 定义命中数必须 = 1 且落在 services/auth.ts", () => {
    const hits = countAuthHeaderDefinitions();
    expect(hits.length, `authHeaders 定义应全仓唯一，实际：${hits.map((h) => h.file).join(", ")}`).toBe(1);
    expect(hits[0].file.replace(/\\/g, "/")).toBe("services/auth.ts");
  });

  it("token 只在 auth.ts 单通道读取：无 localStorage 权限集落盘", () => {
    const content = fs.readFileSync(AUTH_TS, "utf8");
    // 禁把"权限/角色集合"写进 localStorage（只允许读 token 键）
    expect(content).not.toMatch(/localStorage\.setItem\(\s*["'][a-zA-Z_]*permission/i);
    expect(content).not.toMatch(/localStorage\.setItem\(\s*["'][a-zA-Z_]*role/i);
    expect(content).not.toMatch(/localStorage\.setItem\(\s*["'][a-zA-Z_]*clearance/i);
  });

  it("authHeaders 返回 Content-Type + Bearer token（有 token 时）", () => {
    localStorage.setItem("token", "tkt-123");
    const h = authHeaders();
    expect(h["Content-Type"]).toBe("application/json");
    expect(h["Authorization"]).toBe("Bearer tkt-123");
    localStorage.removeItem("token");
  });

  it("缺 token 时 authHeaders 无 Authorization 键（不回退假 token）", () => {
    localStorage.clear();
    sessionStorage.clear();
    const h = authHeaders();
    expect(h["Content-Type"]).toBe("application/json");
    expect(h["Authorization"]).toBeUndefined();
  });

  it("authOnlyHeaders：有 token 只带 Authorization；无 token 返回空对象", () => {
    localStorage.setItem("token", "tkt-9");
    expect(authOnlyHeaders()).toEqual({ Authorization: "Bearer tkt-9" });
    localStorage.removeItem("token");
    expect(authOnlyHeaders()).toEqual({});
  });

  it("getAuthToken 只读四键 union，绝不返回越权默认", () => {
    sessionStorage.clear();
    const empty = getAuthToken();
    expect(empty).toBe("");
    localStorage.setItem("jwt", "jwt-77");
    expect(getAuthToken()).toBe("jwt-77");
    localStorage.clear();
    localStorage.removeItem("jwt");
  });
});
