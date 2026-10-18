/**
 * SSE 承流帧契约（前端单源）— 详设-06 §1.9.2 / §9.3.3 + 篇13 §13.4.5。
 *
 * 与后端唯一登记处 `runtime/common-api .../common/stream/SseFrameContract.java`
 * 逐字对齐（帧名、键名、op/decision 词表、缺失标记 -1 / "-"）。两侧必须是同一口径，
 * 否则前端会把「未上报」读成「实测为零」。对齐由
 * `src/services/__tests__/sseFrames.singleSource.spec.ts` 跨语言读后端源码钉桩，禁人工记忆
 * （本行原写 `src/__tests__/sseFrameSingleSourceGate.test.ts`——该路径实不存在，属注释里的虚假引用，
 * 2026-10-18 #143/B5-5b-2 按实测文件名订正，门禁本体未变）。
 *
 * 前端侧禁在任何其它文件再写帧名字面量（写出/消费都只准 import 本模块）。
 * @license Apache-2.0
 */

/** 七帧固定序（§9.3.3 表首列 1..7）。 */
export const AGENT_LOOP_FRAME_ORDER = [
  "meta",
  "step",
  "token",
  "delta",
  "citation",
  "error",
  "done",
] as const;

export type AgentLoopFrameName = (typeof AGENT_LOOP_FRAME_ORDER)[number];

/** 逐帧键名单源（与 SseFrameContract.KEY_* 同名同值）。 */
export const SSE_KEYS = {
  traceId: "traceId",
  sessionId: "sessionId",
  model: "model",
  contextLimit: "contextLimit",
  estimatedTokens: "estimatedTokens",
  op: "op",
  step: "step",
  tool: "tool",
  decision: "decision",
  promptTokens: "promptTokens",
  completionTokens: "completionTokens",
  estimated: "estimated",
  text: "text",
  forecastRunId: "forecastRunId",
  caliberId: "caliberId",
  version: "version",
  asOfTime: "asOfTime",
  links: "links",
  errorCode: "errorCode",
  class: "class",
  message: "message",
  retryable: "retryable",
  totalTokens: "totalTokens",
  compressionMode: "compressionMode",
  // done 帧专属键（后端 doneFields 实写值，非 KEY_* 常量面；逐字照登）
  turns: "turns",
  success: "success",
  errorClass: "errorClass",
  compressedTurns: "compressedTurns",
  compressSkipped: "compressSkipped",
} as const;

/** 引用帧四要素齐备才算可用（与后端 CITATION_REQUIRED_KEYS 同口径，缺任一项即视为不可用）。 */
export const CITATION_REQUIRED_KEYS = [
  SSE_KEYS.forecastRunId,
  SSE_KEYS.caliberId,
  SSE_KEYS.version,
  SSE_KEYS.asOfTime,
] as const;

/** step 帧 op 词表（单源=AgentLoopTrace.Op 的 wire 值）。 */
export const STEP_OPS = ["plan", "tool_call", "tool_result", "llm_call", "final"] as const;
export type StepOp = (typeof STEP_OPS)[number];

/** 裁决态词表（单源=AgentLoopTrace.Decision；无裁决环节恒 NA）。 */
export const STEP_DECISIONS = ["ALLOW", "DENY", "FAIL_CLOSED", "NA"] as const;
export type StepDecision = (typeof STEP_DECISIONS)[number];

/** 工具类 step 的 op 子集（时间线/思考链只认这两类，plan/llm_call/final 不得冒充工具轮） */
export const TOOL_STEP_OPS = ["tool_call", "tool_result"] as const;

export function isToolStepOp(op: string): boolean {
  return (TOOL_STEP_OPS as readonly string[]).includes(op);
}

/** 拒绝类裁决（时间线按失败态渲染；单源=AgentLoopTrace.Decision 的成员） */
export const DENIAL_DECISIONS: readonly string[] = ["DENY", "FAIL_CLOSED"];

