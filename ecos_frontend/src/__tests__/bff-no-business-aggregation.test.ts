// @vitest-environment node
import { describe, it, expect } from "vitest";
import { readBff, findLines } from "./bff.mri";

// C189/W207 (M0, 不等裁决直改)：BFF 不允许在 /api/audit-logs 上做业务聚合。
// 判定以"字面带聚合语义的模式"为准：
//   a) 单一 handler 内的 map 生成 model 字段（旧版 `executions.map(...)` 手写过 data[]）
//   b) 兜底 BACKEND_URL / :8081（v1 时代 v1 端口，属"审计绕开 gateway"红线）
//   c) `res.json({ success: true, count: ..., data: ... })` 张式返回（= 合成返回，非透传）
describe("bff-no-business-aggregation: /api/audit-logs 只透传，不聚合（C189/W207）", () => {
  const src = readBff();

  it("audit-logs 路由 handler 不再 fetch :8081 或读 v1 BACKEND_URL（豁免注释行的历史参照）", () => {
    // 只扫**代码行**（非注释行）；历史参照允许留在 C189 校訂注释里，但代码红线不残留
    const codeLines = src.split(/\r?\n/).map((t, i) => ({ line: i + 1, text: t })).filter(
      (r) => {
        const t = r.text.trim();
        return !(t.startsWith("//") || t.startsWith("*") || t.startsWith("/*"));
      },
    );
    const codeHit = (re: RegExp) => codeLines.filter((r) => re.test(r.text));
    expect(codeHit(/:8081/)).toEqual([]);
    expect(codeHit(/BACKEND_URL/)).toEqual([]);
    expect(codeHit(/const\s+BACKEND\b/)).toEqual([]);
    expect(codeHit(/fetch\(\s*\$\{BACKEND\}/)).toEqual([]);
  });

  it("audit-logs handler 不再手写过 model（map/executions/id: `aud_${Date.now()}` 等合成点）", () => {
    // "executions.map" 语义已删；"aud_" 前缀、"Just now" 与 `AI-Agent-` 都属合成 model
    expect(findLines(src, /executions\s*\.map/)).toEqual([]);
    expect(findLines(src, /aud_\$\{Date\.now\(\)\}/)).toEqual([]);
    expect(findLines(src, /Just now/)).toEqual([]);
    expect(findLines(src, /AI-Agent-/)).toEqual([]);
  });

  it("audit-logs 前缀 handler 已收敛为 forwardProxy(GATEWAY, ...) 单一通道", () => {
    const auditLines = findLines(src, /\/api\/audit-logs/);
    expect(auditLines.length).toBeGreaterThan(0);
    // C201/W219：允许且仅允许 jsonBody 作为中间件挂在同一 mount 上（app 级解析器已被禁用）。
    const forwardHits = findLines(src, /app\.use\(\s*["']\/api\/audit-logs["']\s*,\s*(?:jsonBody,\s*)?forwardProxy\(GATEWAY/);
    expect(forwardHits.length).toBe(1);
  });

  it("BFF 不再输出 'fallback' 兜底合成（旧版 catch 里 res.json({success:true,count:0,data:[]})）", () => {
    expect(findLines(src, /audit-logs fallback/)).toEqual([]);
    expect(findLines(src, /count:\s*0\s*,\s*data:\s*\[\]/)).toEqual([]);
  });
});
