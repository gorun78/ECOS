// @vitest-environment node
import { describe, it, expect } from "vitest";
import { readBff } from "./bff.mri";

// C175/W193 (M1·A)：vite.middlewares 必须在 /api catch-all 之前注册，
// 否则 vite dev 的 /@vite、/src/* 等开发资源会被 /api 兜底吞掉。
// 判定：读 server.ts，把两次注册各自的**行号**取出来再比较。
describe("bff-middleware-order: vite middlewares 优先级于 /api catch-all（C175/W193）", () => {
  const src = readBff();
  const lines = src.split(/\r?\n/);

  const index = (re: RegExp): number[] => {
    const out: number[] = [];
    lines.forEach((text, i) => {
      if (re.test(text)) out.push(i + 1);
    });
    return out;
  };

  it("vite.middlewares 挂载行存在", () => {
    const hits = index(/app\.use\(vite\.middlewares\)/);
    expect(hits.length).toBeGreaterThanOrEqual(1);
  });

  it("/api catch-all 挂载点行存在（放行 ADR-10 日志锚点）", () => {
    const hits = index(/app\.use\(\s*["']\/api["']\s*,\s*forwardProxy\(GATEWAY,\s*["']gateway\/default["']\)/);
    expect(hits.length).toBeGreaterThanOrEqual(1);
  });

  it("vite.middlewares 的**首次**注册 必须 早于 任一 /api catch-all 的注册（顺序）", () => {
    const viteLine = index(/app\.use\(vite\.middlewares\)/)[0];
    const apiCatchAllLines = index(/app\.use\(\s*["']\/api["']\s*,\s*forwardProxy\(GATEWAY,\s*["']gateway\/default["']\)/);
    expect(apiCatchAllLines.length).toBeGreaterThanOrEqual(1);
    const minApiCatchAll = Math.min(...apiCatchAllLines);
    expect(viteLine).toBeLessThan(minApiCatchAll);
  });

  it("prod 分支下 /api catch-all 与 SPA 静态/get('*') 的顺序仍是 catchall → static → get(*)（C175 同一红线在 prod 侧）", () => {
    const apiCatch = index(/app\.use\(\s*["']\/api["']\s*,\s*forwardProxy\(GATEWAY,\s*["']gateway\/default["']\)/);
    const staticLine = index(/app\.use\(express\.static\(distPath\)\)/)[0];
    const getStarLine = index(/app\.get\(\s*["']\*["']\s*,/)[0];
    expect(staticLine).toBeDefined();
    expect(getStarLine).toBeDefined();
    expect(Math.min(...apiCatch)).toBeLessThan(staticLine);
    expect(staticLine).toBeLessThan(getStarLine);
  });
});
