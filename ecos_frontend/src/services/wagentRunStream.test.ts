/**
 * WAgent run 流承流客户端用例（详设-10 §10.3.6 八帧 + 补充帧 milestone；#143/B5-5b-2）。
 *
 * 与 agent-loop 腿的用例分开写，理由是同两条通道的「结局形状」不同：
 * 本腿后端真发 `id:`（续传为真）、且**允许无 done 自然结束**（预算耗尽未终态时刻意不发末帧）。
 * 若照搬七帧腿的期望，会把这两条真实差异测成假绿。
 *
 * 断言对象始终是 outcome 的分类字段，而不是 handler 次数：本通道最坏的失效是
 * 「把断线当完成」或「把降级帧当未知帧停流」，两者都不抛异常。
 * @license Apache-2.0
 */
// @vitest-environment node
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { API_BASE } from "./httpClient";
import { countsAsDisconnect, isFallbackTriggered, SSE_ACCEPT } from "./agentLoopStream";
import {
  streamWagentRunEvents,
  wagentRunEventsStreamPath,
  type WagentRunStreamHandlers,
} from "./wagentRunStream";
import { TEXT_ABSENT } from "./sseFrames";
import { WAGENT_FRAMES, WAGENT_MILESTONE_FRAME } from "./wagentSseFrames";

vi.mock("./auth", () => ({
  authHeaders: () => ({ Authorization: "Bearer test-token" }),
}));

const encoder = new TextEncoder();

interface FetchCall {
  url: string;
  init: { method: string; headers: Record<string, string>; signal?: AbortSignal };
}

/** 用 ReadableStream 桩住 SSE 应答；chunks 逐段吐出以复现真实分包 */
function mockStreamResponse(
  chunks: string[],
  init: { status?: number; contentType?: string | null } = {},
) {
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
  const status = init.status ?? 200;
  const headers = new Headers({
    "Content-Type": init.contentType ?? "text/event-stream;charset=UTF-8",
  });
  const fetchMock = vi.fn(async (url: string, opts: FetchCall["init"]) => {
    calls.push({ url, init: opts });
    return {
      ok: status >= 200 && status < 300,
      status,
      headers,
      body: init.contentType === null ? null : body,
      text: async () => chunks.join(""),
    } as unknown as Response;
  });
  vi.stubGlobal("fetch", fetchMock);
  return { calls, fetchMock };
}

/** 一帧的线路文本；`id` 只为「带行 id 的帧」（step/milestone）准备，同后端 sinkOf 口径 */
const frame = (event: string, payload: Record<string, unknown>, id?: string) =>
  `${id ? `id: ${id}\n` : ""}event: ${event}\ndata: ${JSON.stringify(payload)}\n\n`;

function spyHandlers() {
  return {
    onMeta: vi.fn(),
    onStep: vi.fn(),
    onToken: vi.fn(),
    onDelta: vi.fn(),
    onCitation: vi.fn(),
    onDegrade: vi.fn(),
    onMilestone: vi.fn(),
    onError: vi.fn(),
    onDone: vi.fn(),
  } satisfies WagentRunStreamHandlers;
}

const request = { runId: "run 1" };

