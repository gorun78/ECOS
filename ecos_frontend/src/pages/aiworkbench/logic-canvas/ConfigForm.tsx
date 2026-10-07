/**
 * ConfigForm — LogicView 节点类型专属配置表单。
 * 由 LogicView.tsx 机械抽取（H6-T4），props 签名与实现与原文逐行一致。
 * @license Apache-2.0
 */
import type { Node } from '@xyflow/react';
import { useLanguage } from '../../../components/LanguageContext';
import type {
  LogicNodeData,
  LogicNodeConfig,
  LogicLLMConfig,
  LogicToolConfig,
  LogicOntologyConfig,
  LogicApprovalConfig,
  LogicConditionConfig,
  LogicTriggerConfig,
} from '../../../types/aiworkbench';

// ── Helper: Config Form per node type ──────────────────────

export default function ConfigForm({
  node,
  onUpdate,
  styles,
}: {
  node: Node<LogicNodeData>;
  onUpdate: (config: LogicNodeConfig) => void;
  styles: any;
}) {
  const { t } = useLanguage();
  const { type, config } = node.data;

  const handleChange = (newConfig: LogicNodeConfig) => {
    onUpdate({ ...newConfig });
  };

  const inputClass = `w-full px-2 py-1.5 border ${styles.cardBorder} rounded-lg text-[10px] font-mono ${styles.inputBg}`;
  const labelClass = `${styles.cardTextMuted} font-bold text-[10px] block mb-0.5`;
  const textareaClass = `w-full px-2 py-1.5 border ${styles.cardBorder} rounded-lg text-[10px] font-mono resize-none ${styles.inputBg}`;

  switch (type) {
    case 'llm': {
      const c = config as LogicLLMConfig;
      return (
        <div className="p-3 space-y-3">
          <div>
            <label className={labelClass}>{t('aiworkbench.logic.config.llm.model')}</label>
            <input className={inputClass} value={c.model} onChange={e => handleChange({ ...c, model: e.target.value })} />
          </div>
          <div>
            <label className={labelClass}>{t('aiworkbench.logic.config.llm.temperature')} ({c.temperature})</label>
            <input className={inputClass} type="range" min="0" max="2" step="0.1" value={c.temperature}
              onChange={e => handleChange({ ...c, temperature: parseFloat(e.target.value) })} />
          </div>
          <div>
            <label className={labelClass}>{t('aiworkbench.logic.config.llm.maxTokens')}</label>
            <input className={inputClass} type="number" value={c.maxTokens}
              onChange={e => handleChange({ ...c, maxTokens: parseInt(e.target.value) || 4096 })} />
          </div>
          <div>
            <label className={labelClass}>{t('aiworkbench.logic.config.llm.systemPrompt')}</label>
            <textarea className={textareaClass} rows={4} value={c.systemPrompt}
              onChange={e => handleChange({ ...c, systemPrompt: e.target.value })} />
          </div>
        </div>
      );
    }
    case 'tool': {
      const c = config as LogicToolConfig;
      return (
        <div className="p-3 space-y-3">
          <div>
            <label className={labelClass}>{t('aiworkbench.logic.config.tool.name')}</label>
            <input className={inputClass} value={c.toolName} onChange={e => handleChange({ ...c, toolName: e.target.value })} />
          </div>
          <div>
            <label className={labelClass}>{t('aiworkbench.logic.config.tool.params')}</label>
            <textarea className={textareaClass} rows={6} value={c.parameters}
              onChange={e => handleChange({ ...c, parameters: e.target.value })} />
          </div>
        </div>
      );
    }
    case 'ontology': {
      const c = config as LogicOntologyConfig;
      return (
        <div className="p-3 space-y-3">
          <div>
            <label className={labelClass}>{t('aiworkbench.logic.config.ontology.objectType')}</label>
            <input className={inputClass} value={c.objectType} onChange={e => handleChange({ ...c, objectType: e.target.value })} />
          </div>
          <div>
            <label className={labelClass}>{t('aiworkbench.logic.config.ontology.queryType')}</label>
            <select className={inputClass} value={c.queryType}
              onChange={e => handleChange({ ...c, queryType: e.target.value as LogicOntologyConfig['queryType'] })}>
              <option value="get">get</option>
              <option value="list">list</option>
              <option value="search">search</option>
              <option value="query">query</option>
            </select>
          </div>
          <div>
            <label className={labelClass}>{t('aiworkbench.logic.config.ontology.filter')}</label>
            <input className={inputClass} value={c.filter} onChange={e => handleChange({ ...c, filter: e.target.value })} />
          </div>
        </div>
      );
    }
    case 'approval': {
      const c = config as LogicApprovalConfig;
      return (
        <div className="p-3 space-y-3">
          <div>
            <label className={labelClass}>{t('aiworkbench.logic.config.approval.approver')}</label>
            <input className={inputClass} value={c.approver} onChange={e => handleChange({ ...c, approver: e.target.value })} />
          </div>
          <div>
            <label className={labelClass}>{t('aiworkbench.logic.config.approval.timeout')}</label>
            <input className={inputClass} type="number" value={c.timeout}
              onChange={e => handleChange({ ...c, timeout: parseInt(e.target.value) || 300 })} />
          </div>
        </div>
      );
    }
    case 'condition': {
      const c = config as LogicConditionConfig;
      return (
        <div className="p-3 space-y-3">
          <div>
            <label className={labelClass}>{t('aiworkbench.logic.config.condition.expr')}</label>
            <input className={inputClass} value={c.conditionExpr}
              onChange={e => handleChange({ ...c, conditionExpr: e.target.value })} />
          </div>
          <div>
            <label className={labelClass}>{t('aiworkbench.logic.config.condition.then')}</label>
            <input className={inputClass} value={c.thenBranch}
              onChange={e => handleChange({ ...c, thenBranch: e.target.value })} />
          </div>
          <div>
            <label className={labelClass}>{t('aiworkbench.logic.config.condition.else')}</label>
            <input className={inputClass} value={c.elseBranch}
              onChange={e => handleChange({ ...c, elseBranch: e.target.value })} />
          </div>
        </div>
      );
    }
    case 'trigger': {
      const c = config as LogicTriggerConfig;
      return (
        <div className="p-3 space-y-3">
          <div>
            <label className={labelClass}>{t('aiworkbench.logic.config.trigger.cron')}</label>
            <input className={inputClass} value={c.cronExpr}
              onChange={e => handleChange({ ...c, cronExpr: e.target.value })} />
          </div>
          <div>
            <label className={labelClass}>{t('aiworkbench.logic.config.trigger.timezone')}</label>
            <input className={inputClass} value={c.timezone}
              onChange={e => handleChange({ ...c, timezone: e.target.value })} />
          </div>
        </div>
      );
    }
    default:
      return <div className={`p-3 ${styles.cardTextMuted} text-xs`}>{t('aiworkbench.logic.unknownNode')}</div>;
  }
}
