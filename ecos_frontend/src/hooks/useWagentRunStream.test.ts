/**
 * useWagentRunStream —— W run 流八帧消费面用例（#143/B5-5b-2）。
 *
 * 这里覆盖 hook 层的静默失真，不是承流客户端已钉过的线路层：正文被整段覆盖、error 清屏、
 * 缺失标记被当成一个降级码、contract/aborted 被算进断线 —— 四者都只表现为界面少一块内容
 * 或多一条假状态，不会抛错。
 *
 * 承流客户端整体 mock，但帧对象一律经 `wagentSseFrames` 的 reader 构造，禁手搓键形：
 * 手搓载荷与登记表漂移时 hook 读不到键照样绿，而界面会显示「未上报」。
 * @license Apache-2.0
 */
// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach } from "vitest";
import { act, renderHook } from "@testing-library/react";
import { SSE_FALLBACK_THRESHOLD, TEXT_ABSENT } from "../services/sseFrames";
import {
  WAGENT_SSE_KEYS,
  readWagentCitationFrame,
  readWagentDegradeFrame,
  readWagentDeltaFrame,
  readWagentDoneFrame,
  readWagentErrorFrame,
  readWagentMetaFrame,
  readWagentMilestoneFrame,
  readWagentStepFrame,
  readWagentTokenFrame,
} from "../services/wagentSseFrames";
import {
  streamWagentRunEvents,
  type WagentRunStreamHandlers,
  type WagentRunStreamOutcome,
  type WagentStreamFailure,
} from "../services/wagentRunStream";
import { useWagentRunStream, type WagentRunTurn } from "./useWagentRunStream";

vi.mock("../services/wagentRunStream", () => ({ streamWagentRunEvents: vi.fn() }));

const streamMock = vi.mocked(streamWagentRunEvents);

const delta = (text: string) => readWagentDeltaFrame({ [WAGENT_SSE_KEYS.text]: text });

function blankOutcome(partial: Partial<WagentRunStreamOutcome> = {}): WagentRunStreamOutcome {
  return {
    frames: [],
    completed: true,
    failure: null,
    sequenceViolation: null,
    discardedTail: false,
    lastEventId: null,
    resumeCapable: false,
    degradationCodes: [],
    ackRequired: false,
    ...partial,
  } as WagentRunStreamOutcome;
}

/** 派发帧 → 以给定结局收束，返回「本轮快照 + hook state」；两者必须同源 */
async function runTurn(
  emit: (handlers: WagentRunStreamHandlers) => void,
  partial: Partial<WagentRunStreamOutcome> = {},
) {
  streamMock.mockImplementation(async (_request, handlers = {}) => {
    emit(handlers);
    return blankOutcome(partial);
  });
  const { result } = renderHook(() => useWagentRunStream());
  let turn: WagentRunTurn = { content: "" } as WagentRunTurn;
  await act(async () => {
    turn = await result.current.subscribe({ runId: "run-1" });
  });
  return { turn, result };
}

async function subscribeUntilThreshold(
  failure: WagentStreamFailure,
  emit?: (handlers: WagentRunStreamHandlers) => void,
) {
  const { result } = renderHook(() => useWagentRunStream());
  for (let round = 0; round < SSE_FALLBACK_THRESHOLD; round += 1) {
    streamMock.mockImplementation(async (_request, handlers = {}) => {
      emit?.(handlers);
      return blankOutcome({ completed: false, failure });
    });
    await act(async () => {
      await result.current.subscribe({ runId: "run-1" });
    });
  }
  return result;
}

beforeEach(() => {
  streamMock.mockReset();
});

