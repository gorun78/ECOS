/**
 * 工作台 L2 — 四按钮操作组（2026-11-27 用户要求；2026-11-27 二次裁决改为与「场景运行」
 * 在顶部工具条同排并行排列）。
 *
 * <p>四枚按钮：场景编辑（切到定义面 `?tab=definition`）、历史版本（scenario-def 仓库
 * Git 提交列表）、场景状态（工具条内弹层，列出本页四项状态，更新时间到秒）、
 * 研究模式（切到 `?tab=research` 独立页，与场景运行互斥）。</p>
 *
 * <p><b>如实宣称</b>：历史版本只走 `services/gitService.ts` 单通道（只传 repositoryId），
 * 按提交信息中的场景 id 过滤；仓库未就绪/请求失败时呈现后端 reason/message 或错误原文，
 * 不伪造提交记录。</p>
 */
import React, { useEffect, useRef, useState } from 'react';
import { Activity, FlaskConical, History, Pencil } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import Modal from '../../../components/common/Modal';
import logger from '../../../services/logger';
import { fetchCommits, type GitCommitsPageVO, type GitCommitVO } from '../../../services/gitService';

/** 场景定义归档仓库（ScenarioDefinitionService.GIT_REPO_SCENARIO 同值） */
const SCENARIO_GIT_REPO = 'scenario-def';
/** 历史版本一次拉取的提交上限（按场景 id 过滤前） */
const COMMITS_LIMIT = 100;

/**
 * 时间戳格式化为「YYYY-MM-DD HH:mm:ss」（本地时区，到秒）。
 * 同时兼容 ISO-LocalDateTime（无时区，按本地）与带 +00:00 偏移的 Git 时间。
 */
export function formatTimestampSeconds(value?: string | null): string {
  if (!value) return '—';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return value;
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} `
    + `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}

export interface ScenarioHeaderActionsProps {
  scenarioId: string;
  /** 当前是否已落在定义面（场景编辑按钮高亮态） */
  editActive: boolean;
  /** 当前是否已落在研究模式页（研究模式按钮高亮态） */
  researchActive: boolean;
  safety: string;
  integrity: string;
  pending: string;
  /** 最近更新时间（已格式化到秒） */
  updated: string;
  onEditDefinition: () => void;
  onResearchMode: () => void;
}

