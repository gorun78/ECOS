/**
 * GitVersionPanel — right-side drawer listing per-pipeline Git history.
 *
 * PMO-52 T3: list 7-char short SHAs newest-first from
 * GET /api/v1/engine/data/pipeline/git/versions/{id}; first entry gets a
 * "latest/current" pill. Empty state = empty array before first commit;
 * error state = message shown inline. onRestore is a callback the parent
 * wires (stub: showLocalToast for now), awaiting the real rollback wave.
 */
import React, { useCallback, useEffect, useState } from 'react';
import { GitBranch, History, Loader2, X, RotateCcw } from 'lucide-react';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';
import { apiFetchData } from '../../../api';

/** Props for the GitVersionPanel. */
export interface GitVersionPanelProps {
  /** Pipeline definition id (matches backend PipelineVO.id). */
  pipelineId: string;
  /** Pipeline display name (shown in header, for contextual emptiness). */
  pipelineName: string;
  /** Close the drawer. */
  onClose: () => void;
  /**
   * Optional restore-to-version callback. When undefined, restore buttons
   * stay disabled and a tooltip hints the feature is pending.
   */
  onRestore?: (ref: string) => Promise<void> | void;
}

/** A single version row in the drawer. */
interface GitVersionEntry {
  /** 7-char short SHA. */
  sha: string;
  /** true for the top of the list (newest = "latest"). */
  isLatest: boolean;
}

const GitVersionPanel: React.FC<GitVersionPanelProps> = ({
  pipelineId,
  pipelineName,
  onClose,
  onRestore,
}) => {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [versions, setVersions] = useState<GitVersionEntry[]>([]);

  // ── Fetch version list once per pipelineId ──────────────
  const refresh = useCallback(async (): Promise<void> => {
    if (!pipelineId) {
      setLoading(false);
      setVersions([]);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const url = `/api/v1/engine/data/pipeline/git/versions/${encodeURIComponent(pipelineId)}`;
      const raw = (await apiFetchData<unknown>(url)) as
        | string[]
        | { data?: string[] }
        | undefined
        | null;
      const list: string[] = Array.isArray(raw)
        ? (raw as string[])
        : (raw as { data?: string[] })?.data ?? [];
      setVersions(
        list
          .filter((v): v is string => typeof v === 'string' && v.length > 0)
          .map((sha, i) => ({ sha: sha.slice(0, 7), isLatest: i === 0 }))
      );
    } catch (e) {
      console.warn('[pipeline-git] list versions failed:', e);
      setError(
        e instanceof Error ? e.message : typeof e === 'string' ? e : 'unknown'
      );
      setVersions([]);
    } finally {
      setLoading(false);
    }
  }, [pipelineId]);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      if (cancelled) return;
      await refresh();
    })();
    return () => {
      cancelled = true;
    };
  }, [refresh]);

  // ── Restore handler (deferred to a real rollback endpoint in a future wave) ──
  const handleRestore = useCallback(
    async (sha: string): Promise<void> => {
      if (!onRestore) return;
      const confirmMsg = t('dw.pipeline.git.restoreConfirm', { sha });
      // Defensive: a real confirmation dialog will land in the restore wave.
      // window.confirm is a safe dev-time guard against accidental clicks.
      const ok = window.confirm ? window.confirm(confirmMsg) : true;
      if (!ok) return;
      try {
        await onRestore(sha);
        // After restore succeeds, the parent commits a new version → refresh
        // so the list shows the new head at the top.
        void refresh();
      } catch (e) {
        console.warn('[pipeline-git] restore failed:', e);
        setError(e instanceof Error ? e.message : 'restore failed');
      }
    },
    [onRestore, refresh, t]
  );

  // ── Loading state ─────────────────────────────────────
  if (loading) {
    return (
      <div
        className={`fixed inset-y-0 right-0 z-40 w-80 flex flex-col border-l ${styles.cardBorder} ${styles.sidebarBg} shadow-2xl`}
        aria-label={t('dw.pipeline.git.title')}
      >
        <div className={`flex items-center gap-2 p-4 border-b ${styles.cardBorder}`}>
          <GitBranch size={14} style={{ color: styles.infoText }} />
          <span>{t('dw.pipeline.git.title')}</span>
        </div>
        <div className="flex-1 py-10 justify-center flex items-center">
          <Loader2 size={18} className={`animate-spin ${styles.cardTextMuted}`} />
        </div>
      </div>
    );
  }

  // ── Render the panel ─────────────────────────────────
  return (
    <div
      className={`fixed inset-y-0 right-0 z-40 w-80 flex flex-col border-l ${styles.cardBorder} ${styles.sidebarBg} shadow-2xl`}
      aria-label={t('dw.pipeline.git.title')}
    >
      {/* Header */}
      <div className={`flex items-center justify-between px-3 py-3 border-b ${styles.cardBorder}`}>
        <div className="flex items-center gap-2 min-w-0">
          <GitBranch size={14} className={styles.infoText} />
          <div className="min-w-0">
            <div className={`text-xs font-medium truncate max-w-[180px] ${styles.cardText}`}>
              {pipelineName || t('dw.pipeline.git.title')}
            </div>
            <div className={`text-[10px] truncate ${styles.cardTextMuted}`}>
              {t('dw.pipeline.git.title')}
            </div>
          </div>
        </div>
        <button
          type="button"
          onClick={onClose}
          className={`p-1 ${styles.cardTextMuted} hover:${styles.cardText} transition-colors no-break`}
          title={t('dw.pipeline.git.title')}
        >
          <X size={14} />
        </button>
      </div>

      {/* Body */}
      <div className="flex-1 overflow-y-auto">
        {error ? (
          <div className={`m-2 p-3 text-[11px] rounded border ${styles.dangerBorder} ${styles.dangerBg} ${styles.dangerText}`}>
            {t('dw.pipeline.git.error')}: {error}
          </div>
        ) : versions.length === 0 ? (
          <div className={`py-10 px-4 flex flex-col items-center gap-2 ${styles.cardTextMuted}`}>
            <History size={20} className="opacity-60" />
            <span className="text-[11px]">{t('dw.pipeline.git.empty')}</span>
          </div>
        ) : (
          <ul className="space-y-1 p-2">
            {versions.map((v) => (
              <li
                key={v.sha}
                className={`flex items-center justify-between gap-2 pl-2 pr-1 py-1.5 text-xs hover:${styles.cardBg} ${styles.cardBg} rounded`}
              >
                <span className="flex items-center gap-1.5 min-w-0">
                  <GitBranch size={12} className={`${styles.cardTextMuted} shrink-0`} />
                  <span className={`font-mono text-[11px] truncate ${styles.cardText}`}>{v.sha}</span>
                  {v.isLatest && (
                    <span className={`ml-1 px-1 py-0.5 rounded text-[9px] font-medium ${styles.infoBg} ${styles.infoText} border ${styles.infoBorder}`}>
                      {t('dw.pipeline.git.current')}
                    </span>
                  )}
                </span>
                <button
                  type="button"
                  disabled={!onRestore}
                  onClick={() => void handleRestore(v.sha)}
                  title={t('dw.pipeline.git.restore')}
                  className={`flex items-center gap-1 px-1.5 py-0.5 text-[10px] ${styles.cardTextMuted} hover:${styles.accentText} disabled:opacity-40 disabled:cursor-not-allowed transition-colors`}
                >
                  <RotateCcw size={10} />
                  {t('dw.pipeline.git.restore')}
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
};

export default GitVersionPanel;