export function isDenialDecision(decision: string): boolean {
  return DENIAL_DECISIONS.includes(decision);
}

/** 无裁决环节的占位值（不参与失败态判定） */
export const DECISION_NA = "NA";

/** 数值缺失标记（= AgentLoopTrace.UNKNOWN）：分项不可得写 -1，禁 0 冒充分项。 */
export const TOKEN_UNKNOWN = -1;
/** 字符串缺失标记（= AgentLoopTrace.ABSENT）。 */
export const TEXT_ABSENT = "-";

/** 断线自动切轮询阈值（§4.4）——前端单源，RunProgress/DegradationBanner 由此导入。 */
export const SSE_FALLBACK_THRESHOLD = 3;

/** 轮询兜底默认间隔秒（§3.4，与 RunProgress 展示注记同口径）。 */
export const POLL_INTERVAL_DEFAULT_SECONDS = 3;

export function isKnownFrame(event: string): event is AgentLoopFrameName {
  return (AGENT_LOOP_FRAME_ORDER as readonly string[]).includes(event);
}

/** 帧序位（1 起）；未知帧名返回 TOKEN_UNKNOWN。 */
export function frameIndex(event: string): number {
  const at = (AGENT_LOOP_FRAME_ORDER as readonly string[]).indexOf(event);
  return at < 0 ? TOKEN_UNKNOWN : at + 1;
}

/**
 * 终态帧序校验 —— 与后端 `SseFrameContract.validateSequence` 同一规则集：
 * 非空、帧名全在词表内、首帧 meta、末帧 done、error 至多一条且必须紧邻 done 之前。
 *
 * 与后端同源的理由也不另写一套：多轮流里 step(llm_call)/token 会在首批 delta 之后再出现，
 * 「按此序假设」指分组相对位置而非全局单调，写死单调闸会把合规轮流判成违例。
 *
 * @returns 违例描述；合法返回 null
 */
export function validateFrameSequence(events: readonly string[]): string | null {
  if (events.length === 0) return "空序列：承流必须至少含 meta 与 done 两帧";
  for (const event of events) {
    if (!isKnownFrame(event)) return `未知帧名: ${event}（词表单源=SseFrameContract）`;
  }
  if (events[0] !== "meta") return `首帧必须为 meta，实得 ${events[0]}`;
  const last = events[events.length - 1];
  if (last !== "done") return `末帧必须为 done，实得 ${last}`;
  const errorIndexes = events
    .map((event, index) => (event === "error" ? index : -1))
    .filter((index) => index >= 0);
  if (errorIndexes.length > 1) return `error 帧至多一条，实得 ${errorIndexes.length} 条`;
  if (errorIndexes.length === 1 && errorIndexes[0] !== events.length - 2) {
    return `error 帧必须紧接 done 之前，实得下标 ${errorIndexes[0]}/${events.length - 1}`;
  }
  return null;
}

/**
 * 在途（尚未收到 done）帧序校验：只断言此刻已可判定的违例——词表内、首帧 meta、
 * error 至多一条、error 之后只准再来 done。
 *
 * 用途：承流过程中第一时间发现违例并停消费，而不是等整条流跑完才报
 * （等 done 会把已渲染的伪造正文留在界面上）。
 */
export function validateFrameSequencePrefix(events: readonly string[]): string | null {
  if (events.length === 0) return null;
  for (const event of events) {
    if (!isKnownFrame(event)) return `未知帧名: ${event}（词表单源=SseFrameContract）`;
  }
  if (events[0] !== "meta") return `首帧必须为 meta，实得 ${events[0]}`;
  const errorAt = events.indexOf("error");
  if (errorAt >= 0) {
    if (events.lastIndexOf("error") !== errorAt) return `error 帧至多一条，实得 2 条`;
    const tail = events.slice(errorAt + 1);
    if (tail.some((event) => event !== "done")) return "error 之后只准出 done";
  }
  return null;
}

