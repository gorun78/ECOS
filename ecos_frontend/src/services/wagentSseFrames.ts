/**
 * WAgent run 流帧契约（前端单源）— 详设-10 §10.3.6 八帧 + 补充帧 milestone。
 *
 * 与后端唯一登记处 `runtime/common-api .../common/stream/SseFrameContract.java` 的 **W 分表**
 * 逐字对齐（八帧序、逐帧键面、通道自加键白名单、缺失标记）。对齐由
 * `src/services/__tests__/sseFrames.singleSource.spec.ts` 跨语言读后端源码钉桩，禁人工记忆。
 *
 * 为什么不并进 `sseFrames.ts`：那是 agent-loop 七帧面（§9.3.3），与 W 八帧是**同文件不同词表**
 * 的两套契约——`step` 在七帧面是 `{op,step,tool,model,decision}`，在 W 面是
 * `{stepKey,stepType,tool,attempt,status,policyEffect}`；同名不同键，合并一个常量表就会
 * 让「借用另一通道的帧名」在编译期看不出来。跨通道互斥在后端由 validateSequence／
 * validateWagentSequence 双向夹住，前端镜像同样各留各的词表。
 *
 * 前端侧禁在任何其它文件再写 W 帧名字面量（消费只准 import 本模块）。
 * @license Apache-2.0
 */

import { TEXT_ABSENT, TOKEN_UNKNOWN, type FramePayload } from "./sseFrames";

/**
 * 八帧帧名（与后端 `SseFrameContract.FRAME_*` 同形）。
 *
 * 先出帧名再拼序，而不是反过来：消费侧的分支标签必须引这里的常量，
 * 不能自己抄一遍字面量——抄了以后，后端改词表时前端不会变红，只会静默丢帧。
 * 类型面先于值面书写，是为了避开「序由名推、名由序校验」的循环推断。
 */
export type WagentFrameName =
  | "meta"
  | "step"
  | "token"
  | "delta"
  | "citation"
  | "degrade"
  | "error"
  | "done";

export const WAGENT_FRAMES = {
  meta: "meta",
  step: "step",
  token: "token",
  delta: "delta",
  citation: "citation",
  degrade: "degrade",
  error: "error",
  done: "done",
} as const satisfies Record<WagentFrameName, string>;

/** 八帧固定序（§10.3.6 表首列 1..8）。 */
export const WAGENT_FRAME_ORDER: readonly WagentFrameName[] = [
  WAGENT_FRAMES.meta,
  WAGENT_FRAMES.step,
  WAGENT_FRAMES.token,
  WAGENT_FRAMES.delta,
  WAGENT_FRAMES.citation,
  WAGENT_FRAMES.degrade,
  WAGENT_FRAMES.error,
  WAGENT_FRAMES.done,
];

/**
 * 补充帧：Run 生命周期里程碑（后端裁决 D-#143B5-5b-1：八帧表对那 8 类事件无帧位，
 * 塞进 `step` 就是把非步骤事件冒充步骤）。它可被识别但**不在八帧序内**，
 * 序位恒取缺失标记，与未知帧名同值——禁在前端给它排座次。
 */
export const WAGENT_MILESTONE_FRAME = "milestone";

/** W 通道键面（与 SseFrameContract.KEY_* 同名同值；含与 agent-loop 面同名但不同形的帧）。 */
export const WAGENT_SSE_KEYS = {
  traceId: "traceId",
  runId: "runId",
  planSource: "planSource",
  budget: "budget",
  streamMode: "streamMode",
  payloadKind: "payloadKind",
  eventId: "eventId",
  eventType: "eventType",
  occurTime: "occurTime",
  stepKey: "stepKey",
  stepType: "stepType",
  tool: "tool",
  attempt: "attempt",
  status: "status",
  policyEffect: "policyEffect",
  promptTokens: "promptTokens",
  completionTokens: "completionTokens",
  estimated: "estimated",
  text: "text",
  claimId: "claimId",
  evidenceId: "evidenceId",
  sourceEngine: "sourceEngine",
  sourceObjectId: "sourceObjectId",
  version: "version",
  asOf: "asOf",
  dgCode: "dgCode",
  appliesTo: "appliesTo",
  requiresAck: "requiresAck",
  note: "note",
  errorCode: "errorCode",
  message: "message",
  retryable: "retryable",
  grade: "grade",
  degradations: "degradations",
} as const;