describe("useWagentRunStream 八帧消费面", () => {
  it("delta 逐帧追加而非整段覆盖，缺失标记不入正文", async () => {
    const { turn, result } = await runTurn((h) => {
      h.onDelta?.(delta("本季营收"));
      h.onDelta?.(delta(TEXT_ABSENT));
      h.onDelta?.(delta(" 132 万"));
    });
    expect(turn.content).toBe("本季营收 132 万");
    expect(result.current.state.content).toBe(turn.content);
  });

  it("step/milestone/citation 按出现序累积，meta/token/done 各归其位", async () => {
    const { turn } = await runTurn((h) => {
      h.onMeta?.(readWagentMetaFrame({ [WAGENT_SSE_KEYS.runId]: "run-1" }));
      h.onStep?.(readWagentStepFrame({ [WAGENT_SSE_KEYS.stepKey]: "s1" }));
      h.onMilestone?.(readWagentMilestoneFrame({ [WAGENT_SSE_KEYS.eventId]: "101" }));
      h.onStep?.(readWagentStepFrame({ [WAGENT_SSE_KEYS.stepKey]: "s2" }));
      h.onToken?.(readWagentTokenFrame({ [WAGENT_SSE_KEYS.promptTokens]: 12 }));
      h.onCitation?.(readWagentCitationFrame({ [WAGENT_SSE_KEYS.evidenceId]: "ev-1" }));
      h.onDone?.(readWagentDoneFrame({ [WAGENT_SSE_KEYS.status]: "SUCCEEDED" }));
    });
    expect(turn.steps.map((step) => step.stepKey)).toEqual(["s1", "s2"]);
    expect(turn.milestones.map((milestone) => milestone.eventId)).toEqual(["101"]);
    expect(turn.citations.map((citation) => citation.evidenceId)).toEqual(["ev-1"]);
    expect(turn.meta?.runId).toBe("run-1");
    expect(turn.usage?.promptTokens).toBe(12);
    expect(turn.doneFrame?.status).toBe("SUCCEEDED");
  });

  it("降级码按出现序去重一次，缺失标记不入册，done 的 degradations 并入", async () => {
    const { turn } = await runTurn((h) => {
      h.onDegrade?.(readWagentDegradeFrame({ [WAGENT_SSE_KEYS.dgCode]: "OPA_UNAVAILABLE" }));
      h.onDegrade?.(readWagentDegradeFrame({ [WAGENT_SSE_KEYS.dgCode]: "OPA_UNAVAILABLE" }));
      h.onDegrade?.(readWagentDegradeFrame({}));
      h.onDone?.(readWagentDoneFrame({ [WAGENT_SSE_KEYS.degradations]: ["CLS_DEGRADED", TEXT_ABSENT] }));
    });
    expect(turn.degradations).toHaveLength(3);
    expect(turn.degradationCodes).toEqual(["OPA_UNAVAILABLE", "CLS_DEGRADED"]);
  });

  it("ackRequired 只由降级帧决定；无降级帧即 false，非「已判定不需确认」", async () => {
    const clean = await runTurn((h) => {
      h.onDone?.(readWagentDoneFrame({ [WAGENT_SSE_KEYS.status]: "SUCCEEDED" }));
    });
    expect(clean.turn.ackRequired).toBe(false);

    const flagged = await runTurn((h) => {
      h.onDegrade?.(readWagentDegradeFrame({ [WAGENT_SSE_KEYS.dgCode]: "MASK_PARTIAL" }));
      h.onDegrade?.(
        readWagentDegradeFrame({
          [WAGENT_SSE_KEYS.dgCode]: "OPA_UNAVAILABLE",
          [WAGENT_SSE_KEYS.requiresAck]: true,
        }),
      );
    });
    expect(flagged.turn.ackRequired).toBe(true);
  });

  it("error 帧不清屏：已渲染正文与降级态一并留着", async () => {
    const { turn } = await runTurn((h) => {
      h.onDelta?.(delta("前半段正文"));
      h.onDegrade?.(readWagentDegradeFrame({ [WAGENT_SSE_KEYS.dgCode]: "CLS_DEGRADED" }));
      h.onError?.(readWagentErrorFrame({ [WAGENT_SSE_KEYS.errorCode]: "TOOL_TIMEOUT" }));
    });
    expect(turn.content).toBe("前半段正文");
    expect(turn.degradationCodes).toEqual(["CLS_DEGRADED"]);
    expect(turn.errorFrame?.errorCode).toBe("TOOL_TIMEOUT");
  });

  it("断线阈值单源：传输类才计数，contract/aborted 不计，降级回调只触发一次", async () => {
    const onFallbackToPoll = vi.fn();
    const { result } = renderHook(() => useWagentRunStream({ onFallbackToPoll }));
    const failures: WagentStreamFailure[] = [
      { kind: "transport", status: null, message: "网络不可达", traceId: null },
      { kind: "contract", detail: "帧序违例" },
      { kind: "aborted" },
      { kind: "transport", status: null, message: "网络不可达", traceId: null },
      { kind: "transport", status: null, message: "网络不可达", traceId: null },
    ];
    const counts: number[] = [];
    for (const failure of failures) {
      streamMock.mockImplementation(async () => blankOutcome({ completed: false, failure }));
      await act(async () => {
        await result.current.subscribe({ runId: "run-1" });
      });
      counts.push(result.current.state.disconnectCount);
    }
    expect(counts).toEqual([1, 1, 1, 2, SSE_FALLBACK_THRESHOLD]);
    expect(result.current.state.degradedToPoll).toBe(true);
    expect(onFallbackToPoll).toHaveBeenCalledTimes(1);
    expect(onFallbackToPoll).toHaveBeenCalledWith(SSE_FALLBACK_THRESHOLD);

    // 降级态跨轮保持：下一轮即使收 done，也禁假装恢复实时
    streamMock.mockImplementation(async () => blankOutcome());
    await act(async () => {
      await result.current.subscribe({ runId: "run-1" });
    });
    expect(result.current.state.degradedToPoll).toBe(true);
    expect(result.current.state.disconnectCount).toBe(SSE_FALLBACK_THRESHOLD);
  });

  it("未收 done 的自然结束计入断线，且续传凭据落到 state（本通道 resumeCapable 为真）", async () => {
    const { result } = await runTurn(() => undefined, {
      completed: false,
      resumeCapable: true,
      lastEventId: "102",
      failure: { kind: "interrupted", detail: "流在收到 done 之前中断" },
    });
    expect(result.current.state.resumeCapable).toBe(true);
    expect(result.current.state.lastEventId).toBe("102");
    expect(result.current.state.running).toBe(false);
    expect(result.current.state.disconnectCount).toBe(1);
  });

  it("abort 中止在途本轮的 signal（承流客户端拿到的就是同一个）", async () => {
    let release: () => void = () => undefined;
    let seen: AbortSignal | null = null;
    streamMock.mockImplementation(async (request) => {
      seen = request.abortSignal ?? null;
      await new Promise<void>((resolve) => {
        release = resolve;
      });
      return blankOutcome({ completed: false, failure: { kind: "aborted" } });
    });
    const { result } = renderHook(() => useWagentRunStream());
    let pending: Promise<WagentRunTurn> = Promise.resolve({ content: "" } as WagentRunTurn);
    await act(async () => {
      pending = result.current.subscribe({ runId: "run-1" });
      await Promise.resolve();
    });
    expect(result.current.state.running).toBe(true);
    expect(seen).toBeInstanceOf(AbortSignal);
    expect(seen?.aborted).toBe(false);
    act(() => result.current.abort());
    expect(seen?.aborted).toBe(true);
    await act(async () => {
      release();
      await pending;
    });
    expect(result.current.state.running).toBe(false);
  });

  it("reset 清零正文与跨轮降级态，且下一轮确实回到未降级判定", async () => {
    const result = await subscribeUntilThreshold(
      { kind: "interrupted", detail: "流在收到 done 之前中断" },
      (h) => h.onDelta?.(delta("上一轮正文")),
    );
    expect(result.current.state.content).toBe("上一轮正文");
    expect(result.current.state.degradedToPoll).toBe(true);

    act(() => result.current.reset());
    expect(result.current.state.content).toBe("");
    expect(result.current.state.disconnectCount).toBe(0);
    expect(result.current.state.degradedToPoll).toBe(false);
    expect(result.current.state.steps).toEqual([]);
    expect(result.current.state.degradations).toEqual([]);

    streamMock.mockImplementation(async () => blankOutcome());
    await act(async () => {
      await result.current.subscribe({ runId: "run-1" });
    });
    expect(result.current.state.degradedToPoll).toBe(false);
    expect(result.current.state.disconnectCount).toBe(0);
  });
});