// ── 帧载荷读取（缺失即 -1 / "-"，禁补零补串） ──

export type FramePayload = Record<string, unknown>;

function numOrUnknown(value: unknown): number {
  return typeof value === "number" && Number.isFinite(value) ? value : TOKEN_UNKNOWN;
}

function strOrAbsent(value: unknown): string {
  return typeof value === "string" && value.trim() !== "" ? value : TEXT_ABSENT;
}

function boolOrFalse(value: unknown): boolean {
  return value === true;
}

/** traceId 每帧必有（后端 frame() 注入），缺失即缺失标记，不补随机值。 */
export function readTraceId(payload: FramePayload): string {
  return strOrAbsent(payload[SSE_KEYS.traceId]);
}

export interface AgentLoopMetaFrame {
  traceId: string;
  sessionId: string;
  model: string;
  contextLimit: number;
  estimatedTokens: number;
}

export function readMetaFrame(payload: FramePayload): AgentLoopMetaFrame {
  return {
    traceId: readTraceId(payload),
    sessionId: strOrAbsent(payload[SSE_KEYS.sessionId]),
    model: strOrAbsent(payload[SSE_KEYS.model]),
    contextLimit: numOrUnknown(payload[SSE_KEYS.contextLimit]),
    estimatedTokens: numOrUnknown(payload[SSE_KEYS.estimatedTokens]),
  };
}

export interface AgentLoopStepFrame {
  traceId: string;
  /** op 原样保留（后端映射单源在 AgentLoopService#decisionOf，前端不再二次映射） */
  op: string;
  step: number;
  tool: string;
  model: string;
  decision: string;
}

export function readStepFrame(payload: FramePayload): AgentLoopStepFrame {
  return {
    traceId: readTraceId(payload),
    op: strOrAbsent(payload[SSE_KEYS.op]),
    step: numOrUnknown(payload[SSE_KEYS.step]),
    tool: strOrAbsent(payload[SSE_KEYS.tool]),
    model: strOrAbsent(payload[SSE_KEYS.model]),
    decision: strOrAbsent(payload[SSE_KEYS.decision]),
  };
}

export interface AgentLoopTokenFrame {
  traceId: string;
  promptTokens: number;
  completionTokens: number;
  estimated: boolean;
}

export function readTokenFrame(payload: FramePayload): AgentLoopTokenFrame {
  return {
    traceId: readTraceId(payload),
    promptTokens: numOrUnknown(payload[SSE_KEYS.promptTokens]),
    completionTokens: numOrUnknown(payload[SSE_KEYS.completionTokens]),
    estimated: boolOrFalse(payload[SSE_KEYS.estimated]),
  };
}

export interface AgentLoopDeltaFrame {
  traceId: string;
  text: string;
}

export function readDeltaFrame(payload: FramePayload): AgentLoopDeltaFrame {
  return { traceId: readTraceId(payload), text: strOrAbsent(payload[SSE_KEYS.text]) };
}

export interface AgentLoopCitationFrame {
  traceId: string;
  forecastRunId: string;
  caliberId: string;
  version: string;
  asOfTime: string;
  links: string[];
}

export function readCitationFrame(payload: FramePayload): AgentLoopCitationFrame {
  const raw = payload[SSE_KEYS.links];
  const links = Array.isArray(raw) ? raw.filter((x): x is string => typeof x === "string") : [];
  return {
    traceId: readTraceId(payload),
    forecastRunId: strOrAbsent(payload[SSE_KEYS.forecastRunId]),
    caliberId: strOrAbsent(payload[SSE_KEYS.caliberId]),
    version: strOrAbsent(payload[SSE_KEYS.version]),
    asOfTime: strOrAbsent(payload[SSE_KEYS.asOfTime]),
    links,
  };
}

