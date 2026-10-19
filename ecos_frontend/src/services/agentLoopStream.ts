/**
 * Agent Loop 承流客户端（详设-06 §9.3.3 七帧 + 篇13 §13.4.5）—— 前端唯一 SSE 消费通道。
 *
 * 三条落地裁决（照登于差距分析 #143/B5-3，禁静默假设）：
 *
 * 1. **端点是 POST，`EventSource` 不可用**。§13.4.5 写的「EventSource 自动携带
 *    Last-Event-ID → BFF 透传 → gateway 不 strip」在现签名下不成立：
 *    `POST /v1/agent-loop/chat`（`Accept: text/event-stream` 与同路径 JSON 端点分流），
 *    而 EventSource 只能 GET。API 只增不改 ⇒ 本通道用 fetch + ReadableStream，
 *    `Last-Event-ID` 由调用方手工透传（见 `request.lastEventId`）。
 * 2. **后端 `SseEmitter.event()` 未设 `.id(...)` ⇒ 无续播凭据**（D-B5-9）。
 *    本模块照实解析 `id:`（有则记 `lastEventId`、置 `resumeCapable`），
 *    但当前恒为 null ⇒ 断线重连＝整轮重跑，不是续播。禁在前端把重连渲染成「接着上次」。
 * 3. **不做静默自动重试**。agent-loop 一轮里含工具执行副作用，重发＝重复执行；
 *    §4.4 只要求「断线 3 次自动切轮询」，故断线计数与降级由消费方（`useAgentLoopStream`）
 *    决定，本模块只如实报告一次尝试的结局。
 *
 * 基址单源：URL 一律由 `httpClient#API_BASE` 派生（W206/C188 门禁，禁硬编码前缀）。
 * @license Apache-2.0
 */

import { API_BASE, rawStreamUrlFetch } from "./httpClient";
import { createSseParser, decodeFrameData } from "./sseParser";
import {
  SSE_FALLBACK_THRESHOLD,
  isKnownFrame,
  readCitationFrame,
  readDoneFrame,
  readDeltaFrame,
  readErrorFrame,
  readMetaFrame,
  readStepFrame,
  readTokenFrame,
  validateFrameSequence,
  validateFrameSequencePrefix,
  type AgentLoopCitationFrame,
  type AgentLoopDoneFrame,
  type AgentLoopDeltaFrame,
  type AgentLoopErrorFrame,
  type AgentLoopMetaFrame,
  type AgentLoopStepFrame,
  type AgentLoopTokenFrame,
} from "./sseFrames";

/** 流式对话端点相对路径（拼在 API_BASE 之后；同路径 JSON 端点靠 Accept 分流） */
export const AGENT_LOOP_CHAT_REL_PATH = "/v1/agent-loop/chat";
/** 必须显式声明的 Accept，否则 Spring 选到非流式 `@PostMapping("/chat")` */
export const SSE_ACCEPT = "text/event-stream";

export interface AgentLoopChatRequest {
  agentId: string;
  message: string;
  sessionId?: string;
  model?: string;
  temperature?: number;
  maxTokens?: number;
  systemPrompt?: string;
  /** 会话线程标识，后端默认 "main" */
  thread?: string;
  /** 上次已消费到的 SSE id（当前后端不发 id ⇒ 恒缺，见文件头裁决 2） */
  lastEventId?: string | null;
  abortSignal?: AbortSignal;
}

export interface AgentLoopStreamHandlers {
  onMeta?: (frame: AgentLoopMetaFrame) => void;
  onStep?: (frame: AgentLoopStepFrame) => void;
  onToken?: (frame: AgentLoopTokenFrame) => void;
  /** 增量正文：调用方必须追加，禁整段覆盖（§13.4.5） */
  onDelta?: (frame: AgentLoopDeltaFrame) => void;
  onCitation?: (frame: AgentLoopCitationFrame) => void;
  onError?: (frame: AgentLoopErrorFrame) => void;
  onDone?: (frame: AgentLoopDoneFrame) => void;
}

/** 一次承流的失败分类（四类互斥，禁折叠成「出错了」） */
export type AgentLoopStreamFailure =
  | { kind: "transport"; status: number | null; message: string; traceId: string | null }
  | { kind: "contract"; detail: string }
  | { kind: "interrupted"; detail: string }
  | { kind: "aborted" };

export interface AgentLoopStreamOutcome {
  /** 已消费的帧名序列（含未知帧 ⇒ 供终态校验复现违例） */
  frames: string[];
  /** 是否收到 done 帧 */
  completed: boolean;
  failure: AgentLoopStreamFailure | null;
  /** 终态帧序违例（与后端 validateSequence 同规则）；无违例为 null */
  sequenceViolation: string | null;
  /** 流尾有未闭合残块被丢弃（§13.4.5 半截正文不得当事件消费） */
  discardedTail: boolean;
  /** 最后一个 `id:` 值；后端未发 id 时恒 null */
  lastEventId: string | null;
  /** 后端是否下发过 id ⇒ false 时禁把重连描述成续播 */
  resumeCapable: boolean;
}

function isAbortError(e: unknown): boolean {
  return e instanceof Error && (e.name === "AbortError" || e.name === "TimeoutError");
}

/**
 * §4.4 断线口径（纯函数，供无 DOM 单测钉住）：只有「连接断了/没跑完」才计入断线。
 *
 * `aborted`＝用户主动停，`contract`＝服务端违约（重发也照样违例，降级到轮询也救不了）。
 * 把这两类计入断线会造出「点三次取消就自动降级」的假故障，故一律不计。
 */
export function countsAsDisconnect(failure: AgentLoopStreamFailure | null): boolean {
  return failure !== null && (failure.kind === "interrupted" || failure.kind === "transport");
}

