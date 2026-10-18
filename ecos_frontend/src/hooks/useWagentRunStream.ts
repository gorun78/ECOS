/**
 * useWagentRunStream — WAgent run 流八帧承流的前端消费单源（详设-10 §10.3.6；#143/B5-5b-2）。
 *
 * 帧 → 界面语义（逐帧照做，禁自创）：
 * - `meta`     → Run 上下文（planSource/budget/streamMode/payloadKind 原样保留，前端不拆解预算）；
 * - `step`     → 步骤时间线追加（stepKey 是幂等位，同 key 多帧＝重试轮次，按 attempt 区分）；
 * - `token`    → 用量卡片（分项 -1 保持「未上报」，禁折算冒充分项）；
 * - `delta`    → 正文**追加**（§13.4.5 明文禁整段覆盖）；
 * - `citation` → 有据段落引用列表；
 * - `degrade`  → **降级横幅的单源读取位**（§10.3.6 帧 6 原注）：dgCode 入降级码列表，
 *                requiresAck 单独成态，禁折叠进「出错了」；
 * - `milestone`→ 生命周期时间线（非第九帧，无正文，禁当步骤渲染）；
 * - `error`    → 失败面板且保留已渲染内容（不清屏）；
 * - `done`     → 终态 + grade + degradations[]。
 *
 * 与 `useAgentLoopStream` 的三点不同形（不是复制粘贴的理由，是必须分文件的理由）：
 * 1. 本腿 `resumeCapable` 为真（后端对带行 id 的帧写 `id:`）⇒ 断线后可带 Last-Event-ID 续订，
 *    hook 如实暴露 `lastEventId`，由调用方决定是否续订；本 hook **不自动续订**（同不自动重发原则）。
 * 2. 后端在预算耗尽而未终态时刻意不发 done ⇒ 无 done 归断线，计入 §4.4 计数。
 * 3. 达阈值的兜底通道本批只置 `degradedToPoll` 并回调一次：W 侧轮询面是
 *    `RUN_PROGRESS_CAPABILITY` + 进度 JSON 端点，把 run 流降级成哪种轮询形态属页面级裁决，
 *    不在 hook 里替调用方选（选了就成第二真值面）。
 *
 * `subscribe()` 返回本轮收集结果快照而非依赖 hook state —— 调用方 await 后读 state
 * 会拿到渲染前旧值，正文与降级码会「少一截」。
 * @license Apache-2.0
 */

import { useCallback, useRef, useState } from "react";
import { countsAsDisconnect, isFallbackTriggered } from "../services/agentLoopStream";
import {
  streamWagentRunEvents,
  type WagentRunStreamOutcome,
  type WagentRunStreamRequest,
  type WagentStreamFailure,
} from "../services/wagentRunStream";
import { TEXT_ABSENT } from "../services/sseFrames";
import {
  anyRequiresAck,
  dedupeDegradationCodes,
  type WagentCitationFrame,
  type WagentDegradeFrame,
  type WagentDoneFrame,
  type WagentErrorFrame,
  type WagentMetaFrame,
  type WagentMilestoneFrame,
  type WagentStepFrame,
  type WagentTokenFrame,
} from "../services/wagentSseFrames";

/** 一次承流的收集结果（渲染层直接落界面，不等 state 回流） */
export interface WagentRunTurn {
  meta: WagentMetaFrame | null;
  steps: WagentStepFrame[];
  milestones: WagentMilestoneFrame[];
  /** 增量正文（delta 帧追加，缺失标记不入正文） */
  content: string;
  citations: WagentCitationFrame[];
  usage: WagentTokenFrame | null;
  /** 降级帧原样收集（横幅 reasons 由它们拼，禁在本 hook 之外再算一遍降级原因） */
  degradations: WagentDegradeFrame[];
  /** done 帧 degradations[] 与 degrade 帧 dgCode 合并后的去重码表 */
  degradationCodes: string[];
  /** 任一降级帧 requiresAck ⇒ true；无降级帧即 false（非「已判定不需确认」） */
  ackRequired: boolean;
  errorFrame: WagentErrorFrame | null;
  doneFrame: WagentDoneFrame | null;
  failure: WagentStreamFailure | null;
  outcome: WagentRunStreamOutcome | null;
}

export interface WagentRunStreamState extends WagentRunTurn {
  running: boolean;
  /** 承流未收 done 的累计次数（transport/interrupted 两类，aborted/contract 不计） */
  disconnectCount: number;
  /** 断线达阈值 ⇒ 按降级形态渲染，禁声称实时 */
  degradedToPoll: boolean;
  /** 后端是否下发过行 id ⇒ 本腿为真时续订才有意义（与 agent-loop 腿恒 false 不同形） */
  resumeCapable: boolean;
  /** 最后已消费到的行 id，供调用方发起续订 */
  lastEventId: string | null;
}

const EMPTY_TURN: WagentRunTurn = {
  meta: null,
  steps: [],
  milestones: [],
  content: "",
  citations: [],
  usage: null,
  degradations: [],
  degradationCodes: [],
  ackRequired: false,
  errorFrame: null,
  doneFrame: null,
  failure: null,
  outcome: null,
};

