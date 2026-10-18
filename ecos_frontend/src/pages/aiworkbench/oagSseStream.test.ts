/**
 * OAG Playground 承流用例 —— D-B5-11（页面侧第二 SSE 实现面）收口判据。
 *
 * 判据对象是「线路级切分是否还是第二实现面」：手切 `indexOf('\n\n')` 的形态对
 * `\r\n` 分隔的流**一个事件都派发不出来**（后端 Spring 的 SSE 写出正是 CRLF），
 * 界面表现为「点了运行、转圈结束、什么都没有」。故第一条用例就是 CRLF 全事件面。
 *
 * 只测 transport 层，不测 React 页面（本机 Node 20.18.1 的 jsdom 依赖链不可跑，
 * 见 vite.config.ts 覆盖率注释）。
 * @license Apache-2.0
 */
// @vitest-environment node
import { describe, it, expect, vi } from "vitest";
import { parseSseStream, type OagSseHandlers, type OagStepEvent } from "./api";

vi.mock("../../services/auth", () => ({
  authHeaders: () => ({ Authorization: "Bearer test-token" }),
}));

const encoder = new TextEncoder();

/** 把若干段文本当作 SSE 响应体逐段吐出（段边界刻意切在帧中间以复现真实分包） */
function streamResponse(chunks: string[]): Response {
  let index = 0;
  const body = {
    getReader: () => ({
      read: async () => {
        if (index >= chunks.length) return { done: true, value: undefined };
        return { done: false, value: encoder.encode(chunks[index++]) };
      },
      cancel: vi.fn(async () => undefined),
    }),
  };
  return { ok: true, status: 200, headers: new Headers(), body } as unknown as Response;
}

function spyHandlers() {
  return {
    onStep: vi.fn(),
    onResponse: vi.fn(),
    onDone: vi.fn(),
    onBlocked: vi.fn(),
    onError: vi.fn(),
  } satisfies OagSseHandlers;
}

/** 一条完整事件的 CRLF 写法（Spring 默认行分隔符） */
const crlf = (event: string, data: unknown) =>
  `event: ${event}\r\ndata: ${JSON.stringify(data)}\r\n\r\n`;

describe("OAG 承流 —— 线路级单源（D-B5-11）", () => {
  it("dispatches every event type from a CRLF-delimited stream", async () => {
    const h = spyHandlers();
    const res = streamResponse([
      crlf("node", { node: 'plan', status: 'RUNNING', traceId: 't-1' }),
      crlf("response", { content: '营收 1.2 亿', traceId: 't-1' }),
      crlf("blocked", { reason: '越权', traceId: 't-1' }),
      crlf("done", { traceId: 't-1', sessionId: 's-1', elapsedMs: 12, status: 'SUCCESS' }),
    ]);

    await parseSseStream(res, h);

    expect(h.onStep).toHaveBeenCalledTimes(1);
    expect(h.onStep.mock.calls[0][0] as OagStepEvent).toMatchObject({ node: 'plan', status: 'RUNNING' });
    expect(h.onResponse).toHaveBeenCalledWith({ content: '营收 1.2 亿', traceId: 't-1' });
    expect(h.onBlocked).toHaveBeenCalledWith({ reason: '越权', traceId: 't-1' });
    expect(h.onDone).toHaveBeenCalledWith({
      traceId: 't-1', sessionId: 's-1', elapsedMs: 12, status: 'SUCCESS',
    });
    expect(h.onError).not.toHaveBeenCalled();
  });

  it("dispatches events from an LF stream too (no regression on the working shape)", async () => {
    const h = spyHandlers();
    const res = streamResponse(['event: node\ndata: {"node":"act","status":"DONE"}\n\n']);

    await parseSseStream(res, h);

    expect(h.onStep).toHaveBeenCalledTimes(1);
  });

  it("keeps one event intact when the block is split across chunks mid-payload", async () => {
    const h = spyHandlers();
    // 断点落在 data 行中间：手切形态靠 buffer 侥幸能对，收口后必须仍对 ⇒ 防改坏
    const res = streamResponse(['event: response\ndata: {"content":"四', '季度营收","traceId":"t-2"}\n\n']);

    await parseSseStream(res, h);

    expect(h.onResponse).toHaveBeenCalledWith({ content: '四季度营收', traceId: 't-2' });
  });

  it("joins multi-line data segments before parsing", async () => {
    const h = spyHandlers();
    // 一条事件的 data 拆成两行：必须以 \n 连接后再 parse（连接后仍是合法 JSON）
    const res = streamResponse(['event: response\ndata: {"content":\ndata: "x"}\n\n']);

    await parseSseStream(res, h);

    expect(h.onResponse).toHaveBeenCalledWith({ content: 'x', traceId: '' });
  });

  it("ignores heartbeat comment lines and id/retry-only blocks", async () => {
    const h = spyHandlers();
    const res = streamResponse([': heartbeat\n\nid: 7\nretry: 3000\n\nevent: node\ndata: {"node":"m"}\n\n']);

    await parseSseStream(res, h);

    expect(h.onStep).toHaveBeenCalledTimes(1);
  });

  it("drops a tail block that never got its blank line instead of fabricating an event", async () => {
    const h = spyHandlers();
    const res = streamResponse(['event: done\ndata: {"traceId":"t-3"}\n\nevent: node\ndata: {"node":"pa']);

    await parseSseStream(res, h);

    expect(h.onDone).toHaveBeenCalledTimes(1);
    expect(h.onStep).not.toHaveBeenCalled();
  });

  it("routes an unknown event name to no handler (禁把契约外事件冒充已知帧)", async () => {
    const h = spyHandlers();
    const res = streamResponse(['event: telemetry\ndata: {"node":"z"}\n\n']);

    await parseSseStream(res, h);

    expect(h.onStep).not.toHaveBeenCalled();
    expect(h.onResponse).not.toHaveBeenCalled();
    expect(h.onDone).not.toHaveBeenCalled();
    expect(h.onBlocked).not.toHaveBeenCalled();
    expect(h.onError).not.toHaveBeenCalled();
  });

  it("keeps a non-JSON payload as the message text for the error leg", async () => {
    const h = spyHandlers();
    const res = streamResponse(['event: error\ndata: 上游超时\n\n']);

    await parseSseStream(res, h);

    expect(h.onError).toHaveBeenCalledWith({ message: '上游超时', traceId: undefined });
  });
});
