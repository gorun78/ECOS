/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 *
 * PMO: ChatbotStudio Router Page — 壳组件，从场景工作台独立路由 /chatbot_studio 挂载。
 * 职责：加载 agents / models / guardrails 三类数据后渲染 ChatbotStudioView，
 * 提供 onUpdateAgents / onAddAuditLog 回写信道与 showToast。
 * 不持有 ChatbotStudioView 内部状态；纯 props 代理层，避免与 AI 工作台共享实例。
 */
import { useEffect, useState } from 'react';
import ChatbotStudioView from './ChatbotStudioView';
import { AIPAgent, AIPModel, AIPGuardrail, AIPAuditLog } from '../../types/aiworkbench';
import {
  fetchAIPAgentsFromMesh,
  fetchAgentModels,
  fetchGuardrailPolicies,
} from './api';
import { useTheme } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';
import { useToast } from '../../components/common/Toast';

export default function ChatbotStudioRouterPage() {
  const { styles } = useTheme();
  const { t } = useLanguage();
  // §4.5 P1：原 showToast→console.info 零可见反馈 → 接全局 ToastProvider
  const { showToast } = useToast();

  const [agents, setAgents] = useState<AIPAgent[]>([]);
  const [models, setModels] = useState<AIPModel[]>([]);
  const [guardrails, setGuardrails] = useState<AIPGuardrail[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);
  // 独立路由页无工作台总览的审计流视图：会话内累积（标注 localOrigin，不假装已持久化）。
  // 真实服务端审计流见 AI 工作台总览（GET /api/v1/aip/audit-logs）。
  const [sessionAudit, setSessionAudit] = useState<AIPAuditLog[]>([]);

  const loadData = () => {
    setLoadError(null);
    // 每个 fetch 内部 catch 归一化为 LoadResult 结构，避免联合类型访问失败
    Promise.all([
      fetchAIPAgentsFromMesh()
        .then((data) => ({ __error: false as const, data }))
        .catch((e) => ({ __error: true as const, data: [] as AIPAgent[], message: e?.message || String(e) })),
      fetchAgentModels()
        .then((data) => ({ __error: false as const, data }))
        .catch((e) => ({ __error: true as const, data: [] as AIPModel[], message: e?.message || String(e) })),
      fetchGuardrailPolicies()
        .then((data) => ({ __error: false as const, data }))
        .catch((e) => ({ __error: true as const, data: [] as AIPGuardrail[], message: e?.message || String(e) })),
    ]).then(([a, m, g]) => {
      const errs: string[] = [];
      if (a.__error) errs.push(a.message);
      if (m.__error) errs.push(m.message);
      if (g.__error) errs.push(g.message);
      if (errs.length > 0) setLoadError(errs.join(' | '));
      setAgents(a.data);
      setModels(m.data);
      setGuardrails(g.data);
    });
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleAddAuditLog = (log: AIPAuditLog) => {
    // §4.5 P2：console.info 泄漏已移除；本地累积并显式标注 localOrigin
    setSessionAudit(prev => [...prev, { ...log, localOrigin: true }]);
  };

  if (loadError) {
    return (
      <div className={`h-full w-full flex flex-col items-center justify-center gap-3 text-sm ${styles.appBg} ${styles.appText} px-6`}>
        <p className="text-center font-semibold">{t('network.error.title')}</p>
        <p className={`opacity-70 text-xs text-center max-w-md`}>{loadError}</p>
        <button
          type="button"
          onClick={loadData}
          className={`rounded-md px-3 py-1.5 text-xs font-semibold border ${styles.cardBorder} bg-transparent shadow-xs hover:opacity-80 cursor-pointer`}
        >
          {t('network.retry')}
        </button>
      </div>
    );
  }

  return (
    <div className={`h-full w-full ${styles.appBg} ${styles.appText}`}>
      {/* §4.5：sessionAudit 计数可见（本地会话审计 ≠ 服务端审计流，如实标注） */}
      {sessionAudit.length > 0 && (
        <div className={`px-3 py-1 text-[9px] ${styles.cardTextMuted} border-b ${styles.cardBorder}`}>
          {t('aiworkbench.chatbot.sessionAuditLocalNote').replace('{count}', String(sessionAudit.length))}
        </div>
      )}
      <ChatbotStudioView
        agents={agents}
        models={models}
        guardrails={guardrails}
        onUpdateAgents={setAgents}
        onAddAuditLog={handleAddAuditLog}
        showToast={showToast}
      />
    </div>
  );
}
