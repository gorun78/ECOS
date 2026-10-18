/**
 * SSE 线路级解析器用例（详设-06 §1.9.2 + 篇13 §13.4.4）。
 *
 * 每条用例对应 W3C eventsource「 interpreting text/event-stream」里的一条硬规则；
 * 之所以逐条写死：分块边界的 `\r` 挂起、空 data 不派发、残块丢弃这三条一旦回退，
 * 界面表现为「少一句正文」而不是报错，属于最难在联调里发现的假绿形态。
 * @license Apache-2.0
 */
// @vitest-environment node
import { describe, it, expect } from "vitest";
import { createSseParser, decodeFrameData } from "./sseParser";

const collect = (...chunks: string[]) => {
  const parser = createSseParser();
  const events = chunks.flatMap((c) => parser.push(c));
  return { parser, events };
};

describe("createSseParser", () => {
  it("splits CRLF delimited blocks", () => {
    const { events } = collect("event: meta\r\ndata: {\"traceId\":\"t1\"}\r\n\r\n");
    expect(events).toEqual([{ event: "meta", data: '{"traceId":"t1"}', id: null, retry: null }]);
  });

  it("treats a lone CR as a line delimiter too", () => {
    // 末位 \r 无法当场定性（可能是 \r\n 的前半，必须挂到下段），故第二段送注释行把空行的分隔符坐实
    const { parser, events } = collect('event: delta\rdata: {"text":"a"}\r\r', ": beat");
    expect(events).toEqual([{ event: "delta", data: '{"text":"a"}', id: null, retry: null }]);
    // 挂起的 \r 已在本段定性为分隔符，pending 只剩未闭合的注释行本身
    expect(parser.pendingLength).toBe(": beat".length);
  });

  it("does not invent a blank line when a chunk ends with the CR of CRLF", () => {
    const { parser, events } = collect("data: {\"text\":\"a\"}\r", "\n\r\n");
    expect(events).toHaveLength(1);
    expect(parser.pendingLength).toBe(0);
  });

  it("reassembles a block split across chunk boundaries", () => {
    const { events } = collect("event: del", "ta\ndata: {\"te", "xt\":\"hi\"}\n\n");
    expect(events).toEqual([{ event: "delta", data: '{"text":"hi"}', id: null, retry: null }]);
  });

  it("strips exactly one space after the colon", () => {
    const { events } = collect('data: {"text":" x"}\n\n');
    expect(events[0].data).toBe('{"text":" x"}');
    const noSpace = collect("data:{\"a\":1}\n\n");
    expect(noSpace.events[0].data).toBe('{"a":1}');
  });

  it("ignores comment lines (heartbeat / anti-buffering)", () => {
    const { events } = collect(": keep-alive\nevent: done\ndata: {}\n\n");
    expect(events).toEqual([{ event: "done", data: "{}", id: null, retry: null }]);
  });

  it("joins multi-line data with a newline", () => {
    const { events } = collect("data: line1\ndata: line2\n\n");
    expect(events[0].data).toBe("line1\nline2");
  });

  it("never dispatches a block without data (bare event/id/retry or blank line)", () => {
    const { events } = collect("event: step\n\nid: 42\n\nretry: 3000\n\n");
    expect(events).toEqual([]);
  });

  it("keeps id and retry, dropping a non-integer retry and a NUL-bearing id", () => {
    const { events } = collect("id: 7\nretry: not-a-number\nretry: 500\ndata: x\n\n");
    expect(events[0].id).toBe("7");
    expect(events[0].retry).toBe(500);
    const nul = collect('id: a\u0000b\ndata: x\n\n');
    expect(nul.events[0].id).toBeNull();
  });

  it("ignores unknown fields instead of failing the stream", () => {
    const { events } = collect("event: delta\ndata: {}\nfoo: bar\n\n");
    expect(events).toHaveLength(1);
  });

  it("discards an unterminated tail on end() and records the fact", () => {
    const { parser, events } = collect('event: delta\ndata: {"text":"half');
    expect(events).toHaveLength(0);
    parser.end();
    expect(parser.discardedIncompleteTail).toBe(true);
    expect(parser.pendingLength).toBe(0);
  });

  it("reports no discarded tail when the stream closed cleanly", () => {
    const { parser } = collect("event: done\ndata: {}\n\n");
    parser.end();
    expect(parser.discardedIncompleteTail).toBe(false);
  });
});

describe("decodeFrameData", () => {
  it("accepts a JSON object", () => {
    expect(decodeFrameData('{"traceId":"t"}')).toEqual({ traceId: "t" });
  });

  it("rejects arrays, scalars and malformed payloads so they cannot masquerade as frames", () => {
    expect(decodeFrameData("[1,2]")).toBeNull();
    expect(decodeFrameData('"text"')).toBeNull();
    expect(decodeFrameData("42")).toBeNull();
    expect(decodeFrameData("null")).toBeNull();
    expect(decodeFrameData("{oops")).toBeNull();
  });
});