/**
 * 逐帧键面登记表（前端镜像；后端 `WAGENT_FRAME_KEYS` 是唯一真值，本表由跨语言门钉死）。
 *
 * 用途不是校验后端，而是让消费侧「按帧位取键」而不是按记忆取键：取一个没人发的键
 * 并在界面显示「未上报」，与把未上报读成实测零是同一类失真。
 */
export const WAGENT_FRAME_KEYS: Record<WagentFrameName, readonly string[]> = {
  [WAGENT_FRAMES.meta]: [
    WAGENT_SSE_KEYS.traceId,
    WAGENT_SSE_KEYS.runId,
    WAGENT_SSE_KEYS.planSource,
    WAGENT_SSE_KEYS.budget,
  ],
  [WAGENT_FRAMES.step]: [
    WAGENT_SSE_KEYS.traceId,
    WAGENT_SSE_KEYS.stepKey,
    WAGENT_SSE_KEYS.stepType,
    WAGENT_SSE_KEYS.tool,
    WAGENT_SSE_KEYS.attempt,
    WAGENT_SSE_KEYS.status,
    WAGENT_SSE_KEYS.policyEffect,
  ],
  [WAGENT_FRAMES.token]: [
    WAGENT_SSE_KEYS.traceId,
    WAGENT_SSE_KEYS.promptTokens,
    WAGENT_SSE_KEYS.completionTokens,
    WAGENT_SSE_KEYS.estimated,
  ],
  [WAGENT_FRAMES.delta]: [WAGENT_SSE_KEYS.traceId, WAGENT_SSE_KEYS.text],
  [WAGENT_FRAMES.citation]: [
    WAGENT_SSE_KEYS.traceId,
    WAGENT_SSE_KEYS.claimId,
    WAGENT_SSE_KEYS.evidenceId,
    WAGENT_SSE_KEYS.sourceEngine,
    WAGENT_SSE_KEYS.sourceObjectId,
    WAGENT_SSE_KEYS.version,
    WAGENT_SSE_KEYS.asOf,
  ],
  [WAGENT_FRAMES.degrade]: [
    WAGENT_SSE_KEYS.traceId,
    WAGENT_SSE_KEYS.dgCode,
    WAGENT_SSE_KEYS.appliesTo,
    WAGENT_SSE_KEYS.requiresAck,
    WAGENT_SSE_KEYS.note,
  ],
  [WAGENT_FRAMES.error]: [
    WAGENT_SSE_KEYS.traceId,
    WAGENT_SSE_KEYS.errorCode,
    WAGENT_SSE_KEYS.message,
    WAGENT_SSE_KEYS.retryable,
  ],
  [WAGENT_FRAMES.done]: [
    WAGENT_SSE_KEYS.traceId,
    WAGENT_SSE_KEYS.runId,
    WAGENT_SSE_KEYS.status,
    WAGENT_SSE_KEYS.grade,
    WAGENT_SSE_KEYS.degradations,
  ],
};

/** 补充帧 milestone 的键面（后端单列一张表，不与八帧共用）。 */
export const WAGENT_MILESTONE_FRAME_KEYS = [
  WAGENT_SSE_KEYS.traceId,
  WAGENT_SSE_KEYS.eventId,
  WAGENT_SSE_KEYS.eventType,
  WAGENT_SSE_KEYS.occurTime,
  WAGENT_SSE_KEYS.payloadKind,
];

/** 通道自加键白名单（后端在发射侧硬拒白名单外新键；前端据此区分「契约键」与「通道标注键」）。 */
export const WAGENT_CHANNEL_EXTRA_KEYS = [
  WAGENT_SSE_KEYS.eventId,
  WAGENT_SSE_KEYS.streamMode,
  WAGENT_SSE_KEYS.payloadKind,
];

export function isKnownWagentFrame(event: string): boolean {
  return (WAGENT_FRAME_ORDER as readonly string[]).includes(event) || event === WAGENT_MILESTONE_FRAME;
}

