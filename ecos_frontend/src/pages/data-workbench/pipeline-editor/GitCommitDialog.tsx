/**
 * GitCommitDialog — Git 提交对话框
 * commit message + 分支选择 + 变更文件列表
 * @license Apache-2.0
 */
import React, { useState, useEffect, useCallback } from 'react';
import {
  GitBranch, GitCommit, FileText,
  Loader2, X, Check, ChevronDown, Plus,
} from 'lucide-react';
import { apiFetch, apiFetchData } from '../../../api';
import { useTheme } from '../../../components/ThemeContext';

// ─── Types ────────────────────────────────────────────

interface GitBranch {
  name: string;
  current: boolean;
}

interface GitFileStatus {
  path: string;
  status: 'modified' | 'added' | 'deleted' | 'untracked' | 'renamed';
}

interface GitCommitDialogProps {
  pipelineId: string;
  pipelineName: string;
  onClose: () => void;
  onSuccess?: (commitId: string, message: string) => void;
  showToast?: (type: 'success' | 'error' | 'info', msg: string) => void;
}

// ─── Status config ────────────────────────────────────

const statusConfigFn = (styles: Record<string, string>): Record<string, { label: string; color: string; bg: string }> => ({
  modified: { label: 'M', color: styles.warningText, bg: styles.warningBg },
  added: { label: 'A', color: styles.successText, bg: styles.successBg },
  deleted: { label: 'D', color: styles.dangerText, bg: styles.dangerBg },
  untracked: { label: 'U', color: styles.cardTextMuted, bg: styles.sidebarBg },
  renamed: { label: 'R', color: styles.infoText, bg: styles.infoBg },
});

// ─── Component ────────────────────────────────────────

