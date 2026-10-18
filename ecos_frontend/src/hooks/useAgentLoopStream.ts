/**
 * useAgentLoopStream — Agent Loop 七帧承流的前端消费单源（篇13 §13.4.5）。
 *
 * 帧 → 界面语义（逐帧照做，禁自创）：
 * - `meta`  → Run 上下文写入 `useAgentStore.runContext`（不在本 hook 另存一份，防双源）；
 * - `step`  → 时间线追加；
 * - `token` → 用量合并进卡片 data（分项 -1 时保持「未上报」语义，禁折算冒充分项）；
 * - `delta` → 正文**追加**（§13.4.5 明文禁整段覆盖）；
 * - `citation` → 累积进引用列表，由 `<EvidenceDrawer>` 承接（缺四要素仍入列并显「无来源」）；
 * - `error` → 进 `ErrorPanel` 且**保留已渲染内容**（不清屏）；
 * - `done`  → 终态与统计，触发调用方缓存失效重拉。
 *
 * 断线策略（§4.4 + `services/agentLoopStream.ts` 裁决 3）：一次尝试只计一次断线，
 * 不自动重发（一轮含工具执行副作用，重发＝重复执行）；累计达阈值后本轮不补跑，
 * 只把**后续**轮次改走兜底通道，并如实标 `degradedToPoll` + 回调一次。
 *
 * `run()` 返回本轮收集结果快照（content/steps/citations/...）而非依赖 hook state ——
 * 调用方 await 之后读 state 会拿到渲染前的旧值，正文明显会"少一截"。
 * @license Apache-2.0
 */

import { useCallback, useRef, useState } from 'react';
import {
  countsAsDisconnect,
  isFallbackTriggered,
  streamAgentLoopChat,
  type AgentLoopChatRequest,
  type AgentLoopStreamFailure,
  type AgentLoopStreamOutcome,
} from '../services/agentLoopStream';
import {
  type AgentLoopCitationFrame,
  type AgentLoopDoneFrame,
  type AgentLoopErrorFrame,
  type AgentLoopMetaFrame,
  type AgentLoopStepFrame,
  type AgentLoopTokenFrame,
} from '../services/sseFrames';
import { useAgentStore, type AgentRunContext } from '../stores/agent/useAgentStore';

/** 一轮承流的收集结果（渲染层直接落气泡，不等 state 回流） */
export interface AgentLoopRunResult {
  content: string;
  steps: AgentLoopStepFrame[];
  citations: AgentLoopCitationFrame[];
  usage: AgentLoopTokenFrame | null;
  meta: AgentLoopMetaFrame | null;
  errorFrame: AgentLoopErrorFrame | null;
  doneFrame: AgentLoopDoneFrame | null;
  failure: AgentLoopStreamFailure | null;
  outcome: AgentLoopStreamOutcome | null;
  channel: 'sse' | 'poll';
}

export interface AgentLoopStreamState extends AgentLoopRunResult {
  running: boolean;
  /** 承流未收 done 的累计次数（transport/interrupted 两类，abort 不计） */
  disconnectCount: number;
  /** 断线达阈值 ⇒ 按降级形态渲染，禁声称实时 */
  degradedToPoll: boolean;
}

const EMPTY_TURN: AgentLoopRunResult = {
  content: '',
  steps: [],
  citations: [],
  usage: null,
  meta: null,
  errorFrame: null,
  doneFrame: null,
  failure: null,
  outcome: null,
  channel: 'sse',
};

const INITIAL: AgentLoopStreamState = {
  ...EMPTY_TURN,
  running: false,
  disconnectCount: 0,
  degradedToPoll: false,
};

export interface UseAgentLoopStreamOptions {
  /**
   * 断线达阈值后的兜底通道。§4.4 的"轮询"在 agent-loop 侧尚无可轮询的 run 状态端点
   * （余项 R-B5-3a），故由调用方以既有非流式 JSON 回执实现，返回正文文本。
   * 未提供时只标 degradedToPoll，不擅自编造兜底结果。
   */
  fallbackRunner?: (request: AgentLoopChatRequest) => Promise<string>;
  /** 达阈值回调（仅触发一次） */
  onFallbackToPoll?: (disconnectCount: number) => void;
}