/** W 帧序位（1 起，按 §10.3.6 表首列）；`milestone` 与未知帧名同取缺失标记。 */
export function wagentFrameIndex(event: string): number {
  const at = (WAGENT_FRAME_ORDER as readonly string[]).indexOf(event as WagentFrameName);
  return at < 0 ? TOKEN_UNKNOWN : at + 1;
}

/** W 通道词表（八帧 + milestone），供门禁对账，禁在调用侧另抄一份。 */
export function wagentVocabulary(): string[] {
  return [...WAGENT_FRAME_ORDER, WAGENT_MILESTONE_FRAME];
}

/**
 * 末帧（收流后）帧序校验——与后端 `validateWagentSequence` 同一规则集：
 * 非空、帧名全在 W 词表内、首帧 meta、末帧 done、error 至多一条且必须紧邻 done 之前。
 * 不校验全局单调（多轮里 step 会在 token/degrade 之后再出现）。
 */
export function validateWagentSequence(events: readonly string[]): string | null {
  if (events.length === 0) return "空序列：W 通道必须至少含 meta 与 done 两帧";
  for (const event of events) {
    if (!isKnownWagentFrame(event)) return `未知 WAgent 帧名: ${event}（词表单源=SseFrameContract）`;
  }
  if (events[0] !== WAGENT_FRAMES.meta) return `首帧必须为 ${WAGENT_FRAMES.meta}，实得 ${events[0]}`;
  const last = events[events.length - 1];
  if (last !== WAGENT_FRAMES.done) return `末帧必须为 ${WAGENT_FRAMES.done}，实得 ${last}`;
  const errorIndexes = events
    .map((event, index) => (event === WAGENT_FRAMES.error ? index : -1))
    .filter((index) => index >= 0);
  if (errorIndexes.length > 1) return `error 帧至多一条，实得 ${errorIndexes.length} 条`;
  if (errorIndexes.length === 1 && errorIndexes[0] !== events.length - 2) {
    return `error 帧必须紧接 done 之前，实得下标 ${errorIndexes[0]}/${events.length - 1}`;
  }
  return null;
}

/**
 * 在途帧序校验：只断言此刻已可判定的违例（词表内、首帧 meta、error 至多一条、
 * error 之后只准再来 done）。用途与 agent-loop 面同：第一时间停消费，
 * 不等 done 才报——等 done 会把已渲染的伪造正文留在界面上。
 */
export function validateWagentSequencePrefix(events: readonly string[]): string | null {
  if (events.length === 0) return null;
  for (const event of events) {
    if (!isKnownWagentFrame(event)) return `未知 WAgent 帧名: ${event}（词表单源=SseFrameContract）`;
  }
  if (events[0] !== WAGENT_FRAMES.meta) return `首帧必须为 ${WAGENT_FRAMES.meta}，实得 ${events[0]}`;
  const errorAt = events.indexOf(WAGENT_FRAMES.error);
  if (errorAt >= 0) {
    if (events.lastIndexOf(WAGENT_FRAMES.error) !== errorAt) return "error 帧至多一条，实得 2 条";
    const tail = events.slice(errorAt + 1);
    if (tail.some((event) => event !== WAGENT_FRAMES.done)) return "error 之后只准出 done";
  }
  return null;
}

// ── 帧载荷读取（缺失即 -1 / "-"，禁补零补串） ──

function numOrUnknown(value: unknown): number {
  return typeof value === "number" && Number.isFinite(value) ? value : TOKEN_UNKNOWN;
}

function strOrAbsent(value: unknown): string {
  return typeof value === "string" && value.trim() !== "" ? value : TEXT_ABSENT;
}

function boolOrFalse(value: unknown): boolean {
  return value === true;
}

/** 三态布尔：键缺席 ≠ false（后端对「无权威可判定值」的口径就是不落键）。 */
function boolOrMissing(value: unknown): boolean | null {
  return typeof value === "boolean" ? value : null;
}

function strArrayOrEmpty(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((x): x is string => typeof x === "string") : [];
}

export interface WagentMetaFrame {
  traceId: string;
  runId: string;
  planSource: string;
  /** 预算为结构化对象，前端不拆解（拆了就成第二真值面）；键缺席即 null，消费侧按缺失渲染 */
  budget: unknown;
  /** F10-16 通道形态声明（服务端决定，前端不自行选择） */
  streamMode: string;
  /** §6.3 载荷形态诚实标注（后端正文外泄裁决未定前恒为 withheld 形态） */
  payloadKind: string;
}