const GitCommitDialog: React.FC<GitCommitDialogProps> = ({
  pipelineId,
  pipelineName,
  onClose,
  onSuccess,
  showToast,
}) => {
  const { styles } = useTheme();
  const [commitMessage, setCommitMessage] = useState(`feat(pipeline): update ${pipelineName}`);
  const [branches, setBranches] = useState<GitBranch[]>([]);
  const [selectedBranch, setSelectedBranch] = useState('');
  const [newBranchName, setNewBranchName] = useState('');
  const [showNewBranch, setShowNewBranch] = useState(false);
  const [files, setFiles] = useState<GitFileStatus[]>([]);
  const [loading, setLoading] = useState(true);
  const [committing, setCommitting] = useState(false);
  const [savingOnly, setSavingOnly] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Load branches and status
  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      try {
        const [branchesData, statusData] = await Promise.all([
          apiFetchData<{ data?: GitBranch[] }>(
            `/api/v1/engine/data/pipeline/git/branches?pipelineId=${encodeURIComponent(pipelineId)}`
          ).catch(() => ({ data: [{ name: 'main', current: true }] })),
          apiFetchData<{ data?: { files: GitFileStatus[] } }>(
            `/api/v1/engine/data/pipeline/tasks/${encodeURIComponent(pipelineId)}/git/status`
          ).catch(() => ({ data: { files: [] } })),
        ]);

        if (cancelled) return;

        const brs = (branchesData as any)?.data || (branchesData as any)?.branches || [{ name: 'main', current: true }];
        setBranches(Array.isArray(brs) ? brs : []);
        const current = Array.isArray(brs) ? brs.find((b: GitBranch) => b.current)?.name || brs[0]?.name || 'main' : 'main';
        setSelectedBranch(current);

        const fileList = (statusData as any)?.data?.files || [];
        setFiles(fileList);
      } catch {
        setBranches([{ name: 'main', current: true }]);
        setSelectedBranch('main');
        setFiles([]);
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    load();
    return () => { cancelled = true; };
  }, [pipelineId]);

  const handleSaveOnly = useCallback(async () => {
    setSavingOnly(true);
    setError(null);
    try {
      await apiFetch(`/api/v1/engine/data/pipeline/tasks/${encodeURIComponent(pipelineId)}`, {
        method: 'PUT',
        body: JSON.stringify({ name: pipelineName }),
      });
      showToast?.('success', 'Pipeline 已保存');
      onClose();
    } catch (e: any) {
      setError(e?.message || '保存失败');
      showToast?.('error', `保存失败: ${e?.message || '未知错误'}`);
    } finally {
      setSavingOnly(false);
    }
  }, [pipelineId, pipelineName, onClose, showToast]);

  const handleCommit = useCallback(async () => {
    if (!commitMessage.trim()) {
      setError('请输入 commit message');
      return;
    }
    setCommitting(true);
    setError(null);
    try {
      const branchName = showNewBranch && newBranchName.trim() ? newBranchName.trim() : selectedBranch;
      const resp = await apiFetchData<{ data?: { commitId: string } }>(
        `/api/v1/engine/data/pipeline/tasks/${encodeURIComponent(pipelineId)}/git/commit`,
        {
          method: 'POST',
          body: JSON.stringify({
            message: commitMessage.trim(),
            branch: branchName,
            author: 'ecos-user',
          }),
        }
      );
      const commitId = (resp as any)?.data?.commitId || (resp as any)?.commitId || '';
      showToast?.('success', commitId ? `已提交: ${commitId.slice(0, 7)}` : `已提交到 ${branchName}`);
      onSuccess?.(commitId, commitMessage.trim());
      onClose();
    } catch (e: any) {
      setError(e?.message || '提交失败');
      showToast?.('error', `Git 提交失败: ${e?.message || '未知错误'}`);
    } finally {
      setCommitting(false);
    }
  }, [commitMessage, selectedBranch, newBranchName, showNewBranch, pipelineId, onClose, onSuccess, showToast]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      {/* Backdrop */}
      <div className="fixed inset-0 bg-black/40" onClick={onClose} />

      {/* Dialog */}
      <div className="relative z-10 ${styles.cardBg} rounded-xl shadow-2xl w-[520px] max-h-[600px] flex flex-col overflow-hidden">
        {/* Header */}
        <div className={`flex items-center justify-between px-4 py-3 border-b ${styles.cardBorder} ${styles.cardBg} shrink-0`}>
          <div className="flex items-center gap-2">
            <GitCommit size={16} className={`${styles.accentText}`} />
            <span className={`text-sm font-bold ${styles.cardText}`}>保存 Pipeline</span>
          </div>
          <button
            onClick={onClose}
            className={`p-1 rounded hover:${styles.sidebarBg} ${styles.cardTextMuted} hover:${styles.cardTextMuted} transition-colors`}
          >
            <X size={16} />
          </button>
        </div>

        {/* Body */}
        <div className="flex-1 overflow-y-auto p-4 space-y-4">
          {/* Pipeline name display */}
          <div>
            <label className={`text-[11px] ${styles.muted} uppercase tracking-wider block mb-1`}>
              Pipeline
            </label>
            <div className={`flex items-center gap-2 px-3 py-2 ${styles.infoBg} border ${styles.accentBorder} rounded-lg`}>
              <FileText size={14} className={`${styles.accentText}`} />
              <span className={`text-xs font-semibold ${styles.accentText}`}>{pipelineName}</span>
            </div>
          </div>

          {/* Commit message */}
          <div>
            <label className={`text-[11px] ${styles.muted} uppercase tracking-wider block mb-1`}>
              Commit Message
            </label>
            <textarea
              value={commitMessage}
              onChange={(e) => setCommitMessage(e.target.value)}
              placeholder="描述你的变更..."
              rows={3}
              className={`w-full px-3 py-2 text-xs border ${styles.cardBorder} rounded-lg font-mono resize-none focus:${styles.infoBorder} focus:ring-1 focus:${styles.infoBorder} outline-none transition-colors`}
            />
          </div>

          {/* Branch selection */}
          <div>
            <label className={`text-[11px] ${styles.muted} uppercase tracking-wider block mb-1`}>
              分支
            </label>
            {showNewBranch ? (
              <div className="flex gap-2">
                <div className="flex-1 relative">
                  <GitBranch size={13} className={`absolute left-2.5 top-1/2 -translate-y-1/2 ${styles.cardTextMuted}`} />
                  <input
                    type="text"
                    value={newBranchName}
                    onChange={(e) => setNewBranchName(e.target.value)}
                    placeholder="新分支名，如 feature/transform-v2"
                    className={`w-full pl-8 pr-3 py-1.5 text-xs border ${styles.cardBorder} rounded-lg focus:${styles.infoBorder} focus:ring-1 focus:${styles.infoBorder} outline-none transition-colors`}
                    autoFocus
                  />
                </div>
                <button
                  onClick={() => setShowNewBranch(false)}
                  className={`px-2 py-1 text-[10px] ${styles.muted} hover:${styles.cardTextMuted} transition-colors`}
                >
                  <X size={14} />
                </button>
              </div>
            ) : (
              <div className="flex gap-2">
                <div className="flex-1 relative">
                  <select
                    value={selectedBranch}
                    onChange={(e) => setSelectedBranch(e.target.value)}
                    className={`w-full pl-8 pr-3 py-1.5 text-xs border ${styles.cardBorder} rounded-lg appearance-none ${styles.cardBg} focus:${styles.infoBorder} outline-none transition-colors`}
                  >
                    {branches.map((b) => (
                      <option key={b.name} value={b.name}>
                        {b.name} {b.current ? '(current)' : ''}
                      </option>
                    ))}
                  </select>
                  <GitBranch size={13} className={`absolute left-2.5 top-1/2 -translate-y-1/2 ${styles.cardTextMuted}`} />
                  <ChevronDown size={13} className={`absolute right-2.5 top-1/2 -translate-y-1/2 ${styles.cardTextMuted} pointer-events-none`} />
                </div>
                <button
                  onClick={() => { setShowNewBranch(true); setNewBranchName(''); }}
                  className={`flex items-center gap-1 px-2 py-1 text-[10px] ${styles.accentText} hover:${styles.infoBg} rounded-lg transition-colors`}
                >
                  <Plus size={12} />
                  新分支
                </button>
              </div>
            )}
          </div>

          {/* Changed files list */}
          <div>
            <label className={`text-[11px] ${styles.muted} uppercase tracking-wider block mb-1`}>
              变更文件
              {loading ? (
                <Loader2 size={10} className="inline ml-1 animate-spin" />
              ) : (
                <span className={`${styles.cardTextMuted} ml-1`}>({files.length})</span>
              )}
            </label>
            <div className={`border ${styles.cardBorder} rounded-lg overflow-hidden max-h-32 overflow-y-auto`}>
              {loading ? (
                <div className="flex items-center justify-center py-4">
                  <Loader2 size={14} className={`${styles.cardTextMuted} animate-spin`} />
                </div>
              ) : files.length === 0 ? (
                <div className={`flex items-center gap-1.5 px-3 py-3 text-[10px] ${styles.cardTextMuted}`}>
                  <Check size={12} className={`${styles.successText}`} />
                  无文件变更
                </div>
              ) : (
                files.map((f, i) => {
                  const cfg = statusConfigFn(styles as unknown as Record<string, string>)[f.status];
                  return (
                    <div
                      key={`${f.path}-${i}`}
                      className={`flex items-center gap-2 px-3 py-1.5 border-b ${styles.cardBorder} last:border-b-0 hover:${styles.cardBg} transition-colors`}
                    >
                      <span className={`w-4 h-4 flex items-center justify-center rounded text-[9px] font-bold shrink-0 ${cfg?.bg || ''} ${cfg?.color || ''}`}>
                        {cfg?.label || '?'}
                      </span>
                      <FileText size={12} className={`${styles.cardTextMuted} shrink-0`} />
                      <span className={`text-[10px] ${styles.cardTextMuted} truncate font-mono`}>{f.path}</span>
                    </div>
                  );
                })
              )}
            </div>
          </div>

          {/* Error display */}
          {error && (
            <div className={`px-3 py-2 border ${styles.dangerBorder} ${styles.dangerBg} rounded-lg text-[11px] ${styles.dangerText}`}>
              {error}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className={`flex items-center justify-between gap-2 px-4 py-3 border-t ${styles.cardBorder} ${styles.cardBg} shrink-0`}>
          <button
            onClick={onClose}
            className={`px-3 py-1.5 text-xs ${styles.cardTextMuted} hover:${styles.sidebarBg} rounded-lg transition-colors`}
          >
            取消
          </button>
          <div className="flex items-center gap-2">
            <button
              onClick={handleSaveOnly}
              disabled={savingOnly || committing}
              className={`flex items-center gap-1.5 px-4 py-1.5 text-xs border ${styles.inputBorder} ${styles.cardTextMuted} hover:${styles.sidebarBg} rounded-lg transition-colors disabled:opacity-50`}
            >
              {savingOnly ? <Loader2 size={12} className="animate-spin" /> : null}
              仅保存
            </button>
            <button
              onClick={handleCommit}
              disabled={committing || savingOnly}
              className={`flex items-center gap-1.5 px-4 py-1.5 text-xs ${styles.accentBg} hover:${styles.accentBg} ${styles.cardText} rounded-lg font-medium transition-colors disabled:opacity-50 shadow-sm`}
            >
              {committing ? (
                <Loader2 size={12} className="animate-spin" />
              ) : (
                <GitCommit size={12} />
              )}
              保存并提交
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default GitCommitDialog;
