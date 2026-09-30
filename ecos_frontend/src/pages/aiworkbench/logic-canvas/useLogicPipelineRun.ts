/**
 * useLogicPipelineRun — LogicView 画布执行引擎（真跑 + 轮询 + 清理），由 LogicView.tsx 机械抽取（H6-T4）。
 * state / ref / effect 的声明顺序与原文一致，保证 effect 执行次序不变。
 * @license Apache-2.0
 */
import { useCallback, useEffect, useRef, useState, type Dispatch, type SetStateAction } from 'react';
import type { Node, Edge } from '@xyflow/react';
import { apiFetchData } from '../../../api';
import { useLanguage } from '../../../components/LanguageContext';
import type { AIPLogicPipeline, LogicNodeData, LogicEdgeData, LogicNodeStatus } from '../../../types/aiworkbench';
import { topologicalSort } from './graph';
import type { BackendPipelineExec, NodeTrace } from './types';

export default function useLogicPipelineRun({
  nodes,
  edges,
  selectedPipeline,
  selectedPipelineId,
  setNodes,
  showToast,
}: {
  nodes: Node<LogicNodeData>[];
  edges: Edge<LogicEdgeData>[];
  selectedPipeline: AIPLogicPipeline | undefined;
  selectedPipelineId: string;
  setNodes: Dispatch<SetStateAction<Node<LogicNodeData>[]>>;
  showToast?: (type: 'success' | 'info' | 'error', msg: string) => void;
}) {
  const { t } = useLanguage();

  // ── Execution Engine ──
  const [isExecuting, setIsExecuting] = useState(false);
  const [logs, setLogs] = useState<string[]>([]);
  const [showLogs, setShowLogs] = useState(false);
  const [totalDuration, setTotalDuration] = useState<number | null>(null);
  const [traceMap, setTraceMap] = useState<Record<string, NodeTrace>>({});
  /** 全局 AbortController（5min 自动 abort） — 切 pipeline / 卸载时清除 */
  const execAbortRef = useRef<AbortController | null>(null);
  /** 轮询定时器 ID */
  const pollTimerRef = useRef<number | null>(null);
  /** 启动时刻，用于算 totalDuration */
  const runStartRef = useRef<number | null>(null);

  /** 清理进行中的执行（abort + 清 timer + 重置 isExecuting + 节点回 idle） */
  const cleanupExec = useCallback(() => {
    execAbortRef.current?.abort();
    execAbortRef.current = null;
    if (pollTimerRef.current != null) {
      window.clearInterval(pollTimerRef.current);
      pollTimerRef.current = null;
    }
    runStartRef.current = null;
    if (isExecuting) {
      setIsExecuting(false);
      setTotalDuration(null);
      setNodes(prev => prev.map(n => ({ ...n, data: { ...n.data, status: 'idle' as LogicNodeStatus } })));
    }
  }, [isExecuting, setNodes]);

  /**
   * 启动 Pipeline 真执行 + 轮询 executions。
   * 后端端点:
   *   POST /api/v1/aip/studio/pipelines/{pipelineId}/execute  → { executionId, status:'running', ... }
   *   GET  /api/v1/aip/studio/pipelines/{executionId}/executions → { executionId, status, ... }
   * 语义：
   *   - 启动失败（后端不可用 / 404 / 未注册）→ toast error，画布保留，节点保持 idle
   *   - 轮询每 2s 一次，到 completed/failed 或 5min 自动 abort
   *   - 后端 P2 暂无 nodeTraces 字段，状态回写以 pipeline 整体为粒度:
   *     running → 全部 inTopoOrder 标 running，completed → 全 success，failed → 全 error
   */
  const startPipelineRun = useCallback(async () => {
    if (nodes.length === 0 || !selectedPipeline) return;
    cleanupExec();
    setIsExecuting(true);
    setShowLogs(true);
    setLogs([]);
    setTotalDuration(null);
    setTraceMap({});
    runStartRef.current = Date.now();

    // 输入参数：pipeline.inputs 全部用 testInputs 占位
    const inputParams: Record<string, string> = {};
    for (const inp of selectedPipeline.inputs) {
      inputParams[inp.name] = selectedPipeline.testInputs?.[inp.name] || '';
    }

    // ── 1. 启动执行 ──
    let execId = '';
    try {
      const exec = await apiFetchData<BackendPipelineExec & { executionId?: string }>(
        `/api/v1/aip/studio/pipelines/${encodeURIComponent(selectedPipeline.id)}/execute`,
        { method: 'POST', body: JSON.stringify(inputParams) }
      );
      execId = (exec && (exec as BackendPipelineExec).executionId) || '';
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      // graceful: 不清空画布、不抛错 toast
      showToast?.('error', `${t('aiworkbench.logic.run.serviceUnavailable')}: ${msg}`);
      cleanupExec();
      return;
    }
    if (!execId) {
      showToast?.('error', t('aiworkbench.logic.run.failedNoId'));
      cleanupExec();
      return;
    }
    setLogs([t('aiworkbench.logic.run.started', { id: execId })]);

    // ── 2. 轮询 executions（每 2s 一次，5min 总超时）──
    const ac = new AbortController();
    execAbortRef.current = ac;

    const pollOnce = async (): Promise<void> => {
      if (ac.signal.aborted) return;
      try {
        const res = await apiFetchData<BackendPipelineExec>(
          `/api/v1/aip/studio/pipelines/${encodeURIComponent(execId)}/executions`,
          { signal: ac.signal }
        );
        if (!res) return;
        // 后端 P1: 仅 status/startedAt/completedAt/result.message，无 nodeTraces
        // 状态回写：按拓扑顺序遍历 nodes，把整体状态映射到每个节点
        const topo = topologicalSort(nodes, edges);
        const finalStatusMap: Record<string, LogicNodeStatus> = {};
        for (const nodeId of topo) finalStatusMap[nodeId] = res.status === 'completed' ? 'success' : res.status === 'failed' ? 'error' : 'running';
        setNodes(prev => prev.map(n => finalStatusMap[n.id] ? { ...n, data: { ...n.data, status: finalStatusMap[n.id] } } : n));
        setLogs(prev => [...prev, res.status === 'running' ? t('aiworkbench.logic.run.polling', { id: execId }) : t('aiworkbench.logic.run.finished', { msg: res.result?.message || res.status, elapsed: Date.now() - (runStartRef.current || Date.now()) })]);
        if (res.status === 'completed' || res.status === 'failed') {
          if (pollTimerRef.current != null) window.clearInterval(pollTimerRef.current);
          pollTimerRef.current = null;
          setIsExecuting(false);
          setTotalDuration(Date.now() - (runStartRef.current || Date.now()));
          if (res.status === 'completed') {
            showToast?.('success', t('aiworkbench.logic.run.success'));
          } else {
            showToast?.('error', t('aiworkbench.logic.run.fail', { msg: res.result?.message || res.status }));
          }
        }
      } catch (e) {
        // 后端 404 / 5xx → 终止轮询
        if (ac.signal.aborted) return;
        const msg = e instanceof Error ? e.message : String(e);
        if (pollTimerRef.current != null) window.clearInterval(pollTimerRef.current);
        pollTimerRef.current = null;
        setNodes(prev => prev.map(n => ({ ...n, data: { ...n.data, status: n.data.status === 'idle' ? 'idle' : 'error' as LogicNodeStatus } })));
        setLogs(prev => [...prev, t('aiworkbench.logic.run.pollFailed', { msg })]);
        showToast?.('error', `${t('aiworkbench.logic.run.serviceUnavailable')}: ${msg}`);
        setIsExecuting(false);
        setTotalDuration(Date.now() - (runStartRef.current || Date.now()));
      }
    };

    pollOnce(); // 立即首查一次
    pollTimerRef.current = window.setInterval(pollOnce, 2000);

    // 5min 整体超时
    window.setTimeout(() => {
      if (ac.signal.aborted) return;
      try { ac.abort(); } catch { /* noop */ }
      if (pollTimerRef.current != null) window.clearInterval(pollTimerRef.current);
      pollTimerRef.current = null;
      setLogs(prev => [...prev, t('aiworkbench.logic.run.timeout')]);
      showToast?.('error', t('aiworkbench.logic.run.timeout'));
      setIsExecuting(false);
    }, 300_000);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [nodes, edges, selectedPipeline, cleanupExec, showToast, t, setNodes]);

  /** 切换 pipeline / 卸载时清理执行 */
  useEffect(() => {
    return () => cleanupExec();
  }, [selectedPipelineId, cleanupExec]);

  return {
    isExecuting,
    logs,
    setLogs,
    showLogs,
    setShowLogs,
    totalDuration,
    traceMap,
    startPipelineRun,
  };
}
