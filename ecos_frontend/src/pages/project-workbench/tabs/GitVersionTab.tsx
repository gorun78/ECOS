/**
 * ECOS 项目工作台 — Git 配置版本中心 Tab
 * 场景配置的 Git 版本管理界面（本地模拟）。
 * 
 * 父级状态引用: gitCommits, gitBranches, selectedScenarioId,
 *   gitCommitMsg, setGitCommitMsg, gitTerminalLogs, isGitPushing,
 *   gitViewMode, setGitViewMode, selectedCommitId, setSelectedCommitId,
 *   handleGitCommitManual, handleGitCheckoutCommit, handleSwitchGitBranch, handleGitPushRemote
 */

import React from 'react';
import LucideIcon from '../../../components/LucideIcon';

interface GitVersionTabProps {
  /** 所有场景的 commit 历史 */
  gitCommits: { [scenarioId: string]: any[] };
  /** 所有场景的当前分支 */
  gitBranches: { [scenarioId: string]: string };
  /** 当前选中场景 ID */
  selectedScenarioId: string;
  /** 新 commit 消息输入 */
  gitCommitMsg: string;
  setGitCommitMsg: (v: string) => void;
  /** 终端模拟日志 */
  gitTerminalLogs: string[];
  /** push 进行中 */
  isGitPushing: boolean;
  /** 视图模式 */
  gitViewMode: 'visual' | 'json';
  setGitViewMode: (v: 'visual' | 'json') => void;
  /** 选中的 commit ID */
  selectedCommitId: string | null;
  setSelectedCommitId: (id: string | null) => void;
  /** 操作处理器 */
  handleGitCommitManual: (message: string) => void;
  handleGitCheckoutCommit: (commit: any) => void;
  handleSwitchGitBranch: (branchName: string) => void;
  handleGitPushRemote: () => void;
}

