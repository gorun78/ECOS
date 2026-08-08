import React from 'react';
import { Bot, Workflow, Database, ShieldAlert, Cpu, Activity, Info } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';

export default function ClosedLoopTab() {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();

  return (
    <div className="space-y-6 max-w-5xl">
      <div className={`${styles.accentBg} text-white p-5 rounded-2xl flex flex-col justify-between gap-2 shadow-md relative overflow-hidden`}>
        <div className="absolute right-0 bottom-0 opacity-10 transform translate-x-10 translate-y-10 scale-150"><Bot size={240} /></div>
        <div className="space-y-1 z-10">
          <span className="px-2 py-0.5 bg-blue-500 text-white text-[9px] font-black rounded uppercase tracking-wider">
            {t("knowledge.closedlooptab.双轨知识闭环")}
          </span>
          <h1 className="text-base font-black tracking-tight">
            {t("knowledge.closedlooptab.知识闭环设计器_aip_pipeline_configura")}
          </h1>
          <p className="text-xs text-slate-300 font-sans max-w-2xl leading-relaxed">
            {t("knowledge.closedlooptab.轨道a_平台自用_元数据同步_血缘解析_本体对齐")}
          </p>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {[
          { q: t("knowledge.closedlooptab.为什么需要双轨知识闭环"), a: t("knowledge.closedlooptab.传统ai_copilot仅有通用常识_对业务上下文_数据时效") },
          { q: t("knowledge.closedlooptab.知识库如何自动装配"), a: t("knowledge.closedlooptab.联邦多模元数据同步引擎从集成工作台拉取宽表结构与血缘_提取本") },
          { q: t("knowledge.closedlooptab.安全护栏的作用"), a: t("knowledge.closedlooptab.闭环关键在于数据流的向外延展与阻断返回_当agent利用知识") },
          { q: t("knowledge.closedlooptab.agent_sandbox如何完善"), a: t("knowledge.closedlooptab.aip_workbench中建立一键同步与仿真沙箱_开发者可") },
        ].map((item, i) => (
          <div key={i} className="bg-white border border-slate-200 p-4 rounded-xl shadow-xs space-y-2">
            <h3 className="font-black text-slate-800 text-xs flex items-center gap-1.5 text-blue-600">
              {[<Workflow size={13} key="w"/>, <Database size={13} key="d"/>, <ShieldAlert size={13} key="s"/>, <Cpu size={13} key="c"/>][i]}
              <span>Q{i+1}: {item.q}</span>
            </h3>
            <p className="text-[11px] text-slate-500 leading-relaxed font-sans">{item.a}</p>
          </div>
        ))}
      </div>

      <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-xs space-y-4">
        <h3 className="text-xs font-extrabold text-slate-400 uppercase tracking-wider flex items-center gap-1.5">
          <Activity size={12} className="text-blue-500" />
          <span>{t("knowledge.closedlooptab.闭环拓扑流程")}</span>
        </h3>

        <div className="grid grid-cols-1 md:grid-cols-4 gap-3 text-center">
          {[
            { label: 'STEP 1', title: t("knowledge.closedlooptab.元数据同步"), desc: t("knowledge.closedlooptab.doris宽表_血缘_数据监控"), color: 'slate' },
            { label: 'STEP 2', title: t("knowledge.closedlooptab.本体对齐"), desc: t("knowledge.closedlooptab.objecttypes_links_action算子"), color: 'blue' },
            { label: 'STEP 3', title: t("knowledge.closedlooptab.安全护栏"), desc: t("knowledge.closedlooptab.隔离网域_密级锁_redact掩码"), color: 'indigo' },
            { label: 'STEP 4', title: t("knowledge.closedlooptab.rag闭环"), desc: t("knowledge.closedlooptab.统一向量切片_零幻觉生成"), color: 'dark' },
          ].map((step, i) => (
            <div key={i} className={`p-3 rounded-xl space-y-1.5 relative ${
              step.color === 'dark' ? 'bg-slate-900 text-slate-300' :
              step.color === 'blue' ? 'bg-blue-50/50 border border-blue-200' :
              step.color === 'indigo' ? 'bg-indigo-50/50 border border-indigo-200' :
              'bg-slate-50 border border-slate-200'
            }`}>
              <div className={`text-[10px] font-mono font-bold ${
                step.color === 'dark' ? 'text-blue-400' :
                step.color === 'blue' ? 'text-blue-500' :
                step.color === 'indigo' ? 'text-indigo-500' : 'text-slate-400'
              }`}>{step.label}</div>
              <h4 className={`font-bold text-xs ${
                step.color === 'dark' ? 'text-white' : 'text-slate-800'
              }`}>{step.title}</h4>
              <p className={`text-[10px] font-sans ${
                step.color === 'dark' ? 'text-slate-400' : 'text-slate-500'
              }`}>{step.desc}</p>
            </div>
          ))}
        </div>

        <div className="p-3 bg-blue-50 border border-blue-200/50 rounded-lg text-[11px] text-blue-700 leading-relaxed flex items-start gap-2">
          <Info size={14} className="shrink-0 mt-0.5" />
          <span>{t("knowledge.closedlooptab.闭环行动倡议_使用下方配置面板启动闭环管道")}</span>
        </div>
      </div>
    </div>
  );
}
