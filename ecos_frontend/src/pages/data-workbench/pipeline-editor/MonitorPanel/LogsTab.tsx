/**
 * LogsTab — 单列流式日志 (增量拉取 + 自动滚动 + 级别筛选 + 关键字搜索 + 节点下拉)
 *
 * - 后端 /api/v1/pipeline/debug/executions/{execId}/logs 返回 NodeLog[]
 *   (调试执行是同步事件, 此端点按 executionId 一次性返回全量, 前端 1.2s 做增量)
 * - ERROR 行带红色左边框, WARNING 黄色, INFO 默认
 * - 点击 ERROR/WARNING 行 → 触发 dw-monitor-navigate (画布定位节点)
 * - 顶部 22px 工具栏: [级别 ▼ | 节点 ▼ | 关键字 ⊞ | 计数]
 * - autoScroll 由面板壳传入, 关闭时新增日志不滚动
 *
 * @license Apache-2.0
 */
import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AlertOctagon, AlertTriangle, Info, Search } from 'lucide-react';
import { useTheme } from '../../../../components/ThemeContext';
import { useLanguage } from '../../../../components/LanguageContext';
import { getDebugSession, getExecutionLog } from '../../pipelineDebugApi';
import type { DebugLogLine } from '../../pipelineDebugApi';

const LEVELS = ['INFO', 'WARNING', 'ERROR'] as const;
type Level = (typeof LEVELS)[number];

interface Props {
  /** 是否自动滚动 (面板 Tab 栏开关) */
  autoScroll: boolean;
  /** 可选 live session (缺失则从 sessionStorage 读 executionId, 否则轮询 debug session 端点) */
  session?: { sessionId: string } | null;
}