export function readWagentMetaFrame(payload: FramePayload): WagentMetaFrame {
  return {
    traceId: strOrAbsent(payload[WAGENT_SSE_KEYS.traceId]),
    runId: strOrAbsent(payload[WAGENT_SSE_KEYS.runId]),
    planSource: strOrAbsent(payload[WAGENT_SSE_KEYS.planSource]),
    budget: payload[WAGENT_SSE_KEYS.budget] ?? null,
    streamMode: strOrAbsent(payload[WAGENT_SSE_KEYS.streamMode]),
    payloadKind: strOrAbsent(payload[WAGENT_SSE_KEYS.payloadKind]),
  };
}

export interface WagentStepFrame {
  traceId: string;
  eventId: string;
  stepKey: string;
  stepType: string;
  tool: string;
  /** 重试轮次；不可得即 -1，禁 0 冒充首轮 */
  attempt: number;
  status: string;
  policyEffect: string;
}

export function readWagentStepFrame(payload: FramePayload): WagentStepFrame {
  return {
    traceId: strOrAbsent(payload[WAGENT_SSE_KEYS.traceId]),
    eventId: strOrAbsent(payload[WAGENT_SSE_KEYS.eventId]),
    stepKey: strOrAbsent(payload[WAGENT_SSE_KEYS.stepKey]),
    stepType: strOrAbsent(payload[WAGENT_SSE_KEYS.stepType]),
    tool: strOrAbsent(payload[WAGENT_SSE_KEYS.tool]),
    attempt: numOrUnknown(payload[WAGENT_SSE_KEYS.attempt]),
    status: strOrAbsent(payload[WAGENT_SSE_KEYS.status]),
    policyEffect: strOrAbsent(payload[WAGENT_SSE_KEYS.policyEffect]),
  };
}

export interface WagentTokenFrame {
  traceId: string;
  promptTokens: number;
  completionTokens: number;
  estimated: boolean;
}

/** 契约在册帧（§10.3.6 帧 3）；当前后端零生产者 ⇒ 正常流里读不到，读到即按此形解析。 */
export function readWagentTokenFrame(payload: FramePayload): WagentTokenFrame {
  return {
    traceId: strOrAbsent(payload[WAGENT_SSE_KEYS.traceId]),
    promptTokens: numOrUnknown(payload[WAGENT_SSE_KEYS.promptTokens]),
    completionTokens: numOrUnknown(payload[WAGENT_SSE_KEYS.completionTokens]),
    estimated: boolOrFalse(payload[WAGENT_SSE_KEYS.estimated]),
  };
}

export interface WagentDeltaFrame {
  traceId: string;
  text: string;
}

/** 契约在册帧（§10.3.6 帧 4）；调用方必须追加、禁整段覆盖。 */
export function readWagentDeltaFrame(payload: FramePayload): WagentDeltaFrame {
  return { traceId: strOrAbsent(payload[WAGENT_SSE_KEYS.traceId]), text: strOrAbsent(payload[WAGENT_SSE_KEYS.text]) };
}

export interface WagentCitationFrame {
  traceId: string;
  claimId: string;
  evidenceId: string;
  sourceEngine: string;
  sourceObjectId: string;
  version: string;
  asOf: string;
}

/** 契约在册帧（§10.3.6 帧 5）；W 面 citation 与 agent-loop citation 同名不同键，禁互相回填。 */
export function readWagentCitationFrame(payload: FramePayload): WagentCitationFrame {
  return {
    traceId: strOrAbsent(payload[WAGENT_SSE_KEYS.traceId]),
    claimId: strOrAbsent(payload[WAGENT_SSE_KEYS.claimId]),
    evidenceId: strOrAbsent(payload[WAGENT_SSE_KEYS.evidenceId]),
    sourceEngine: strOrAbsent(payload[WAGENT_SSE_KEYS.sourceEngine]),
    sourceObjectId: strOrAbsent(payload[WAGENT_SSE_KEYS.sourceObjectId]),
    version: strOrAbsent(payload[WAGENT_SSE_KEYS.version]),
    asOf: strOrAbsent(payload[WAGENT_SSE_KEYS.asOf]),
  };
}

