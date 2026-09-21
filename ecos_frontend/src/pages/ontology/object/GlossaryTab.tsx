/**
 * GlossaryTab — 对象类型详情「术语」Tab
 *
 * 展示当前对象类型（本体实体）已绑定的术语（词条）：
 *   - 名称 + 词条类型徽标 + 状态徽标 + 主术语徽标
 *   - 每条提供「设为主术语」与「解绑」操作
 *   - 顶部「关联术语」按钮 → TermSearchModal 选中词条后批量绑定
 *
 * 数据与写入全部走后端接口（services/glossary）；绑定/解绑/设主术语成功后重拉列表
 * （前端铁律 §4.8-3 写后刷新）。主术语唯一性由后端保证，前端只按 isPrimary 渲染。
 *
 * @license Apache-2.0
 */

import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { BookOpen, Loader2, Plus, RotateCw, Star, Tag, Unlink } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import type { ObjectType } from '../../../types/ontology';
import {
  bindTermToEntity,
  listTermsByEntity,
  type GlossaryTerm,
} from '../../../services/glossary';
import TermSearchModal from '../../../components/ontology-workbench/modals/TermSearchModal';
import {
  termStatusClass,
  termStatusLabelKey,
  termTypeLabelKey,
} from '../../../components/ontology-workbench/panels/glossaryTermDisplay';

interface GlossaryTabProps {
  /** 当前对象类型（其 id 即本体实体主键，如 "ent004"） */
  objectType: ObjectType;
  /** 全局轻提示（操作结果反馈）；未注入时静默不提示 */
  onToast?: (type: 'success' | 'info' | 'error', message: string) => void;
}

