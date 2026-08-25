/**
 * GitPanel — Git 版本管理面板
 *
 * 数据工作台左侧版本面板，提供分支选择、未提交变更列表、
 * 提交历史和 Git 操作栏。
 *
 * 功能:
 *  1. BranchSelector: 下拉选择分支 — GET /api/v1/ecos/git/branches
 *  2. UncommittedChangesList: 未提交变更列表 — GET /api/v1/ecos/git/status
 *  3. CommitHistoryList: 最近10条提交 — GET /api/v1/ecos/git/commits
 *  4. GitActionBar: commit / push / pull / tag 四个操作按钮
 *
 * 暗色主题: bg-[#141924], border-[#1E293B]
 * 字体: text-xs / text-[10px]
 *
 * @license Apache-2.0
 */

import React, { useState, useEffect, useCallback, useMemo } from 'react';
import {
  GitBranch,
  GitCommit,
  Plus,
  RotateCcw,
  Tag,
  ArrowUp,
  ArrowDown,
  Check,
  X,
  Loader2,
  ChevronDown,
  Clock,
  FileText,
} from 'lucide-react';
import {
  fetchBranches as fetchGitBranches,
  fetchStatus as fetchGitStatus,
  fetchCommits as fetchGitCommits,
  commit as gitCommit,
  createTag as gitCreateTag,
} from '../../services/gitService';
import { apiFetchData } from '../../api';
import { useTheme } from '../../components/ThemeContext';

// ── TypeScript 接口 ──────────────────────────────────────────

interface GitBranch {
  name: string;
  current: boolean;
  remote?: boolean;
}

interface GitFileStatus {
  path: string;
  status: 'modified' | 'added' | 'deleted' | 'untracked' | 'renamed';
  oldPath?: string;
}

interface GitStatusResponse {
  branch: string;
  files: GitFileStatus[];
  ahead: number;
  behind: number;
}

interface GitCommit {
  hash: string;
  shortHash: string;
  author: string;
  email: string;
  message: string;
  timestamp: string;
}

// ── 状态标签映射 ────────────────────────────────────────────

const STATUS_CONFIG: Record<string, { label: string; color: string; bg: string }> = {
  modified:  { label: 'M', color: `${styles.warningText}`, bg: `${styles.warningBg}` },
  added:     { label: 'A', color: `${styles.successText}`, bg: `${styles.successBg}` },
  deleted:   { label: 'D', color: `${styles.dangerText}`,    bg: `${styles.dangerBg}` },
  untracked: { label: 'U', color: `${styles.cardTextMuted}`,   bg: `${styles.sidebarBg}` },
  renamed:   { label: 'R', color: `${styles.infoText}`,    bg: `${styles.infoBg}` },
};

// ── 组件 ────────────────────────────────────────────────────

interface GitPanelProps {
  repoId: string;
}

