/**
 * WAgent run 流承流客户端（详设-10 §10.3.6 八帧 + 补充帧 milestone；#143/B5-5b-2）。
 *
 * 与 `agentLoopStream`（agent-loop 七帧）分文件的理由与后端一致：两条通道同名帧不同键形，
 * 合一套 switch 就会让「借用另一通道的帧」在码面上看不出来。词表/键面/校验全部
 * import 自 `wagentSseFrames`（前端镜像单源），禁在本模块写帧名字面量。
 *
 * 三条通道事实（照登，不套用 agent-loop 的结论）：
 *
 * 1. **端点是 GET，`Last-Event-ID` 是后端真消费的续传凭据**。后端 `sinkOf` 对带行 id 的帧
 *    （step/milestone）写 `id:` 位，泵侧按 run_event 行 id 定位后序回放 ⇒ 本通道
 *    `resumeCapable` 与 agent-loop 腿（后端不发 id，恒 null，D-B5-9）**不同形**，
 *    禁把两条通道的续传能力写成同一个结论。
 * 2. **无 done 而流自然结束 = 断线（interrupted），不是契约违例**。后端在流预算耗尽
 *    且 Run 未终态时刻意不发 done（发了就是冒充终态），前端若把这判成 contract，
 *    §4.4 的断线计数就永远收不到这一类，降级到轮询也不会触发。
 * 3. **不做静默自动重试**。Run 含工具副作用，重发＝重复执行；断线口径复用
 *    `agentLoopStream#countsAsDisconnect`（§4.4 单源，aborted/contract 不计），
 *    禁在此另写一套计数规则。
 *
 * 基址单源：URL 一律由 `httpClient#API_BASE` 派生（W206/C188 门禁，禁硬编码前缀）。
 * @license Apache-2.0
 */

import { API_BASE } from "./httpClient";
import { authHeaders } from "./auth";
import { createSseParser, decodeFrameData } from "./sseParser";
import {
  SSE_ACCEPT,
  countsAsDisconnect,
  isFallbackTriggered,
  type AgentLoopStreamFailure,
} from "./agentLoopStream";
import {
  WAGENT_FRAMES,
  WAGENT_MILESTONE_FRAME,
  anyRequiresAck,
  dedupeDegradationCodes,
  isKnownWagentFrame,
  readWagentCitationFrame,
  readWagentDegradeFrame,
  readWagentDeltaFrame,
  readWagentDoneFrame,
  readWagentErrorFrame,
  readWagentMetaFrame,
  readWagentMilestoneFrame,
  readWagentStepFrame,
  readWagentTokenFrame,
  validateWagentSequence,
  validateWagentSequencePrefix,
  type WagentCitationFrame,
  type WagentDegradeFrame,
  type WagentDeltaFrame,
  type WagentDoneFrame,
  type WagentErrorFrame,
  type WagentMetaFrame,
  type WagentMilestoneFrame,
  type WagentStepFrame,
  type WagentTokenFrame,
} from "./wagentSseFrames";

/** §5.2 #7 SSE 实流通道（RUN_EVENTS_SSE_STREAM）；`/api` 前缀由 API_BASE 单源补 */
export function wagentRunEventsStreamPath(runId: string): string {
  return `/v1/wagent/runs/${encodeURIComponent(runId)}/events/stream`;
}

/**
 * `text/event-stream` 常量单源在 `agentLoopStream`（两条通道同一个 Accept 值，
 * 各写一份会让「改了 agent-loop 腿的 Accept」静默漏掉本腿）。此处只 re-export。
 */
export { SSE_ACCEPT };

export interface WagentRunStreamRequest {
  runId: string;
  /** 上次已消费到的 SSE id（本通道后端真发 id ⇒ 非 agent-loop 的恒 null） */
  lastEventId?: string | null;
  abortSignal?: AbortSignal;
}

export interface WagentRunStreamHandlers {
  onMeta?: (frame: WagentMetaFrame) => void;
  onStep?: (frame: WagentStepFrame) => void;
  onToken?: (frame: WagentTokenFrame) => void;
  /** 增量正文：调用方必须追加，禁整段覆盖（§13.4.5） */
  onDelta?: (frame: WagentDeltaFrame) => void;
  onCitation?: (frame: WagentCitationFrame) => void;
  /** 帧 6：降级帧——降级横幅的单源读取位（§10.3.6 原注） */
  onDegrade?: (frame: WagentDegradeFrame) => void;
  /** 补充帧：Run 生命周期里程碑（非第九帧，无正文） */
  onMilestone?: (frame: WagentMilestoneFrame) => void;
  onError?: (frame: WagentErrorFrame) => void;
  onDone?: (frame: WagentDoneFrame) => void;
}

