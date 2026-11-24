/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 *
 * OntologyBindingTab — 「本体与逻辑绑定」标签页（从 ConfigPanel 拆出，H6-T4 体积治理同批）。
 *
 * 无假真理 P0：对象类型与动作算子一律取真实端点，不预置演示清单。
 *   - 对象类型 GET /api/v1/ontology/data/objects（经 facade fetchOntologyDataObjects）
 *   - 动作算子 GET /api/v1/ontology/action-types（经 facade fetchActionTypes）
 *   - 函数注册表无列表端点：仅回显已持久化的函数 id（真值 = metadata.toolWhitelist 的
 *     func_* 项），并显式标注「注册表未接入」，不造可选清单。
 * 加载中/失败/空态分别如实渲染，失败可重试；空态文案说明去本体工作台先建模。
 */
import React, { useCallback, useEffect, useState } from 'react';
import type { AIPAgent } from '../../../types/aiworkbench';
import * as Icons from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { fetchOntologyDataObjects, fetchActionTypes } from '../../../services/ontologyApi';
import DemoBadge from '../DemoBadge';

const Icon = ({ name, size, className }: { name: string; size?: number; className?: string }) => {
  const Comp = (Icons as any)[name] || (Icons as any).HelpCircle;
  return <Comp size={size} className={className} />;
};

/** 绑定候选（id 为持久化标识：对象用 code 优先，后端重建 id 时绑定仍可解析）。 */
interface BindingOption {
  id: string;
  label: string;
  desc: string;
}

interface OntologyBindingTabProps {
  activeChatbot: AIPAgent;
  selectedObjects: string[];
  setSelectedObjects: React.Dispatch<React.SetStateAction<string[]>>;
  selectedActions: string[];
  setSelectedActions: React.Dispatch<React.SetStateAction<string[]>>;
  selectedFunctions: string[];
  styles: Record<string, string>;
}

