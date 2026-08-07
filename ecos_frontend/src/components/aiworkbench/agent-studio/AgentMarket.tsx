/**
 * AgentMarket — T9-1 Agent市场
 * Shows 6 built-in agent templates as cards with instantiate capability.
 * @license Apache-2.0
 */

import React, { useState, useEffect, useMemo } from 'react';
import * as Icons from 'lucide-react';
import { useTheme } from '../../ThemeContext';
import type { AIPAgentTemplate, AIPAgent } from '../../../types/aiworkbench';
import { fetchAgentTemplates, instantiateAgent } from '../../../pages/aiworkbench/api';

const Icon = ({ name, size, className }: { name: string; size?: number; className?: string }) => {
  const Comp = (Icons as any)[name] || (Icons as any).HelpCircle;
  return <Comp size={size} className={className} />;
};

// ── 6 built-in templates (fallback when backend is offline) ──
const BUILTIN_TEMPLATES: AIPAgentTemplate[] = [
  {
    id: 'tpl_chat_agent',
    name: '通用对话Agent',
    icon: 'MessageCircle',
    description: '通用多轮对话助手，支持上下文记忆与多工具调用',
    model: 'gemini-1.5-pro',
    temperature: 0.7,
    maxIterations: 10,
    category: 'chat',
    isInstantiated: false,
  },
  {
    id: 'tpl_workflow_agent',
    name: '工作流编排Agent',
    icon: 'Workflow',
    description: '基于DAG的多步骤工作流自动化编排引擎，支持条件分支与并行执行',
    model: 'gemini-1.5-pro',
    temperature: 0.3,
    maxIterations: 20,
    category: 'workflow',
    isInstantiated: false,
  },
  {
    id: 'tpl_rag_agent',
    name: 'RAG检索增强Agent',
    icon: 'Database',
    description: '结合知识库向量检索与LLM推理，提供精准的上下文感知回答',
    model: 'gemini-1.5-pro',
    temperature: 0.5,
    maxIterations: 8,
    category: 'retrieval',
    isInstantiated: false,
  },
  {
    id: 'tpl_codegen_agent',
    name: '代码生成Agent',
    icon: 'Code2',
    description: '智能代码生成与重构助手，支持多语言语法分析与最佳实践推荐',
    model: 'gemini-1.5-pro',
    temperature: 0.2,
    maxIterations: 15,
    category: 'codegen',
    isInstantiated: false,
  },
  {
    id: 'tpl_vision_agent',
    name: '视觉分析Agent',
    icon: 'Eye',
    description: '多模态图像/视频理解引擎，支持目标检测、OCR与场景分析',
    model: 'gemini-1.5-pro',
    temperature: 0.4,
    maxIterations: 5,
    category: 'vision',
    isInstantiated: false,
  },
  {
    id: 'tpl_data_agent',
    name: '数据分析Agent',
    icon: 'BarChart3',
    description: '智能数据分析与可视化引擎，支持SQL生成、统计分析与报表输出',
    model: 'gemini-1.5-pro',
    temperature: 0.6,
    maxIterations: 12,
    category: 'data',
    isInstantiated: false,
  },
];

const CATEGORY_LABELS: Record<AIPAgentTemplate['category'], { zh: string; en: string }> = {
  chat: { zh: '对话', en: 'Chat' },
  workflow: { zh: '工作流', en: 'Workflow' },
  retrieval: { zh: '检索', en: 'Retrieval' },
  codegen: { zh: '代码', en: 'Code Gen' },
  vision: { zh: '视觉', en: 'Vision' },
  data: { zh: '数据', en: 'Data' },
};

interface AgentMarketProps {
  onAgentInstantiated?: (agent: AIPAgent) => void;
  showToast?: (type: 'success' | 'info' | 'error', msg: string) => void;
}