export interface WagentDegradeFrame {
  traceId: string;
  dgCode: string;
  appliesTo: string;
  requiresAck: boolean;
  note: string;
}

/** 帧 6：降级帧——前端降级横幅的单源读取位（§10.3.6 原注）。 */
export function readWagentDegradeFrame(payload: FramePayload): WagentDegradeFrame {
  return {
    traceId: strOrAbsent(payload[WAGENT_SSE_KEYS.traceId]),
    dgCode: strOrAbsent(payload[WAGENT_SSE_KEYS.dgCode]),
    appliesTo: strOrAbsent(payload[WAGENT_SSE_KEYS.appliesTo]),
    requiresAck: boolOrFalse(payload[WAGENT_SSE_KEYS.requiresAck]),
    note: strOrAbsent(payload[WAGENT_SSE_KEYS.note]),
  };
}

/**
 * 降级码归并（承流客户端与消费 hook 共用同一判据，禁两处各写一份）：
 * 按出现序去重，且缺失标记「-」不入册——把「未上报」当成一个降级码，
 * 界面上就会多出一条谁也解释不了的降级原因。
 */
export function dedupeDegradationCodes(codes: readonly string[]): string[] {
  const out: string[] = [];
  for (const code of codes) {
    if (code !== TEXT_ABSENT && !out.includes(code)) out.push(code);
  }
  return out;
}

/** 是否存在需人工确认的降级帧（与承流客户端 ackRequired 同判据）。 */
export function anyRequiresAck(frames: readonly { requiresAck: boolean }[]): boolean {
  return frames.some((frame) => frame.requiresAck);
}

export interface WagentMilestoneFrame {
  traceId: string;
  eventId: string;
  eventType: string;
  occurTime: string;
  payloadKind: string;
}

/** 补充帧（非第九帧）：只带事件身份三件 + 载荷形态标注，后端不下发正文。 */
export function readWagentMilestoneFrame(payload: FramePayload): WagentMilestoneFrame {
  return {
    traceId: strOrAbsent(payload[WAGENT_SSE_KEYS.traceId]),
    eventId: strOrAbsent(payload[WAGENT_SSE_KEYS.eventId]),
    eventType: strOrAbsent(payload[WAGENT_SSE_KEYS.eventType]),
    occurTime: strOrAbsent(payload[WAGENT_SSE_KEYS.occurTime]),
    payloadKind: strOrAbsent(payload[WAGENT_SSE_KEYS.payloadKind]),
  };
}

export interface WagentErrorFrame {
  traceId: string;
  errorCode: string;
  message: string;
  /** 三态：键缺席＝后端未注册该码的可重试性（禁渲染成「已判定不可重试」） */
  retryable: boolean | null;
}

export function readWagentErrorFrame(payload: FramePayload): WagentErrorFrame {
  return {
    traceId: strOrAbsent(payload[WAGENT_SSE_KEYS.traceId]),
    errorCode: strOrAbsent(payload[WAGENT_SSE_KEYS.errorCode]),
    message: strOrAbsent(payload[WAGENT_SSE_KEYS.message]),
    retryable: boolOrMissing(payload[WAGENT_SSE_KEYS.retryable]),
  };
}

export interface WagentDoneFrame {
  traceId: string;
  runId: string;
  /** 终态字符串（引擎状态机值）；缺失即缺失标记，前端不推断 */
  status: string;
  grade: string;
  degradations: string[];
}

/** 帧 8：末帧必发；`status` 为终态，故后端在预算耗尽而未终态时故意不发本帧。 */
export function readWagentDoneFrame(payload: FramePayload): WagentDoneFrame {
  return {
    traceId: strOrAbsent(payload[WAGENT_SSE_KEYS.traceId]),
    runId: strOrAbsent(payload[WAGENT_SSE_KEYS.runId]),
    status: strOrAbsent(payload[WAGENT_SSE_KEYS.status]),
    grade: strOrAbsent(payload[WAGENT_SSE_KEYS.grade]),
    degradations: strArrayOrEmpty(payload[WAGENT_SSE_KEYS.degradations]),
  };
}
