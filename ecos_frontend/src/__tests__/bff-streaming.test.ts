// @vitest-environment node
import { describe, it, expect } from "vitest";
import { readBff } from "./bff.mri";

/**
 * C174/W192（分册08 M0）：BFF SSE「承流不缓冲」静态护栏。
 *
 * 背景（诚实登记实现/护栏分工）：
 *   - server.ts 已实现 SSE 逐块 pipe（`text/event-stream` 分支：`getReader().read()` 逐块 +
 *     `res.write(value)`，客户端断开即 break），全缓冲旧写法 `await upstream.text()` 已移除于 SSE 路径。
 *   - 本护栏**只落可静态断言的最小不变量**：SSE 分支必须存在、必须走逐块 reader、禁在该分支内
 *     出现全缓冲（`upstream.text()` / `.join(`），且需有客户端断连 break。
 *   - **运行时字节级**「flush 无缓冲 / Last-Event-ID 续传」断言 = P-4 BFF live 侧
 *     （设计-08 规划的 `bff-streaming.spec.ts › sseChunksFlushWithoutBuffering` +
 *     PRD-08 验收起 `BffEventStreamPassthroughTest`），归 LIVE 层本轮诚实留白，不放码不落测。
 *
 * 注意：`upstream.text()` 在**非 SSE 分支**（JSON else 之外的兜底 text 分支）合法存在，
 * 故断言**限定在 SSE 分支切片内**，不做全文件缺席断言。
 */

/** 抽取 SSE 分支切片：从 `contentType.includes("text/event-stream")` 行起，到其后的 `} else` 行为止（不含）。 */
function sseBranch(src: string): string {
  const lines = src.split(/\r?\n/);
  const start = lines.findIndex((l) => /contentType\.includes\(\s*["']text\/event-stream["']\s*\)/.test(l));
  if (start === -1) return "";
  let end = lines.length;
  for (let i = start + 1; i < lines.length; i++) {
    if (/^\s*\}\s*else\b/.test(lines[i])) { end = i; break; }
  }
  return lines.slice(start, end).join("\n");
}

describe("bff-streaming: SSE 承流不缓冲（C174/W192）", () => {
  it("server.ts 存在 text/event-stream 专属分支（先前旧全缓冲 `await upstream.text()` 已移除于该分支）", () => {
    const src = readBff();
    expect(src).toMatch(/text\/event-stream/);
    const seg = sseBranch(src);
    expect(seg.length, "未定位到 SSE 分支切片（contentType.includes(text/event-stream) 缺失）").toBeGreaterThan(20);
  });

  it("SSE 分支走逐块 pipe：getReader().read() + res.write（禁全缓冲 upstream.text() / .join(）", () => {
    const seg = sseBranch(readBff());
    // 逐块读 + 逐块写（ADR-15 制品≠承流：本层只透传不解析）
    expect(seg).toMatch(/getReader\(\)/);
    expect(seg).toMatch(/reader\.read\(\)/);
    expect(seg).toMatch(/res\.write\(/);
    // 该分支内不得出现全缓冲语义（会直接把 SSE 流缓冲到结束，等于杀死流式）
    expect(seg).not.toMatch(/upstream\.text\(\)/);
    expect(seg).not.toMatch(/\.join\(/);
  });

  it("客户端断连即 break（防连接泄漏，逐块循环有出口）", () => {
    const seg = sseBranch(readBff());
    expect(seg).toMatch(/break/);
    // 断连判定依据：res 已不可写（writableEnded）或 ctx/req 关闭信号
    expect(seg).toMatch(/writableEnded|req\.socket|res\.close/);
  });

  it("透传 Last-Event-ID（SSE 续传前置：BFF 不得静默吞头）", () => {
    const src = readBff();
    expect(src).toMatch(/last-event-id/i);
    expect(src).toMatch(/["']Last-Event-ID["']/);
  });
});
