/**
 * StepPublish — 步骤 5：Ontology 逻辑实体绑定与全栈发布
 *
 * 从 `components/pipeline/InteractiveStepGuide.tsx` 机械抽取（H6-T4 组件行数治理）：
 * usePublishStep 承载发布态与 setTimeout 逻辑，StepPublish 为原 JSX 片段。
 *
 * @license Apache-2.0
 */

import React, { useState } from 'react';
import { CheckCircle2, Layers } from 'lucide-react';
import { useTheme } from '../ThemeContext';

type ToastFn = (type: 'success' | 'error' | 'info', message: string) => void;

export function usePublishStep(
  toast: ToastFn,
  handleCommitToGit: (message: string, filesChanged: string[]) => void,
) {
  // --- STEP 5: ONTOLOGY STATE ---
  const [publishedOntology, setPublishedOntology] = useState<boolean>(false);
  const [isPublishing, setIsPublishing] = useState<boolean>(false);

  const publishOntology = () => {
    setIsPublishing(true);
    setTimeout(() => {
      setIsPublishing(false);
      setPublishedOntology(true);
      toast('success', '🏆 发布成功！业务实体 Flight 和 Link [Flight_to_Pilot] 已同步到全局 Ontology 中！');
      handleCommitToGit('feat(ontology): 全量发布并更新逻辑航空业务实体 Object Type: Flight (Silver-To-Ontology映射)', ['ontology_schema.json']);
    }, 1500);
  };

  return { publishedOntology, isPublishing, publishOntology };
}

interface StepPublishProps {
  publishedOntology: boolean;
  isPublishing: boolean;
  publishOntology: () => void;
}

export default function StepPublish({
  publishedOntology,
  isPublishing,
  publishOntology,
}: StepPublishProps) {
  const { styles } = useTheme();

  return (
    <div className="space-y-4 font-sans">
      <div className={`p-4 ${styles.appBg} border ${styles.cardBorder} rounded-xl space-y-3`}>
        <span className={`text-[10px] font-extrabold ${styles.cardTextMuted} uppercase tracking-wider font-mono block`}>
          Ontology 物理-逻辑属性映射引擎 (Ontology Mapping Core)
        </span>

        <div className="grid grid-cols-12 gap-4 items-center">
          {/* Physical dataset */}
          <div className={`col-span-4 ${styles.cardBg} p-3 rounded-lg border ${styles.cardBorder} text-[11px] space-y-1.5 shadow-2xs`}>
            <span className={`text-[9px] ${styles.appBorder} ${styles.cardTextMuted} font-mono font-bold px-1.5 py-0.2 rounded uppercase`}>PHYSICAL SILVER VIEW</span>
            <div className={`font-mono ${styles.cardText} text-xs truncate`}>ds_flights_clean</div>
            <div className={`text-[9px] ${styles.cardTextMuted} space-y-0.5 font-mono`}>
              <div>• flight_id (PK)</div>
              <div>• carrier (String)</div>
              <div>• delay_minutes (Double)</div>
              <div>• pilot_id (FK)</div>
            </div>
          </div>

          {/* Mapping line */}
          <div className={`col-span-4 flex flex-col items-center justify-center ${styles.cardTextMuted} text-xs`}>
            <span className={`${styles.inputBg} ${styles.cardText} px-2 py-0.5 rounded text-[8px] font-mono mb-1 font-bold animate-pulse`}>SCHEMA SYNC</span>
            <div className={`w-full h-0.5 bg-gradient-to-r from-slate-200 via-indigo-400 to-slate-200 relative`}>
              <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 bg-indigo-500 text-white rounded-full p-0.5 animate-ping">
                <Layers size={8} />
              </div>
            </div>
          </div>

          {/* Ontology logic model */}
          <div className="col-span-4 bg-indigo-950 text-indigo-100 p-3 rounded-lg border border-indigo-800 text-[11px] space-y-1.5 shadow-md">
            <span className="text-[9px] bg-indigo-800 text-indigo-100 font-mono font-bold px-1.5 py-0.2 rounded uppercase">ONTOLOGY LOGIC OBJECT</span>
            <div className="font-extrabold text-white text-xs flex items-center gap-1">
              <Layers size={11} className="text-amber-400 animate-spin" style={{ animationDuration: '4s' }} />
              <span>Flight (航班实体)</span>
            </div>
            <div className="text-[9px] text-indigo-300 space-y-0.5 font-mono">
              <div>• Primary ID ➔ Object UUID</div>
              <div>• Title ➔ title_display</div>
              <div>• Status ➔ delay_status_sla</div>
              <div>• Link ➔ [Flight_to_Pilot]</div>
            </div>
          </div>
        </div>
      </div>

      <div className={`p-4 ${styles.cardBg} border ${styles.cardBorder} rounded-xl flex flex-col md:flex-row justify-between items-center gap-4`}>
        <div className="space-y-1">
          <div className={`text-xs font-bold ${styles.cardText}`}>将物理银/金牌数据集物化同步至业务 Ontology</div>
          <p className={`text-[10px] ${styles.cardTextMuted} leading-relaxed max-w-xl`}>
            发布完成后，业务决策人员可在 Foundry Object Explorer 或 Workshop 中，完全绕过 SQL 代码，直接检索到以面向对象的形式封装的 <code>Flight</code> 航班对象。
          </p>
        </div>

        <div className="flex gap-3 items-center shrink-0">
          <button
            onClick={publishOntology}
            disabled={isPublishing || publishedOntology}
            className={`px-5 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white font-bold text-xs rounded-xl transition-all shadow-xs flex items-center gap-1.5 cursor-pointer disabled:${styles.inputBg} disabled:text-${styles.cardTextMuted}`}
          >
            {isPublishing ? (
              <>
                <span className={`h-3.5 w-3.5 border-2 ${styles.cardBorder} border-t-transparent rounded-full animate-spin`}></span>
                <span>元数据同步中...</span>
              </>
            ) : publishedOntology ? (
              <>
                <CheckCircle2 size={13} className="text-emerald-400 animate-bounce" />
                <span>已全量发布成功</span>
              </>
            ) : (
              <>
                <Layers size={13} />
                <span>编译并一键发布至 Ontology 实体层</span>
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
}