export default function ScenarioHeaderActions({
  scenarioId,
  editActive,
  researchActive,
  safety,
  integrity,
  pending,
  updated,
  onEditDefinition,
  onResearchMode,
}: ScenarioHeaderActionsProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [statusOpen, setStatusOpen] = useState(false);
  const [versionsOpen, setVersionsOpen] = useState(false);
  const groupRef = useRef<HTMLDivElement | null>(null);

  // 点击弹层外部/按 Esc 关闭场景状态
  useEffect(() => {
    if (!statusOpen) return;
    const onDown = (e: MouseEvent) => {
      if (groupRef.current && !groupRef.current.contains(e.target as Node)) setStatusOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setStatusOpen(false);
    };
    document.addEventListener('mousedown', onDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [statusOpen]);

  const idleBtn = `inline-flex items-center gap-1 text-xs font-bold px-2.5 py-1 rounded border cursor-pointer ${styles.cardBg} ${styles.cardTextMuted} ${styles.cardBorder}`;
  const activeBtn = `inline-flex items-center gap-1 text-xs font-bold px-2.5 py-1 rounded border cursor-pointer ${styles.accentBg} ${styles.accentSolidText} ${styles.accentBorder}`;

  const statusRows: Array<{ id: string; label: string; value: string; color: string }> = [
    { id: 'safety', label: t('workspace.l2.stat.safety'), value: safety, color: styles.infoText },
    { id: 'integrity', label: t('workspace.l2.stat.integrity'), value: integrity, color: styles.accentText },
    { id: 'pending', label: t('workspace.l2.stat.pending'), value: pending, color: styles.warningText },
    { id: 'updated', label: t('workspace.l2.stat.updated'), value: updated, color: styles.successText },
  ];

  return (
    <div ref={groupRef} className="flex items-center gap-1.5 relative">
      <button
        type="button"
        onClick={onEditDefinition}
        className={editActive ? activeBtn : idleBtn}
      >
        <Pencil size={12} />
        {t('workspace.l2.act.edit')}
      </button>

      <button
        type="button"
        onClick={() => setVersionsOpen(true)}
        className={idleBtn}
      >
        <History size={12} />
        {t('workspace.l2.act.versions')}
      </button>

      <button
        type="button"
        onClick={() => setStatusOpen(v => !v)}
        aria-expanded={statusOpen}
        className={statusOpen ? activeBtn : idleBtn}
      >
        <Activity size={12} />
        {t('workspace.l2.act.status')}
      </button>

      <button
        type="button"
        onClick={onResearchMode}
        className={researchActive ? activeBtn : idleBtn}
      >
        <FlaskConical size={12} />
        {t('workspace.l2.act.research')}
      </button>

      {statusOpen && (
        <div
          role="dialog"
          aria-label={t('workspace.l2.act.status')}
          className={`absolute right-0 top-full mt-1 z-20 w-72 max-w-[calc(100vw-2rem)] rounded-lg border shadow-lg p-3 ${styles.cardBg} ${styles.cardBorder}`}
        >
          <div className={`text-[10px] font-bold uppercase mb-2 ${styles.cardTextMuted}`}>
            {t('workspace.l2.status.title')}
          </div>
          <ul className="space-y-1.5">
            {statusRows.map(row => (
              <li key={row.id} className="flex items-center justify-between gap-3">
                <span className={`text-[11px] font-bold ${styles.cardTextMuted}`}>{row.label}</span>
                <span className={`text-[11px] font-mono font-bold ${row.color}`}>{row.value}</span>
              </li>
            ))}
          </ul>
        </div>
      )}

      <ScenarioVersionsModal
        scenarioId={scenarioId}
        open={versionsOpen}
        onClose={() => setVersionsOpen(false)}
      />
    </div>
  );
}

interface VersionsModalProps {
  scenarioId: string;
  open: boolean;
  onClose: () => void;
}

type VersionsState =
  | { phase: 'loading' }
  | { phase: 'error'; message: string }
  | { phase: 'unavailable'; reason: string }
  | { phase: 'ready'; rows: GitCommitVO[] };

/** 解析提交信息里的定义版本号（`Scenario definition release <id> v<N> …`） */
function parseVersion(message: string): string | null {
  const m = /(?:^|\s)v(\d+)(?:\s|$)/.exec(message);
  return m ? m[1] : null;
}

function ScenarioVersionsModal({ scenarioId, open, onClose }: VersionsModalProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [state, setState] = useState<VersionsState>({ phase: 'loading' });

  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    setState({ phase: 'loading' });
    fetchCommits(SCENARIO_GIT_REPO, 0, COMMITS_LIMIT)
      .then((page: GitCommitsPageVO) => {
        if (cancelled) return;
        const items = Array.isArray(page.items) ? page.items : [];
        if (page.status && page.status !== 'ok' && items.length === 0) {
          setState({ phase: 'unavailable', reason: page.reason || page.status });
          return;
        }
        // 仓库承载全部场景的定义归档，按提交信息中的场景 id 过滤出本场景的历史
        const rows = items.filter(c => typeof c.message === 'string' && c.message.includes(scenarioId));
        setState({ phase: 'ready', rows });
      })
      .catch((e: unknown) => {
        if (cancelled) return;
        logger.error('workbench-versions', e);
        setState({ phase: 'error', message: e instanceof Error ? e.message : String(e) });
      });
    return () => { cancelled = true; };
  }, [open, scenarioId]);

  return (
    <Modal
      isOpen={open}
      onClose={onClose}
      title={t('workspace.l2.versions.title')}
      size="lg"
      footer={null}
    >
      <p className={`text-[11px] leading-relaxed mb-2 ${styles.cardTextMuted}`}>
        {t('workspace.l2.versions.hint')}
      </p>
      {state.phase === 'loading' && (
        <p className={`text-xs ${styles.cardTextMuted}`}>{t('workspace.l2.versions.loading')}</p>
      )}
      {state.phase === 'unavailable' && (
        <p className={`text-xs ${styles.warningText}`}>
          {t('workspace.l2.versions.unavailable', { reason: state.reason })}
        </p>
      )}
      {state.phase === 'error' && (
        <p className={`text-xs ${styles.dangerText}`}>
          {t('workspace.l2.versions.error', { msg: state.message })}
        </p>
      )}
      {state.phase === 'ready' && state.rows.length === 0 && (
        <p className={`text-xs ${styles.cardTextMuted}`}>{t('workspace.l2.versions.empty')}</p>
      )}
      {state.phase === 'ready' && state.rows.length > 0 && (
        <ul className="max-h-80 overflow-y-auto">
          {state.rows.map(row => {
            const version = parseVersion(row.message);
            return (
              <li
                key={row.commitId || row.id}
                className={`flex items-center gap-2 py-1.5 border-b last:border-b-0 ${styles.cardBorder}`}
              >
                <span className={`font-mono text-[11px] ${styles.accentText}`}>
                  {(row.commitId || row.id).slice(0, 8)}
                </span>
                {version && (
                  <span className={`text-[10px] border px-1 rounded font-mono ${styles.successBg} ${styles.successText} ${styles.successBorder}`}>
                    v{version}
                  </span>
                )}
                <span className="ml-auto flex items-center gap-2">
                  <span className={`text-[11px] ${styles.cardTextMuted}`}>{row.author || '—'}</span>
                  <span className={`text-[11px] font-mono ${styles.cardTextMuted}`}>
                    {formatTimestampSeconds(row.committedAt)}
                  </span>
                </span>
              </li>
            );
          })}
        </ul>
      )}
      {state.phase === 'ready' && state.rows.length > 0 && (
        <p className={`mt-2 text-[10px] ${styles.cardTextMuted}`}>
          {t('workspace.l2.versions.count', { n: state.rows.length })}
        </p>
      )}
    </Modal>
  );
}