export default function GitPanel({ repoId }: GitPanelProps) {
  const { styles } = useTheme();
  // 状态
  const [branches, setBranches] = useState<GitBranch[]>([]);
  const [selectedBranch, setSelectedBranch] = useState<string>('');
  const [status, setStatus] = useState<GitStatusResponse | null>(null);
  const [commits, setCommits] = useState<GitCommit[]>([]);

  const [loadingBranches, setLoadingBranches] = useState(false);
  const [loadingStatus, setLoadingStatus] = useState(false);
  const [loadingCommits, setLoadingCommits] = useState(false);
  const [actionLoading, setActionLoading] = useState<string | null>(null);

  const [error, setError] = useState<string | null>(null);
  const [branchDropdownOpen, setBranchDropdownOpen] = useState(false);

  // ── 数据获取 ──────────────────────────────────────────

  const fetchBranches = useCallback(async () => {
    setLoadingBranches(true);
    setError(null);
    try {
      const data = await fetchGitBranches<GitBranch[]>(repoId);
      setBranches(Array.isArray(data) ? data : []);
      const current = Array.isArray(data)
        ? data.find((b) => b.current)?.name ?? data[0]?.name ?? ''
        : '';
      if (current) setSelectedBranch(current);
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : 'Failed to fetch branches';
      setError(msg);
    } finally {
      setLoadingBranches(false);
    }
  }, [repoId]);

  const fetchStatus = useCallback(async () => {
    setLoadingStatus(true);
    setError(null);
    try {
      const data = await fetchGitStatus<GitStatusResponse>(repoId);
      setStatus(data);
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : 'Failed to fetch status';
      setError(msg);
    } finally {
      setLoadingStatus(false);
    }
  }, [repoId]);

  const fetchCommits = useCallback(async () => {
    setLoadingCommits(true);
    setError(null);
    try {
      const data = await fetchGitCommits<GitCommit[]>(repoId, undefined, 10);
      setCommits(Array.isArray(data) ? data : []);
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : 'Failed to fetch commits';
      setError(msg);
    } finally {
      setLoadingCommits(false);
    }
  }, [repoId]);

  // 初始加载 & 分支切换重新拉取
  useEffect(() => {
    fetchBranches();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    fetchStatus();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (selectedBranch) fetchCommits();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedBranch]);

  const refreshAll = useCallback(() => {
    fetchBranches();
    fetchStatus();
    if (selectedBranch) fetchCommits();
  }, [fetchBranches, fetchStatus, fetchCommits, selectedBranch]);

  // ── 操作处理 ──────────────────────────────────────────

  const handleGitAction = useCallback(async (action: string) => {
    setActionLoading(action);
    setError(null);
    try {
      switch (action) {
        case 'commit':
          await gitCommit(repoId);
          break;
        case 'tag':
          await gitCreateTag(repoId);
          break;
        case 'push':
        case 'pull':
          // Generic POST actions not yet covered by dedicated service functions
          await apiFetchData(`/api/v1/ecos/git/${action}?repoId=${encodeURIComponent(repoId)}`, { method: 'POST' });
          break;
        default:
          await apiFetchData(`/api/v1/ecos/git/${action}?repoId=${encodeURIComponent(repoId)}`, { method: 'POST' });
      }
      // 操作成功后刷新状态
      await Promise.all([fetchStatus(), fetchCommits()]);
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : `Git ${action} failed`;
      setError(msg);
    } finally {
      setActionLoading(null);
    }
  }, [fetchStatus, fetchCommits, repoId]);

  // ── 派生数据 ──────────────────────────────────────────

  const changeCount = useMemo(() => status?.files?.length ?? 0, [status]);

  // ── 渲染 ────────────────────────────────────────────

  return (
    <div className={`flex flex-col h-full bg-[#141924] ${styles.cardText} font-sans select-none`}>
      {/* 标题栏 */}
      <div className="flex items-center justify-between px-3 py-2.5 border-b border-[#1E293B] shrink-0">
        <div className="flex items-center gap-2">
          <GitBranch className={`w-4 h-4 ${styles.infoText}`} />
          <span className={`text-xs font-bold uppercase tracking-wider ${styles.cardText}`}>
            Git 版本
          </span>
        </div>
        <button
          onClick={refreshAll}
          className={`p-1 rounded hover:${styles.cardBg} ${styles.cardTextMuted} hover:${styles.cardText} transition`}
          title="刷新"
        >
          <RotateCcw className="w-3.5 h-3.5" />
        </button>
      </div>

      {/* 错误提示 */}
      {error && (
        <div className={`mx-3 mt-2 px-2.5 py-1.5 rounded border ${styles.dangerBorder} ${styles.dangerBg} ${styles.dangerText} text-[10px] flex items-start gap-1.5`}>
          <X className="w-3 h-3 mt-px shrink-0" />
          <span className="leading-relaxed">{error}</span>
        </div>
      )}

      <div className="flex-1 overflow-y-auto space-y-0.5">
        {/* ── 1. BranchSelector ─────────────────────── */}
        <div className="px-3 pt-3 pb-1">
          <div className="relative">
            <label className={`text-[10px] uppercase tracking-wider ${styles.muted} mb-1 block`}>
              分支
            </label>
            <button
              onClick={() => setBranchDropdownOpen(!branchDropdownOpen)}
              disabled={loadingBranches}
              className={`w-full flex items-center justify-between px-2.5 py-1.5 rounded-lg border border-[#1E293B] bg-[#1E2533] text-xs ${styles.cardText} hover:${styles.cardBorder} transition disabled:opacity-50`}
            >
              <span className="flex items-center gap-1.5 truncate">
                {loadingBranches ? (
                  <Loader2 className={`w-3 h-3 animate-spin ${styles.muted}`} />
                ) : (
                  <GitBranch className={`w-3 h-3 ${styles.infoText} shrink-0`} />
                )}
                <span className="truncate">{selectedBranch || '—'}</span>
              </span>
              <ChevronDown
                className={`w-3 h-3 ${styles.muted} transition-transform ${
                  branchDropdownOpen ? 'rotate-180' : ''
                }`}
              />
            </button>

            {/* 下拉菜单 */}
            {branchDropdownOpen && (
              <div className="absolute z-20 top-full left-0 right-0 mt-1 rounded-lg border border-[#1E293B] bg-[#1A1F2E] shadow-xl max-h-48 overflow-y-auto py-1">
                {branches.length === 0 && !loadingBranches ? (
                  <div className={`px-3 py-2 text-[10px] ${styles.muted} text-center`}>
                    无分支数据
                  </div>
                ) : (
                  branches.map((b) => (
                    <button
                      key={b.name}
                      onClick={() => {
                        setSelectedBranch(b.name);
                        setBranchDropdownOpen(false);
                      }}
                      className={`w-full flex items-center gap-2 px-3 py-1.5 text-xs transition text-left ${
                        b.name === selectedBranch
                          ? `${styles.infoBg} ${styles.infoText}`
                          : `${styles.cardTextMuted} hover:bg-white/5`
                      }`}
                    >
                      <GitBranch className="w-3 h-3 shrink-0" />
                      <span className="truncate">{b.name}</span>
                      {b.current && (
                        <span className={`text-[9px] px-1 py-px rounded ${styles.successBg} ${styles.successText} ml-auto shrink-0`}>
                          current
                        </span>
                      )}
                    </button>
                  ))
                )}
              </div>
            )}
          </div>
        </div>

        {/* ── Quick Stats ─────────────────────────── */}
        {status && (
          <div className="flex items-center gap-2 px-3 py-1.5">
            <span className={`text-[10px] ${styles.muted}`}>
              {status.ahead > 0 && (
                <span className="inline-flex items-center gap-0.5 mr-2">
                  <ArrowUp className={`w-2.5 h-2.5 ${styles.successText}`} />
                  <span className={`${styles.successText}`}>{status.ahead}</span>
                </span>
              )}
              {status.behind > 0 && (
                <span className="inline-flex items-center gap-0.5">
                  <ArrowDown className={`w-2.5 h-2.5 ${styles.warningText}`} />
                  <span className={`${styles.warningText}`}>{status.behind}</span>
                </span>
              )}
              {status.ahead === 0 && status.behind === 0 && (
                <span className={`${styles.successText}`}>
                  <Check className="w-2.5 h-2.5 inline mr-0.5" />
                  已同步
                </span>
              )}
            </span>
          </div>
        )}

        {/* ── 2. UncommittedChangesList ────────────── */}
        <div className="px-3 pt-2 pb-1">
          <h3 className={`text-[10px] uppercase tracking-wider ${styles.muted} mb-1.5 flex items-center justify-between`}>
            <span>未提交变更</span>
            {loadingStatus ? (
              <Loader2 className={`w-3 h-3 animate-spin ${styles.muted}`} />
            ) : (
              <span className={`${styles.cardTextMuted} tabular-nums`}>{changeCount}</span>
            )}
          </h3>

          {loadingStatus ? (
            <div className="flex items-center justify-center py-4">
              <Loader2 className={`w-4 h-4 ${styles.muted} animate-spin`} />
            </div>
          ) : changeCount === 0 ? (
            <div className={`flex items-center gap-1.5 py-2 text-[10px] ${styles.cardTextMuted}`}>
              <Check className={`w-3 h-3 ${styles.cardTextMuted}`} />
              工作区干净
            </div>
          ) : (
            <div className="space-y-0.5 max-h-40 overflow-y-auto">
              {status!.files.map((f, i) => {
                const cfg = STATUS_CONFIG[f.status] ?? STATUS_CONFIG.modified;
                return (
                  <div
                    key={`${f.path}-${i}`}
                    className={`flex items-center gap-1.5 px-2 py-1 rounded hover:${styles.cardBg} transition text-[10px]`}
                  >
                    <span
                      className={`w-4 h-4 flex items-center justify-center rounded text-[9px] font-bold ${cfg.bg} ${cfg.color} shrink-0`}
                    >
                      {cfg.label}
                    </span>
                    <FileText className={`w-3 h-3 ${styles.cardTextMuted} shrink-0`} />
                    <span className={`truncate ${styles.cardTextMuted}`}>{f.path}</span>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* ── 3. CommitHistoryList ─────────────────── */}
        <div className="px-3 pt-2 pb-1">
          <h3 className={`text-[10px] uppercase tracking-wider ${styles.muted} mb-1.5 flex items-center justify-between`}>
            <span>最近提交</span>
            {loadingCommits ? (
              <Loader2 className={`w-3 h-3 animate-spin ${styles.muted}`} />
            ) : (
              <span className={`${styles.cardTextMuted} tabular-nums`}>
                {commits.length}
              </span>
            )}
          </h3>

          {loadingCommits ? (
            <div className="flex items-center justify-center py-4">
              <Loader2 className={`w-4 h-4 ${styles.muted} animate-spin`} />
            </div>
          ) : commits.length === 0 ? (
            <div className={`flex items-center gap-1.5 py-2 text-[10px] ${styles.cardTextMuted}`}>
              <X className="w-3 h-3" />
              无提交记录
            </div>
          ) : (
            <div className="space-y-px max-h-64 overflow-y-auto">
              {commits.map((c) => (
                <div
                  key={c.hash}
                  className={`flex items-start gap-2 px-2 py-1.5 rounded hover:${styles.cardBg} transition group`}
                >
                  <GitCommit className={`w-3 h-3 ${styles.infoText} mt-0.5 shrink-0`} />
                  <div className="min-w-0 flex-1">
                    <div className={`text-[10px] ${styles.cardText} truncate leading-tight`}>
                      {c.message}
                    </div>
                    <div className="flex items-center gap-1.5 mt-0.5">
                      <span className={`text-[9px] ${styles.muted} truncate max-w-[120px]`}>
                        {c.author}
                      </span>
                      <span className={`text-[9px] ${styles.cardTextMuted}`}>
                        {formatRelativeTime(c.timestamp)}
                      </span>
                      <span className={`text-[9px] font-mono ${styles.cardTextMuted} ml-auto shrink-0`}>
                        {c.shortHash}
                      </span>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* ── 4. GitActionBar ────────────────────────── */}
      <div className="shrink-0 border-t border-[#1E293B] p-2.5 space-y-2">
        {/* 操作按钮行 */}
        <div className="grid grid-cols-4 gap-1.5">
          <ActionButton
            icon={<Plus className="w-3 h-3" />}
            label="Commit"
            loading={actionLoading === 'commit'}
            onClick={() => handleGitAction('commit')}
            variant="primary"
          />
          <ActionButton
            icon={<ArrowUp className="w-3 h-3" />}
            label="Push"
            loading={actionLoading === 'push'}
            onClick={() => handleGitAction('push')}
          />
          <ActionButton
            icon={<ArrowDown className="w-3 h-3" />}
            label="Pull"
            loading={actionLoading === 'pull'}
            onClick={() => handleGitAction('pull')}
          />
          <ActionButton
            icon={<Tag className="w-3 h-3" />}
            label="Tag"
            loading={actionLoading === 'tag'}
            onClick={() => handleGitAction('tag')}
          />
        </div>

        {/* 刷新 */}
        <button
          onClick={refreshAll}
          disabled={actionLoading !== null}
          className={`w-full flex items-center justify-center gap-1.5 py-1.5 rounded-lg border border-[#1E293B] text-[10px] ${styles.muted} hover:${styles.cardTextMuted} hover:${styles.cardBorder} transition disabled:opacity-50`}
        >
          <RotateCcw className="w-3 h-3" />
          刷新全部
        </button>
      </div>
    </div>
  );
}

// ── 子组件: ActionButton ────────────────────────────────────

interface ActionButtonProps {
  icon: React.ReactNode;
  label: string;
  loading: boolean;
  onClick: () => void;
  variant?: 'primary' | 'default';
}

function ActionButton({ icon, label, loading, onClick, variant = 'default' }: ActionButtonProps) {
  const base =
    'flex items-center justify-center gap-1 px-2 py-1.5 rounded-lg text-[10px] font-semibold transition disabled:opacity-50';
  const primary =
    `${styles.accentBg} hover:${styles.infoBg} ${styles.cardText} border ${styles.infoBorder}`;
  const defaultStyle =
    `border border-[#1E293B] ${styles.cardTextMuted} hover:${styles.cardText} hover:${styles.cardBorder}`;

  return (
    <button
      onClick={onClick}
      disabled={loading}
      className={`${base} ${variant === 'primary' ? primary : defaultStyle}`}
    >
      {loading ? (
        <Loader2 className="w-3 h-3 animate-spin" />
      ) : (
        icon
      )}
      <span>{label}</span>
    </button>
  );
}

// ── 工具函数 ──────────────────────────────────────────────

function formatRelativeTime(timestamp: string): string {
  if (!timestamp) return '';
  try {
    const now = Date.now();
    const then = new Date(timestamp).getTime();
    if (isNaN(then)) return timestamp.slice(0, 10);

    const diffSec = Math.floor((now - then) / 1000);
    if (diffSec < 60) return `${diffSec}s`;
    const diffMin = Math.floor(diffSec / 60);
    if (diffMin < 60) return `${diffMin}m`;
    const diffHr = Math.floor(diffMin / 60);
    if (diffHr < 24) return `${diffHr}h`;
    const diffDay = Math.floor(diffHr / 24);
    if (diffDay < 30) return `${diffDay}d`;
    const diffMonth = Math.floor(diffDay / 30);
    if (diffMonth < 12) return `${diffMonth}mo`;
    return `${Math.floor(diffMonth / 12)}y`;
  } catch {
    return timestamp.slice(0, 10);
  }
}