/** 与 agent-loop 腿同一套四类互斥结局（禁折叠成「出错了」） */
export type WagentStreamFailure = AgentLoopStreamFailure;

export interface WagentRunStreamOutcome {
  /** 已消费的帧名序列（含未知帧 ⇒ 供终态校验复现违例） */
  frames: string[];
  /** 是否收到 done 帧（本通道在无终态时合法缺席，见文件头裁决 2） */
  completed: boolean;
  failure: WagentStreamFailure | null;
  /** 末帧后帧序违例（与后端 validateWagentSequence 同规则） */
  sequenceViolation: string | null;
  /** 流尾有未闭合残块被丢弃 */
  discardedTail: boolean;
  lastEventId: string | null;
  /** 后端是否下发过 id ⇒ 本通道为真（与 agent-loop 腿不同形，禁共用结论） */
  resumeCapable: boolean;
  /** 本流出现过的降级码（帧 6 ∪ 帧 8 degradations[]，按出现序去重） */
  degradationCodes: string[];
  /** 是否有任一降级帧要求确认（requiresAck）；无降级帧即 false，非「已判定不需确认」 */
  ackRequired: boolean;
}

function isAbortError(e: unknown): boolean {
  return e instanceof Error && (e.name === "AbortError" || e.name === "TimeoutError");
}

/** 非 2xx / 非 SSE 应答：只取信封里的 `message` + `traceId`，禁把原始响应体铺到界面。 */
async function describeNonStreamError(res: Response): Promise<WagentStreamFailure> {
  let message = `HTTP ${res.status}`;
  let traceId: string | null = res.headers.get("X-ECOS-Trace-Id");
  try {
    const text = await res.text();
    const parsed: unknown = text ? JSON.parse(text) : null;
    if (parsed && typeof parsed === "object") {
      const bag = parsed as Record<string, unknown>;
      if (typeof bag.message === "string" && bag.message.trim()) message = bag.message;
      if (!traceId && typeof bag.traceId === "string") traceId = bag.traceId;
    }
  } catch {
    // 响应体非 JSON：保持 `HTTP <status>`，不猜文案
  }
  return { kind: "transport", status: res.status, message, traceId };
}

/**
 * 订阅一个 Run 的事件流，直到 done／断连／主动中止。
 *
 * 不 throw：结局四分类必须显式落地。§10.3.6 的 token/delta/citation 三帧当前零生产者
 * （后端裁决「不发即无假观测」），本函数照常解析——出现即按登记表形消费，
 * 禁因「后端没发过」而当未知帧停流。
 */
