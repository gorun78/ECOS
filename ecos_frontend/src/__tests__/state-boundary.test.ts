// @vitest-environment node
import { describe, it, expect } from "vitest";
import { readFileSync, readdirSync, statSync } from "node:fs";
import path from "node:path";

// C195/W213 (M1·B) 状态管理分工（分册08 §3.3）：
//   服务端态 → @tanstack/react-query；客户端态 → zustand（跨组件 UI 态）；会话/导航 → URL+sessionStorage。
// 红线：zustand store 里不得出现"拉服务端数据"的动作（fetch / apiFetch / axios / 直接解包 .data），
// 否则服务端数据副本会绕过 react-query 缓存/失效纪律，落入 §3.3 明令禁止项。
const SRC = path.resolve(__dirname, "..");
const STORES = path.join(SRC, "stores");

function walkStoreFiles(dir: string, out: string[] = []): string[] {
  for (const e of readdirSync(dir)) {
    if (e === "node_modules" || e.startsWith(".")) continue;
    const full = path.join(dir, e);
    const st = statSync(full);
    if (st.isDirectory()) walkStoreFiles(full, out);
    else if (/\.ts(x?)$/.test(e)) out.push(full);
  }
  return out;
}

describe("state-boundary (C195/W213)：zustand store 不承载服务端数据拉取（§3.3 分工红线）", () => {
  const storeFiles = walkStoreFiles(STORES);
  it("stores/ 下至少存在 store 文件（红线不空转）", () => {
    expect(storeFiles.length).toBeGreaterThanOrEqual(1);
  });

  const serverFetchRe = /fetch\s*\(|apiFetchData|apiFetch\b|\baxios\b|useQuery\s*\(|\.\s*data\s*\)\s*\./g;
  it("任一 zustand store 源码不得出现服务端数据拉取/解包动作", () => {
    const violations: string[] = [];
    for (const f of storeFiles) {
      const src = readFileSync(f, "utf8");
      const srcNoComments = src
        .split(/\r?\n/)
        .filter((l) => {
          const t = l.trim();
          return !(t.startsWith("//") || t.startsWith("*") || t.startsWith("/*"));
        })
        .join("\n");
      const hits = [...srcNoComments.matchAll(serverFetchRe)];
      for (const h of hits) {
        violations.push(`${path.relative(SRC, f)} :: ${h[0]}`);
      }
    }
    expect(violations).toEqual([]);
  });
});