/** §4.4 阈值口径：累计断线达 SSE_FALLBACK_THRESHOLD ⇒ 后续轮次改走兜底通道（本轮不补跑）。 */
export function isFallbackTriggered(disconnectCount: number): boolean {
  return disconnectCount >= SSE_FALLBACK_THRESHOLD;
}

/**
 * 非 2xx / 非 SSE 应答的原因提取：只取信封里的 `message` + `traceId`
 * 两个业务字段，禁把原始响应体（可能含 DSN、SQL、堆栈）铺到界面。
 */
async function describeNonStreamError(res: Response): Promise<AgentLoopStreamFailure> {
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
 * 发起一次流式对话。不 throw（AbortError 亦归入 outcome.failure）——
 * 承流结局必须四类之一显式落地，禁靠异常类型把「断线」和「契约违例」混成一条 toast。
 */
export async function streamAgentLoopChat(
  request: AgentLoopChatRequest,
  handlers: AgentLoopStreamHandlers = {},
): Promise<AgentLoopStreamOutcome> {
  const frames: string[] = [];
  const outcome: AgentLoopStreamOutcome = {
    frames,
    completed: false,
    failure: null,
    sequenceViolation: null,
    discardedTail: false,
    lastEventId: null,
    resumeCapable: false,
  };

  const url =
    `${API_BASE}${AGENT_LOOP_CHAT_REL_PATH}` +
    (request.thread ? `?thread=${encodeURIComponent(request.thread)}` : "");
  // 头集合与改前 `{...authHeaders(), Accept}` 逐键一致：Authorization 由单源拼，
  // Content-Type 是改前 authHeaders() 的既有键 ⇒ 必须在此显式补回（不新增、不删除键）。
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    Accept: SSE_ACCEPT,
  };
  if (request.lastEventId) headers["Last-Event-ID"] = request.lastEventId;

  let res: Response;
  try {
    res = await rawStreamUrlFetch(url, {
      method: "POST",
      headers,
      body: JSON.stringify({
        agentId: request.agentId,
        message: request.message,
        ...(request.sessionId ? { sessionId: request.sessionId } : {}),
        ...(request.model ? { model: request.model } : {}),
        ...(request.temperature !== undefined ? { temperature: request.temperature } : {}),
        ...(request.maxTokens !== undefined ? { maxTokens: request.maxTokens } : {}),
        ...(request.systemPrompt ? { systemPrompt: request.systemPrompt } : {}),
      }),
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

  const parser = createSseParser();
  const decoder = new TextDecoder();
  const reader = res.body.getReader();

  /** 逐帧派发；返回 false ⇒ 契约违例，停止消费 */
  const consume = (event: { event: string; data: string; id: string | null }): boolean => {
    frames.push(event.event);
    if (event.id !== null) {
      outcome.lastEventId = event.id;
      outcome.resumeCapable = true;
    }
    const prefixViolation = validateFrameSequencePrefix(frames);
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
      case "meta":
        handlers.onMeta?.(readMetaFrame(payload));
        break;
      case "step":
        handlers.onStep?.(readStepFrame(payload));
        break;
      case "token":
        handlers.onToken?.(readTokenFrame(payload));
        break;
      case "delta":
        handlers.onDelta?.(readDeltaFrame(payload));
        break;
      case "citation":
        handlers.onCitation?.(readCitationFrame(payload));
        break;
      case "error":
        handlers.onError?.(readErrorFrame(payload));
        break;
      case "done":
        handlers.onDone?.(readDoneFrame(payload));
        outcome.completed = true;
        break;
      default:
        // isKnownFrame 已在 prefix 校验里挡住，正常不会到这里；留显式违例出口防静默吞帧
        outcome.failure = { kind: "contract", detail: `未知帧名: ${event.event}` };
        return false;
    }
    return true;
  };

  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      const events = parser.push(decoder.decode(value, { stream: true }));
      for (const event of events) {
        if (!isKnownFrame(event.event)) {
          frames.push(event.event);
          outcome.failure = {
            kind: "contract",
            detail: `未知帧名: ${event.event}（词表单源=SseFrameContract）`,
          };
          break;
        }
        if (!consume(event)) break;
      }
      if (outcome.failure) {
        void reader.cancel().catch((): void => undefined);
        break;
      }
    }
  } catch (e: unknown) {
    outcome.failure = isAbortError(e)
      ? { kind: "aborted" }
      : { kind: "interrupted", detail: "流在收到 done 之前中断" };
  } finally {
    // 收尾把 decoder 里可能的半字符也吐给 parser（不影响 discardedTail 判定，仅减少假残块）
    if (!outcome.failure) {
      for (const event of parser.push(decoder.decode())) {
        if (!isKnownFrame(event.event)) {
          frames.push(event.event);
          outcome.failure = {
            kind: "contract",
            detail: `未知帧名: ${event.event}（词表单源=SseFrameContract）`,
          };
          break;
        }
        if (!consume(event)) break;
      }
    }
    parser.end();
    outcome.discardedTail = parser.discardedIncompleteTail;
  }

  if (outcome.failure === null) {
    if (!outcome.completed) {
      // 无 done 而流自然结束 = 断线，不是契约违例。「末帧必须为 done」只是截断的后果，
      // 归到 contract 会让消费方的断线计数（§4.4 三次切轮询）永远收不到这一类。
      outcome.failure = { kind: "interrupted", detail: "流在收到 done 之前中断" };
    } else {
      outcome.sequenceViolation = validateFrameSequence(frames);
      if (outcome.sequenceViolation) {
        outcome.failure = { kind: "contract", detail: outcome.sequenceViolation };
      }
    }
  }
  return outcome;
}
