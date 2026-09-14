/**
 * SessionList — Agent Playground 侧栏会话列表
 * 职责：拉取/展示/切换/新建历史会话
 * @license Apostle-2.0
 */
import React, { useEffect, useState, useCallback } from 'react';
import { History, RefreshCw, Plus, MessageSquare, Loader2, AlertCircle } from 'lucide-react';
import { fetchAgentSessions } from '../../../pages/aiworkbench/api';
import type { AgentSessionSaved } from '../../../types/aiworkbench';
import { useTheme } from '../../ThemeContext';
import { useLanguage } from '../../LanguageContext';

interface SessionListProps {
  /** 当前 Agent ID；为 null 时不请求 */
  agentId: string | null;
  /** 当前激活会话 ID（null = 全新会话） */
  activeSessionId: string | null;
  /** 切换会话，null 表示新建会话 */
  onSelect: (sessionId: string | null) => void;
  /** 新建会话回调 */
  onNewSession: () => void;
}

/**
 * Agent Playground 会话侧栏。
 * 加载/切换/新建会话，对齐 fetchAgentSessions 接口。
 */
export default function SessionList({
  agentId,
  activeSessionId,
  onSelect,
  onNewSession,
}: SessionListProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [sessions, setSessions] = useState<AgentSessionSaved[]>([]);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string>('');

  const load = useCallback(async () => {
    if (!agentId) {
      setSessions([]);
      return;
    }
    setLoading(true);
    setError('');
    try {
      const list = await fetchAgentSessions(agentId);
      // 按 lastActiveAt 倒序，便于开发者从最近的会话切入
      const sorted = [...list].sort((a, b) =>
        (a.lastActiveAt || '').localeCompare(b.lastActiveAt || ''));
      setSessions(sorted);
    } catch (e) {
      setError(e?.message || String(e) || t('aiworkbench.playground.loadError'));
      setSessions([]);
    } finally {
      setLoading(false);
    }
  }, [agentId, t]);

  useEffect(() => {
    void load();
  }, [load]);

  const canCreate = Boolean(agentId);

  return (
    <aside className={`w-60 shrink-0 border-r ${styles.cardBorder} flex flex-col h-full select-none`}>
      {/* 标题栏 + 刷新 */}
      <div className={`p-3 border-b ${styles.cardBorder} flex items-center justify-between shrink-0`}>
        <span className={`flex items-center gap-1.5 font-semibold text-xs ${styles.cardText}`}>
          <History size={13} className={styles.muted} />
          {t('aiworkbench.playground.sessionListTitle')}
        </span>
        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={() => void load()}
            disabled={!agentId || loading}
            className={`p-1 rounded ${styles.cardBg} border ${styles.cardBorder} ${styles.muted} hover:opacity-75 disabled:opacity-40 cursor-pointer`}
            title={t('aiworkbench.playground.refreshSessions')}
            aria-label={t('aiworkbench.playground.refreshSessions')}
          >
            <RefreshCw size={12} className={loading ? 'animate-spin' : ''} />
          </button>
        </div>
      </div>

      {/* 新建按钮 */}
      <div className={`p-2 border-b ${styles.cardBorder} shrink-0`}>
        <button
          type="button"
          onClick={onNewSession}
          disabled={!canCreate}
          className={`w-full px-2 py-1.5 rounded-lg text-[11px] font-semibold flex items-center justify-center gap-1.5 ${
            canCreate
              ? `${styles.accentBg} text-white ${styles.accentHover} hover:opacity-90`
              : `${styles.badgeBg} ${styles.muted} opacity-60`
          } cursor-pointer transition-opacity disabled:cursor-not-allowed`}
        >
          <Plus size={12} />
          {t('aiworkbench.playground.newSession')}
        </button>
      </div>

      {/* 列表区 */}
      <div className="flex-1 overflow-y-auto p-2 space-y-1">
        {error && (
          <div className={`p-2 rounded-lg border ${styles.dangerBorder} ${styles.dangerBg} ${styles.dangerText} text-[10px] flex items-start gap-1.5`}>
            <AlertCircle size={11} className="shrink-0 mt-0.5" />
            <span>{error}</span>
          </div>
        )}

        {!error && sessions.length === 0 && !loading && (
          <div className="px-2 py-4 text-center">
            <MessageSquare size={18} className={`mx-auto mb-2 ${styles.muted} opacity-50`} />
            <p className={`text-[10px] ${styles.muted}`}>{t('aiworkbench.playground.noSessions')}</p>
          </div>
        )}

        {loading && (
          <div className="px-2 py-4 flex items-center justify-center gap-1.5 text-[10px]">
            <Loader2 size={12} className="animate-spin" />
            <span className={styles.muted}>{t('aiworkbench.playground.loadingSessions')}</span>
          </div>
        )}

        {!loading && sessions.map((s) => {
          const isActive = s.id === activeSessionId;
          const time = (s.lastActiveAt || s.createdAt || '').slice(0, 16).replace('T', ' ');
          return (
            <button
              key={s.id}
              type="button"
              onClick={() => onSelect(s.id)}
              className={`w-full p-2 rounded-lg text-left border transition-colors cursor-pointer flex items-start gap-2 ${
                isActive
                  ? `${styles.sidebarActiveBg} ${styles.sidebarActiveText}`
                  : `border-transparent hover:${styles.sidebarHoverBg} ${styles.cardText}`
              }`}
            >
              <MessageSquare size={11} className={`shrink-0 mt-0.5 ${isActive ? styles.sidebarActiveText : styles.muted}`} />
              <div className="min-w-0 flex-1">
                <div className={`text-[11px] font-medium truncate ${isActive ? styles.sidebarActiveText : styles.cardText}`}>
                  {s.id.slice(0, 8)}…
                </div>
                <div className={`text-[9px] ${styles.muted} font-mono flex items-center gap-1 mt-0.5`}>
                  <span>{time && time.replace('T', ' ')}</span>
                  {typeof s.messageCount === 'number' && (
                    <span className={`ml-auto px-1 rounded ${styles.badgeBg}`}>
                      {s.messageCount} {t('aiworkbench.playground.messagesSent')}
                    </span>
                  )}
                </div>
              </div>
            </button>
          );
        })}
      </div>
    </aside>
  );
}
