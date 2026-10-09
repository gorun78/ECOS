// @vitest-environment node
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * 详细设计-06 F06-21-1 — BFF 侧 SSE 直通（X-79，P1 阻塞项）的 AI 侧验收断言。
 *
 * <p>本册归属：`server.ts` 通用代理的流式改造属分册 08（前端与 BFF）C 系列，本册只
 * 登记跨册接缝需求并给出 AI 侧可判定断言。F06-21 的性能硬判据
 * 「生产链路下流式首帧早于完整响应到达」需要 7 JAR + 前端 + 真实 LLM key 同跑，
 * 属 E2E 面；本 spec 以<b>源码扫描</b>固化其前提条件防回退：BFF 的 `forwardProxy`
 * 必须对 `text/event-stream` 走流式管道，禁止 `await upstream.text()` 把整条流攒到
 * 回连结束才下发（那会令 Agent 流式对话逐 token 推送语义失效）。
 */
describe("ai › BFF 生产链路 SSE 流式直通（F06-21-1 safeguard）", () => {
  const serverSrc = readFileSync(resolve(__dirname, "../../server.ts"), "utf8");

  it("forwardProxy 存在 text/event-stream 专用分支", () => {
    expect(serverSrc).toContain('includes("text/event-stream")');
  });

  it("SSE 分支用 web ReadableStream.getReader 逐块写回（非 await 缓冲）", () => {
    expect(serverSrc).toContain("webBody.getReader()");
    expect(serverSrc).toContain("res.write(Buffer.from(value))");
  });

  it("SSE 分支显式关闭缓冲并 flush 响应头", () => {
    expect(serverSrc).toContain('res.setHeader("X-Accel-Buffering", "no")');
    expect(serverSrc).toContain("flushHeaders");
  });

  it("SSE 分支不再整段 await upstream.text()（回退到缓冲即 FAIL）", () => {
    // JSON/text 分支仍允许 await upstream.text()（非流式场景无碍）——
    // 故只断言 SSE 分支内不存在对 same-response 的 await-many-buffering 模式：
    // 'text/event-stream' 分支应以 getReader 消费，而不是 'await upstream.text()'。
    const sseSlice = serverSrc.slice(
      serverSrc.indexOf('includes("text/event-stream")'),
      serverSrc.indexOf("application/json", serverSrc.indexOf('includes("text/event-stream")')),
    );
    expect(sseSlice).not.toMatch(/await\s+upstream\.text\(\)/);
    expect(sseSlice).toContain("getReader()");
  });
});
