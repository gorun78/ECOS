/**
 * GuideTab — ECOS Guide / Top-Level Architecture Blueprint
 * Extracted from DataWorkbenchLayout.tsx
 * @license Apache-2.0
 */

import React from 'react';
import { useLanguage } from '../../components/LanguageContext';
import LucideIcon from './LucideIcon';

interface GuideTabProps {
  showToast: (type: string, message: string) => void;
  setActiveTab: (tab: string) => void;
}

const GuideTab: React.FC<GuideTabProps> = ({ setActiveTab }) => {
  const { t } = useLanguage();

  const tools = [
    { id: 'pipeline-builder', label: t('dw.tab.pipeline_builder'), icon: 'Workflow', color: 'border-blue-500 bg-blue-50/10 text-blue-700' },
    { id: 'code-repositories', label: t('dw.tab.code_repositories'), icon: 'FileCode', color: 'border-indigo-500 bg-indigo-50/10 text-indigo-700' },
    { id: 'code-workbooks', label: t('dw.tab.code_workbooks'), icon: 'BookOpen', color: 'border-violet-500 bg-violet-50/10 text-violet-700' },
    { id: 'contour', label: t('dw.tab.contour'), icon: 'Layers', color: 'border-amber-500 bg-amber-50/10 text-amber-700' },
  ];

  const steps = [
    { step: 1, title: 'Ingest', sub: '物理源注册与数据拉取', icon: 'Database', color: 'from-blue-500 to-indigo-600', desc: '对接关系型 DB、S3、REST API 等多源协议，将原始报文直接落入 DFS Bronze 原生存根层。' },
    { step: 2, title: 'Transform', sub: '算子清洗与数据建模', icon: 'Cpu', color: 'from-indigo-500 to-purple-600', desc: '运用内置转换函数进行空值填充、格式转换与 Colocate Join，形成物理干净的 Silver 数据模型。' },
    { step: 3, title: 'Verify', sub: '分支协同与血缘演练', icon: 'GitBranch', color: 'from-purple-500 to-pink-600', desc: '利用独立 Git 开发分支提交 PR，完成对代码和拓扑的冷演练部署。' },
    { step: 4, title: 'Schedule', sub: '调度生命周期与健康监控', icon: 'Activity', color: 'from-pink-500 to-rose-600', desc: '配置按需触发或 cron 定时调度，注入行数、空值上限等健康规则，建立异常 SLA 自动熔断机制。' },
    { step: 5, title: 'Publish', sub: 'Ontology映射绑定与发布', icon: 'Layers', color: 'from-rose-500 to-amber-600', desc: '将清洗完的 Gold 物理表关联到业务 Ontology，通过主外键自动建立链接以供全栈消费。' },
  ];

  return (
    <div className="flex-1 flex flex-col overflow-y-auto bg-slate-50 p-6 select-none">
      {/* Header Banner */}
      <div className="mb-6 bg-gradient-to-r from-slate-900 via-indigo-950 to-slate-900 text-white rounded-2xl p-6 shadow-md border border-slate-800 relative overflow-hidden">
        <div className="absolute right-0 top-0 translate-x-12 -translate-y-8 w-64 h-64 bg-indigo-500/10 rounded-full blur-3xl pointer-events-none" />
        <div className="relative z-10 flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
          <div>
            <div className="flex items-center gap-2 mb-2">
              <span className="bg-amber-500 text-slate-950 text-[10px] font-bold uppercase px-2 py-0.5 rounded-full">Palantir Docs</span>
              <span className="text-[11px] text-slate-300 font-mono">ECOS / Building Pipelines / Overview</span>
            </div>
            <h2 className="text-xl font-black tracking-tight text-white font-sans">{t('dw.tab.guide')}</h2>
            <p className="text-xs text-slate-300 mt-1.5 leading-relaxed max-w-3xl">
              将 Pipeline Builder、Code Repositories、Code Workbooks、Contour 四大核心工具配属到 Ingest、Transform、Verify、Schedule、Publish 五个标准生命周期环节，打通从原始物理连接到高可用业务对象的全链路。
            </p>
          </div>
          <div className="bg-white/10 backdrop-blur-md rounded-xl p-3 border border-white/10 shrink-0 text-right">
            <span className="text-[9px] text-indigo-200 block font-mono font-bold uppercase tracking-wider">首选数据引擎</span>
            <span className="text-sm font-extrabold text-amber-400 block font-mono">In-Memory / Apache Doris</span>
          </div>
        </div>
      </div>

      {/* Tool Selector */}
      <div className="mb-6">
        <h3 className="text-xs font-bold text-slate-800 uppercase tracking-wider mb-3 flex items-center gap-2">
          <LucideIcon name="Settings" size={14} className="text-blue-600" />
          <span>一、ECOS 核心管道构建四大开发工具</span>
        </h3>
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
          {tools.map(tool => (
            <button key={tool.id} onClick={() => setActiveTab(tool.id)}
              className="text-left p-4 rounded-xl border-2 transition-all flex flex-col gap-2 bg-white border-slate-200/60 hover:border-slate-300 hover:shadow-sm cursor-pointer">
              <div className="flex items-center gap-2">
                <div className={`p-1.5 rounded-md ${tool.color}`}>
                  <LucideIcon name={tool.icon} size={14} />
                </div>
                <span className="text-xs font-extrabold text-slate-800">{tool.label}</span>
              </div>
              <p className="text-[11px] text-slate-500 leading-relaxed font-sans">
                {tool.id === 'pipeline-builder' && '无代码/低代码图形化编排界面，支持高性能计算与内置函数注入。'}
                {tool.id === 'code-repositories' && '代码至上（Code-First）的高级开发仓库，Git 分支协同与多语言编译器。'}
                {tool.id === 'code-workbooks' && '沙箱交互式分析环境，支持 Python/R/SQL 混写与可视化分支图。'}
                {tool.id === 'contour' && '基于分析板的数据过滤与汇总，百亿级数据集亚秒交互式下钻。'}
              </p>
            </button>
          ))}
        </div>
      </div>

      {/* Pipeline Steps */}
      <div>
        <h3 className="text-xs font-bold text-slate-800 uppercase tracking-wider mb-3 flex items-center gap-2">
          <LucideIcon name="Activity" size={14} className="text-blue-600" />
          <span>二、标准数据管道构建五步法</span>
        </h3>
        <div className="space-y-3">
          {steps.map(s => (
            <div key={s.step} className="bg-white border border-slate-200 rounded-xl p-4 flex items-start gap-3">
              <div className={`p-2 rounded-lg inline-flex items-center justify-center text-white bg-gradient-to-br ${s.color} shrink-0`}>
                <LucideIcon name={s.icon} size={14} />
              </div>
              <div className="flex-1">
                <div className="flex items-center gap-2 mb-1">
                  <span className="text-xs font-extrabold text-slate-800">步骤 {s.step}: {s.title}</span>
                  <span className="text-[10px] text-slate-400">({s.sub})</span>
                </div>
                <p className="text-[11px] text-slate-600 leading-relaxed">{s.desc}</p>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

export default GuideTab;