export async function streamWagentRunEvents(
  request: WagentRunStreamRequest,
  handlers: WagentRunStreamHandlers = {},
): Promise<WagentRunStreamOutcome> {
  const frames: string[] = [];
  const degradationCodes: string[] = [];
  const outcome: WagentRunStreamOutcome = {
    frames,
    completed: false,
    failure: null,
    sequenceViolation: null,
    discardedTail: false,
    lastEventId: null,
    resumeCapable: false,
    degradationCodes,
    ackRequired: false,
  };

  const headers: Record<string, string> = { ...authHeaders(), Accept: SSE_ACCEPT };
  if (request.lastEventId) headers["Last-Event-ID"] = request.lastEventId;

  let res: Response;
  try {
    res = await fetch(`${API_BASE}${wagentRunEventsStreamPath(request.runId)}`, {
      method: "GET",
      headers,
      signal: request.abortSignal,
    });
  } catch (e: unknown) {
    outcome.failure = isAbortError(e)
      ? { kind: "aborted" }
      : { kind: "transport", status: null, message: "网络不可达或网关无应答", traceId: null };
    return outcome;
  }

  if (!res.ok) {
    outcome.failure = await describeNonStreamError(res);
    return outcome;
  }
  const contentType = res.headers.get("Content-Type") || "";
  if (!contentType.includes(SSE_ACCEPT) || !res.body) {
    outcome.failure = {
      kind: "contract",
      detail: `服务端未按 SSE 应答（实得 Content-Type=${contentType || "缺失"}）`,
    };
    return outcome;
  }

  /**
   * 降级面汇总：判据（缺失标记不入册 + 按出现序去重 / 任一 requiresAck）单源在
   * `wagentSseFrames`，与消费 hook 同规则；本处只做「帧 6 ∪ 帧 8」的累积，禁各写一份。
   */
  const degradeFrames: WagentDegradeFrame[] = [];
  const refreshDegradation = (doneCodes: readonly string[] = []): void => {
    const next = dedupeDegradationCodes([...degradeFrames.map((frame) => frame.dgCode), ...doneCodes]);
    degradationCodes.length = 0;
    degradationCodes.push(...next);
    outcome.ackRequired = anyRequiresAck(degradeFrames);
  };

  const consume = (event: { event: string; data: string; id: string | null }): boolean => {
    frames.push(event.event);
    if (event.id !== null) {
      outcome.lastEventId = event.id;
      outcome.resumeCapable = true;
    }
    const prefixViolation = validateWagentSequencePrefix(frames);
    if (prefixViolation) {
      outcome.failure = { kind: "contract", detail: prefixViolation };
      return false;
    }
    const payload = decodeFrameData(event.data);
    if (payload === null) {
      outcome.failure = { kind: "contract", detail: `帧 ${event.event} 载荷非 JSON 对象` };
      return false;
    }
    switch (event.event) {
      case WAGENT_FRAMES.meta:
        handlers.onMeta?.(readWagentMetaFrame(payload));
        break;
      case WAGENT_FRAMES.step:
        handlers.onStep?.(readWagentStepFrame(payload));
        break;
      case WAGENT_FRAMES.token:
        handlers.onToken?.(readWagentTokenFrame(payload));
        break;
      case WAGENT_FRAMES.delta:
        handlers.onDelta?.(readWagentDeltaFrame(payload));
        break;
      case WAGENT_FRAMES.citation:
        handlers.onCitation?.(readWagentCitationFrame(payload));
        break;
      case WAGENT_FRAMES.degrade: {
        const frame = readWagentDegradeFrame(payload);
        degradeFrames.push(frame);
        refreshDegradation();
        handlers.onDegrade?.(frame);
        break;
      }
      case WAGENT_MILESTONE_FRAME:
        handlers.onMilestone?.(readWagentMilestoneFrame(payload));
        break;
      case WAGENT_FRAMES.error:
        handlers.onError?.(readWagentErrorFrame(payload));
        break;
      case WAGENT_FRAMES.done: {
        const frame = readWagentDoneFrame(payload);
        refreshDegradation(frame.degradations);
        handlers.onDone?.(frame);
        outcome.completed = true;
        break;
      }
      default:
        // isKnownWagentFrame 已在 prefix 校验里挡住，留显式违例出口防静默吞帧
        outcome.failure = { kind: "contract", detail: `未知帧名: ${event.event}` };
        return false;
    }
    return true;
  };

  const parser = createSseParser();
  const decoder = new TextDecoder();
  const reader = res.body.getReader();

  /** 逐块派发；返回 false ⇒ 已定违例，停止消费 */
  const drain = (chunks: ReturnType<typeof parser.push>): boolean => {
    for (const event of chunks) {
      if (!isKnownWagentFrame(event.event)) {
        frames.push(event.event);
        outcome.failure = {
          kind: "contract",
          detail: `未知 WAgent 帧名: ${event.event}（词表单源=SseFrameContract）`,
        };
        return false;
      }
      if (!consume(event)) return false;
    }
    return true;
  };

  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      if (!drain(parser.push(decoder.decode(value, { stream: true })))) {
        void reader.cancel().catch((): void => undefined);
        break;
      }
    }
  } catch (e: unknown) {
    outcome.failure = isAbortError(e)
      ? { kind: "aborted" }
      : { kind: "interrupted", detail: "流在收到 done 之前中断" };
  } finally {
    if (!outcome.failure) {
      drain(parser.push(decoder.decode()));
    }
    parser.end();
    outcome.discardedTail = parser.discardedIncompleteTail;
  }

  if (outcome.failure === null) {
    if (!outcome.completed) {
      outcome.failure = { kind: "interrupted", detail: "流在收到 done 之前中断" };
    } else {
      outcome.sequenceViolation = validateWagentSequence(frames);
      if (outcome.sequenceViolation) {
        outcome.failure = { kind: "contract", detail: outcome.sequenceViolation };
      }
    }
  }
  return outcome;
}

/** §4.4 断线口径转口（单源在 agentLoopStream，本模块只 re-export 免第二判据） */
export const wagentCountsAsDisconnect = countsAsDisconnect;
export const wagentIsFallbackTriggered = isFallbackTriggered;
