/**
 * SSE 帧契约用例（详设-06 §9.3.3 + 篇13 §13.4.5）：前端校验器与后端
 * `SseFrameContract.validateSequence` 必须同规则、同缺失标记口径。
 *
 * 关键反向用例：多轮流序列（step/token 在首批 delta 之后再出现一轮）**必须判合法**。
 * 若把「帧位单调不减」写成硬闸，这条会红 —— 那正是自造判据的形状。
 * @license Apache-2.0
 */
// @vitest-environment node
import { describe, it, expect } from "vitest";
import {
  AGENT_LOOP_FRAME_ORDER,
  CITATION_REQUIRED_KEYS,
  SSE_KEYS,
  TEXT_ABSENT,
  TOKEN_UNKNOWN,
  citationToEvidence,
  frameIndex,
  isCitationUsable,
  isDenialDecision,
  isKnownFrame,
  isToolStepOp,
  readCitationFrame,
  readDoneFrame,
  readMetaFrame,
  readStepFrame,
  readTokenFrame,
  readDeltaFrame,
  readErrorFrame,
  validateFrameSequence,
  validateFrameSequencePrefix,
} from "./sseFrames";

describe("validateFrameSequence", () => {
  it("accepts the full seven-frame sequence in declared order", () => {
    expect(validateFrameSequence(AGENT_LOOP_FRAME_ORDER)).toBeNull();
  });

  it("accepts a multi-round stream where step/token reappear after the first delta", () => {
    const multiRound = [
      "meta",
      "step",
      "delta",
      "step", // 第二轮 llm_call
      "token",
      "delta",
      "citation",
      "done",
    ];
    expect(validateFrameSequence(multiRound)).toBeNull();
  });

  it("rejects an empty sequence instead of calling it a clean run", () => {
    expect(validateFrameSequence([])).toMatch(/空序列/);
  });

  it("rejects a frame name outside the vocabulary", () => {
    expect(validateFrameSequence(["meta", "tool_call", "done"])).toMatch(/未知帧名: tool_call/);
  });

  it("rejects a sequence that does not start with meta", () => {
    expect(validateFrameSequence(["delta", "done"])).toMatch(/首帧必须为 meta，实得 delta/);
  });

  it("rejects a sequence that does not end with done", () => {
    expect(validateFrameSequence(["meta", "delta"])).toMatch(/末帧必须为 done，实得 delta/);
  });

  it("rejects two error frames", () => {
    expect(validateFrameSequence(["meta", "error", "error", "done"])).toMatch(/error 帧至多一条，实得 2 条/);
  });

  it("rejects an error frame that is not immediately before done", () => {
    expect(validateFrameSequence(["meta", "error", "delta", "done"])).toMatch(
      /error 帧必须紧接 done 之前，实得下标 1\/3/,
    );
  });

  it("accepts an error frame right before done", () => {
    expect(validateFrameSequence(["meta", "delta", "error", "done"])).toBeNull();
  });
});

describe("validateFrameSequencePrefix", () => {
  it("returns null for an empty in-flight sequence", () => {
    expect(validateFrameSequencePrefix([])).toBeNull();
  });

  it("flags a non-meta first frame the moment it arrives", () => {
    expect(validateFrameSequencePrefix(["delta"])).toMatch(/首帧必须为 meta/);
  });

  it("flags content after error without waiting for done", () => {
    expect(validateFrameSequencePrefix(["meta", "error", "delta"])).toMatch(/error 之后只准出 done/);
  });

  it("flags a second error frame", () => {
    expect(validateFrameSequencePrefix(["meta", "error", "error"])).toMatch(/error 帧至多一条/);
  });

  it("allows the pending tail of a healthy stream", () => {
    expect(validateFrameSequencePrefix(["meta", "step", "delta"])).toBeNull();
  });
});

describe("frame vocabulary helpers", () => {
  it("knows only the seven frames and indexes them from 1", () => {
    expect(isKnownFrame("citation")).toBe(true);
    expect(isKnownFrame("tool_result")).toBe(false);
    expect(frameIndex("meta")).toBe(1);
    expect(frameIndex("done")).toBe(7);
    expect(frameIndex("nope")).toBe(TOKEN_UNKNOWN);
  });

  it("restricts tool steps to tool_call/tool_result and denials to DENY/FAIL_CLOSED", () => {
    expect(isToolStepOp("tool_call")).toBe(true);
    expect(isToolStepOp("llm_call")).toBe(false);
    expect(isDenialDecision("DENY")).toBe(true);
    expect(isDenialDecision("FAIL_CLOSED")).toBe(true);
    expect(isDenialDecision("ALLOW")).toBe(false);
    expect(isDenialDecision("NA")).toBe(false);
  });
});

