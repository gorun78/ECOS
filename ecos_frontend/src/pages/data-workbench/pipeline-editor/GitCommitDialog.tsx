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

const STATUS_CONFIG: Record<string, { label: string; color: string; bg: string }> = {
  modified: { label: 'M', color: 'text-amber-400', bg: 'bg-amber-500/10' },
  added: { label: 'A', color: 'text-emerald-400', bg: 'bg-emerald-500/10' },
  deleted: { label: 'D', color: 'text-rose-400', bg: 'bg-rose-500/10' },
  untracked: { label: 'U', color: 'text-slate-400', bg: 'bg-slate-500/10' },
  renamed: { label: 'R', color: 'text-blue-400', bg: 'bg-blue-500/10' },
};

// ─── Component ────────────────────────────────────────

const GitCommitDialog: React.FC<GitCommitDialogProps> = ({
  pipelineId,
  pipelineName,
  onClose,
  onSuccess,
  showToast,
}) => {
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
      <div className="relative z-10 bg-white rounded-xl shadow-2xl w-[520px] max-h-[600px] flex flex-col overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-4 py-3 border-b border-slate-200 bg-slate-50 shrink-0">
          <div className="flex items-center gap-2">
            <GitCommit size={16} className="text-indigo-600" />
            <span className="text-sm font-bold text-slate-800">保存 Pipeline</span>
          </div>
          <button
            onClick={onClose}
            className="p-1 rounded hover:bg-slate-200 text-slate-400 hover:text-slate-600 transition-colors"
          >
            <X size={16} />
          </button>
        </div>

        {/* Body */}
        <div className="flex-1 overflow-y-auto p-4 space-y-4">
          {/* Pipeline name display */}
          <div>
            <label className="text-[11px] text-slate-500 uppercase tracking-wider block mb-1">
              Pipeline
            </label>
            <div className="flex items-center gap-2 px-3 py-2 bg-blue-50 border border-blue-200 rounded-lg">
              <FileText size={14} className="text-blue-600" />
              <span className="text-xs font-semibold text-blue-800">{pipelineName}</span>
            </div>
          </div>

          {/* Commit message */}
          <div>
            <label className="text-[11px] text-slate-500 uppercase tracking-wider block mb-1">
              Commit Message
            </label>
            <textarea
              value={commitMessage}
              onChange={(e) => setCommitMessage(e.target.value)}
              placeholder="描述你的变更..."
              rows={3}
              className="w-full px-3 py-2 text-xs border border-slate-200 rounded-lg font-mono resize-none focus:border-indigo-400 focus:ring-1 focus:ring-indigo-200 outline-none transition-colors"
            />
          </div>

          {/* Branch selection */}
          <div>
            <label className="text-[11px] text-slate-500 uppercase tracking-wider block mb-1">
              分支
            </label>
            {showNewBranch ? (
              <div className="flex gap-2">
                <div className="flex-1 relative">
                  <GitBranch size={13} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400" />
                  <input
                    type="text"
                    value={newBranchName}
                    onChange={(e) => setNewBranchName(e.target.value)}
                    placeholder="新分支名，如 feature/transform-v2"
                    className="w-full pl-8 pr-3 py-1.5 text-xs border border-slate-200 rounded-lg focus:border-indigo-400 focus:ring-1 focus:ring-indigo-200 outline-none transition-colors"
                    autoFocus
                  />
                </div>
                <button
                  onClick={() => setShowNewBranch(false)}
                  className="px-2 py-1 text-[10px] text-slate-500 hover:text-slate-700 transition-colors"
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
                    className="w-full pl-8 pr-3 py-1.5 text-xs border border-slate-200 rounded-lg appearance-none bg-white focus:border-indigo-400 outline-none transition-colors"
                  >
                    {branches.map((b) => (
                      <option key={b.name} value={b.name}>
                        {b.name} {b.current ? '(current)' : ''}
                      </option>
                    ))}
                  </select>
                  <GitBranch size={13} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400" />
                  <ChevronDown size={13} className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 pointer-events-none" />
                </div>
                <button
                  onClick={() => { setShowNewBranch(true); setNewBranchName(''); }}
                  className="flex items-center gap-1 px-2 py-1 text-[10px] text-indigo-600 hover:bg-indigo-50 rounded-lg transition-colors"
                >
                  <Plus size={12} />
                  新分支
                </button>
              </div>
            )}
          </div>

          {/* Changed files list */}
          <div>
            <label className="text-[11px] text-slate-500 uppercase tracking-wider block mb-1">
              变更文件
              {loading ? (
                <Loader2 size={10} className="inline ml-1 animate-spin" />
              ) : (
                <span className="text-slate-400 ml-1">({files.length})</span>
              )}
            </label>
            <div className="border border-slate-200 rounded-lg overflow-hidden max-h-32 overflow-y-auto">
              {loading ? (
                <div className="flex items-center justify-center py-4">
                  <Loader2 size={14} className="text-slate-400 animate-spin" />
                </div>
              ) : files.length === 0 ? (
                <div className="flex items-center gap-1.5 px-3 py-3 text-[10px] text-slate-400">
                  <Check size={12} className="text-green-400" />
                  无文件变更
                </div>
              ) : (
                files.map((f, i) => {
                  const cfg = STATUS_CONFIG[f.status];
                  return (
                    <div
                      key={`${f.path}-${i}`}
                      className="flex items-center gap-2 px-3 py-1.5 border-b border-slate-50 last:border-b-0 hover:bg-slate-50 transition-colors"
                    >
                      <span className={`w-4 h-4 flex items-center justify-center rounded text-[9px] font-bold shrink-0 ${cfg?.bg || ''} ${cfg?.color || ''}`}>
                        {cfg?.label || '?'}
                      </span>
                      <FileText size={12} className="text-slate-400 shrink-0" />
                      <span className="text-[10px] text-slate-600 truncate font-mono">{f.path}</span>
                    </div>
                  );
                })
              )}
            </div>
          </div>

          {/* Error display */}
          {error && (
            <div className="px-3 py-2 border border-red-200 bg-red-50 rounded-lg text-[11px] text-red-600">
              {error}
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-between gap-2 px-4 py-3 border-t border-slate-200 bg-slate-50 shrink-0">
          <button
            onClick={onClose}
            className="px-3 py-1.5 text-xs text-slate-600 hover:bg-slate-200 rounded-lg transition-colors"
          >
            取消
          </button>
          <div className="flex items-center gap-2">
            <button
              onClick={handleSaveOnly}
              disabled={savingOnly || committing}
              className="flex items-center gap-1.5 px-4 py-1.5 text-xs border border-slate-300 text-slate-700 hover:bg-slate-100 rounded-lg transition-colors disabled:opacity-50"
            >
              {savingOnly ? <Loader2 size={12} className="animate-spin" /> : null}
              仅保存
            </button>
            <button
              onClick={handleCommit}
              disabled={committing || savingOnly}
              className="flex items-center gap-1.5 px-4 py-1.5 text-xs bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg font-medium transition-colors disabled:opacity-50 shadow-sm"
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