export default function GitVersionTab(props: GitVersionTabProps) {
  const {
    gitCommits, gitBranches, selectedScenarioId,
    gitCommitMsg, setGitCommitMsg, gitTerminalLogs, isGitPushing,
    gitViewMode, setGitViewMode, selectedCommitId, setSelectedCommitId,
    handleGitCommitManual, handleGitCheckoutCommit, handleSwitchGitBranch, handleGitPushRemote
  } = props;

  const currentCommits = gitCommits[selectedScenarioId] || [];
  const currentBranch = gitBranches[selectedScenarioId] || 'main';
  const selectedCommit = currentCommits.find(c => c.id === selectedCommitId) || null;

  const onSubmitCommit = (e: React.FormEvent) => {
    e.preventDefault();
    handleGitCommitManual(gitCommitMsg);
  };

  return (
    <div className="space-y-4">
      {/* Git Status Bar */}
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-4 flex items-center gap-4">
        <div className="flex items-center gap-2 p-2 bg-slate-950 rounded border border-slate-800">
          <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
          <span className="text-xs font-mono font-bold text-slate-200">
            <LucideIcon name="GitBranch" size={11} className="inline mr-1 text-orange-400" />
            {currentBranch}
          </span>
        </div>
        <div className="text-xs text-slate-400 font-mono flex items-center gap-4">
          <span>{currentCommits.length} commits</span>
          <span className="text-slate-600">|</span>
          <span>ECOS Zero-Trust 签名认证 ✓</span>
        </div>
        <div className="ml-auto">
          <button
            onClick={handleGitPushRemote}
            disabled={isGitPushing}
            className="px-3 py-1.5 bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 text-white text-xs font-bold rounded flex items-center gap-1 transition-all cursor-pointer"
          >
            <LucideIcon name="Upload" size={11} />
            {isGitPushing ? '正在推送...' : 'Push to Remote'}
          </button>
        </div>
      </div>

      {/* Commit History */}
      <div className="bg-slate-950 border border-slate-800 rounded-xl overflow-hidden">
        <div className="px-4 py-3 bg-slate-950/80 border-b border-slate-800 flex items-center justify-between">
          <span className="text-xs font-bold text-white flex items-center gap-2">
            <LucideIcon name="History" size={13} className="text-indigo-400" />
            Commit History ({currentBranch})
          </span>
          <div className="flex gap-1">
            <button
              onClick={() => setGitViewMode('visual')}
              className={`px-2 py-0.5 text-[10px] font-bold rounded cursor-pointer ${gitViewMode === 'visual' ? 'bg-indigo-600 text-white' : 'bg-slate-800 text-slate-400'}`}
            >Visual</button>
            <button
              onClick={() => setGitViewMode('json')}
              className={`px-2 py-0.5 text-[10px] font-bold rounded cursor-pointer ${gitViewMode === 'json' ? 'bg-indigo-600 text-white' : 'bg-slate-800 text-slate-400'}`}
            >JSON</button>
          </div>
        </div>

        {currentCommits.length === 0 ? (
          <div className="p-8 text-center text-slate-500">
            <LucideIcon name="GitCommit" size={32} className="mx-auto opacity-30 mb-2" />
            <p className="text-xs">暂无版本提交记录。请先通过 7 步向导创建场景。</p>
          </div>
        ) : gitViewMode === 'visual' ? (
          <div className="divide-y divide-slate-800/60 max-h-72 overflow-y-auto">
            {[...currentCommits].reverse().map(commit => (
              <div
                key={commit.id}
                onClick={() => setSelectedCommitId(selectedCommitId === commit.id ? null : commit.id)}
                className={`p-3 cursor-pointer transition-all ${
                  selectedCommitId === commit.id ? 'bg-indigo-950/40 border-l-2 border-indigo-500' : 'hover:bg-slate-900/60'
                }`}
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="text-[11px] font-mono text-amber-400 font-bold">{commit.hash}</span>
                    <span className="text-[11px] font-sans text-slate-200 truncate max-w-md">{commit.message}</span>
                  </div>
                  <div className="flex items-center gap-2 shrink-0">
                    <span className="text-[10px] text-slate-500 font-mono">{commit.date}</span>
                    <span className="text-[10px] text-slate-400">by {commit.author}</span>
                  </div>
                </div>

                {selectedCommitId === commit.id && (
                  <div className="mt-2 pt-2 border-t border-slate-800 flex gap-2">
                    <button
                      onClick={(e) => { e.stopPropagation(); handleGitCheckoutCommit(commit); }}
                      className="px-2 py-1 bg-amber-600 hover:bg-amber-700 text-white text-[10px] font-bold rounded cursor-pointer flex items-center gap-1"
                    >
                      <LucideIcon name="RotateCcw" size={10} />
                      回滚至此版本
                    </button>
                    <div className="flex-1 bg-slate-950 rounded p-2 text-[10px] font-mono text-slate-400 overflow-x-auto">
                      <span className="text-slate-500">bindings:</span>{' '}
                      {commit.bindings ? `${commit.bindings.datasets?.length || 0} datasets, ${commit.bindings.objectTypes?.length || 0} objects, ${commit.bindings.securityPolicies?.length || 0} policies` : '(empty)'}
                    </div>
                  </div>
                )}
              </div>
            ))}
          </div>
        ) : (
          <pre className="p-4 text-[11px] font-mono text-slate-300 max-h-72 overflow-y-auto whitespace-pre-wrap">
            {JSON.stringify(currentCommits, null, 2)}
          </pre>
        )}
      </div>

      {/* New Commit Form */}
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-4">
        <span className="text-xs font-bold text-white flex items-center gap-2 mb-3">
          <LucideIcon name="PlusCircle" size={13} className="text-indigo-400" />
          手动冻结配置版本 (Manual Commit)
        </span>
        <form onSubmit={onSubmitCommit} className="flex gap-2">
          <input
            type="text"
            value={gitCommitMsg}
            onChange={(e) => setGitCommitMsg(e.target.value)}
            placeholder="输入本次配置变更的 commit message..."
            className="flex-1 p-2 bg-slate-950 border border-slate-800 rounded text-xs text-white outline-none font-mono"
          />
          <button
            type="submit"
            className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-bold rounded cursor-pointer flex items-center gap-1"
          >
            <LucideIcon name="GitCommit" size={12} />
            Commit
          </button>
        </form>
      </div>

      {/* Branch Switcher */}
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-4">
        <span className="text-xs font-bold text-white flex items-center gap-2 mb-2">
          <LucideIcon name="GitFork" size={13} className="text-indigo-400" />
          分支切换 (Switch Branch)
        </span>
        <div className="flex gap-1.5 flex-wrap">
          {['main', 'develop', 'feature/safety-audit', 'release/v2.1'].map(branch => (
            <button
              key={branch}
              onClick={() => handleSwitchGitBranch(branch)}
              className={`px-2.5 py-1 rounded text-[10px] font-mono font-bold cursor-pointer transition-all ${
                currentBranch === branch
                  ? 'bg-emerald-950 text-emerald-400 border border-emerald-800/40'
                  : 'bg-slate-800 text-slate-400 border border-slate-700 hover:border-slate-600'
              }`}
            >
              {branch}
            </button>
          ))}
        </div>
      </div>

      {/* Terminal Logs (Push Simulation) */}
      {gitTerminalLogs.length > 0 && (
        <div className="bg-black border border-slate-800 rounded-xl overflow-hidden">
          <div className="px-3 py-2 bg-slate-950 border-b border-slate-800 flex items-center gap-2">
            <span className="w-2.5 h-2.5 rounded-full bg-red-500" />
            <span className="w-2.5 h-2.5 rounded-full bg-amber-500" />
            <span className="w-2.5 h-2.5 rounded-full bg-emerald-500" />
            <span className="text-[10px] text-slate-500 ml-2 font-mono">ECOS Git Terminal</span>
          </div>
          <pre className="p-3 text-[11px] font-mono text-emerald-400 leading-relaxed max-h-60 overflow-y-auto">
            {gitTerminalLogs.join('\n')}
          </pre>
        </div>
      )}
    </div>
  );
}
