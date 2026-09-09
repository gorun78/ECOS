/**
 * MonitorPanel — 管道画布下方的操作监视面板
 *
 * 折叠态 (28px)：状态点 · 节点进度 x/y · 已处理行数 · 耗时 · ▲展开
 * 展开态 (默认 240px, 高度 160~480px)：
 *   顶部 36px Tab 栏 [概览 | 日志 | 断点调试 | 数据预览] + 暂停滚动开关
 *   4px 拖拽分隔条 (持久化 panelHeight 到 localStorage)
 *
 * 主题 (useTheme)、i18n (useLanguage)、lucide-react 图标；
 * 不硬编码中文 / 颜色。
 *
 * @license Apache-2.0
 */
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { ChevronsDownUp, ChevronsUpDown, PauseCircle, PlayCircle } from 'lucide-react';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';
import OverviewTab from './MonitorPanel/OverviewTab';
import LogsTab from './MonitorPanel/LogsTab';
import DebugTab from './MonitorPanel/DebugTab';
import PreviewTab from './MonitorPanel/PreviewTab';
import type { MonitorTabId } from './MonitorPanel/types';

const COLLAPSED_H = 28;
const MIN_HEIGHT = 160;
const MAX_HEIGHT = 480;
const TAB_BAR_H = 36;
const PERSIST_KEY = 'dw-pipeline-monitor-panel';
const TAB_IDS: MonitorTabId[] = ['overview', 'logs', 'debug', 'preview'];

/** 全局事件 — 调试会话状态变化时由 DebugTab 推过来 (供折叠条状态点联动)。 */
export interface MonitorPanelState {
  nodeId: number;
  total: number;
  rows: number;
  elapsedMs: number;
  status: 'idle' | 'created' | 'running' | 'broken' | 'success' | 'failed' | 'stopped' | 'paused';
}

interface Props {
  /** 调试会话的 live snapshot（可选，缺失时由子组件内部自取）。 */
  session?: {
    sessionId: string;
    state: string;
    totalNodes: number;
    completedNodes: number;
    rowsProcessed: number;
    startedAt?: string;
    finishedAt?: string;
  } | null;
}

