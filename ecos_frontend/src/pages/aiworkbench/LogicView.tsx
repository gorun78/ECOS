/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState } from 'react';
import { AIPLogicPipeline, AIPLogicBlock, AIPModel } from '../../types/aiworkbench';
import * as Icons from 'lucide-react';
import { useTheme } from '../../components/ThemeContext';

const Icon = ({ name, size, className }: { name: string; size?: number; className?: string }) => {
  const Comp = (Icons as any)[name] || (Icons as any).HelpCircle;
  return <Comp size={size} className={className} />;
};

interface LogicViewProps {
  pipelines: AIPLogicPipeline[];
  models: AIPModel[];
  onUpdatePipelines: (updated: AIPLogicPipeline[]) => void;
  showToast?: (type: 'success' | 'info' | 'error', msg: string) => void;
}

export default function LogicView({
  pipelines,
  models,
  onUpdatePipelines,
  showToast,
}: LogicViewProps) {
  const { styles } = useTheme();
  const [selectedPipelineId, setSelectedPipelineId] = useState<string>(pipelines[0]?.id || '');
  const [isTesting, setIsTesting] = useState(false);
  const [testTrace, setTestTrace] = useState<string[]>([]);
  const [testResult, setTestResult] = useState<any | null>(null);
  
  // Modal states
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [editingPipeline, setEditingPipeline] = useState<AIPLogicPipeline | null>(null);
  const [formName, setFormName] = useState('');
  const [formDesc, setFormDesc] = useState('');
  const [formInputName, setFormInputName] = useState('');
  const [formInputType, setFormInputType] = useState('string');

  const selectedPipeline = pipelines.find(p => p.id === selectedPipelineId);

  const handleStartCreate = () => {
    setEditingPipeline(null);
    setFormName('');
    setFormDesc('');
    setFormInputName('flight_number');
    setFormInputType('string');
    setShowCreateModal(true);
  };

  const handleStartEdit = (p: AIPLogicPipeline) => {
    setEditingPipeline(p);
    setFormName(p.name);
    setFormDesc(p.description);
    setFormInputName(p.inputs[0]?.name || 'flight_number');
    setFormInputType(p.inputs[0]?.type || 'string');
    setShowCreateModal(true);
  };

  const handleDelete = (id: string) => {
    if (!window.confirm('确定要删除这个 AIP 逻辑编排流程吗？这将使依赖它的所有前端卡片和 Ontology 函数失效。')) return;
    const updated = pipelines.filter(p => p.id !== id);
    onUpdatePipelines(updated);
    if (selectedPipelineId === id && updated.length > 0) {
      setSelectedPipelineId(updated[0].id);
    }
    showToast?.('success', '已删除逻辑编排流');
  };

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    if (!formName.trim()) return;

    if (editingPipeline) {
      const updated = pipelines.map(p => {
        if (p.id === editingPipeline.id) {
          return {
            ...p,
            name: formName.trim(),
            description: formDesc.trim(),
            inputs: [{ name: formInputName, type: formInputType }],
            lastUpdated: '2026-07-03 12:00'
          };
        }
        return p;
      });
      onUpdatePipelines(updated);
      showToast?.('success', '逻辑编排修改已保存');
    } else {
      const newId = `pipe-${Date.now().toString().slice(-4)}`;
      const newPipe: AIPLogicPipeline = {
        id: newId,
        name: formName.trim(),
        description: formDesc.trim(),
        status: 'active',
        creator: '系统管理员',
        lastUpdated: '2026-07-03 12:00',
        inputs: [{ name: formInputName, type: formInputType }],
        testInputs: { [formInputName]: 'UA102' },
        blocks: [
          {
            id: 'b-1',
            type: 'input',
            name: '输入变量',
            config: { variableName: formInputName, dataType: formInputType }
          },
          {
            id: 'b-2',
            type: 'query_ontology',
            name: 'Ontology 对象集检索',
            config: { queryTarget: 'ObjectType: Flight', queryFilter: `id === input.${formInputName}` }
          },
          {
            id: 'b-3',
            type: 'llm',
            name: '大语言模型(LLM)评估',
            config: {
              modelId: 'gemini-1.5-pro',
              systemPrompt: '请基于本体输入数据提供合理的调度建议。',
              userPromptTemplate: `相关本体数据: {{b-2.flight_data}}\n请进行推理。`,
              temperature: 0.3
            }
          },
          {
            id: 'b-4',
            type: 'output',
            name: '规则输出契约',
            config: { outputSchema: '{\n  "analysis": "string",\n  "decision": "string"\n}' }
          }
        ]
      };
      onUpdatePipelines([...pipelines, newPipe]);
      setSelectedPipelineId(newId);
      showToast?.('success', '成功创建 AIP 逻辑编排流程');
    }
    setShowCreateModal(false);
  };

  // Run simulated testing pipeline with visual stepping logs
  const handleRunTest = async () => {
    if (!selectedPipeline) return;
    setIsTesting(true);
    setTestTrace([]);
    setTestResult(null);

    const steps = [
      '⚡ [0.0s] 正在初始化逻辑工作区，校验输入契约...',
      `🔍 [0.4s] 正在检索航空核心本体。解析规则: [ObjectType: Flight] 检索 ID "${selectedPipeline.testInputs?.[selectedPipeline.inputs[0]?.name] || 'DL440'}"`,
      `📦 [0.9s] 成功获取 Ontology 对象关联图谱。航班: DL440 (起飞: ATL, 降落: DFW, 状态: 延迟)。执飞飞机: N204DL (维保到期日: 2026-06-01)。机组: 张建国 (资质: Captain)`,
      `🧠 [1.5s] 正在将多路上下文拼装至 ${selectedPipeline.blocks.find(b => b.type === 'llm')?.config.modelId || 'gemini-1.5-pro'} 输入窗口，配置 Temperature: 0.3...`,
      '📡 [2.2s] 建立私有隔离加密连接通道，开始流式获取大模型推理数据...',
      '🛡️ [2.8s] 安全过滤审计检查: 1245 tokens 满足安全阻断评级规范 (Guardrail: Clear)。',
      '🤖 [3.4s] LLM 推理判定输出：判定根本原因是执飞飞机 N204DL 存在起落架液压延迟，AOC 需更新执勤方案。',
      '💾 [3.8s] 正在将输出结果进行契约格式化 (JSON schema 规整校验)...'
    ];

    for (let i = 0; i < steps.length; i++) {
      await new Promise(resolve => setTimeout(resolve, 450));
      setTestTrace(prev => [...prev, steps[i]]);
    }

    setIsTesting(false);
    
    if (selectedPipeline.id === 'pipe-delay-reason') {
      setTestResult({
        delayType: 'MECHANICAL_FAULT (机械故障引发延迟)',
        primaryCause: '执飞飞机 N204DL 超期未执行液压杆微调。起落架温感误报触发警告。',
        recommendedAction: 'act_reschedule_flight (重新调配机组并更换为 N101UA 飞机执飞)',
        notificationTemplate: '「重要通告」DL440 航班因执行计划性设备检测，现延误至 12:45，在此向各位旅客致以最诚挚歉意。',
        stats: {
          latencyMs: 3820,
          tokensUsed: 1825,
          estimatedCost: '$0.012'
        }
      });
    } else {
      setTestResult({
        isCompliant: false,
        violationDetails: '飞行员 P02 在近30天内执勤总小时数达到 91.5 小时（CAAC 法规上限为 90 小时）。',
        suggestedAlternatePilots: ['P01 (张建国 - 执飞资质齐全，剩余执勤空间 12 小时)', 'P03 (David Smith)'],
        stats: {
          latencyMs: 3640,
          tokensUsed: 1450,
          estimatedCost: '$0.021'
        }
      });
    }
  };

  return (
    <div className={`flex h-full overflow-hidden select-none ${styles.appBg} text-xs`}>
      
      {/* 1. Left Pipelines List */}
      <div className={`w-56 ${styles.cardBg} border-r ${styles.cardBorder} flex flex-col h-full shrink-0`}>
        <div className={`p-3 border-b ${styles.cardBorder} flex items-center justify-between ${styles.inputBg}`}>
          <span className={`font-bold ${styles.cardText}`}>逻辑流列表 ({pipelines.length})</span>
          <button
            onClick={handleStartCreate}
            className="p-1 bg-blue-50 hover:bg-blue-100 text-blue-600 border border-blue-200 rounded-md transition-colors cursor-pointer"
            title="新增逻辑流"
          >
            <Icon name="Plus" size={12} />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-1.5 space-y-1">
          {pipelines.map(p => {
            const isSelected = selectedPipelineId === p.id;
            return (
              <div
                key={p.id}
                onClick={() => {
                  setSelectedPipelineId(p.id);
                  setTestResult(null);
                  setTestTrace([]);
                }}
                className={`p-2.5 rounded-lg cursor-pointer transition-all flex flex-col gap-1.5 ${
                  isSelected
                    ? `${styles.accentBg} text-white shadow-xs`
                    : 'text-slate-600 hover:bg-slate-50'
                }`}
              >
                <div className="flex items-center gap-1.5 font-bold">
                  <Icon name="Cpu" size={12} className={isSelected ? 'text-blue-400 animate-pulse' : 'text-slate-500'} />
                  <span className="truncate">{p.name}</span>
                </div>
                <p className={`text-[10px] line-clamp-2 leading-relaxed ${isSelected ? 'text-slate-400' : 'text-slate-400'}`}>
                  {p.description}
                </p>
                <div className={`flex items-center justify-between text-[9px] border-t ${styles.inputBorder}/10 pt-1`}>
                  <span className={`font-mono ${isSelected ? 'text-slate-500' : 'text-slate-400'}`}>{p.lastUpdated.split(' ')[0]}</span>
                  <span className="px-1 bg-emerald-500/10 text-emerald-600 rounded">已就绪</span>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* 2. Main Logic Pipeline Config Area */}
      {selectedPipeline ? (
        <div className="flex-1 flex overflow-hidden">
          <div className={`flex-1 flex flex-col h-full ${styles.inputBg} overflow-y-auto p-5 space-y-5`}>
            
            {/* Pipeline Header */}
            <div className={`flex items-start justify-between ${styles.cardBg} border ${styles.cardBorder} p-4 rounded-xl shadow-xs`}>
              <div className="space-y-1">
                <div className="flex items-center gap-2">
                  <h2 className={`text-sm font-black ${styles.cardText}`}>{selectedPipeline.name}</h2>
                  <span className="px-2 py-0.5 rounded bg-blue-50 text-blue-600 border border-blue-200 text-[10px] font-bold uppercase">{selectedPipeline.id}</span>
                </div>
                <p className={`text-xs ${styles.cardTextMuted} leading-relaxed max-w-xl`}>{selectedPipeline.description}</p>
                <div className={`flex items-center gap-4 text-[10px] ${styles.cardTextMuted} pt-1`}>
                  <span>创建人: <span className={`font-semibold ${styles.cardTextMuted}`}>{selectedPipeline.creator}</span></span>
                  <span>修改时间: <span className={`font-mono font-semibold ${styles.cardTextMuted}`}>{selectedPipeline.lastUpdated}</span></span>
                </div>
              </div>

              <div className="flex items-center gap-2">
                <button
                  onClick={() => handleStartEdit(selectedPipeline)}
                  className={`px-2.5 py-1.5 ${styles.appBg} hover:bg-slate-200 ${styles.cardTextMuted} border ${styles.cardBorder} rounded-lg hover:shadow-xs transition-all cursor-pointer flex items-center gap-1`}
                >
                  <Icon name="Edit" size={11} />
                  <span>修改配置</span>
                </button>
                <button
                  onClick={() => handleDelete(selectedPipeline.id)}
                  className="px-2.5 py-1.5 bg-red-50 hover:bg-red-100 text-red-600 border border-red-200 rounded-lg hover:shadow-xs transition-all cursor-pointer flex items-center gap-1"
                >
                  <Icon name="Trash2" size={11} />
                  <span>删除流程</span>
                </button>
              </div>
            </div>

            {/* Visual Block-based Configuration Node Layout */}
            <div className="space-y-4">
              <h3 className={`text-xs font-extrabold ${styles.cardTextMuted} uppercase tracking-wider`}>业务逻辑计算链 (Blocks Flow)</h3>
              
              <div className="space-y-3 relative before:absolute before:left-6 before:top-2 before:bottom-2 before:w-0.5 before:bg-slate-200/80 before:-z-10">
                {selectedPipeline.blocks.map((block, idx) => {
                  return (
                    <div key={block.id} className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl shadow-xs overflow-hidden hover:shadow-md transition-shadow`}>
                      {/* Block Header */}
                      <div className={`px-4 py-2.5 ${styles.inputBg} border-b ${styles.cardBorder} flex items-center justify-between`}>
                        <div className={`flex items-center gap-2 font-bold ${styles.cardText}`}>
                          <span className={`p-1 rounded text-white ${
                            block.type === 'input' ? 'bg-indigo-500' :
                            block.type === 'query_ontology' ? 'bg-blue-500' :
                            block.type === 'llm' ? 'bg-purple-500' :
                            block.type === 'ontology_action' ? 'bg-amber-500' : 'bg-emerald-500'
                          }`}>
                            <Icon name={
                              block.type === 'input' ? 'LogIn' :
                              block.type === 'query_ontology' ? 'Database' :
                              block.type === 'llm' ? 'Cpu' :
                              block.type === 'ontology_action' ? 'Zap' : 'LogOut'
                            } size={12} />
                          </span>
                          <span>步骤 {idx + 1}: {block.name}</span>
                        </div>
                        <span className={`font-mono text-[9px] ${styles.cardTextMuted} ${styles.appBg} px-1 py-0.5 rounded uppercase`}>{block.type}</span>
                      </div>

                      {/* Block Body */}
                      <div className={`p-4 text-xs space-y-3 ${styles.cardTextMuted} leading-relaxed`}>
                        {block.type === 'input' && (
                          <div className="flex items-center gap-4">
                            <div>
                              <span className={`${styles.cardTextMuted} font-bold uppercase text-[9px]`}>输入变量名:</span>
                              <p className={`font-mono ${styles.cardText} font-bold text-xs`}>{block.config.variableName}</p>
                            </div>
                            <div>
                              <span className={`${styles.cardTextMuted} font-bold uppercase text-[9px]`}>数据契约:</span>
                              <p className={`font-mono ${styles.cardText} font-bold text-xs`}>{block.config.dataType?.toUpperCase()}</p>
                            </div>
                          </div>
                        )}

                        {block.type === 'query_ontology' && (
                          <div className="space-y-1.5">
                            <div className="flex items-center gap-1">
                              <span className={`${styles.cardTextMuted} font-bold uppercase text-[9px]`}>目标对象集:</span>
                              <span className="px-1.5 py-0.5 bg-blue-50 text-blue-600 font-mono font-bold rounded text-[10px]">{block.config.queryTarget}</span>
                            </div>
                            <div className={`flex items-center gap-1.5 font-mono text-[10px] ${styles.inputBg} p-2 border ${styles.cardBorder} rounded-lg`}>
                              <span className="text-purple-600 font-bold">Filter:</span>
                              <span className={`${styles.cardTextMuted}`}>{block.config.queryFilter}</span>
                            </div>
                          </div>
                        )}

                        {block.type === 'llm' && (
                          <div className="space-y-2">
                            <div className={`flex items-center gap-4 border-b ${styles.cardBorder} pb-2`}>
                              <div>
                                <span className={`${styles.cardTextMuted} font-bold uppercase text-[9px]`}>宿主大模型:</span>
                                <p className={`font-bold ${styles.cardText} text-[11px]`}>{block.config.modelId}</p>
                              </div>
                              <div>
                                <span className={`${styles.cardTextMuted} font-bold uppercase text-[9px]`}>温度 (Temp):</span>
                                <p className={`font-mono ${styles.cardText} text-[11px]`}>{block.config.temperature}</p>
                              </div>
                            </div>
                            
                            <div className="space-y-1">
                              <span className={`${styles.cardTextMuted} font-bold uppercase text-[9px]`}>系统调优指令 (System Prompt):</span>
                              <p className={`${styles.inputBg} p-2.5 border ${styles.cardBorder} rounded-lg ${styles.cardTextMuted} text-[11px] leading-relaxed font-sans`}>{block.config.systemPrompt}</p>
                            </div>

                            <div className="space-y-1 pt-1">
                              <span className={`${styles.cardTextMuted} font-bold uppercase text-[9px]`}>动态提示词模板 (Prompt Template):</span>
                              <p className={`${styles.inputBg} p-2.5 border ${styles.cardBorder} rounded-lg ${styles.cardTextMuted} text-[11px] leading-relaxed font-mono whitespace-pre-line`}>{block.config.userPromptTemplate}</p>
                            </div>
                          </div>
                        )}

                        {block.type === 'ontology_action' && (
                          <div className="space-y-2">
                            <div className="flex items-center gap-1.5">
                              <span className={`${styles.cardTextMuted} font-bold uppercase text-[9px]`}>关联触发动作 (Action Type):</span>
                              <span className="px-1.5 py-0.5 bg-amber-50 text-amber-600 border border-amber-200 font-mono font-bold rounded text-[10px]">{block.config.actionTypeId}</span>
                            </div>
                            <div className={`${styles.inputBg} p-2.5 border ${styles.cardBorder} rounded-lg space-y-1 text-[10px] font-mono`}>
                              <span className={`${styles.cardTextMuted} font-bold uppercase text-[9px] block mb-1`}>变量绑定关系 (Parameter Mapping):</span>
                              {Object.entries(block.config.actionMapping || {}).map(([k, v]) => (
                                <div key={k} className="flex items-center gap-2">
                                  <span className={`${styles.cardText} font-bold`}>{k}</span>
                                  <span className={`${styles.cardTextMuted}`}>←</span>
                                  <span className="text-blue-600 font-bold">{v}</span>
                                </div>
                              ))}
                            </div>
                          </div>
                        )}

                        {block.type === 'output' && (
                          <div className="space-y-1">
                            <span className={`${styles.cardTextMuted} font-bold uppercase text-[9px]`}>JSON Schema 结构验证契约:</span>
                            <pre className={`${styles.appBg} ${styles.cardText} p-2.5 rounded-lg text-[10px] font-mono overflow-x-auto leading-relaxed`}>{block.config.outputSchema}</pre>
                          </div>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>

          </div>

          {/* 3. Right Testing & Trace Playground Panel */}
          <div className={`w-80 ${styles.cardBg} border-l ${styles.cardBorder} flex flex-col h-full shrink-0`}>
            <div className={`p-3 border-b ${styles.cardBorder} ${styles.inputBg} flex items-center justify-between`}>
              <span className={`font-bold ${styles.cardText}`}>逻辑流在线调试中心</span>
              <span className="h-2 w-2 rounded-full bg-emerald-500 animate-pulse" />
            </div>

            <div className="flex-1 overflow-y-auto p-4 space-y-4">
              {/* Test Inputs */}
              <div className="space-y-2">
                <h4 className={`font-extrabold ${styles.cardTextMuted} uppercase tracking-wider text-[10px]`}>1. 变量测试输入</h4>
                
                {selectedPipeline.inputs.map(input => (
                  <div key={input.name} className="space-y-1">
                    <label className={`block ${styles.cardTextMuted} font-bold text-[10px] font-mono`}>{input.name} ({input.type})</label>
                    <input
                      type="text"
                      value={selectedPipeline.testInputs?.[input.name] || ''}
                      onChange={e => {
                        const updated = pipelines.map(p => {
                          if (p.id === selectedPipeline.id) {
                            return {
                              ...p,
                              testInputs: {
                                ...p.testInputs,
                                [input.name]: e.target.value
                              }
                            };
                          }
                          return p;
                        });
                        onUpdatePipelines(updated);
                      }}
                      className={`w-full px-2.5 py-1.5 border ${styles.cardBorder} rounded-lg focus:outline-hidden focus:border-blue-500 font-mono text-xs`}
                      placeholder={input.placeholder}
                    />
                  </div>
                ))}
              </div>

              {/* Action trigger button */}
              <button
                onClick={handleRunTest}
                disabled={isTesting}
                className={`w-full py-2 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-lg text-xs transition-colors shadow-xs flex items-center justify-center gap-1.5 cursor-pointer ${
                  isTesting ? 'opacity-70 cursor-not-allowed' : ''
                }`}
              >
                {isTesting ? (
                  <>
                    <span className={`w-3.5 h-3.5 border-2 ${styles.cardBorder} border-t-transparent rounded-full animate-spin`} />
                    <span>调试计算中...</span>
                  </>
                ) : (
                  <>
                    <Icon name="Play" size={13} />
                    <span>一键部署测试运行</span>
                  </>
                )}
              </button>

              {/* Steps/Trace visual list */}
              {testTrace.length > 0 && (
                <div className="space-y-2">
                  <h4 className={`font-extrabold ${styles.cardTextMuted} uppercase tracking-wider text-[10px]`}>2. AIP 决策路径溯源 (Logic Trace)</h4>
                  <div className={`${styles.appBg} rounded-xl p-3 max-h-56 overflow-y-auto space-y-2 text-[10px] font-mono ${styles.cardTextMuted}`}>
                    {testTrace.map((log, idx) => (
                      <p key={idx} className={`leading-relaxed ${idx === testTrace.length - 1 && isTesting ? 'text-blue-400 animate-pulse' : ''}`}>
                        {log}
                      </p>
                    ))}
                  </div>
                </div>
              )}

              {/* Processed predictions result */}
              {testResult && (
                <div className="space-y-2">
                  <h4 className={`font-extrabold ${styles.cardTextMuted} uppercase tracking-wider text-[10px]`}>3. 结构化契约输出</h4>
                  <div className={`${styles.inputBg} border ${styles.cardBorder} rounded-xl p-3 text-xs space-y-2`}>
                    
                    {selectedPipeline.id === 'pipe-delay-reason' ? (
                      <>
                        <div>
                          <span className={`${styles.cardTextMuted} font-bold uppercase text-[8px] block`}>原因分类 (delayType):</span>
                          <span className="px-1.5 py-0.5 bg-red-50 text-red-600 border border-red-200 rounded font-mono font-bold text-[10px] inline-block mt-0.5">{testResult.delayType}</span>
                        </div>
                        <div>
                          <span className={`${styles.cardTextMuted} font-bold uppercase text-[8px] block`}>根因报告 (primaryCause):</span>
                          <p className={`${styles.cardTextMuted} leading-relaxed font-semibold`}>{testResult.primaryCause}</p>
                        </div>
                        <div>
                          <span className={`${styles.cardTextMuted} font-bold uppercase text-[8px] block`}>推荐动作指令 (recommendedAction):</span>
                          <div className="p-1 px-2 bg-amber-50 border border-amber-200 rounded text-[10px] font-mono font-bold text-amber-700 flex items-center gap-1 mt-0.5">
                            <Icon name="Zap" size={10} />
                            <span>{testResult.recommendedAction}</span>
                          </div>
                        </div>
                        <div>
                          <span className={`${styles.cardTextMuted} font-bold uppercase text-[8px] block`}>公共发布通告模板:</span>
                          <p className={`${styles.cardBg} p-2 border ${styles.cardBorder} rounded-lg text-[11px] leading-relaxed ${styles.cardTextMuted} mt-0.5`}>{testResult.notificationTemplate}</p>
                        </div>
                      </>
                    ) : (
                      <>
                        <div>
                          <span className={`${styles.cardTextMuted} font-bold uppercase text-[8px] block`}>合规验证 (isCompliant):</span>
                          <span className={`px-2 py-0.5 rounded font-bold text-[10px] inline-block mt-0.5 ${
                            testResult.isCompliant ? 'bg-emerald-50 text-emerald-600 border border-emerald-200' : 'bg-rose-50 text-rose-600 border border-rose-200'
                          }`}>
                            {testResult.isCompliant ? '合规通过' : '违反民航法规'}
                          </span>
                        </div>
                        <div>
                          <span className={`${styles.cardTextMuted} font-bold uppercase text-[8px] block`}>违规判定详情:</span>
                          <p className={`${styles.cardTextMuted} leading-relaxed font-semibold mt-0.5`}>{testResult.violationDetails}</p>
                        </div>
                        <div>
                          <span className={`${styles.cardTextMuted} font-bold uppercase text-[8px] block`}>备用飞行员候选推荐:</span>
                          <div className="space-y-1 mt-1">
                            {testResult.suggestedAlternatePilots.map((pilot: string, i: number) => (
                              <div key={i} className={`${styles.cardBg} px-2 py-1 border ${styles.cardBorder} rounded text-[10px] font-semibold ${styles.cardTextMuted}`}>
                                {pilot}
                              </div>
                            ))}
                          </div>
                        </div>
                      </>
                    )}

                    {/* Meta stats */}
                    <div className={`border-t ${styles.cardBorder} pt-2 flex items-center justify-between font-mono text-[9px] ${styles.cardTextMuted}`}>
                      <span>耗时: {testResult.stats.latencyMs}ms</span>
                      <span>消耗: {testResult.stats.tokensUsed}T</span>
                      <span>估算费用: {testResult.stats.estimatedCost}</span>
                    </div>

                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      ) : (
        <div className={`flex-1 flex flex-col items-center justify-center ${styles.cardTextMuted}`}>
          <Icon name="Cpu" size={32} className={`${styles.cardTextMuted} animate-bounce mb-2`} />
          <span>请在左侧选择或添加逻辑流进行设计</span>
        </div>
      )}

      {/* Logic Pipeline Modal (Create / Edit) */}
      {showCreateModal && (
        <div className={`fixed inset-0 z-50 flex items-center justify-center ${styles.appBg}/40 backdrop-blur-xs`}>
          <div className={`${styles.cardBg} rounded-xl shadow-2xl border ${styles.cardBorder} w-full max-w-md overflow-hidden`}>
            
            <div className={`px-4 py-3 border-b ${styles.cardBorder} ${styles.inputBg} flex items-center justify-between`}>
              <h3 className={`font-bold ${styles.cardText} text-xs`}>
                {editingPipeline ? '修改逻辑流' : '新增 AIP 逻辑开发流'}
              </h3>
              <button
                type="button"
                onClick={() => setShowCreateModal(false)}
                className={`${styles.cardTextMuted} hover:${styles.cardTextMuted} cursor-pointer`}
              >
                <Icon name="X" size={15} />
              </button>
            </div>

            <form onSubmit={handleSave} className="p-4 space-y-4">
              <div className="space-y-1">
                <label className={`block ${styles.cardTextMuted} font-semibold`}>名称 (Name) <span className="text-red-500">*</span></label>
                <input
                  type="text"
                  value={formName}
                  onChange={e => setFormName(e.target.value)}
                  placeholder="例如: 机组执勤时间合规评估"
                  className={`w-full px-2.5 py-1.5 border ${styles.cardBorder} rounded-lg text-xs`}
                  required
                />
              </div>

              <div className="space-y-1">
                <label className={`block ${styles.cardTextMuted} font-semibold`}>描述 (Description) <span className="text-red-500">*</span></label>
                <textarea
                  value={formDesc}
                  onChange={e => setFormDesc(e.target.value)}
                  placeholder="说明该逻辑决策流对航空业务的判定范围和目的"
                  rows={2}
                  className={`w-full px-2.5 py-1.5 border ${styles.cardBorder} rounded-lg text-xs resize-none`}
                  required
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1">
                  <label className={`block ${styles.cardTextMuted} font-semibold`}>输入参数名</label>
                  <input
                    type="text"
                    value={formInputName}
                    onChange={e => setFormInputName(e.target.value)}
                    placeholder="如: flight_number"
                    className={`w-full px-2.5 py-1.5 border ${styles.cardBorder} rounded-lg text-xs font-mono`}
                  />
                </div>
                <div className="space-y-1">
                  <label className={`block ${styles.cardTextMuted} font-semibold`}>参数类型</label>
                  <select
                    value={formInputType}
                    onChange={e => setFormInputType(e.target.value)}
                    className={`w-full px-2.5 py-1.5 border ${styles.cardBorder} rounded-lg text-xs`}
                  >
                    <option value="string">String (字符串)</option>
                    <option value="integer">Integer (整型)</option>
                    <option value="boolean">Boolean (布尔)</option>
                  </select>
                </div>
              </div>

              <div className={`pt-2 border-t ${styles.cardBorder} flex items-center justify-end gap-2`}>
                <button
                  type="button"
                  onClick={() => setShowCreateModal(false)}
                  className={`px-3 py-1.5 border ${styles.cardBorder} rounded-lg hover:${styles.inputBg} ${styles.cardTextMuted} transition-colors cursor-pointer text-[11px] font-semibold`}
                >
                  取消
                </button>
                <button
                  type="submit"
                  className="px-4 py-1.5 bg-blue-600 hover:bg-blue-700 text-white rounded-lg transition-colors font-bold shadow-sm cursor-pointer text-[11px]"
                >
                  保存
                </button>
              </div>
            </form>

          </div>
        </div>
      )}

    </div>
  );
}