beforeEach(() => {
  vi.useRealTimers();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("WAgent run 流承流", () => {
  it("URL 由 API_BASE 派生、GET + Accept，且 runId 经编码", async () => {
    const { calls } = mockStreamResponse([
      frame(WAGENT_FRAMES.meta, { traceId: "t", runId: "run 1" }),
      frame(WAGENT_FRAMES.done, { traceId: "t", runId: "run 1", status: "SUCCEEDED" }),
    ]);
    await streamWagentRunEvents(request);
    expect(calls[0].url).toBe(`${API_BASE}${wagentRunEventsStreamPath("run 1")}`);
    expect(calls[0].url).toContain("/v1/wagent/runs/run%201/events/stream");
    expect(calls[0].init.method).toBe("GET");
    expect(calls[0].init.headers.Accept).toBe(SSE_ACCEPT);
    expect(calls[0].init.headers.Authorization).toBe("Bearer test-token");
  });

  it("Last-Event-ID 只在调用方给出时才发（本通道后端真消费它）", async () => {
    const { calls } = mockStreamResponse([
      frame(WAGENT_FRAMES.meta, {}),
      frame(WAGENT_FRAMES.done, {}),
    ]);
    await streamWagentRunEvents({ ...request, lastEventId: "42" });
    expect(calls[0].init.headers["Last-Event-ID"]).toBe("42");
  });

  it("真发六帧：帧序完整、逐帧派发到对应 handler、降级码入册", async () => {
    mockStreamResponse([
      frame(WAGENT_FRAMES.meta, { traceId: "t", runId: "r", planSource: "SKILL" }),
      frame(WAGENT_FRAMES.step, { traceId: "t", stepKey: "s1", status: "RUNNING" }, "11"),
      frame(WAGENT_MILESTONE_FRAME, { traceId: "t", eventType: "PlanProposed" }, "12"),
      frame(WAGENT_FRAMES.degrade, { traceId: "t", dgCode: "DG-M1", requiresAck: false, note: "n" }),
      frame(WAGENT_FRAMES.step, { traceId: "t", stepKey: "s1", status: "DONE" }, "13"),
      frame(WAGENT_FRAMES.done, { traceId: "t", runId: "r", status: "SUCCEEDED", degradations: ["DG-M1"] }),
    ]);
    const h = spyHandlers();
    const out = await streamWagentRunEvents(request, h);
    expect(out.frames).toEqual([
      WAGENT_FRAMES.meta,
      WAGENT_FRAMES.step,
      WAGENT_MILESTONE_FRAME,
      WAGENT_FRAMES.degrade,
      WAGENT_FRAMES.step,
      WAGENT_FRAMES.done,
    ]);
    expect(out.completed).toBe(true);
    expect(out.failure).toBeNull();
    expect(out.sequenceViolation).toBeNull();
    expect(out.degradationCodes).toEqual(["DG-M1"]); // 帧 6 ∪ 帧 8 按出现序去重，不得计两次
    expect(h.onMeta).toHaveBeenCalledTimes(1);
    expect(h.onStep).toHaveBeenCalledTimes(2);
    expect(h.onMilestone).toHaveBeenCalledTimes(1);
    expect(h.onDegrade).toHaveBeenCalledTimes(1);
    expect(h.onDone).toHaveBeenCalledTimes(1);
  });

  it("requiresAck 为真时置 ackRequired；无降级帧时保持 false（非「已判定不需确认」）", async () => {
    mockStreamResponse([
      frame(WAGENT_FRAMES.meta, {}),
      frame(WAGENT_FRAMES.degrade, { dgCode: "DG-M2", requiresAck: true, note: "需确认" }),
      frame(WAGENT_FRAMES.done, { status: "DEGRADED", degradations: ["DG-M2"] }),
    ]);
    const withAck = await streamWagentRunEvents(request);
    expect(withAck.ackRequired).toBe(true);

    mockStreamResponse([
      frame(WAGENT_FRAMES.meta, {}),
      frame(WAGENT_FRAMES.done, { status: "SUCCEEDED" }),
    ]);
    const noAck = await streamWagentRunEvents(request);
    expect(noAck.ackRequired).toBe(false);
    expect(noAck.degradationCodes).toEqual([]);
  });

  it("dgCode 缺失（后端落缺失标记）不入册，禁把「-」当一个降级码", async () => {
    mockStreamResponse([
      frame(WAGENT_FRAMES.meta, {}),
      frame(WAGENT_FRAMES.degrade, { dgCode: TEXT_ABSENT, requiresAck: false }),
      frame(WAGENT_FRAMES.done, { status: "DEGRADED" }),
    ]);
    const out = await streamWagentRunEvents(request);
    expect(out.degradationCodes).toEqual([]);
  });

  it("带 id 的帧置 resumeCapable 并记最后一个 id；本通道与 agent-loop 腿不同形", async () => {
    mockStreamResponse([
      frame(WAGENT_FRAMES.meta, {}, undefined),
      frame(WAGENT_FRAMES.step, { stepKey: "s1" }, "101"),
      frame(WAGENT_FRAMES.step, { stepKey: "s2" }, "102"),
      frame(WAGENT_FRAMES.done, { status: "SUCCEEDED" }),
    ]);
    const out = await streamWagentRunEvents(request);
    expect(out.resumeCapable).toBe(true);
    expect(out.lastEventId).toBe("102");
  });

  it("无 done 自然结束判为断线（不是契约违例），且计入 §4.4 断线", async () => {
    // 后端在流预算耗尽而 Run 未终态时刻意不发 done（发了就是冒充终态）
    mockStreamResponse([
      frame(WAGENT_FRAMES.meta, {}),
      frame(WAGENT_FRAMES.step, { stepKey: "s1" }, "1"),
    ]);
    const out = await streamWagentRunEvents(request);
    expect(out.completed).toBe(false);
    expect(out.failure?.kind).toBe("interrupted");
    expect(countsAsDisconnect(out.failure)).toBe(true);
    expect(isFallbackTriggered(1)).toBe(false);
    expect(isFallbackTriggered(3)).toBe(true);
  });

  it("非 SSE 应答（Content-Type 不符）判为契约违例，不计断线", async () => {
    mockStreamResponse(['{"message":"boom"}'], { contentType: "application/json" });
    const out = await streamWagentRunEvents(request);
    expect(out.failure?.kind).toBe("contract");
    expect(countsAsDisconnect(out.failure)).toBe(false);
  });

  it("非 2xx 只取信封 message + traceId，禁把原始响应体铺到界面", async () => {
    mockStreamResponse(
      ['{"message":"运行不存在","traceId":"tr-9","sql":"SELECT * FROM secret"}'],
      { status: 404, contentType: "application/json" },
    );
    const out = await streamWagentRunEvents(request);
    expect(out.failure).toEqual({ kind: "transport", status: 404, message: "运行不存在", traceId: "tr-9" });
    expect(JSON.stringify(out)).not.toContain("SELECT");
  });

  it("词表外帧名即停消费（含自拼第九帧），后续 done 不得再派发", async () => {
    mockStreamResponse([
      frame(WAGENT_FRAMES.meta, {}),
      frame("run_finished", { status: "SUCCEEDED" }),
      frame(WAGENT_FRAMES.done, { status: "SUCCEEDED" }),
    ]);
    const h = spyHandlers();
    const out = await streamWagentRunEvents(request, h);
    expect(out.failure?.kind).toBe("contract");
    expect(out.completed).toBe(false);
    expect(h.onDone).not.toHaveBeenCalled();
    expect(out.frames).toEqual([WAGENT_FRAMES.meta, "run_finished"]);
  });

  it("degrade 不是第九帧：词表认它，但八帧序位取缺失标记", async () => {
    mockStreamResponse([
      frame(WAGENT_FRAMES.meta, {}),
      frame(WAGENT_FRAMES.degrade, { dgCode: "DG-M1" }),
      frame(WAGENT_FRAMES.error, { errorCode: "E-WA-GEN", message: "m" }),
      frame(WAGENT_FRAMES.done, { status: "FAILED", degradations: ["DG-M1"] }),
    ]);
    const out = await streamWagentRunEvents(request);
    expect(out.failure).toBeNull(); // error 紧邻 done 之前＝合法失败腿
    expect(out.completed).toBe(true);
  });

  it("error 之后出非 done 帧＝在途即停（不等末帧才报，防已渲染正文留在界面）", async () => {
    mockStreamResponse([
      frame(WAGENT_FRAMES.meta, {}),
      frame(WAGENT_FRAMES.error, { errorCode: "E-WA-GEN" }),
      frame(WAGENT_FRAMES.delta, { text: "伪造正文" }),
    ]);
    const h = spyHandlers();
    const out = await streamWagentRunEvents(request, h);
    expect(out.failure?.kind).toBe("contract");
    expect(h.onDelta).not.toHaveBeenCalled();
  });

  it("首帧非 meta 即停；非 JSON 载荷即契约违例", async () => {
    mockStreamResponse([frame(WAGENT_FRAMES.step, {}), frame(WAGENT_FRAMES.done, {})]);
    expect((await streamWagentRunEvents(request)).failure?.kind).toBe("contract");

    mockStreamResponse(["event: meta\ndata: not-json\n\n", frame(WAGENT_FRAMES.done, {})]);
    const out = await streamWagentRunEvents(request);
    expect(out.failure).toEqual({ kind: "contract", detail: `帧 ${WAGENT_FRAMES.meta} 载荷非 JSON 对象` });
  });

  it("中止归入 aborted 且不计断线（点三次取消不该自动降级）", async () => {
    const ctrl = new AbortController();
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        const e = new Error("stopped");
        e.name = "AbortError";
        throw e;
      }),
    );
    const out = await streamWagentRunEvents({ ...request, abortSignal: ctrl.signal });
    expect(out.failure).toEqual({ kind: "aborted" });
    expect(countsAsDisconnect(out.failure)).toBe(false);
  });

  it("reader 抛错归入 interrupted（网络断，非服务端违约）", async () => {
    const body = {
      getReader: () => ({
        read: async () => {
          throw new TypeError("network gone");
        },
        cancel: vi.fn(async () => undefined),
      }),
    };
    vi.stubGlobal(
      "fetch",
      vi.fn(async () =>
        ({
          ok: true,
          status: 200,
          headers: new Headers({ "Content-Type": "text/event-stream" }),
          body,
          text: async () => "",
        }) as unknown as Response,
      ),
    );
    const out = await streamWagentRunEvents(request);
    expect(out.failure?.kind).toBe("interrupted");
    expect(countsAsDisconnect(out.failure)).toBe(true);
  });

  it("跨分包帧不被当未知帧：半截线路文本留在解析器里补齐", async () => {
    mockStreamResponse([
      `event: ${WAGENT_FRAMES.meta}\ndata: {"traceId":`,
      `"t"}\n\n`,
      `event: ${WAGENT_FRAMES.done}\ndata: {"status":"SUCCEEDED"}\n\n`,
    ]);
    const out = await streamWagentRunEvents(request);
    expect(out.failure).toBeNull();
    expect(out.completed).toBe(true);
    expect(out.frames).toEqual([WAGENT_FRAMES.meta, WAGENT_FRAMES.done]);
  });
});