export function useAgentLoopStream(options: UseAgentLoopStreamOptions = {}) {
  const [state, setState] = useState<AgentLoopStreamState>(INITIAL);
  const abortRef = useRef<AbortController | null>(null);
  const disconnectRef = useRef(0);
  const degradedRef = useRef(false);
  const fallbackFiredRef = useRef(false);
  const setRunContext = useAgentStore((s) => s.setRunContext);
  const optionsRef = useRef(options);
  optionsRef.current = options;

  /** 本轮收集箱：hook 内单写，state 与返回值同源于它，杜绝两处各自再算一遍 */
  const turnRef = useRef<AgentLoopRunResult>({ ...EMPTY_TURN });

  const publish = useCallback((next: Partial<AgentLoopStreamState>) => {
    setState((prev) => ({ ...prev, ...next, ...turnRef.current }));
  }, []);

  const reset = useCallback(() => {
    abortRef.current?.abort();
    abortRef.current = null;
    disconnectRef.current = 0;
    degradedRef.current = false;
    fallbackFiredRef.current = false;
    turnRef.current = { ...EMPTY_TURN };
    setRunContext(null);
    setState({ ...INITIAL });
  }, [setRunContext]);

  const startTurn = useCallback(() => {
    turnRef.current = { ...EMPTY_TURN };
    setState((prev) => ({ ...prev, ...EMPTY_TURN, running: true }));
  }, []);

  const runStream = useCallback(
    async (request: AgentLoopChatRequest): Promise<AgentLoopRunResult> => {
      const controller = new AbortController();
      abortRef.current = controller;

      const collect = <K extends keyof AgentLoopRunResult>(key: K, value: AgentLoopRunResult[K]) => {
        turnRef.current = { ...turnRef.current, [key]: value };
      };

      const outcome = await streamAgentLoopChat(request, {
        onMeta: (meta) => {
          collect('meta', meta);
          setRunContext({
            traceId: meta.traceId,
            sessionId: meta.sessionId,
            model: meta.model,
            contextLimit: meta.contextLimit,
            estimatedTokens: meta.estimatedTokens,
            channel: 'sse',
          });
          publish({});
        },
        onStep: (step) => {
          collect('steps', [...turnRef.current.steps, step]);
          publish({});
        },
        onToken: (usage) => {
          collect('usage', usage);
          publish({});
        },
        onDelta: (delta) => {
          // 追加而非覆盖：§13.4.5 对 delta 帧的硬要求
          collect('content', turnRef.current.content + delta.text);
          publish({});
        },
        onCitation: (citation) => {
          collect('citations', [...turnRef.current.citations, citation]);
          publish({});
        },
        onError: (errorFrame) => {
          // 保留已渲染正文（error 不清屏），只叠加失败面板
          collect('errorFrame', errorFrame);
          publish({});
        },
        onDone: (doneFrame) => {
          collect('doneFrame', doneFrame);
          publish({});
        },
      });

      turnRef.current = { ...turnRef.current, outcome, failure: outcome.failure };
      abortRef.current = null;
      return turnRef.current;
    },
    [publish, setRunContext],
  );

  /** 走非流式兜底通道（降级后每轮都用它，直到调用方 reset） */
  const runFallback = useCallback(async (request: AgentLoopChatRequest): Promise<AgentLoopRunResult> => {
    startTurn();
    turnRef.current = { ...turnRef.current, channel: 'poll' };
    const runner = optionsRef.current.fallbackRunner;
    if (!runner) {
      turnRef.current = {
        ...turnRef.current,
        failure: { kind: 'transport', status: null, message: '兜底通道未由调用方提供', traceId: null },
      };
      publish({ running: false });
      return turnRef.current;
    }
    try {
      const text = await runner(request);
      turnRef.current = { ...turnRef.current, content: text };
    } catch (e: unknown) {
      const message = e instanceof Error ? e.message : String(e);
      turnRef.current = {
        ...turnRef.current,
        failure: { kind: 'transport', status: null, message, traceId: null },
      };
    }
    publish({ running: false });
    return turnRef.current;
  }, [publish, startTurn]);

  const run = useCallback(
    async (request: AgentLoopChatRequest): Promise<AgentLoopRunResult> => {
      // 已降级 ⇒ 本轮直接走兜底，不再撞实时通道
      if (degradedRef.current) return runFallback(request);

      startTurn();
      let result: AgentLoopRunResult;
      try {
        result = await runStream(request);
      } finally {
        abortRef.current = null;
      }
      const failure = result.failure;
      if (countsAsDisconnect(failure)) disconnectRef.current += 1;
      if (isFallbackTriggered(disconnectRef.current)) {
        degradedRef.current = true;
        if (!fallbackFiredRef.current) {
          fallbackFiredRef.current = true;
          optionsRef.current.onFallbackToPoll?.(disconnectRef.current);
        }
      }
      // 达阈值本轮不补跑兜底：本轮已产生过工具副作用，静默重发＝重复执行
      publish({
        running: false,
        disconnectCount: disconnectRef.current,
        degradedToPoll: degradedRef.current,
      });
      return result;
    },
    [publish, runFallback, runStream, startTurn],
  );

  const abort = useCallback(() => {
    abortRef.current?.abort();
  }, []);

  return { state, run, abort, reset };
}
