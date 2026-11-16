/**
 * AIP Copilot — automation hook
 * 保留 4 条**有真实后端调用**的安全场景执行流（runAgentAutomation）与消息状态。
 * 显示文案一律取自 `copilot.*` i18n 命名空间。
 *
 * 2026-11-14 验证批裁决（V-COP-022/025/026/027 实测）：原 12 条场景里 8 条
 * （pipeline/ontology/health/lineage + 4 条 ws_*）是 `await sleep()` + 成功 toast 的
 * 纯演示流，无任何后端调用；其中「本体建对象」连内存都没变（App.tsx 未向抽屉
 * 传 setObjectTypes，落到默认空实现）。这 8 条已整体删除。
 *
 * 同时剔除保留下来的 4 条里的**本地 state 伪写入**：抽屉的状态 props 全部是
 * `(() => {})` 空实现（App.tsx 未传），写进去既不落库也不会被任何页面读到 ——
 * 保留会继续产生「toast 说建了组织/改了策略，实际什么都没有」的假成功。
 * 现在这 4 条只保留真实调用：
 *   - sec_gdpr / sec_finance → POST /api/v1/security/policy/evaluate
 *   - sec_row_col → 同 ABAC 通道（按行列策略数据集评估）
 *   - sec_audit → GET /api/v1/security/audit-logs
 *
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import { useCallback, useState } from 'react';
import { useLanguage } from '../LanguageContext';
import { AgentScenarioType, scenarioPrompt } from './AgentScenarioData';
import { evaluateAbacPolicy } from '../../api/securityClient';
import { fetchSecurityAuditLogs } from '../../services/knowledgeQueryApi';

interface Message {
  id: string;
  sender: 'user' | 'agent';
  text: string;
  timestamp: string;
  isExecuting?: boolean;
  executionStep?: string;
  completed?: boolean;
  automationType?: AgentScenarioType;
}

export interface AipAutomationProps {
  showToast: (type: 'success' | 'info' | 'error', message: string) => void;
  /** 场景卡片跳转用的视图切换回调（安全中心）。 */
  onViewModeChange: (mode: 'security') => void;
  onSecurityTabChange: (tab: 'overview' | 'orgs' | 'dac' | 'mac' | 'pbac' | 'row_col' | 'audit') => void;
}

/** 每条场景对应的 ABAC 评估目标（真实接口参数，非展示文案）。 */
const ABAC_TARGETS: Record<'sec_gdpr' | 'sec_row_col' | 'sec_finance', {
  userId: string;
  datasetId: string;
  purposeId: string;
}> = {
  sec_gdpr: { userId: 'analyst_li', datasetId: 'ds_passenger_manifest', purposeId: 'purpose_eu_passenger_audit' },
  sec_row_col: { userId: 'analyst_li', datasetId: 'ds_pilots_biography', purposeId: 'purpose_pilot_ops' },
  sec_finance: { userId: 'analyst_li', datasetId: 'ds_ticket_sales', purposeId: 'purpose_expired_finance' },
};

