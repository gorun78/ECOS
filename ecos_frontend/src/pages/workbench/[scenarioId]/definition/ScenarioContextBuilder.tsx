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
import React, { useCallback, useMemo, useState } from 'react';
import { Hammer } from 'lucide-react';
import Modal from '../../../../components/common/Modal';
import { useLanguage } from '../../../../components/LanguageContext';
import { useTheme } from '../../../../components/ThemeContext';
import type { ContextSourceRefInput } from '../../../../services/cognitiveClient';
import { resolveSourceVersion } from '../../../../services/scenarioSourceVersion';
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
    // 场景定义侧绑定的是「知识库」而非单篇文章 ⇒ 用 KNOWLEDGE_BASE（按库聚合读版本）。
    // 早期误映射成 KNOWLEDGE 时，会把库 id 当文章 id 去读，后端恒 404。
    { key: 'knowledgeBases', sourceType: 'KNOWLEDGE_BASE' },
    // V367 批次 E：单篇文章走 KNOWLEDGE reader，认知资产走 COGNITIVE reader
    { key: 'knowledgeArticles', sourceType: 'KNOWLEDGE' },
    { key: 'cognitiveAssets', sourceType: 'COGNITIVE' },
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

/**
 * 取来源的真实版本号（后端 Context Builder 的 `requiredAssets[].version` 强制非空，
 * 且明确禁止用请求声明的值回填 ⇒ 前端必须从源侧读回真实版本，否则 400）。
 *
 * <p>版本读取单源在 `services/scenarioSourceVersion.ts`（URL 站点集中一处，
 * 避免页面层再开第二生产方）。任一腿不可读（404/无版本字段）返回 null，
 * 由面板如实标注「该来源当前不可读」，禁静默留空让用户误以为是自己没填。</p>
 */

export default function ScenarioContextBuilder({ scenarioId, definition }: Props) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [open, setOpen] = useState(false);

  const blueprint = useMemo(() => pickBlueprint(definition), [definition]);
  const boundAssets = useMemo(() => assetsFromBindings(definition), [definition]);

  /**
   * 打开面板时补齐版本：并发读各来源真实版本，读不到的标出（禁留空让用户猜）。
   * 只在打开时解析一次——绑定变化后重新打开会重新解析。
   */
  const [initialAssets, setInitialAssets] = useState<ContextSourceRefInput[]>([]);
  const [unresolved, setUnresolved] = useState<Array<{ sourceType: string; sourceId: string }>>([]);
  const [resolving, setResolving] = useState(false);

  const openPanel = useCallback(() => {
    setOpen(true);
    setResolving(true);
    setUnresolved([]);
    setInitialAssets(boundAssets.map((a) => ({ ...a })));
    Promise.all(boundAssets.map(async (a) => {
      const version = await resolveSourceVersion(a.sourceType, a.sourceId);
      return { ...a, version: version ?? undefined, _ok: version !== null };
    })).then((rows) => {
      setInitialAssets(rows.map(({ _ok, ...rest }) => rest));
      setUnresolved(rows.filter((r) => !r._ok).map((r) => ({ sourceType: r.sourceType, sourceId: r.sourceId })));
    }).finally(() => setResolving(false));
  }, [boundAssets]);

  return (
    <>
      <button
        type="button"
        onClick={openPanel}
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
          {boundAssets.length > 0 ? (
            <p className={`text-[11px] ${styles.cardTextMuted}`}>
              {resolving
                ? t('workspace.ctxb.resolving', { n: boundAssets.length })
                : t('workspace.ctxb.from_bindings', { n: boundAssets.length })}
            </p>
          ) : (
            <p className={`text-[11px] ${styles.cardTextMuted}`}>{t('workspace.ctxb.no_bindings')}</p>
          )}
          {unresolved.length > 0 && (
            <div className={`text-[11px] rounded border px-2 py-1.5 ${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`}>
              <div className="font-bold">{t('workspace.ctxb.unreadable_title', { n: unresolved.length })}</div>
              <ul className="mt-1 space-y-0.5">
                {unresolved.map((u) => (
                  <li key={`${u.sourceType}:${u.sourceId}`} className="font-mono break-all">
                    {u.sourceType} · {u.sourceId}
                  </li>
                ))}
              </ul>
              <div className="mt-1">{t('workspace.ctxb.unreadable_hint')}</div>
            </div>
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