export default function LogsTab({ autoScroll, session }: Props) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [levels, setLevels] = useState<Set<Level>>(new Set(LEVELS));
  const [keyword, setKeyword] = useState<string>('');
  const [nodeIdFilter, setNodeIdFilter] = useState<string>('all');
  const [lines, setLines] = useState<DebugLogLine[]>([]);
  const [lastSeq, setLastSeq] = useState<number>(0);
  const [knownNodes, setKnownNodes] = useState<string[]>([]);
  const scrollRef = useRef<HTMLDivElement>(null);
  const execIdRef = useRef<string>('');
  const pollRef = useRef<number>(0);

  // ── Resolved executionId — session 优先, 否则 sessionStorage ──
  const sessionId = session?.sessionId || '';
  useEffect(() => {
    if (sessionId) {
      let cancelled = false;
      (async () => {
        try {
          const snap = await getDebugSession(sessionId);
          if (!cancelled && snap?.executionId) {
            execIdRef.current = snap.executionId;
          }
        } catch {
          // ignore
        }
      })();
      return () => {
        cancelled = true;
      };
    }
  }, [sessionId]);

  const executionId = execIdRef.current;

  // ── Poll execution logs every 1.2s ──
  useEffect(() => {
    if (!executionId) return;
    if (pollRef.current) {
      window.clearInterval(pollRef.current);
    }
    let cancelled = false;
    const tick = async () => {
      try {
        const all = await getExecutionLog(executionId);
        if (!cancelled && Array.isArray(all)) {
          // 节点列表去重
          const ids = Array.from(new Set(all.map((l) => l.nodeId).filter(Boolean) as string[]));
          setKnownNodes(ids);
          const fresh = all.filter((l) => l.seq > lastSeq);
          if (fresh.length > 0) {
            setLines((prev) => [...prev, ...fresh]);
            setLastSeq(all[all.length - 1]?.seq || lastSeq);
            // 容量上限
            if (lines.length + fresh.length > 1000) {
              setLines((prev) => [...prev.slice(prev.length - 700), ...fresh]);
            }
          }
        }
      } catch {
        // ignore
      }
    };
    tick();
    pollRef.current = window.setInterval(tick, 1200);
    return () => {
      cancelled = true;
      if (pollRef.current) {
        window.clearInterval(pollRef.current);
        pollRef.current = 0;
      }
    };
  }, [executionId, lastSeq]); // eslint-disable-line react-hooks/exhaustive-deps

  // ── Auto-scroll when new lines come (and autoScroll is on) ──
  useEffect(() => {
    if (autoScroll && scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [lines, autoScroll]);

  // ── Apply filters ──
  const filtered = useMemo(() => {
    const kw = keyword.trim().toLowerCase();
    return lines.filter((l) => {
      if (!levels.has(l.level)) return false;
      if (nodeIdFilter !== 'all' && l.nodeId !== nodeIdFilter) return false;
      if (kw && !l.message.toLowerCase().includes(kw)) return false;
      return true;
    });
  }, [lines, levels, nodeIdFilter, keyword]);

  const toggleLevel = (lv: Level) => {
    setLevels((prev) => {
      const next = new Set(prev);
      if (next.has(lv)) next.delete(lv);
      else next.add(lv);
      return next;
    });
  };

  const onLineClick = (l: DebugLogLine) => {
    if (l.nodeId) {
      window.dispatchEvent(new CustomEvent('dw-monitor-navigate', { detail: { nodeId: l.nodeId } }));
    }
  };

  return (
    <div className="h-full flex flex-col gap-1 pr-1">
      {/* Toolbar */}
      <div className={`flex items-center gap-1 text-[10px] ${styles.cardTextMuted} shrink-0`}>
        {LEVELS.map((lv) => {
          const Icon = lv === 'ERROR' ? AlertOctagon : lv === 'WARNING' ? AlertTriangle : Info;
          const active = levels.has(lv);
          const activeCls =
            lv === 'ERROR'
              ? `${styles.dangerBg} ${styles.dangerText}`
              : lv === 'WARNING'
                ? `${styles.warningBg} ${styles.warningText}`
                : `${styles.infoBg} ${styles.infoText}`;
          return (
            <button
              key={lv}
              onClick={() => toggleLevel(lv)}
              className={`flex items-center gap-1 px-2 py-0.5 rounded border ${styles.cardBorder} ${
                active ? activeCls : `${styles.sidebarBg} ${styles.cardTextMuted}`
              }`}
            >
              <Icon size={10} />
              {lv}
            </button>
          );
        })}
        <select
          value={nodeIdFilter}
          onChange={(e) => setNodeIdFilter(e.target.value)}
          className={`text-[10px] px-1 py-0.5 rounded border ${styles.cardBorder} ${styles.sidebarBg} ${styles.cardText} outline-none`}
        >
          <option value="all">{t('dw.monitor.log.allNodes')}</option>
          {knownNodes.map((n) => (
            <option key={n} value={n}>
              {n}
            </option>
          ))}
        </select>
        <div className={`relative flex-1`}>
          <Search size={10} className={`absolute left-1.5 top-1/2 -translate-y-1/2 ${styles.cardTextMuted}`} />
          <input
            type="text"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder={t('dw.monitor.log.search')}
            className={`w-full text-[10px] px-2 py-0.5 pl-5 rounded border ${styles.cardBorder} ${styles.sidebarBg} ${styles.cardText} outline-none`}
          />
        </div>
        <span className={`px-1.5 py-0.5 rounded ${styles.sidebarBg} ${styles.cardText} font-mono`}>
          {lines.length}
        </span>
      </div>

      {/* Log lines */}
      <div
        ref={scrollRef}
        className="flex-1 overflow-y-auto flex flex-col gap-px pr-1"
        style={{ background: styles.sidebarBg }}
      >
        {filtered.length === 0 ? (
          <div className={`flex items-center justify-center h-full text-[10px] ${styles.cardTextMuted}`}>
            {t('dw.monitor.log.empty')}
          </div>
        ) : (
          filtered.map((l) => {
            const isErr = l.level === 'ERROR';
            const isWarn = l.level === 'WARNING';
            return (
              <button
                key={l.seq}
                onClick={() => onLineClick(l)}
                className={`text-left text-[10px] font-mono px-2 py-0.5 flex items-start gap-2 ${
                  isErr
                    ? `border-l-2 ${styles.dangerText}`
                    : isWarn
                      ? `border-l-2 ${styles.warningText}`
                      : ''
                }`}
                style={{
                  borderLeftColor: isErr ? undefined : isWarn ? undefined : 'transparent',
                  borderColor: isErr ? styles.dangerBorder : isWarn ? styles.warningBorder : undefined,
                }}
              >
                <span className={`shrink-0 w-16 text-right ${styles.cardTextMuted}`}>
                  {new Date(l.atMs).toLocaleTimeString()}
                </span>
                <span
                  className={`shrink-0 w-12 text-[9px] font-semibold ${
                    isErr ? styles.dangerText : isWarn ? styles.warningText : styles.infoText
                  }`}
                >
                  {l.level}
                </span>
                {l.nodeId && (
                  <span className={`shrink-0 max-w-[120px] truncate ${styles.cardTextMuted}`}>{l.nodeId}</span>
                )}
                <span className={`flex-1 whitespace-pre-wrap break-all ${styles.cardText}`}>{l.message}</span>
              </button>
            );
          })
        )}
      </div>
    </div>
  );
}