/** 引用四要素是否齐备（缺任一项 ⇒ 该引用不可用，前端显式「无来源」而非折叠掉）。 */
export function isCitationUsable(frame: AgentLoopCitationFrame): boolean {
  return CITATION_REQUIRED_KEYS.every(
    (key) => (frame as unknown as FramePayload)[key] !== TEXT_ABSENT,
  );
}

/** 证据三要素（与 EvidenceDrawer 的 EvidenceBundle 同形，此处不引组件以免 services 依赖 React）。 */
export interface EvidenceTriple {
  source: string;
  version: string;
  snapshot: string;
}

/**
 * 引用帧 → 证据三要素（§13.4.5「来源·版本·快照定位符」）。
 *
 * 不做任何补全：四要素不齐时逐字段落缺失标记，由 EvidenceDrawer 渲染「无来源」。
 */
export function citationToEvidence(frame: AgentLoopCitationFrame): EvidenceTriple {
  const parts = [
    `${SSE_KEYS.forecastRunId}=${frame.forecastRunId}`,
    `${SSE_KEYS.caliberId}=${frame.caliberId}`,
  ];
  const snapshot = [`${SSE_KEYS.asOfTime}=${frame.asOfTime}`, ...frame.links].join(" | ");
  return { source: parts.join(" | "), version: frame.version, snapshot };
}

export interface AgentLoopErrorFrame {
  traceId: string;
  errorCode: string;
  /** D.5 分类名（键名 `class` 与后端一致） */
  errorClass: string;
  message: string;
  retryable: boolean;
}

export function readErrorFrame(payload: FramePayload): AgentLoopErrorFrame {
  return {
    traceId: readTraceId(payload),
    errorCode: strOrAbsent(payload[SSE_KEYS.errorCode]),
    errorClass: strOrAbsent(payload[SSE_KEYS.class]),
    message: strOrAbsent(payload[SSE_KEYS.message]),
    retryable: boolOrFalse(payload[SSE_KEYS.retryable]),
  };
}

export interface AgentLoopDoneFrame {
  traceId: string;
  sessionId: string;
  turns: number;
  totalTokens: number;
  success: boolean;
  /** 失败腿才有的四键；未上报 ⇒ 缺失标记，禁由 error 帧回填（两帧同分类不同形） */
  errorCode: string;
  errorClass: string;
  retryable: boolean | null;
  message: string;
  /** 压缩态未判定时后端整组键缺席 ⇒ 前端保持 null（禁渲染「未上报」以外的值） */
  compressionMode: string | null;
  compressedTurns: number | null;
  compressSkipped: boolean | null;
}

export function readDoneFrame(payload: FramePayload): AgentLoopDoneFrame {
  const retryRaw = payload[SSE_KEYS.retryable];
  const modeRaw = payload[SSE_KEYS.compressionMode];
  const turnsRaw = payload[SSE_KEYS.compressedTurns];
  const skipRaw = payload[SSE_KEYS.compressSkipped];
  return {
    traceId: readTraceId(payload),
    sessionId: strOrAbsent(payload[SSE_KEYS.sessionId]),
    turns: numOrUnknown(payload[SSE_KEYS.turns]),
    totalTokens: numOrUnknown(payload[SSE_KEYS.totalTokens]),
    success: boolOrFalse(payload[SSE_KEYS.success]),
    errorCode: strOrAbsent(payload[SSE_KEYS.errorCode]),
    errorClass: strOrAbsent(payload[SSE_KEYS.errorClass]),
    retryable: typeof retryRaw === "boolean" ? retryRaw : null,
    message: strOrAbsent(payload[SSE_KEYS.message]),
    compressionMode: typeof modeRaw === "string" && modeRaw.trim() !== "" ? modeRaw : null,
    compressedTurns: typeof turnsRaw === "number" && Number.isFinite(turnsRaw) ? turnsRaw : null,
    compressSkipped: typeof skipRaw === "boolean" ? skipRaw : null,
  };
}
