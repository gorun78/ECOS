/**
 * 场景定义页右上「场景上下文构建」入口（2026-11-23 场景工作台简化）。
 *
 * <p>职责：把当前「场景定义」的真实内容（定义版本 / 目标 / 成功标准 / 数据·本体·知识绑定）
 * 作为权威源清单输入，交给既有 Context Builder 端点
 * （{@code POST /api/v1/cognitive/context/build}，经 {@link ContextBuildPanel} 单一通道），
 * 构建场景运行时上下文包。本组件不合成任何就绪态/digest，结果与错误族全部由面板按
 * 服务端返回原样呈现。</p>
 *
 * <p>预填口径（只取真实绑定，禁臆造）：
 * datasets→DATA_ASSET、objectTypes→ONTOLOGY、knowledgeBases→KNOWLEDGE。其余绑定类型
 * （规则集/Agent/安全策略/接口）不是 Context Builder 支持的来源类型（后端无 reader，
 * 必需源会 fail-closed 503），不预填、不伪装成可用来源。</p>
 */
import React, { useMemo, useState } from 'react';
import { Hammer } from 'lucide-react';
import Modal from '../../../../components/common/Modal';
import { useLanguage } from '../../../../components/LanguageContext';
import { useTheme } from '../../../../components/ThemeContext';
import type { ContextSourceRefInput } from '../../../../services/cognitiveClient';
import type { ScenarioBlueprintVO } from '../../../../services/scenarioBlueprintClient';
import type { DefinitionVO, DefinitionVersionVO } from '../../../../services/scenarioDefinitionClient';
import ContextBuildPanel from '../blueprint/ContextBuildPanel';

interface Props {
  scenarioId: string;
  definition: DefinitionVO | null;
}

/** 定义版本 VO（后端 ScenarioBlueprintVO 序列化）→ 蓝图视图模型：只挑真实字段，不补造。 */
function toBlueprintVM(v: DefinitionVersionVO): ScenarioBlueprintVO {
  const scope = v.scope;
  const transitions = v.allowedTransitions;
  return {
    id: v.id,
    scenarioId: v.scenarioId,
    version: v.version,
    status: v.status,
    goalStatement: v.goalStatement,
    successCriteria: Array.isArray(v.successCriteria) ? v.successCriteria : undefined,
    scope: scope && typeof scope === 'object' ? (scope as Record<string, unknown>) : undefined,
    allowedTransitions: Array.isArray(transitions) ? transitions : undefined,
  };
}

/** 选蓝本：优先未发布草稿（可迭代），否则取最高版本（发布谱线）。 */
function pickBlueprint(definition: DefinitionVO | null): ScenarioBlueprintVO | null {
  const versions = (definition?.versions ?? []).filter((v) => v && typeof v.version === 'number');
  if (versions.length === 0) return null;
  const drafts = versions.filter((v) => String(v.status).toUpperCase() === 'DRAFT');
  const pool = drafts.length > 0 ? drafts : versions;
  const best = pool.reduce((a, b) => (b.version > a.version ? b : a));
  return toBlueprintVM(best);
}

/** 绑定 → 上下文来源（仅 Context Builder 已有 reader 的三类）。 */
function assetsFromBindings(definition: DefinitionVO | null): ContextSourceRefInput[] {
  const bindings = (definition?.bindings ?? {}) as Record<string, string[]>;
  const pairs: Array<{ key: string; sourceType: string }> = [
    { key: 'datasets', sourceType: 'DATA_ASSET' },
    { key: 'objectTypes', sourceType: 'ONTOLOGY' },
    { key: 'knowledgeBases', sourceType: 'KNOWLEDGE' },
  ];
  const out: ContextSourceRefInput[] = [];
  const seen = new Set<string>();
  for (const p of pairs) {
    for (const id of bindings[p.key] ?? []) {
      const sourceId = String(id ?? '').trim();
      if (!sourceId) continue;
      const dedupe = `${p.sourceType}:${sourceId}`;
      if (seen.has(dedupe)) continue;
      seen.add(dedupe);
      out.push({ sourceType: p.sourceType, sourceId, required: true });
    }
  }
  return out;
}

export default function ScenarioContextBuilder({ scenarioId, definition }: Props) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [open, setOpen] = useState(false);

  const blueprint = useMemo(() => pickBlueprint(definition), [definition]);
  const initialAssets = useMemo(() => assetsFromBindings(definition), [definition]);

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className={`inline-flex items-center gap-1 px-3 py-1.5 rounded border text-xs font-bold cursor-pointer ${styles.accentBg} ${styles.accentSolidText} ${styles.accentBorder}`}
      >
        <Hammer size={12} />
        {t('workspace.ctxb.button')}
      </button>

      <Modal
        isOpen={open}
        onClose={() => setOpen(false)}
        title={t('workspace.ctxb.title')}
        size="full"
        cancelText={t('workspace.ctxb.close')}
        onCancel={() => setOpen(false)}
        closeOnOverlay={false}
      >
        <div className="space-y-2">
          <p className={`text-[11px] ${styles.cardTextMuted}`}>{t('workspace.ctxb.hint')}</p>
          {initialAssets.length > 0 && (
            <p className={`text-[11px] ${styles.cardTextMuted}`}>
              {t('workspace.ctxb.from_bindings', { n: initialAssets.length })}
            </p>
          )}
          {blueprint ? (
            <ContextBuildPanel scenarioId={scenarioId} blueprint={blueprint} initialAssets={initialAssets} />
          ) : (
            <div className={`text-xs ${styles.cardTextMuted} border border-dashed ${styles.cardBorder} rounded p-3`}>
              {t('workspace.ctxb.no_version')}
            </div>
          )}
        </div>
      </Modal>
    </>
  );
}