export function useAipAutomation({ showToast, onViewModeChange, onSecurityTabChange }: AipAutomationProps) {
  const { t } = useLanguage();

  const [messages, setMessages] = useState<Message[]>(() => {
    const os = (typeof window !== 'undefined' && window.localStorage.getItem('ecos_locale')) as 'zh' | 'en' | null;
    return [{
      id: 'welcome',
      sender: 'agent',
      text: os ? t('copilot.chat.welcome') : t('copilot.chat.welcome'),
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    }];
  });
  const [isTyping, setIsTyping] = useState(false);
  const [currentStep, setCurrentStep] = useState('');

  const setAgentResult = useCallback((agentMsgId: string, text: string) => {
    setMessages((prev) => prev.map((m) => m.id === agentMsgId
      ? { ...m, text, isExecuting: false, completed: true }
      : m));
    setCurrentStep('');
  }, []);

  /**
   * 真实安全引擎评估通道：verdict 只来自 POST /api/v1/security/policy/evaluate 的实际返回。
   * 引擎不可用 → 错误提示，前端永不合成放行（与 security-engine DENY 语义对接）。
   *
   * <p>V-COP-026（2026-11-14 验证）：原实现只把裁决弹成 toast，消息体仍是静态 i18n 文案，
   * 于是「智能体回了什么」与「引擎实际判了什么」两不相干——用户读到的是一段固定剧本。
   * 现在把裁决**回传给调用方并写进消息体**；toast 保留为辅助提示。
   *
   * @return 引擎真实裁决（GRANTED/DENIED），不可用时返回 null（调用方据此写「无裁决」文案）
   */
  const runRealSecurityEval = useCallback(async (
    userId: string, datasetId: string, purposeId: string,
  ): Promise<'GRANTED' | 'DENIED' | null> => {
    try {
      const res = await evaluateAbacPolicy({
        subject: { userId, roles: [] },
        resource: { type: 'Dataset', id: datasetId },
        action: 'READ_DATASET',
        context: { purpose: purposeId },
      });
      const verdict: 'GRANTED' | 'DENIED' = res.allowed ? 'GRANTED' : 'DENIED';
      showToast(res.allowed ? 'success' : 'error',
        t('copilot.toast.sim.engineVerdict', { verdict }));
      return verdict;
    } catch (e) {
      console.warn('[AipAutomation] security engine evaluate unavailable — no verdict synthesized (fail-closed):', e);
      showToast('error', t('copilot.toast.sim.engineFail'));
      return null;
    }
  }, [showToast, t]);

  const runAgentAutomation = useCallback(async (type: AgentScenarioType, customPromptText?: string) => {
    const userPrompt = customPromptText || scenarioPrompt(t, type);
    setMessages((prev) => [...prev, {
      id: `user-${Date.now()}`,
      sender: 'user',
      text: userPrompt,
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    }]);

    setIsTyping(true);

    const intentKeyMap: Record<AgentScenarioType, string> = {
      sec_gdpr: 'copilot.intent.sec_gdpr',
      sec_row_col: 'copilot.intent.sec_row_col',
      sec_finance: 'copilot.intent.sec_finance',
      sec_audit: 'copilot.intent.sec_audit',
    };
    const agentMsgId = `agent-exec-${Date.now()}`;
    const scenarioName = t(intentKeyMap[type]);

    setMessages((prev) => [...prev, {
      id: agentMsgId,
      sender: 'agent',
      text: `🤖 **${t('copilot.chat.securityEngine')}**\n\n  ${t('copilot.chat.intentDetect')}: ${scenarioName}\n  ${t('copilot.chat.securityEngineName')}\n\n${t('copilot.chat.fullChain')}`,
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      isExecuting: true,
      executionStep: t('copilot.chat.intentCompiling'),
      automationType: type,
    }]);
    setIsTyping(false);

    const resultKey = `copilot.result.${type}`;

    if (type === 'sec_audit') {
      setCurrentStep(t('copilot.step.security.1'));
      onViewModeChange('security');

      setCurrentStep(t('copilot.step.audit.1'));
      onSecurityTabChange('audit');

      // 告警只呈现真实审计流；引擎不可用 → 错误态，不合成告警。
      // V-COP-026：审计条数必须进消息体（原实现只弹 toast）。
      let auditSummary: string;
      try {
        const { ok, json } = await fetchSecurityAuditLogs();
        if (!ok) {
          showToast('error', t('copilot.toast.sim.engineFail'));
          auditSummary = t('copilot.result.auditUnavailable');
        } else {
          const rows = Array.isArray(json?.data) ? json.data : (Array.isArray(json) ? json : []);
          showToast('info', t('copilot.toast.sec_audit.real', { count: rows.length }));
          auditSummary = t('copilot.result.auditCount', { count: rows.length });
        }
      } catch {
        showToast('error', t('copilot.toast.sim.engineFail'));
        auditSummary = t('copilot.result.auditUnavailable');
      }

      setAgentResult(agentMsgId, `${t(resultKey)}\n\n${auditSummary}`);
      return;
    }

    setCurrentStep(t('copilot.step.security.1'));
    onViewModeChange('security');

    if (type === 'sec_row_col') {
      setCurrentStep(t('copilot.step.row_col.1'));
      onSecurityTabChange('row_col');
    } else {
      setCurrentStep(type === 'sec_gdpr' ? t('copilot.step.sim.1') : t('copilot.step.fin.4'));
      onSecurityTabChange('overview');
    }

    const target = ABAC_TARGETS[type];
    const verdict = await runRealSecurityEval(target.userId, target.datasetId, target.purposeId);

    // V-COP-026：裁决写进消息体。无裁决（引擎不可用）时明确写"本次无裁决"，不复用成功文案。
    const verdictLine = verdict
      ? t('copilot.result.verdictLine', { verdict, datasetId: target.datasetId })
      : t('copilot.result.verdictUnavailable', { datasetId: target.datasetId });

    setAgentResult(agentMsgId, `${t(resultKey)}\n\n${verdictLine}`);
  }, [t, onViewModeChange, onSecurityTabChange, showToast, setAgentResult, runRealSecurityEval]);

  return { messages, isTyping, currentStep, setMessages, runAgentAutomation };
}