export default function GlossaryTab({ objectType, onToast }: GlossaryTabProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  /** 已绑定词条列表 */
  const [terms, setTerms] = useState<GlossaryTerm[]>([]);
  /** 列表加载中 */
  const [loading, setLoading] = useState(true);
  /** 加载错误：null = 无错误；空串 = 后端无可读文案（渲染时用默认文案兜底） */
  const [error, setError] = useState<string | null>(null);
  /** 重试计数：自增即触发重新拉取 */
  const [reloadToken, setReloadToken] = useState(0);
  const [searchModalOpen, setSearchModalOpen] = useState(false);
  /** 正在提交的单条词条 id（禁用该行按钮防重复提交） */
  const [pendingTermId, setPendingTermId] = useState<number | null>(null);
  /** 批量关联提交中（禁用「关联术语」按钮） */
  const [binding, setBinding] = useState(false);

  // 挂载 / 切换对象类型 / 重试时拉取已绑定词条（cancelled 防竞态）
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    listTermsByEntity(objectType.id)
      .then((list) => {
        if (!cancelled) {
          setTerms(list);
        }
      })
      .catch((err: any) => {
        if (!cancelled) {
          setError(err?.message || '');
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [objectType.id, reloadToken]);

  /** 重新拉取已绑定词条（写入成功后调用，满足写后刷新） */
  const refreshTerms = useCallback(async () => {
    setTerms(await listTermsByEntity(objectType.id));
  }, [objectType.id]);

  /** 已绑定词条 id 集合（字符串形态，供搜索弹窗标记「已关联」） */
  const boundTermIds: Set<string> = useMemo(
    () => new Set(terms.map((term) => String(term.id))),
    [terms],
  );

  /** 关联词条：搜索弹窗回传字符串 id，绑定接口要求 number，故需 Number() 归一 */
  const handleBindTerms = useCallback(
    async (termIds: string[]) => {
      setSearchModalOpen(false);
      if (termIds.length === 0) {
        return;
      }
      setBinding(true);
      try {
        for (const termId of termIds) {
          await bindTermToEntity(Number(termId), objectType.id);
        }
        await refreshTerms();
        onToast?.(
          'success',
          t('ow.glossaryBinding.bindSuccess').replace('{count}', String(termIds.length)),
        );
      } catch (err: any) {
        onToast?.('error', err?.message || t('ow.glossaryBinding.saveFailed'));
      } finally {
        setBinding(false);
      }
    },
    [objectType.id, refreshTerms, onToast, t],
  );

  /**
   * 单条词条写入（设为主术语 / 解绑）：成功后重拉列表并提示。
   *
   * @param termId 词条 id
   * @param objectTypeId 目标实体；null = 解绑（后端同时撤下主术语标记）
   * @param primary 是否同时设为主术语（同实体原主术语由后端自动撤下）
   * @param successMsg 成功提示文案
   */
  const mutateTerm = useCallback(
    async (termId: number, objectTypeId: string | null, primary: boolean, successMsg: string) => {
      setPendingTermId(termId);
      try {
        await bindTermToEntity(termId, objectTypeId, primary);
        await refreshTerms();
        onToast?.('success', successMsg);
      } catch (err: any) {
        onToast?.('error', err?.message || t('ow.glossaryBinding.saveFailed'));
      } finally {
        setPendingTermId(null);
      }
    },
    [refreshTerms, onToast, t],
  );

  return (
    <div className="space-y-4 max-w-3xl">
      {/* 标题栏 */}
      <div className="flex items-center justify-between">
        <h3 className={`text-sm font-semibold ${styles.cardText} flex items-center gap-1.5`}>
          <BookOpen size={14} className={styles.muted} />
          {t('ow.glossaryBinding.title')}
          <span className={`text-xs font-normal ${styles.muted}`}>({terms.length})</span>
        </h3>
        <button
          type="button"
          onClick={() => setSearchModalOpen(true)}
          disabled={binding}
          className={`text-xs px-2.5 py-1.5 rounded border font-medium transition-colors
            flex items-center gap-1.5 disabled:opacity-50
            ${styles.badgeBg} ${styles.accentBorder} ${styles.accentText}`}
        >
          <Plus size={13} />
          {t('ow.glossaryBinding.bind')}
        </button>
      </div>

      {/* 加载 / 错误 / 空态 / 列表 */}
      {loading ? (
        <div className={`flex items-center justify-center py-10 ${styles.muted}`}>
          <Loader2 size={16} className="animate-spin mr-2" />
          <span className="text-xs">{t('ow.glossaryBinding.loading')}</span>
        </div>
      ) : error !== null ? (
        <div className={`flex flex-col items-center justify-center py-10 ${styles.muted}`}>
          <p className={`text-xs ${styles.dangerText}`}>
            {error || t('ow.glossaryBinding.loadFailed')}
          </p>
          <button
            type="button"
            onClick={() => setReloadToken((token) => token + 1)}
            className={`mt-2 text-xs flex items-center gap-1 ${styles.accentText}`}
          >
            <RotateCw size={12} />
            {t('ow.glossaryBinding.retry')}
          </button>
        </div>
      ) : terms.length === 0 ? (
        <div
          className={`flex flex-col items-center justify-center py-12 border rounded-lg
            ${styles.cardBorder} ${styles.muted}`}
        >
          <BookOpen size={24} className="mb-2 opacity-30" />
          <p className="text-xs">{t('ow.glossaryBinding.empty')}</p>
          <p className="text-[10px] mt-0.5 opacity-70">{t('ow.glossaryBinding.emptyHint')}</p>
        </div>
      ) : (
        <div className="space-y-1.5">
          {terms.map((term) => {
            const statusKey = termStatusLabelKey(term.status);
            const typeKey = termTypeLabelKey(term.termType);
            const isPending = pendingTermId === term.id;
            return (
              <div
                key={term.id}
                className={`flex items-center gap-2 px-3 py-2 rounded-lg border
                  ${styles.cardBorder} ${styles.cardBg}`}
              >
                <Tag size={13} className={`${styles.muted} shrink-0`} />
                <span className={`text-xs font-medium ${styles.cardText} truncate`}>
                  {term.name}
                </span>

                {/* 词条类型徽标 */}
                <span
                  className={`text-[10px] px-1.5 py-0.5 rounded border shrink-0
                    ${styles.badgeBg} ${styles.badgeText} ${styles.cardBorder}`}
                >
                  {t(typeKey || term.termType)}
                </span>

                {/* 状态徽标 */}
                <span
                  className={`text-[10px] px-1.5 py-0.5 rounded border shrink-0
                    ${termStatusClass(term.status, styles)}`}
                >
                  {t(statusKey || term.status)}
                </span>

                {/* 主术语徽标 */}
                {term.isPrimary && (
                  <span
                    className={`text-[10px] px-1.5 py-0.5 rounded border shrink-0
                      flex items-center gap-1
                      ${styles.badgeBg} ${styles.accentText} ${styles.accentBorder}`}
                  >
                    <Star size={9} />
                    {t('ow.glossaryBinding.primaryBadge')}
                  </span>
                )}

                <div className="ml-auto flex items-center gap-1.5 shrink-0">
                  {!term.isPrimary && (
                    <button
                      type="button"
                      onClick={() =>
                        mutateTerm(
                          term.id,
                          objectType.id,
                          true,
                          t('ow.glossaryBinding.setPrimarySuccess'),
                        )
                      }
                      disabled={isPending}
                      className={`text-[10px] px-2 py-1 rounded border transition-colors
                        flex items-center gap-1 disabled:opacity-50
                        ${styles.cardBorder} ${styles.muted} ${styles.sidebarHoverBg}`}
                    >
                      <Star size={11} />
                      {t('ow.glossaryBinding.setPrimary')}
                    </button>
                  )}
                  <button
                    type="button"
                    onClick={() =>
                      mutateTerm(term.id, null, false, t('ow.glossaryBinding.unbindSuccess'))
                    }
                    disabled={isPending}
                    className={`text-[10px] px-2 py-1 rounded border transition-colors
                      flex items-center gap-1 disabled:opacity-50
                      ${styles.dangerBorder} ${styles.dangerText} ${styles.dangerBg}`}
                  >
                    <Unlink size={11} />
                    {t('ow.glossaryBinding.unbind')}
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* 词条搜索弹窗 */}
      <TermSearchModal
        open={searchModalOpen}
        onClose={() => setSearchModalOpen(false)}
        onSelect={handleBindTerms}
        alreadyBoundIds={boundTermIds}
      />
    </div>
  );
}