describe("frame payload readers never fabricate values", () => {
  it("maps absent numbers to -1 and absent strings to '-' (禁 0 冒充分项)", () => {
    const meta = readMetaFrame({});
    expect(meta.contextLimit).toBe(TOKEN_UNKNOWN);
    expect(meta.estimatedTokens).toBe(TOKEN_UNKNOWN);
    expect(meta.traceId).toBe(TEXT_ABSENT);
    expect(meta.model).toBe(TEXT_ABSENT);

    const token = readTokenFrame({});
    expect(token.promptTokens).toBe(TOKEN_UNKNOWN);
    expect(token.completionTokens).toBe(TOKEN_UNKNOWN);
    expect(token.estimated).toBe(false);

    const step = readStepFrame({});
    expect(step.step).toBe(TOKEN_UNKNOWN);
    expect(step.decision).toBe(TEXT_ABSENT);

    expect(readDeltaFrame({}).text).toBe(TEXT_ABSENT);
    expect(readErrorFrame({}).errorCode).toBe(TEXT_ABSENT);
  });

  it("keeps a reported zero as zero (缺失标记与实测 0 不混同)", () => {
    expect(readTokenFrame({ [SSE_KEYS.promptTokens]: 0 }).promptTokens).toBe(0);
    expect(readStepFrame({ [SSE_KEYS.step]: 0 }).step).toBe(0);
  });

  it("leaves unreported compression fields as null instead of false/0", () => {
    const done = readDoneFrame({ [SSE_KEYS.totalTokens]: 10 });
    expect(done.compressionMode).toBeNull();
    expect(done.compressedTurns).toBeNull();
    expect(done.compressSkipped).toBeNull();
    expect(done.retryable).toBeNull();
    expect(done.success).toBe(false);
    expect(done.turns).toBe(TOKEN_UNKNOWN);
  });

  it("reads the failure leg of done without borrowing from the error frame", () => {
    const done = readDoneFrame({
      [SSE_KEYS.success]: false,
      [SSE_KEYS.errorCode]: "AI-504",
      [SSE_KEYS.errorClass]: "LLM_TIMEOUT",
      [SSE_KEYS.retryable]: true,
      [SSE_KEYS.message]: "timeout",
      [SSE_KEYS.compressionMode]: "SLIDING_WINDOW",
      [SSE_KEYS.compressedTurns]: 4,
      [SSE_KEYS.compressSkipped]: false,
    });
    expect(done.errorCode).toBe("AI-504");
    expect(done.errorClass).toBe("LLM_TIMEOUT");
    expect(done.retryable).toBe(true);
    expect(done.compressionMode).toBe("SLIDING_WINDOW");
    expect(done.compressedTurns).toBe(4);
    expect(done.compressSkipped).toBe(false);
  });

  it("drops a non-array links field rather than wrapping a stray value", () => {
    expect(readCitationFrame({ [SSE_KEYS.links]: "http://x" }).links).toEqual([]);
    expect(
      readCitationFrame({ [SSE_KEYS.links]: ["a", 1, "b"] }).links,
    ).toEqual(["a", "b"]);
  });
});

describe("citation usability & evidence triple", () => {
  const full = {
    [SSE_KEYS.forecastRunId]: "run-1",
    [SSE_KEYS.caliberId]: "cal-1",
    [SSE_KEYS.version]: "v3",
    [SSE_KEYS.asOfTime]: "2026-09-30",
    [SSE_KEYS.links]: ["minio://a"],
  };

  it("requires all four elements and names them", () => {
    expect(CITATION_REQUIRED_KEYS).toHaveLength(4);
    expect(isCitationUsable(readCitationFrame(full))).toBe(true);
    expect(isCitationUsable(readCitationFrame({ ...full, [SSE_KEYS.version]: "" }))).toBe(false);
  });

  it("builds source/version/snapshot without padding missing parts", () => {
    const evidence = citationToEvidence(readCitationFrame(full));
    expect(evidence.source).toBe("forecastRunId=run-1 | caliberId=cal-1");
    expect(evidence.version).toBe("v3");
    expect(evidence.snapshot).toBe("asOfTime=2026-09-30 | minio://a");

    const partial = citationToEvidence(readCitationFrame({ [SSE_KEYS.caliberId]: "cal-9" }));
    expect(partial.source).toBe("forecastRunId=- | caliberId=cal-9");
    expect(partial.version).toBe(TEXT_ABSENT);
  });
});
