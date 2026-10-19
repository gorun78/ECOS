/**
 * Agent Loop 承流客户端用例（篇13 §13.4.5 + 详设-06 §9.3.3）。
 *
 * 覆盖的是「静默失真」而不是「报错」：断线被当成完成、残块被当成正文、
 * 违例帧被当成 delta 渲染，都只会让界面少一句话或多一句假话。故每个分支都断言
 * outcome 的分类字段，不只断言 handler 调用次数。
 * @license Apache-2.0
 */
// @vitest-environment node
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { API_BASE } from "./httpClient";
import {
  AGENT_LOOP_CHAT_REL_PATH,
  SSE_ACCEPT,
  countsAsDisconnect,
  isFallbackTriggered,
  streamAgentLoopChat,
  type AgentLoopStreamHandlers,
} from "./agentLoopStream";
import { AGENT_LOOP_FRAME_ORDER, SSE_FALLBACK_THRESHOLD } from "./sseFrames";

// #143/B5-13 形态订正：本腿传输已合流到 httpClient 的具名流式通道 ⇒ Bearer 拼接从域内
// `authHeaders()` 移到单源内的 `getAuthToken()`。桩只供 authHeaders 会让单源拿到 undefined
// 的 getAuthToken，在 fetch 之前就抛 TypeError ⇒ 全部用例塌成 transport 假红（实跑读出）。
vi.mock("./auth", () => ({
  authHeaders: () => ({ "Content-Type": "application/json", Authorization: "Bearer test-token" }),
  getAuthToken: () => "test-token",
  clearAuthSession: (): void => undefined,
}));

const encoder = new TextEncoder();

interface FetchCall {
  url: string;
  init: { method: string; headers: Record<string, string>; body: string; signal?: AbortSignal };
}

/** 用 ReadableStream 桩住 SSE 应答；chunks 逐段吐出以复现真实分包 */
function mockStreamResponse(chunks: string[], init: { status?: number; contentType?: string; headers?: Record<string, string> } = {}) {
  const calls: FetchCall[] = [];
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
  const headers = new Headers({ "Content-Type": init.contentType ?? `text/event-stream;charset=UTF-8`, ...init.headers });
  const fetchMock = vi.fn(async (url: string, opts: FetchCall["init"]) => {
    calls.push({ url, init: opts });
    return {
      ok: (init.status ?? 200) >= 200 && (init.status ?? 200) < 300,
      status: init.status ?? 200,
      headers,
      body: init.contentType === null ? null : body,
      text: async () => chunks.join(""),
    } as unknown as Response;
  });
  vi.stubGlobal("fetch", fetchMock);
  return { calls, fetchMock };
}

const frame = (event: string, payload: Record<string, unknown>) =>
  `event: ${event}\ndata: ${JSON.stringify(payload)}\n\n`;

function spyHandlers() {
  return {
    onMeta: vi.fn(),
    onStep: vi.fn(),
    onToken: vi.fn(),
    onDelta: vi.fn(),
    onCitation: vi.fn(),
    onError: vi.fn(),
    onDone: vi.fn(),
  } satisfies AgentLoopStreamHandlers;
}

const request = { agentId: "agent-1", message: "营收多少" };