const INITIAL: WagentRunStreamState = {
  ...EMPTY_TURN,
  running: false,
  disconnectCount: 0,
  degradedToPoll: false,
  resumeCapable: false,
  lastEventId: null,
};

export interface UseWagentRunStreamOptions {
  /** 断线达阈值时触发一次（调用方据此把 progressChannel 改按轮询形态渲染） */
  onFallbackToPoll?: (disconnectCount: number) => void;
}

/** 降级码表 = 帧 6 dgCode ∪ 帧 8 degradations[]；判据单源在 wagentSseFrames，禁两处各写一份 */
function codeList(frames: readonly WagentDegradeFrame[], done: WagentDoneFrame | null): string[] {
  return dedupeDegradationCodes([
    ...frames.map((frame) => frame.dgCode),
    ...(done ? done.degradations : []),
  ]);
}

export function useWagentRunStream(options: UseWagentRunStreamOptions = {}) {
  const [state, setState] = useState<WagentRunStreamState>(INITIAL);
  const abortRef = useRef<AbortController | null>(null);
  const disconnectRef = useRef(0);
  const degradedRef = useRef(false);
  const fallbackFiredRef = useRef(false);
  const turnRef = useRef<WagentRunTurn>({ ...EMPTY_TURN });
  const optionsRef = useRef(options);
  optionsRef.current = options;

  const publish = useCallback((next: Partial<WagentRunStreamState>) => {
    setState((prev) => ({ ...prev, ...next, ...turnRef.current }));
  }, []);

  const reset = useCallback(() => {
    abortRef.current?.abort();
    abortRef.current = null;
    disconnectRef.current = 0;
    degradedRef.current = false;
    fallbackFiredRef.current = false;
    turnRef.current = { ...EMPTY_TURN };
    setState({ ...INITIAL });
  }, []);

  const subscribe = useCallback(
    async (request: WagentRunStreamRequest): Promise<WagentRunTurn> => {
      abortRef.current?.abort();
      const controller = new AbortController();
      abortRef.current = controller;
      turnRef.current = { ...EMPTY_TURN };
      setState((prev) => ({ ...prev, ...EMPTY_TURN, running: true }));

      const collect = <K extends keyof WagentRunTurn>(key: K, value: WagentRunTurn[K]) => {
        turnRef.current = { ...turnRef.current, [key]: value };
      };

      const outcome = await streamWagentRunEvents(
        { ...request, abortSignal: controller.signal },
        {
          onMeta: (meta) => {
            collect("meta", meta);
            publish({});
          },
          onStep: (step) => {
            collect("steps", [...turnRef.current.steps, step]);
            publish({});
          },
          onMilestone: (milestone) => {
            collect("milestones", [...turnRef.current.milestones, milestone]);
            publish({});
          },
          onToken: (usage) => {
            collect("usage", usage);
            publish({});
          },
          onDelta: (delta) => {
            // 追加而非覆盖；缺失标记「-」不是正文，拼进去界面就多出假句子
            if (delta.text !== TEXT_ABSENT) collect("content", turnRef.current.content + delta.text);
            publish({});
          },
          onCitation: (citation) => {
            collect("citations", [...turnRef.current.citations, citation]);
            publish({});
          },
          onDegrade: (degrade) => {
            const degradations = [...turnRef.current.degradations, degrade];
            collect("degradations", degradations);
            collect("degradationCodes", codeList(degradations, turnRef.current.doneFrame));
            collect("ackRequired", anyRequiresAck(degradations));
            publish({});
          },
          onError: (errorFrame) => {
            // error 不清屏：已渲染正文与降级态必须留着，失败面板只叠加
            collect("errorFrame", errorFrame);
            publish({});
          },
          onDone: (doneFrame) => {
            collect("doneFrame", doneFrame);
            collect("degradationCodes", codeList(turnRef.current.degradations, doneFrame));
            publish({});
          },
        },
      );

      turnRef.current = { ...turnRef.current, outcome, failure: outcome.failure };
      abortRef.current = null;
      if (countsAsDisconnect(outcome.failure)) disconnectRef.current += 1;
      if (isFallbackTriggered(disconnectRef.current)) {
        degradedRef.current = true;
        if (!fallbackFiredRef.current) {
          fallbackFiredRef.current = true;
          optionsRef.current.onFallbackToPoll?.(disconnectRef.current);
        }
      }
      // 达阈值本轮不补跑：Run 含工具副作用，静默重发＝重复执行
      publish({
        running: false,
        disconnectCount: disconnectRef.current,
        degradedToPoll: degradedRef.current,
        resumeCapable: outcome.resumeCapable,
        lastEventId: outcome.lastEventId,
      });
      return turnRef.current;
    },
    [publish],
  );

  const abort = useCallback(() => {
    abortRef.current?.abort();
  }, []);

  return { state, subscribe, abort, reset };
}
