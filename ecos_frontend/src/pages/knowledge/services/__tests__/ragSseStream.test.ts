/**
 * KB RAG 承流用例 —— D-B5-11 的第二处第二实现面（`pages/knowledge/services/govern.ts`）。
 *
 * 手切形态按 `chunk.split('\n')` 逐行匹配 `^data:`，于是：
 * - 一条 data 被 TCP 分包切成两段时，前半被当成完整 token 吐给界面、后半直接丢失（内容缺一截）；
 * - 同一条事件的多行 data 被当成多件事件（SSE 规范应为一件）；
 * - 后端不支持流式（GET `/rag?stream=true` 实无此端点，见 D-B5-12）时零 token 却返回成功，
 *   界面表现为「回答空白但没报错」。
 * 三条判据各自对应上面一种静默失真。
 * @license Apache-2.0
 */
// @vitest-environment node
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { runRAGQuerySSE } from "../govern";

vi.mock("../../../../services/auth", () => ({
  getAuthToken: () => "test-token",
}));

const encoder = new TextEncoder();

/** 第 1 次 fetch = SSE GET（逐段吐 chunks），第 2 次 = POST 兜底（返回 JSON） */
function stubFetch(sseChunks: string[], fallbackBody: unknown) {
  const calls: { url: string; init?: RequestInit }[] = [];
  const fetchMock = vi.fn(async (url: string, init: RequestInit = {}) => {
    calls.push({ url: String(url), init });
    if (String(url).includes("stream=true")) {
      let i = 0;
      return {
        ok: true,
        status: 200,
        headers: new Headers({ "Content-Type": "text/event-stream" }),
        body: {
          getReader: () => ({
            read: async () =>
              i >= sseChunks.length
                ? { done: true, value: undefined }
                : { done: false, value: encoder.encode(sseChunks[i++]) },
            cancel: vi.fn(async () => undefined),
          }),
        },
      } as unknown as Response;
    }
    return {
      ok: true,
      status: 200,
      headers: new Headers({ "Content-Type": "application/json" }),
      // 兜底腿自 #143/B5-11c 起经单源 `apiFetchEnvelope`，该通道先 `res.text()` 再自行 parse
      // （真实 Response 同时具备 text()/json() 两面 ⇒ 桩必须补齐，否则是桩缺面而不是产品回归）
      text: async () => JSON.stringify(fallbackBody),
      json: async () => fallbackBody,
    } as unknown as Response;
  });
  vi.stubGlobal("fetch", fetchMock);
  return { calls, fetchMock };
}

beforeEach(() => {
  vi.spyOn(console, "info").mockImplementation(() => undefined);
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("KB RAG 承流 —— 线路级单源（D-B5-11）", () => {
  it("does not truncate a token when its data line is split across chunks", async () => {
    stubFetch(["data: 四", "季营收\n\n"], {});
    const onToken = vi.fn();

    await runRAGQuerySSE("营收多少", onToken);

    expect(onToken).toHaveBeenCalledTimes(1);
    expect(onToken).toHaveBeenCalledWith("四季营收");
  });

  it("emits one token for a multi-line data event (SSE 规范：多行 data 属同一事件)", async () => {
    stubFetch(["data: 上游\ndata: 下游\n\n"], {});
    const onToken = vi.fn();

    await runRAGQuerySSE("口径", onToken);

    expect(onToken).toHaveBeenCalledTimes(1);
    expect(onToken).toHaveBeenCalledWith("上游\n下游");
  });

  it("keeps LF and CRLF streams equivalent", async () => {
    const lf = stubFetch(["data: A\n\ndata: B\n\n"], {});
    const lfTokens: string[] = [];
    await runRAGQuerySSE("q", (t) => lfTokens.push(t));
    expect(lfTokens).toEqual(["A", "B"]);
    expect(lf.calls[0].url).toContain("stream=true");

    const crlf = stubFetch(["data: A\r\n\r\ndata: B\r\n\r\n"], {});
    const crlfTokens: string[] = [];
    await runRAGQuerySSE("q", (t) => crlfTokens.push(t));
    expect(crlfTokens).toEqual(["A", "B"]);
    expect(crlf.calls[0].init.headers).toBeTruthy();
  });

  it("falls back to the non-stream endpoint when the SSE leg yields zero tokens", async () => {
    // 后端 GET /rag?stream=true 端点实不存在（D-B5-12）：应答是一个普通 JSON 体、零 data 帧。
    // 旧形态在此静默返回成功 ⇒ 界面「回答空白但不报错」。必须显式转兜底通道。
    const { calls } = stubFetch(['{"success":true,"data":{"answer":"兜底答案"}}'], {
      success: true,
      data: { answer: "兜底答案", answerGenerated: true },
    });
    const onToken = vi.fn();

    const result = await runRAGQuerySSE("营收多少", onToken);

    expect(result.answerGenerated).toBe(true);
    expect(onToken).toHaveBeenCalledWith("兜底答案");
    const methods = calls.map((c) => String(c.init.method ?? "GET"));
    expect(methods).toContain("POST");
  });

  it("still degrades to POST when the SSE request itself fails", async () => {
    stubFetch([], { success: true, data: { answer: "兜底答案", answerGenerated: true } });
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string, init: RequestInit = {}) => {
        if (String(url).includes("stream=true")) throw new Error("SSE 503");
        return {
          ok: true,
          status: 200,
          headers: new Headers({ "Content-Type": "application/json" }),
          text: async () => JSON.stringify({ success: true, data: { answer: "兜底答案", answerGenerated: true } }),
          json: async () => ({ success: true, data: { answer: "兜底答案", answerGenerated: true } }),
        } as unknown as Response;
      }),
    );
    const onToken = vi.fn();

    const result = await runRAGQuerySSE("q", onToken);

    expect(result.answerGenerated).toBe(true);
    expect(onToken).toHaveBeenCalledWith("兜底答案");
  });
});