beforeEach(() => {
  vi.useRealTimers();
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("streamAgentLoopChat request shape", () => {
  it("POSTs with an explicit SSE Accept so Spring picks the streaming mapping", async () => {
    const { calls } = mockStreamResponse([frame("meta", { traceId: "t1" }), frame("done", { success: true })]);
    await streamAgentLoopChat(request, {});
    expect(calls[0].url).toBe(`${API_BASE}${AGENT_LOOP_CHAT_REL_PATH}`);
    expect(calls[0].init.method).toBe("POST");
    expect(calls[0].init.headers.Accept).toBe(SSE_ACCEPT);
    expect(calls[0].init.headers.Authorization).toBe("Bearer test-token");
    expect(JSON.parse(calls[0].init.body)).toMatchObject({ agentId: "agent-1", message: "营收多少" });
  });

  it("appends the thread query and passes Last-Event-ID through when the caller has one", async () => {
    const { calls } = mockStreamResponse([frame("meta", {}), frame("done", {})]);
    await streamAgentLoopChat({ ...request, thread: "ops", lastEventId: "evt-9" }, {});
    expect(calls[0].url).toBe(`${API_BASE}${AGENT_LOOP_CHAT_REL_PATH}?thread=ops`);
    expect(calls[0].init.headers["Last-Event-ID"]).toBe("evt-9");
  });
});

describe("streamAgentLoopChat happy path", () => {
  it("dispatches all seven frames in order and reports a completed stream", async () => {
    const handlers = spyHandlers();
    const chunks = AGENT_LOOP_FRAME_ORDER.map((name) => frame(name, { traceId: "t1" }));
    mockStreamResponse(chunks);
    const outcome = await streamAgentLoopChat(request, handlers);
    expect(outcome.frames).toEqual([...AGENT_LOOP_FRAME_ORDER]);
    expect(handlers.onMeta).toHaveBeenCalledTimes(1);
    expect(handlers.onDelta).toHaveBeenCalledTimes(1);
    expect(handlers.onDone).toHaveBeenCalledTimes(1);
    expect(outcome.completed).toBe(true);
    expect(outcome.sequenceViolation).toBeNull();
    expect(outcome.failure).toBeNull();
  });

  it("accumulates delta text across chunks without overwriting earlier content", async () => {
    const chunks = [
      frame("meta", { traceId: "t1", sessionId: "s1" }),
      frame("delta", { traceId: "t1", text: "营收 " }),
      'event: delta\ndata: {"traceId":"t1","te',
      'xt":"12 亿"}\n\n',
      frame("done", { traceId: "t1", success: true, totalTokens: 8 }),
    ];
    mockStreamResponse(chunks);
    let text = "";
    const outcome = await streamAgentLoopChat(request, { onDelta: (d) => { text += d.text; } });
    expect(text).toBe("营收 12 亿");
    expect(outcome.completed).toBe(true);
    expect(outcome.failure).toBeNull();
    expect(outcome.frames).toEqual(["meta", "delta", "delta", "done"]);
    expect(outcome.sequenceViolation).toBeNull();
  });

  it("marks resume as impossible while the backend sends no id, and possible once it does", async () => {
    mockStreamResponse([frame("meta", {}), frame("done", {})]);
    const noId = await streamAgentLoopChat(request, {});
    expect(noId.lastEventId).toBeNull();
    expect(noId.resumeCapable).toBe(false);

    mockStreamResponse(["id: 17\nevent: meta\ndata: {}\n\n", frame("done", {})]);
    const withId = await streamAgentLoopChat(request, {});
    expect(withId.lastEventId).toBe("17");
    expect(withId.resumeCapable).toBe(true);
  });
});

describe("streamAgentLoopChat failure taxonomy", () => {
  it("stops consuming on a missing meta frame and classifies it as a contract failure", async () => {
    mockStreamResponse([frame("delta", { text: "伪造正文" }), frame("done", {})]);
    const handlers = spyHandlers();
    const outcome = await streamAgentLoopChat(request, handlers);
    expect(outcome.failure).toEqual({ kind: "contract", detail: "首帧必须为 meta，实得 delta" });
    expect(handlers.onDelta).not.toHaveBeenCalled();
  });

  it("classifies an out-of-vocabulary frame name as a contract failure", async () => {
    mockStreamResponse([frame("meta", {}), frame("tool_call", { name: "x" })]);
    const outcome = await streamAgentLoopChat(request, {});
    expect(outcome.failure?.kind).toBe("contract");
    expect(outcome.frames).toEqual(["meta", "tool_call"]);
  });

  it("treats a non-JSON frame payload as contract noise, never as body text", async () => {
    mockStreamResponse([frame("meta", {}), "event: delta\ndata: <html>oops</html>\n\n"]);
    const handlers = spyHandlers();
    const outcome = await streamAgentLoopChat(request, handlers);
    expect(outcome.failure).toEqual({ kind: "contract", detail: "帧 delta 载荷非 JSON 对象" });
    expect(handlers.onDelta).not.toHaveBeenCalled();
  });

  it("flags an error frame that is not immediately before done while streaming", async () => {
    mockStreamResponse([frame("meta", {}), frame("error", { message: "boom" }), frame("delta", { text: "x" })]);
    const outcome = await streamAgentLoopChat(request, {});
    expect(outcome.failure).toEqual({ kind: "contract", detail: "error 之后只准出 done" });
  });

  it("reports a stream that ends without done as interrupted, not completed", async () => {
    mockStreamResponse([frame("meta", {}), frame("delta", { text: "半句" })]);
    const outcome = await streamAgentLoopChat(request, {});
    expect(outcome.completed).toBe(false);
    expect(outcome.failure).toEqual({ kind: "interrupted", detail: "流在收到 done 之前中断" });
  });

  it("discards an unterminated tail and surfaces the discard instead of hiding it", async () => {
    mockStreamResponse([frame("meta", {}), frame("done", {}), 'event: delta\ndata: {"text":"cut']);
    const outcome = await streamAgentLoopChat(request, {});
    expect(outcome.discardedTail).toBe(true);
    expect(outcome.frames).toEqual(["meta", "done"]);
  });

  it("classifies a non-SSE 200 content type as a contract failure", async () => {
    mockStreamResponse(["{}"], { contentType: "application/json" });
    const outcome = await streamAgentLoopChat(request, {});
    expect(outcome.failure?.kind).toBe("contract");
    expect((outcome.failure as { detail: string }).detail).toMatch(/application\/json/);
  });

  it("extracts only message + traceId from an error envelope", async () => {
    mockStreamResponse([JSON.stringify({ message: "LLM 超时", traceId: "tr-77" })], {
      status: 504,
      contentType: "application/json",
    });
    const outcome = await streamAgentLoopChat(request, {});
    expect(outcome.failure).toEqual({ kind: "transport", status: 504, message: "LLM 超时", traceId: "tr-77" });
  });

  it("reads the trace id from the response header when the body has none", async () => {
    mockStreamResponse(["Internal Server Error"], {
      status: 500,
      contentType: "text/plain",
      headers: { "X-ECOS-Trace-Id": "hdr-1" },
    });
    const outcome = await streamAgentLoopChat(request, {});
    expect(outcome.failure).toMatchObject({ kind: "transport", status: 500, traceId: "hdr-1" });
    expect((outcome.failure as { message: string }).message).toBe("HTTP 500");
  });

  it("maps an aborted fetch to the aborted class rather than a failure", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => {
      const err = new Error("aborted");
      err.name = "AbortError";
      throw err;
    }));
    const outcome = await streamAgentLoopChat(request, {});
    expect(outcome.failure).toEqual({ kind: "aborted" });
  });

  it("maps a network throw to a transport failure with no invented status", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => {
      throw new TypeError("Failed to fetch");
    }));
    const outcome = await streamAgentLoopChat(request, {});
    expect(outcome.failure).toEqual({
      kind: "transport",
      status: null,
      message: "网络不可达或网关无应答",
      traceId: null,
    });
  });

  it("flags a terminal sequence violation for frames emitted after done", async () => {
    // done 之后再出帧：在途 prefix 校验放行（首帧 meta、无 error），只有终态校验能抓
    mockStreamResponse([frame("meta", {}), frame("done", { turns: 1 }), frame("delta", { text: "a" })]);
    const outcome = await streamAgentLoopChat(request, {});
    expect(outcome.sequenceViolation).toMatch(/末帧必须为 done，实得 delta/);
    expect(outcome.failure).toEqual({ kind: "contract", detail: outcome.sequenceViolation });
  });
});