export default function OntologyBindingTab({
  activeChatbot,
  selectedObjects,
  setSelectedObjects,
  selectedActions,
  setSelectedActions,
  selectedFunctions,
  styles,
}: OntologyBindingTabProps) {
  const { t } = useLanguage();

  const [objects, setObjects] = useState<BindingOption[] | null>(null);
  const [objectsError, setObjectsError] = useState<string | null>(null);
  const [actions, setActions] = useState<BindingOption[] | null>(null);
  const [actionsError, setActionsError] = useState<string | null>(null);
  const [reloadTick, setReloadTick] = useState(0);

  const reload = useCallback(() => {
    setObjects(null);
    setObjectsError(null);
    setActions(null);
    setActionsError(null);
    setReloadTick((n) => n + 1);
  }, []);

  useEffect(() => {
    let cancelled = false;
    setObjects(null);
    setObjectsError(null);
    setActions(null);
    setActionsError(null);

    fetchOntologyDataObjects()
      .then((rows: any[]) => {
        if (cancelled) return;
        setObjects((Array.isArray(rows) ? rows : [])
          .filter((r) => r && (r.code || r.id))
          .map((r): BindingOption => ({
            id: String(r.code || r.id),
            label: String(r.name || r.code || r.id),
            desc: String(r.description || r.entityType || ''),
          })));
      })
      .catch((e: unknown) => {
        if (cancelled) return;
        setObjects([]);
        setObjectsError(e instanceof Error && e.message ? e.message : String(e));
      });

    fetchActionTypes()
      .then((rows) => {
        if (cancelled) return;
        setActions((Array.isArray(rows) ? rows : [])
          .filter((r) => r && r.id)
          .map((r): BindingOption => ({
            id: String(r.id),
            label: String(r.displayName || r.id),
            desc: String(r.description || ''),
          })));
      })
      .catch((e: unknown) => {
        if (cancelled) return;
        setActions([]);
        setActionsError(e instanceof Error && e.message ? e.message : String(e));
      });

    return () => { cancelled = true; };
  }, [reloadTick]);

  const toggle = (setter: React.Dispatch<React.SetStateAction<string[]>>, id: string) => {
    setter((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]));
  };

  /** 列表状态提示（加载/失败/空），三态各自如实，不把失败渲染成空。 */
  const renderListState = (
    list: BindingOption[] | null,
    error: string | null,
    emptyText: string,
  ) => {
    if (error !== null) {
      return (
        <div className={`text-[9px] ${styles.dangerText} px-2 py-1.5 rounded border ${styles.cardBorder} ${styles.inputBg} flex items-center gap-1.5`}>
          <Icon name="AlertTriangle" size={10} />
          <span>{t('aiworkbench.chatbot.bindingLoadFailed').replace('{message}', error)}</span>
          <button
            type="button"
            onClick={reload}
            className={`ml-auto text-[9px] font-bold ${styles.accentText} cursor-pointer`}
          >
            {t('aiworkbench.chatbot.bindingRetry')}
          </button>
        </div>
      );
    }
    if (list === null) {
      return (
        <div className={`text-[9px] ${styles.cardTextMuted} px-2 py-1.5 italic flex items-center gap-1.5`}>
          <span className={`w-2.5 h-2.5 border-2 ${styles.cardBorder} border-t-transparent rounded-full animate-spin`} />
          <span>{t('aiworkbench.chatbot.bindingLoading')}</span>
        </div>
      );
    }
    if (list.length === 0) {
      return (
        <div className={`text-[9px] ${styles.cardTextMuted} px-2 py-1.5 italic`}>{emptyText}</div>
      );
    }
    return null;
  };

  // 已绑定但不在候选里（例如对象已删/改 code）→ 仍需可见可解绑，否则成了隐藏状态。
  const knownIds = new Set((objects ?? []).map((o) => o.id));
  const orphanObjectIds = selectedObjects.filter((id) => !knownIds.has(id));

  return (
    <div className="space-y-4">
      <div className={`p-3 ${styles.badgeBg} ${styles.accentText} rounded-xl border ${styles.accentBorder} flex items-start gap-2`}>
        <Icon name="Info" size={14} className="shrink-0 mt-0.5" />
        <p className="leading-relaxed">
          <strong>{t('aiworkbench.chatbot.ontologyProtocol')}</strong>{t('aiworkbench.chatbot.ontologyProtocolDesc')}
        </p>
      </div>

      {/* 引擎工具面：metadata.toolWhitelist 真值展示 */}
      {activeChatbot.toolWhitelist && activeChatbot.toolWhitelist.length > 0 && (
        <div className={`p-3 ${styles.cardBg} border ${styles.cardBorder} rounded-xl space-y-2`}>
          <h4 className={`text-[11px] font-extrabold ${styles.cardTextMuted} uppercase tracking-wider font-mono flex items-center gap-1`}>
            <Icon name="Wrench" size={11} />
            <span>{t('aiworkbench.chatbot.toolFaceTitle')}</span>
            <span className={`px-1.5 py-0.5 rounded text-[9px] font-mono ${styles.badgeBg} ${styles.muted}`}>
              {activeChatbot.toolWhitelist.length}
            </span>
          </h4>
          <div className="flex flex-wrap gap-1.5">
            {activeChatbot.toolWhitelist.slice(0, 18).map(name => (
              <span key={name} className={`px-1.5 py-0.5 rounded text-[9px] font-mono ${styles.badgeBg} ${styles.cardTextMuted}`}>
                {name}
              </span>
            ))}
            {activeChatbot.toolWhitelist.length > 18 && (
              <span className={`px-1.5 py-0.5 rounded text-[9px] font-mono ${styles.badgeBg} ${styles.muted}`}>
                +{activeChatbot.toolWhitelist.length - 18}
              </span>
            )}
          </div>
          <p className={`text-[10px] ${styles.cardTextMuted}`}>
            {t('aiworkbench.chatbot.toolFaceHint')}
          </p>
        </div>
      )}

      {/* 1. Object Types — 真值端点 */}
      <div className="space-y-2">
        <h3 className={`text-[11px] font-extrabold ${styles.cardTextMuted} uppercase tracking-wider font-mono flex items-center gap-1`}>
          <Icon name="Layers" size={11} className={styles.cardTextMuted} />
          <span>{t('aiworkbench.chatbot.objectTypeBindings')}</span>
        </h3>
        {renderListState(objects, objectsError, t('aiworkbench.chatbot.bindingObjectsEmpty'))}
        {objectsError === null && objects !== null && objects.length > 0 && (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
            {objects.map(obj => {
              const isBound = selectedObjects.includes(obj.id);
              return (
                <div
                  key={obj.id}
                  onClick={() => toggle(setSelectedObjects, obj.id)}
                  className={`p-2.5 rounded-lg border cursor-pointer transition-all flex items-start gap-2.5 ${
                    isBound ? `${styles.badgeBg} ${styles.accentBorder}` : `${styles.cardBg} ${styles.cardBorder}`
                  }`}
                >
                  <input type="checkbox" checked={isBound} onChange={() => {}} className="mt-0.5 accent-blue-600 rounded" />
                  <div className="flex flex-col min-w-0">
                    <span className={`font-bold ${styles.cardText} text-[11px] truncate`}>{obj.label}</span>
                    <span className={`text-[9px] ${styles.cardTextMuted} font-mono`}>{obj.id}</span>
                    {obj.desc && (
                      <span className={`text-[9px] ${styles.cardTextMuted} leading-tight mt-0.5 line-clamp-2`}>{obj.desc}</span>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
        {orphanObjectIds.length > 0 && (
          <div className="space-y-1">
            <p className={`text-[9px] ${styles.cardTextMuted}`}>{t('aiworkbench.chatbot.bindingOrphanObjects')}</p>
            <div className="flex flex-wrap gap-1.5">
              {orphanObjectIds.map(id => (
                <button
                  type="button"
                  key={id}
                  onClick={() => toggle(setSelectedObjects, id)}
                  className={`inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[9px] font-mono ${styles.inputBg} ${styles.cardTextMuted} border ${styles.cardBorder} cursor-pointer`}
                  title={t('aiworkbench.chatbot.bindingUnbindHint')}
                >
                  <span>{id}</span>
                  <Icon name="X" size={9} />
                </button>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* 2. Action Types — 真值端点 */}
      <div className="space-y-2">
        <h3 className={`text-[11px] font-extrabold ${styles.cardTextMuted} uppercase tracking-wider font-mono flex items-center gap-1`}>
          <Icon name="Settings" size={11} className={styles.cardTextMuted} />
          <span>{t('aiworkbench.chatbot.actionTypeBindings')}</span>
        </h3>
        {renderListState(actions, actionsError, t('aiworkbench.chatbot.bindingActionsEmpty'))}
        {actionsError === null && actions !== null && actions.length > 0 && (
          <div className="space-y-2">
            {actions.map(act => {
              const isBound = selectedActions.includes(act.id);
              return (
                <div
                  key={act.id}
                  onClick={() => toggle(setSelectedActions, act.id)}
                  className={`p-3 rounded-lg border cursor-pointer transition-all flex items-start gap-3 ${
                    isBound ? `${styles.badgeBg} ${styles.accentBorder}` : `${styles.cardBg} ${styles.cardBorder}`
                  }`}
                >
                  <input type="checkbox" checked={isBound} onChange={() => {}} className="mt-0.5 accent-indigo-600 rounded" />
                  <div className="flex-1 flex flex-col min-w-0">
                    <div className="flex items-center justify-between gap-2">
                      <span className={`font-bold ${styles.cardText} text-[11px] truncate`}>{act.label}</span>
                      <span className={`text-[9px] ${styles.appBg} ${styles.cardTextMuted} font-mono px-1 rounded shrink-0`}>{act.id}</span>
                    </div>
                    {act.desc && (
                      <p className={`text-[9px] ${styles.cardTextMuted} mt-1 leading-normal line-clamp-2`}>{act.desc}</p>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* 3. Functions — 无注册表，仅回显已持久化 id */}
      <div className="space-y-2">
        <h3 className={`text-[11px] font-extrabold ${styles.cardTextMuted} uppercase tracking-wider font-mono flex items-center gap-1`}>
          <Icon name="FunctionSquare" size={11} className={styles.cardTextMuted} />
          <span>{t('aiworkbench.chatbot.ontologyBoundFunctions').replace('{count}', String(selectedFunctions.length))}</span>
          <DemoBadge />
        </h3>
        {selectedFunctions.length > 0 ? (
          <div className="flex flex-wrap gap-1.5">
            {selectedFunctions.map(id => (
              <span key={id} className={`px-1.5 py-0.5 rounded text-[9px] font-mono ${styles.inputBg} ${styles.cardTextMuted} border ${styles.cardBorder}`}>
                {id}
              </span>
            ))}
          </div>
        ) : (
          <div className={`text-[9px] ${styles.cardTextMuted} px-2 py-1.5 italic`}>{t('aiworkbench.chatbot.bindingFunctionsEmpty')}</div>
        )}
        <p className={`text-[9px] ${styles.cardTextMuted} leading-normal`}>
          {t('aiworkbench.chatbot.bindingFunctionsNote')}
        </p>
      </div>
    </div>
  );
}
