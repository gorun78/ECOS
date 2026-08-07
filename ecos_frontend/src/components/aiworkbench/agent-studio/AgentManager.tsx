/**
 * AgentManager — T9-2 Agent管理
 * Agent list with CRUD, status toggle, and version rollback (last 3 versions).
 * @license Apache-2.0
 */

import React, { useState, useEffect, useCallback } from 'react';
import * as Icons from 'lucide-react';
import { useTheme } from '../../ThemeContext';
import type { AIPAgent, AIPAgentVersion } from '../../../types/aiworkbench';
import {
  fetchManagedAgents,
  updateManagedAgent,
  toggleAgentStatus,
  fetchAgentVersions,
  rollbackAgent,
} from '../../../pages/aiworkbench/api';

const Icon = ({ name, size, className }: { name: string; size?: number; className?: string }) => {
  const Comp = (Icons as any)[name] || (Icons as any).HelpCircle;
  return <Comp size={size} className={className} />;
};

interface AgentManagerProps {
  showToast?: (type: 'success' | 'info' | 'error', msg: string) => void;
}

export default function AgentManager({ showToast }: AgentManagerProps) {
  const { styles } = useTheme();
  const [agents, setAgents] = useState<AIPAgent[]>([]);
  const [loading, setLoading] = useState(true);

  // Edit modal
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editName, setEditName] = useState('');
  const [editPrompt, setEditPrompt] = useState('');
  const [editModel, setEditModel] = useState('');
  const [editTemp, setEditTemp] = useState(0.7);
  const [editMaxIter, setEditMaxIter] = useState(10);
  const [saving, setSaving] = useState(false);

  // Version rollback
  const [versionAgentId, setVersionAgentId] = useState<string | null>(null);
  const [versions, setVersions] = useState<AIPAgentVersion[]>([]);
  const [rollingBack, setRollingBack] = useState(false);

  const loadAgents = useCallback(async () => {
    setLoading(true);
    try {
      const data = await fetchManagedAgents();
      setAgents(data);
    } catch {
      // keep current list
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadAgents(); }, [loadAgents]);

  const handleEdit = (a: AIPAgent) => {
    setEditingId(a.id);
    setEditName(a.name);
    setEditPrompt(a.systemPrompt);
    setEditModel(a.modelId);
    setEditTemp(0.7);
    setEditMaxIter(10);
  };

  const handleSave = async () => {
    if (!editingId || saving) return;
    setSaving(true);
    try {
      await updateManagedAgent(editingId, {
        name: editName.trim(),
        systemPrompt: editPrompt.trim(),
        model: editModel,
        temperature: editTemp,
        maxIterations: editMaxIter,
      });
      setAgents(prev => prev.map(a =>
        a.id === editingId
          ? { ...a, name: editName.trim(), systemPrompt: editPrompt.trim(), modelId: editModel, lastModified: new Date().toISOString() }
          : a
      ));
      showToast?.('success', 'Agent配置已保存');
      setEditingId(null);
    } catch {
      showToast?.('error', '保存失败，请重试');
    } finally {
      setSaving(false);
    }
  };

  const handleToggleStatus = async (a: AIPAgent) => {
    const newStatus: 'active' | 'development' = a.status === 'active' ? 'development' : 'active';
    try {
      await toggleAgentStatus(a.id, newStatus);
      setAgents(prev => prev.map(x => x.id === a.id ? { ...x, status: newStatus, lastModified: new Date().toISOString() } : x));
      showToast?.('success', newStatus === 'active' ? 'Agent已上线' : 'Agent已下线');
    } catch {
      showToast?.('error', '状态切换失败');
    }
  };

  const handleVersions = async (agentId: string) => {
    if (versionAgentId === agentId) {
      setVersionAgentId(null);
      return;
    }
    setVersionAgentId(agentId);
    try {
      const data = await fetchAgentVersions(agentId);
      // Show last 3 versions
      setVersions(data.slice(-3).reverse());
    } catch {
      setVersions([]);
    }
  };

  const handleRollback = async (agentId: string, version: number) => {
    if (rollingBack) return;
    if (!window.confirm(`确定回滚到版本 v${version} 吗？当前配置将被覆盖。`)) return;
    setRollingBack(true);
    try {
      await rollbackAgent(agentId, version);
      showToast?.('success', `已回滚到版本 v${version}`);
      setVersionAgentId(null);
      loadAgents();
    } catch {
      showToast?.('error', '回滚失败');
    } finally {
      setRollingBack(false);
    }
  };

  return (
    <div className="flex flex-col h-full overflow-hidden">
      {/* Header */}
      <div className={`p-4 border-b ${styles.cardBorder} ${styles.inputBg} flex items-center justify-between`}>
        <div>
          <h2 className={`text-sm font-bold ${styles.cardText}`}>Agent管理</h2>
          <p className={`text-[10px] ${styles.cardTextMuted}`}>
            {loading ? '加载中...' : `共 ${agents.length} 个Agent实例`}
          </p>
        </div>
        <button
          onClick={loadAgents}
          className={`px-3 py-1.5 border ${styles.cardBorder} rounded-lg ${styles.cardTextMuted} hover:${styles.inputBg} cursor-pointer text-[11px] font-semibold flex items-center gap-1`}
        >
          <Icon name="RefreshCw" size={11} />
          刷新
        </button>
      </div>

      {/* Agent Table */}
      <div className="flex-1 overflow-y-auto p-4">
        {loading ? (
          <div className="space-y-3">
            {[1, 2, 3].map(i => (
              <div key={i} className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4 animate-pulse`}>
                <div className={`h-4 w-32 ${styles.inputBg} rounded mb-2`} />
                <div className={`h-3 w-48 ${styles.inputBg} rounded`} />
              </div>
            ))}
          </div>
        ) : agents.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 gap-3">
            <Icon name="Bot" size={32} className={styles.cardTextMuted} />
            <p className={`text-xs ${styles.cardTextMuted}`}>暂无Agent实例，请前往市场创建</p>
          </div>
        ) : (
          <div className="space-y-3">
            {agents.map(a => (
              <div key={a.id}>
                <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4 transition-all ${a.status === 'development' ? 'opacity-60' : ''}`}>
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3 flex-1 min-w-0">
                      <span className={`p-2 rounded-lg ${styles.badgeBg} ${styles.accentText} shrink-0`}>
                        <Icon name={a.avatar || 'Bot'} size={16} />
                      </span>
                      <div className="min-w-0">
                        <div className="flex items-center gap-2">
                          <h3 className={`text-xs font-bold ${styles.cardText} truncate`}>{a.name}</h3>
                          <span className={`w-1.5 h-1.5 rounded-full shrink-0 ${a.status === 'active' ? 'bg-green-500 animate-pulse' : 'bg-amber-500'}`} />
                          <span className={`text-[10px] ${a.status === 'active' ? 'text-green-600' : 'text-amber-600'} font-semibold`}>
                            {a.status === 'active' ? '在线' : '下线'}
                          </span>
                        </div>
                        <p className={`text-[10px] ${styles.cardTextMuted} truncate`}>
                          {a.role} · {a.modelId.replace('-1.5-pro', '')} · 最后修改 {a.lastModified?.slice(0, 10)}
                        </p>
                      </div>
                    </div>

                    {/* Actions */}
                    <div className="flex items-center gap-1.5 shrink-0 ml-3">
                      <button
                        onClick={() => handleEdit(a)}
                        className={`p-1.5 rounded-md ${styles.cardTextMuted} hover:${styles.inputBg} cursor-pointer transition-colors`}
                        title="编辑"
                      >
                        <Icon name="Settings2" size={12} />
                      </button>
                      <button
                        onClick={() => handleToggleStatus(a)}
                        className={`p-1.5 rounded-md cursor-pointer transition-colors ${a.status === 'active' ? 'text-amber-500 hover:bg-amber-50' : 'text-green-500 hover:bg-green-50'}`}
                        title={a.status === 'active' ? '下线' : '上线'}
                      >
                        <Icon name={a.status === 'active' ? 'PauseCircle' : 'PlayCircle'} size={12} />
                      </button>
                      <button
                        onClick={() => handleVersions(a.id)}
                        className={`p-1.5 rounded-md ${versionAgentId === a.id ? styles.accentText : styles.cardTextMuted} hover:${styles.inputBg} cursor-pointer transition-colors`}
                        title="版本历史"
                      >
                        <Icon name="History" size={12} />
                      </button>
                    </div>
                  </div>

                  {/* Version Panel */}
                  {versionAgentId === a.id && (
                    <div className={`mt-3 pt-3 border-t ${styles.cardBorder}`}>
                      <p className={`text-[10px] font-bold ${styles.cardTextMuted} mb-2`}>
                        最近版本（共{versions.length}个）
                      </p>
                      {versions.length === 0 ? (
                        <p className={`text-[10px] ${styles.cardTextMuted}`}>暂无版本记录</p>
                      ) : (
                        <div className="space-y-1.5">
                          {versions.map(v => (
                            <div key={v.id} className={`flex items-center justify-between px-3 py-1.5 rounded ${styles.inputBg} text-[10px]`}>
                              <div className="flex items-center gap-2">
                                <span className={`font-mono font-bold ${styles.cardText}`}>v{v.version}</span>
                                <span className={styles.cardTextMuted}>{v.createdAt?.slice(0, 16).replace('T', ' ')}</span>
                              </div>
                              <button
                                onClick={() => handleRollback(a.id, v.version)}
                                disabled={rollingBack}
                                className={`px-2 py-0.5 ${styles.accentText} hover:${styles.badgeBg} rounded text-[9px] font-semibold cursor-pointer disabled:opacity-50`}
                              >
                                回滚
                              </button>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Edit Modal */}
      {editingId && (
        <div className={`fixed inset-0 z-50 flex items-center justify-center ${styles.appBg}/40 backdrop-blur-xs`}>
          <div className={`${styles.cardBg} rounded-xl shadow-2xl border ${styles.cardBorder} w-full max-w-md overflow-hidden`}>
            <div className={`px-4 py-3 border-b ${styles.cardBorder} ${styles.inputBg} flex items-center justify-between`}>
              <h3 className={`font-bold ${styles.cardText} text-xs`}>编辑Agent配置</h3>
              <button onClick={() => setEditingId(null)} className={`${styles.cardTextMuted} cursor-pointer`}>
                <Icon name="X" size={15} />
              </button>
            </div>

            <div className="p-4 space-y-3 overflow-y-auto max-h-[70vh]">
              <div className="space-y-1">
                <label className={`block text-[11px] ${styles.cardTextMuted} font-semibold`}>名称</label>
                <input
                  type="text"
                  value={editName}
                  onChange={e => setEditName(e.target.value)}
                  className={`w-full px-2.5 py-1.5 border ${styles.inputBorder} rounded-lg text-xs ${styles.cardBg} ${styles.cardText}`}
                />
              </div>
              <div className="space-y-1">
                <label className={`block text-[11px] ${styles.cardTextMuted} font-semibold`}>System Prompt</label>
                <textarea
                  value={editPrompt}
                  onChange={e => setEditPrompt(e.target.value)}
                  rows={5}
                  className={`w-full px-2.5 py-1.5 border ${styles.inputBorder} rounded-lg text-xs resize-none font-mono ${styles.cardBg} ${styles.cardText}`}
                />
              </div>
              <div className="space-y-1">
                <label className={`block text-[11px] ${styles.cardTextMuted} font-semibold`}>模型</label>
                <select
                  value={editModel}
                  onChange={e => setEditModel(e.target.value)}
                  className={`w-full px-2.5 py-1.5 border ${styles.inputBorder} rounded-lg text-xs ${styles.cardBg} ${styles.cardText}`}
                >
                  <option value="gemini-1.5-pro">Gemini 1.5 Pro</option>
                  <option value="gemini-1.5-flash">Gemini 1.5 Flash</option>
                  <option value="claude-3.5-sonnet">Claude 3.5 Sonnet</option>
                  <option value="gpt-4o">GPT-4o</option>
                </select>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1">
                  <label className={`block text-[11px] ${styles.cardTextMuted} font-semibold`}>Temperature</label>
                  <input
                    type="number"
                    value={editTemp}
                    onChange={e => setEditTemp(parseFloat(e.target.value) || 0)}
                    min={0}
                    max={2}
                    step={0.1}
                    className={`w-full px-2.5 py-1.5 border ${styles.inputBorder} rounded-lg text-xs ${styles.cardBg} ${styles.cardText}`}
                  />
                </div>
                <div className="space-y-1">
                  <label className={`block text-[11px] ${styles.cardTextMuted} font-semibold`}>最大轮次</label>
                  <input
                    type="number"
                    value={editMaxIter}
                    onChange={e => setEditMaxIter(parseInt(e.target.value) || 1)}
                    min={1}
                    max={50}
                    className={`w-full px-2.5 py-1.5 border ${styles.inputBorder} rounded-lg text-xs ${styles.cardBg} ${styles.cardText}`}
                  />
                </div>
              </div>
            </div>

            <div className={`px-4 py-3 border-t ${styles.cardBorder} flex justify-end gap-2`}>
              <button
                onClick={() => setEditingId(null)}
                className={`px-3 py-1.5 border ${styles.cardBorder} rounded-lg hover:${styles.inputBg} ${styles.cardTextMuted} transition-colors cursor-pointer text-[11px] font-semibold`}
              >
                取消
              </button>
              <button
                onClick={handleSave}
                disabled={saving}
                className={`px-4 py-1.5 ${styles.accentBg} hover:opacity-90 text-white rounded-lg transition-all font-bold cursor-pointer text-[11px] disabled:opacity-50`}
              >
                {saving ? '保存中...' : '保存'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