describe("§4.4 降级判定（纯函数面）", () => {
  it("只有 transport/interrupted 计入断线，aborted/contract 不计", () => {
    expect(countsAsDisconnect(null)).toBe(false);
    expect(countsAsDisconnect({ kind: "interrupted", detail: "x" })).toBe(true);
    expect(countsAsDisconnect({ kind: "transport", status: null, message: "x", traceId: null })).toBe(true);
    // 主动取消计入断线 ⇒ 点三次「停止」就自动降级，属假故障
    expect(countsAsDisconnect({ kind: "aborted" })).toBe(false);
    // 服务端违约重发也照样违例 ⇒ 计入断线会把契约缺陷洗成「网络不好」
    expect(countsAsDisconnect({ kind: "contract", detail: "x" })).toBe(false);
  });

  it("阈值边界：2 次不降级，3 次降级，更多次保持已降级", () => {
    expect(SSE_FALLBACK_THRESHOLD).toBe(3);
    expect(isFallbackTriggered(0)).toBe(false);
    expect(isFallbackTriggered(SSE_FALLBACK_THRESHOLD - 1)).toBe(false);
    expect(isFallbackTriggered(SSE_FALLBACK_THRESHOLD)).toBe(true);
    expect(isFallbackTriggered(SSE_FALLBACK_THRESHOLD + 5)).toBe(true);
  });

  it("无 done 的自然收尾必须落进断线口径（此路一旦归 contract，断线计数恒零、降级永不触发）", async () => {
    mockStreamResponse([frame("meta", { traceId: "t1" }), frame("delta", { text: "半句" })]);
    const outcome = await streamAgentLoopChat(request, {});
    expect(countsAsDisconnect(outcome.failure)).toBe(true);
  });
});