export default function MonitorPanel({ session }: Props) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [expanded, setExpanded] = useState<boolean>(true);
  const [tab, setTab] = useState<MonitorTabId>('overview');
  const [panelHeight, setPanelHeight] = useState<number>(240);
  const [autoScroll, setAutoScroll] = useState<boolean>(true);
  const [collapsedState, setCollapsedState] = useState<MonitorPanelState>({
    nodeId: 0,
    total: 1,
    rows: 0,
    elapsedMs: 0,
    status: 'idle',
  });
  const dragging = useRef<{ startY: number; startHeight: number } | null>(null);

  // ── Hydrate from localStorage ──
  useEffect(() => {
    try {
      const raw = localStorage.getItem(PERSIST_KEY);
      if (!raw) return;
      const parsed = JSON.parse(raw) as { height?: number; expanded?: boolean; tab?: MonitorTabId };
      if (typeof parsed.height === 'number' && parsed.height >= MIN_HEIGHT && parsed.height <= MAX_HEIGHT) {
        setPanelHeight(parsed.height);
      }
      if (typeof parsed.expanded === 'boolean') {
        setExpanded(parsed.expanded);
      }
      if (parsed.tab && TAB_IDS.includes(parsed.tab)) {
        setTab(parsed.tab);
      }
    } catch {
      // storage disabled / corrupted
    }
  }, []);

  // ── Persist on state change ──
  useEffect(() => {
    try {
      localStorage.setItem(PERSIST_KEY, JSON.stringify({ height: panelHeight, expanded, tab }));
    } catch {
      // ignore
    }
  }, [panelHeight, expanded, tab]);

  // ── Listen to debug-tab state (event bus) ──
  useEffect(() => {
    const onState = (e: Event) => {
      const detail = (e as CustomEvent<MonitorPanelState>).detail;
      if (detail) setCollapsedState(detail);
    };
    window.addEventListener('dw-monitor-state', onState as EventListener);
    return () => window.removeEventListener('dw-monitor-state', onState as EventListener);
  }, []);

  // ── Derive collapsed state from live session (if any) ──
  useEffect(() => {
    if (!session) return;
    const startedMs = session.startedAt ? new Date(session.startedAt).getTime() : 0;
    const finishedMs = session.finishedAt ? new Date(session.finishedAt).getTime() : Date.now();
    const elapsed = session.finishedAt ? finishedMs - startedMs : Date.now() - (startedMs || Date.now());
    const st =
      session.state === 'running'
        ? 'running'
        : session.state === 'broken'
          ? 'broken'
          : session.state === 'completed'
            ? 'success'
            : session.state === 'failed'
              ? 'failed'
              : session.state === 'stopped'
                ? 'stopped'
                : 'idle';
    setCollapsedState({
      nodeId: session.completedNodes,
      total: session.totalNodes,
      rows: session.rowsProcessed,
      elapsedMs: Math.max(0, elapsed || 0),
      status: st as MonitorPanelState['status'],
    });
  }, [session]);

  const toggleExpanded = useCallback(() => setExpanded((v) => !v), []);

  const onMouseDown = useCallback(
    (e: React.MouseEvent<HTMLDivElement>) => {
      e.preventDefault();
      dragging.current = { startY: e.clientY, startHeight: panelHeight };
      const onMove = (me: MouseEvent) => {
        if (!dragging.current) return;
        const delta = dragging.current.startY - me.clientY;
        const next = Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, dragging.current.startHeight + delta));
        setPanelHeight(next);
      };
      const onUp = () => {
        dragging.current = null;
        window.removeEventListener('mousemove', onMove);
        window.removeEventListener('mouseup', onUp);
        document.body.style.userSelect = '';
        document.body.style.cursor = '';
      };
      window.addEventListener('mousemove', onMove);
      window.addEventListener('mouseup', onUp);
      document.body.style.userSelect = 'none';
      document.body.style.cursor = 'row-resize';
    },
    [panelHeight]
  );

  // ── Collapse state color (theme-aware) ──
  const stateColor =
    collapsedState.status === 'failed'
      ? styles.dangerText
      : collapsedState.status === 'success'
        ? styles.successText
        : collapsedState.status === 'running'
          ? styles.accentText
          : collapsedState.status === 'broken'
            ? styles.warningText
            : styles.muted;
  const spin = collapsedState.status === 'running';

  // ── Collapsed bar (28px) ──
  if (!expanded) {
    return (
      <div className={`relative border-t ${styles.cardBorder} ${styles.cardBg}`}>
        <div
          className={`flex items-center gap-3 px-3 select-none cursor-pointer`}
          style={{ height: COLLAPSED_H }}
          onClick={toggleExpanded}
          title={t('dw.monitor.expand')}
        >
          <span
            className={`inline-block w-2 h-2 rounded-full ${spin ? 'animate-pulse' : ''}`}
            style={{ background: 'transparent' }}
          />
          <span className={`inline-block w-2 h-2 rounded-full ${spin ? 'animate-pulse' : ''}`} style={{ backgroundColor: stateColor.includes('Text') ? stateColor.split('text-')[1] : undefined }} />
          <span className="text-xs font-semibold" style={{ color: styles.cardText }}>
            {t('dw.monitor.progress', { x: collapsedState.nodeId, y: collapsedState.total })}
          </span>
          <span className="text-[10px] font-mono" style={{ color: styles.cardTextMuted }}>
            {t('dw.monitor.rows', { n: collapsedState.rows })}
          </span>
          <span className="text-[10px] font-mono" style={{ color: styles.cardTextMuted }}>
            {(collapsedState.elapsedMs / 1000).toFixed(1)}s
          </span>
          <span className="ml-auto flex items-center gap-1" style={{ color: styles.cardTextMuted }}>
            <ChevronsUpDown size={14} />
            <span className="text-[10px]">{t('dw.monitor.expand')}</span>
          </span>
        </div>
      </div>
    );
  }

  // ── Expanded panel ──
  return (
    <div
      className={`relative border-t ${styles.cardBorder} ${styles.cardBg} overflow-hidden`}
      style={{ height: panelHeight + TAB_BAR_H + 4 }}
    >
      {/* 4px drag separator (top edge) */}
      <div
        className="absolute top-0 left-0 right-0 h-1 cursor-row-resize z-20"
        onMouseDown={onMouseDown}
        title={t('dw.monitor.resize')}
      >
        <div className={`mx-2 my-[4px] h-[2px] rounded ${styles.sidebarBg}`} />
      </div>

      {/* 36px Tab bar */}
      <div
        className={`flex items-center px-2 gap-1 border-b ${styles.cardBorder}`}
        style={{ height: TAB_BAR_H }}
      >
        {TAB_IDS.map((id) => (
          <button
            key={id}
            onClick={() => setTab(id)}
            className={`px-3 py-1 text-xs font-semibold rounded transition-colors ${
              tab === id ? `${styles.accentBg} ${styles.accentText}` : `${styles.cardTextMuted}`
            }`}
          >
            {t(`dw.monitor.tab.${id}`)}
          </button>
        ))}
        <span className="ml-auto flex items-center gap-2">
          <button
            onClick={() => setAutoScroll((v) => !v)}
            className={`flex items-center gap-1 text-[10px] px-2 py-0.5 rounded border ${styles.cardBorder} ${
              autoScroll ? `${styles.successBg} ${styles.successText}` : `${styles.sidebarBg} ${styles.cardTextMuted}`
            }`}
            title={autoScroll ? t('dw.monitor.pauseScroll') : t('dw.monitor.resumeScroll')}
          >
            {autoScroll ? <PlayCircle size={12} /> : <PauseCircle size={12} />}
            {autoScroll ? t('dw.monitor.autoScroll') : t('dw.monitor.paused')}
          </button>
          <button
            onClick={toggleExpanded}
            className={`flex items-center gap-1 text-[10px] px-2 py-0.5 rounded border ${styles.cardBorder} ${styles.cardTextMuted}`}
            title={t('dw.monitor.collapse')}
          >
            <ChevronsDownUp size={12} />
            {t('dw.monitor.collapse')}
          </button>
        </span>
      </div>

      {/* Tab content */}
      <div className="overflow-hidden px-2 pb-1" style={{ height: panelHeight - 20 }}>
        {tab === 'overview' && <OverviewTab />}
        {tab === 'logs' && <LogsTab autoScroll={autoScroll} />}
        {tab === 'debug' && <DebugTab session={session} />}
        {tab === 'preview' && <PreviewTab session={session} />}
      </div>
    </div>
  );
}