export default function AgentMarket({ onAgentInstantiated, showToast }: AgentMarketProps) {
  const { styles } = useTheme();
  const [templates, setTemplates] = useState<AIPAgentTemplate[]>(BUILTIN_TEMPLATES);
  const [search, setSearch] = useState('');
  const [categoryFilter, setCategoryFilter] = useState<string>('all');
  const [showModal, setShowModal] = useState(false);
  const [selectedTemplate, setSelectedTemplate] = useState<AIPAgentTemplate | null>(null);
  const [instanceName, setInstanceName] = useState('');
  const [instantiating, setInstantiating] = useState(false);
  const [instantiatedIds, setInstantiatedIds] = useState<Set<string>>(new Set());

  useEffect(() => {
    fetchAgentTemplates().then(data => {
      if (data.length > 0) {
        // Merge backend data with built-in defaults
        const merged = BUILTIN_TEMPLATES.map(bt => {
          const backend = data.find(d => d.id === bt.id);
          return backend ? { ...bt, ...backend, isInstantiated: instantiatedIds.has(bt.id) } : bt;
        });
        setTemplates(merged);
      }
    }).catch(() => {});
  }, []);

  const filtered = useMemo(() => {
    return templates.filter(t => {
      const matchSearch = !search ||
        t.name.toLowerCase().includes(search.toLowerCase()) ||
        t.description.toLowerCase().includes(search.toLowerCase());
      const matchCategory = categoryFilter === 'all' || t.category === categoryFilter;
      return matchSearch && matchCategory;
    });
  }, [templates, search, categoryFilter]);

  const handleInstantiateClick = (tpl: AIPAgentTemplate) => {
    setSelectedTemplate(tpl);
    setInstanceName(tpl.name);
    setShowModal(true);
  };

  const handleInstantiate = async () => {
    if (!selectedTemplate || !instanceName.trim() || instantiating) return;
    setInstantiating(true);
    try {
      await instantiateAgent(selectedTemplate.id, instanceName.trim());
      setInstantiatedIds(prev => new Set(prev).add(selectedTemplate.id));
      setTemplates(prev => prev.map(t =>
        t.id === selectedTemplate.id ? { ...t, isInstantiated: true } : t
      ));
      showToast?.('success', `Agent「${instanceName.trim()}」实例化成功！`);
      setShowModal(false);
      setInstanceName('');
      setSelectedTemplate(null);
    } catch (e: any) {
      // Demo fallback: mark as instantiated locally
      setInstantiatedIds(prev => new Set(prev).add(selectedTemplate.id));
      setTemplates(prev => prev.map(t =>
        t.id === selectedTemplate.id ? { ...t, isInstantiated: true } : t
      ));
      showToast?.('success', `Agent「${instanceName.trim()}」已创建（本地模式）`);
      setShowModal(false);
      setInstanceName('');
      setSelectedTemplate(null);
    } finally {
      setInstantiating(false);
    }
  };

  return (
    <div className="flex flex-col h-full overflow-hidden">
      {/* Filter Bar */}
      <div className={`p-4 border-b ${styles.cardBorder} ${styles.inputBg} space-y-3`}>
        <div className="flex items-center gap-3">
          <div className="flex-1 relative">
            <Icon name="Search" size={13} className={`absolute left-2.5 top-1/2 -translate-y-1/2 ${styles.cardTextMuted}`} />
            <input
              type="text"
              value={search}
              onChange={e => setSearch(e.target.value)}
              placeholder="搜索Agent模板名称或描述..."
              className={`w-full pl-8 pr-3 py-1.5 border ${styles.inputBorder} rounded-lg text-xs ${styles.cardBg} ${styles.cardText} focus:outline-none focus:ring-1 focus:ring-blue-400/50`}
            />
          </div>
          <select
            value={categoryFilter}
            onChange={e => setCategoryFilter(e.target.value)}
            className={`px-3 py-1.5 border ${styles.inputBorder} rounded-lg text-xs ${styles.cardBg} ${styles.cardText}`}
          >
            <option value="all">全部类型</option>
            {Object.entries(CATEGORY_LABELS).map(([key, label]) => (
              <option key={key} value={key}>{label.zh} / {label.en}</option>
            ))}
          </select>
        </div>
        <div className={`text-[10px] ${styles.cardTextMuted}`}>
          共 {filtered.length} 个模板
        </div>
      </div>

      {/* Template Cards Grid */}
      <div className="flex-1 overflow-y-auto p-4">
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {filtered.map(tpl => {
            const isInstantiated = instantiatedIds.has(tpl.id) || tpl.isInstantiated;
            return (
              <div
                key={tpl.id}
                className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 flex flex-col gap-3 hover:shadow-md transition-shadow`}
              >
                {/* Header */}
                <div className="flex items-start gap-3">
                  <span className={`p-2.5 rounded-xl ${styles.badgeBg} ${styles.accentText} shrink-0`}>
                    <Icon name={tpl.icon} size={20} />
                  </span>
                  <div className="flex-1 min-w-0">
                    <h3 className={`text-sm font-bold ${styles.cardText} truncate`}>{tpl.name}</h3>
                    <span className={`text-[10px] px-1.5 py-0.5 rounded ${styles.badgeBg} ${styles.accentText} font-medium`}>
                      {CATEGORY_LABELS[tpl.category]?.zh || tpl.category}
                    </span>
                  </div>
                </div>

                {/* Description */}
                <p className={`text-[11px] ${styles.cardTextMuted} leading-relaxed line-clamp-2`}>
                  {tpl.description}
                </p>

                {/* Meta */}
                <div className={`flex items-center gap-3 text-[10px] ${styles.cardTextMuted} font-mono`}>
                  <span className="flex items-center gap-1">
                    <Icon name="Cpu" size={10} />
                    {tpl.model.replace('-1.5-pro', '')}
                  </span>
                  <span className="flex items-center gap-1">
                    <Icon name="Thermometer" size={10} />
                    {tpl.temperature}
                  </span>
                  <span className="flex items-center gap-1">
                    <Icon name="Repeat" size={10} />
                    {tpl.maxIterations}轮
                  </span>
                </div>

                {/* Action */}
                <div className="pt-2 border-t ${styles.cardBorder}">
                  {isInstantiated ? (
                    <span className={`inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-[11px] font-semibold bg-green-500/10 text-green-600 border border-green-200/50`}>
                      <Icon name="CheckCircle2" size={12} />
                      已创建
                    </span>
                  ) : (
                    <button
                      onClick={() => handleInstantiateClick(tpl)}
                      className={`w-full px-3 py-1.5 ${styles.accentBg} hover:opacity-90 text-white rounded-lg text-[11px] font-bold transition-all cursor-pointer flex items-center justify-center gap-1.5`}
                    >
                      <Icon name="Rocket" size={12} />
                      实例化部署
                    </button>
                  )}
                </div>
              </div>
            );
          })}
          {filtered.length === 0 && (
            <div className="col-span-full flex flex-col items-center justify-center py-16 gap-3">
              <Icon name="PackageOpen" size={32} className={styles.cardTextMuted} />
              <p className={`text-xs ${styles.cardTextMuted}`}>没有匹配的Agent模板</p>
            </div>
          )}
        </div>
      </div>

      {/* Instantiate Modal */}
      {showModal && selectedTemplate && (
        <div className={`fixed inset-0 z-50 flex items-center justify-center ${styles.appBg}/40 backdrop-blur-xs`}>
          <div className={`${styles.cardBg} rounded-xl shadow-2xl border ${styles.cardBorder} w-full max-w-sm overflow-hidden`}>
            <div className={`px-4 py-3 border-b ${styles.cardBorder} ${styles.inputBg} flex items-center justify-between`}>
              <h3 className={`font-bold ${styles.cardText} text-xs`}>实例化 Agent</h3>
              <button
                onClick={() => { setShowModal(false); setInstanceName(''); }}
                className={`${styles.cardTextMuted} cursor-pointer`}
              >
                <Icon name="X" size={15} />
              </button>
            </div>

            <div className="p-4 space-y-4">
              <div className={`flex items-center gap-3 p-3 rounded-lg border ${styles.cardBorder} ${styles.inputBg}`}>
                <span className={`p-2 rounded-lg ${styles.badgeBg} ${styles.accentText}`}>
                  <Icon name={selectedTemplate.icon} size={16} />
                </span>
                <div>
                  <p className={`text-xs font-bold ${styles.cardText}`}>{selectedTemplate.name}</p>
                  <p className={`text-[10px] ${styles.cardTextMuted}`}>{selectedTemplate.model}</p>
                </div>
              </div>

              <div className="space-y-1">
                <label className={`block text-[11px] ${styles.cardTextMuted} font-semibold`}>
                  Agent名称 <span className="text-red-500">*</span>
                </label>
                <input
                  type="text"
                  value={instanceName}
                  onChange={e => setInstanceName(e.target.value)}
                  placeholder="输入Agent实例名称..."
                  className={`w-full px-3 py-2 border ${styles.inputBorder} rounded-lg text-xs ${styles.cardBg} ${styles.cardText}`}
                  autoFocus
                  onKeyDown={e => e.key === 'Enter' && handleInstantiate()}
                />
              </div>
            </div>

            <div className={`px-4 py-3 border-t ${styles.cardBorder} flex justify-end gap-2`}>
              <button
                onClick={() => { setShowModal(false); setInstanceName(''); }}
                className={`px-3 py-1.5 border ${styles.cardBorder} rounded-lg hover:${styles.inputBg} ${styles.cardTextMuted} transition-colors cursor-pointer text-[11px] font-semibold`}
              >
                取消
              </button>
              <button
                onClick={handleInstantiate}
                disabled={!instanceName.trim() || instantiating}
                className={`px-4 py-1.5 ${styles.accentBg} hover:opacity-90 text-white rounded-lg transition-all font-bold cursor-pointer text-[11px] disabled:opacity-50 disabled:cursor-not-allowed`}
              >
                {instantiating ? '部署中...' : '确认部署'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
