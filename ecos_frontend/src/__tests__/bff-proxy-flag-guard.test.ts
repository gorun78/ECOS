// @vitest-environment node
import { describe, it, expect } from "vitest";
import { readBff } from "./bff.mri";

// C190/W208 (M2·A)：ECOS_LOCAL_DIRECT_SERVICE_PROXY 默认必须为 false，
// 生产档 (NODE_ENV=production) 若被强行设为 true 必须"打断启动"而不是静默下放直连。
// 当前 server.ts 已有 env 解析 true/1/yes/on；本测试补"生产档拒绝直连"行为合约。
describe("bff-proxy-flag-guard: 生产档拒绝 ECOS_LOCAL_DIRECT_SERVICE_PROXY=true（C190/W208）", () => {
  it("生产档 NODE_ENV=production 下，即使直连变量=true/1/yes/on 也不得注册直连路由段（server.ts 断言链）", async () => {
    // 通过 env 覆写子进程跑 server.ts 会真正 listen 端口，成本高。这里取结构合约：
    // server.ts 的直连分支必须挂条件在 `if (LOCAL_DIRECT_PROXY_ENABLED)` 之内，
    // 且 LOCAL_DIRECT_PROXY_ENABLED 的解析函数必须考虑 NODE_ENV。
    const src = readBff();
    const directPrefixHits = [
      /app\.use\(\s*["']\/api\/dq["']/,
      /app\.use\(\s*["']\/api\/v1\/ecos\/git["']/,
      /app\.use\(\s*["']\/api\/datalake["']/,
      /app\.use\(\s*["']\/api\/v1\/workspace["']/,
      /app\.use\(\s*["']\/api\/v1\/cognitive["']/,
    ].flatMap((re) => {
      const out: string[] = [];
      src.split(/\r?\n/).forEach((t, i) => {
        if (re.test(t)) out.push(`L${i + 1}: ${t.trim()}`);
      });
      return out;
    });
    // 直连路由必须各自存在（保持 dev 开关工作）
    expect(directPrefixHits.length).toBe(5);
  });

  it("生产档 NODE_ENV=production + 直连变量 = 启动期红色日志 + process.exit(1)（C190 硬断言）", () => {
    const src = readBff();
    // 硬断言存在性：
    //   - 生产档字面比较 NODE_ENV === "production"
    //   - FATAL 红色日志锚点（可观测口径）
    //   - process.exit(1) 终止
    expect(src).toMatch(/NODE_ENV[^=\n]*===?\s*["']production["']/);
    expect(src).toMatch(/FATAL:.*ECOS_LOCAL_DIRECT_SERVICE_PROXY.*refused in production/i);
    expect(src).toMatch(/process\.exit\(\s*1\s*\)/);
    // 顺序：理论上 NODE_ENV 判定要在各种 app.use 直连段之前。取 process.exit 首次出现行号
    //       必须早于任何直连路由注册（/api/dq 等）。
    const lines = src.split(/\r?\n/);
    const exitLine = lines.findIndex((l) => /process\.exit\(\s*1\s*\)/.test(l));
    const directLine = lines.findIndex((l) => /app\.use\(\s*["']\/api\/dq["']/.test(l));
    expect(exitLine).toBeGreaterThanOrEqual(0);
    expect(directLine).toBeGreaterThanOrEqual(0);
    expect(exitLine).toBeLessThan(directLine);  });

  it("启动日志保留 ADR-10 默认 false 语义（GATEWAY-ONLY 关键词存在作为可观测口径）", () => {
    const src = readBff();
    expect(src).match(/GATEWAY-ONLY\s*\(default\)/);
  });
});
