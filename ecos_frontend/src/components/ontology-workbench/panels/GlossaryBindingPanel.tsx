/**
 * GlossaryBindingPanel — 工作台右栏「术语关联」标签页内容
 *
 * 展示当前选中实体已绑定的术语（词条）:
 *   - TermChip: 术语名称 + 状态徽标 + 解绑按钮
 *   - [+ 关联术语] 按钮 → 打开 TermSearchModal
 *
 * 数据与写入全部走后端接口（services/glossary）:
 *   listTermsByEntity / bindTermToEntity
 * 绑定 / 解绑成功后重拉列表（前端铁律 §4.8-3 写后刷新）。
 *
 * @license Apache-2.0
 */

import React, { useCallback, useEffect, useMemo, useState } from "react";
import { BookOpen, X, Plus, Loader2, RotateCw, Tag } from "lucide-react";
import { useTheme } from "../../ThemeContext";
import { useLanguage } from "../../LanguageContext";
import {
  bindTermToEntity,
  listTermsByEntity,
  type GlossaryTerm,
} from "../../../services/glossary";
import { termStatusClass, termStatusLabelKey } from "./glossaryTermDisplay";
import TermSearchModal from "../modals/TermSearchModal";

// ── 术语标签组件 (TermChip) ─────────────────────────────────────

interface TermChipProps {
  term: GlossaryTerm;
  /** 该术语的解绑请求进行中（禁用按钮防重复提交） */
  pending: boolean;
  onUnbind: () => void;
}

function TermChip({ term, pending, onUnbind }: TermChipProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const statusKey = termStatusLabelKey(term.status);

  return (
    <div
      className={`group flex items-center gap-2 px-2.5 py-1.5 rounded-lg border transition
        ${styles.cardBorder} ${styles.cardBg}`}
    >
      {/* 术语名称 */}
      <Tag size={11} className={`${styles.accentText} shrink-0`} />
      <span className={`text-xs ${styles.sidebarText} truncate flex-1 min-w-0`}>
        {term.name}
      </span>

      {/* 状态标签 */}
      <span
        className={`text-[9px] px-1.5 py-0.5 rounded border shrink-0
          ${termStatusClass(term.status, styles)}`}
      >
        {t(statusKey || term.status)}
      </span>

      {/* 解绑按钮 */}
      <button
        type="button"
        onClick={onUnbind}
        disabled={pending}
        className={`p-0.5 rounded opacity-0 group-hover:opacity-100 transition-opacity
          shrink-0 disabled:opacity-50
          ${styles.dangerText} ${styles.sidebarHoverBg}`}
        title={t("ow.glossaryBinding.unbind")}
      >
        <X size={10} />
      </button>
    </div>
  );
}

// ════════════════════════════════════════════════════════════════
// 主组件
// ════════════════════════════════════════════════════════════════

interface GlossaryBindingPanelProps {
  entityId: string;
}

export default function GlossaryBindingPanel({
  entityId,
}: GlossaryBindingPanelProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  /** 该实体已绑定的术语列表 */
  const [terms, setTerms] = useState<GlossaryTerm[]>([]);
  /** 列表加载中 */
  const [loading, setLoading] = useState(true);
  /** 加载错误：null = 无错误；空串 = 后端无可读文案（渲染时用默认文案兜底） */
  const [error, setError] = useState<string | null>(null);
  /** 写入（关联 / 解绑）失败提示 */
  const [opError, setOpError] = useState("");
  /** 重试计数：自增即触发重新拉取 */
  const [reloadToken, setReloadToken] = useState(0);
  const [searchModalOpen, setSearchModalOpen] = useState(false);
  /** 正在解绑的术语 id（禁用该条按钮防重复提交） */
  const [pendingTermId, setPendingTermId] = useState<number | null>(null);

  // 挂载 / 切换实体 / 重试时拉取已绑定术语（cancelled 防竞态）
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    listTermsByEntity(entityId)
      .then((list) => {
        if (!cancelled) {
          setTerms(list);
        }
      })
      .catch((err: any) => {
        if (!cancelled) {
          setError(err?.message || "");
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
  }, [entityId, reloadToken]);

  /** 重新拉取已绑定术语（写入成功后调用，满足写后刷新） */
  const refreshTerms = useCallback(async () => {
    setTerms(await listTermsByEntity(entityId));
  }, [entityId]);

  /** 已绑定术语 id 集合（字符串形态，供搜索弹窗标记「已关联」） */
  const boundTermIdSet: Set<string> = useMemo(
    () => new Set(terms.map((term) => String(term.id))),
    [terms],
  );

  /** 确认关联：搜索弹窗回传字符串 id，绑定接口要求 number，故需 Number() 归一 */
  const handleSelectTerms = useCallback(
    async (termIds: string[]) => {
      setSearchModalOpen(false);
      if (termIds.length === 0) {
        return;
      }
      setOpError("");
      try {
        for (const termId of termIds) {
          await bindTermToEntity(Number(termId), entityId);
        }
        await refreshTerms();
      } catch (err: any) {
        setOpError(
          t("ow.glossaryBinding.saveFailed").replace(
            "{error}",
            String(err?.message || err),
          ),
        );
      }
    },
    [entityId, refreshTerms, t],
  );

  /** 解绑术语（objectTypeId 传 null 即解绑，主术语标记由后端一并撤下） */
  const handleUnbind = useCallback(
    async (termId: number) => {
      setPendingTermId(termId);
      setOpError("");
      try {
        await bindTermToEntity(termId, null);
        await refreshTerms();
      } catch (err: any) {
        setOpError(
          t("ow.glossaryBinding.saveFailed").replace(
            "{error}",
            String(err?.message || err),
          ),
        );
      } finally {
        setPendingTermId(null);
      }
    },
    [refreshTerms, t],
  );

  return (
    <div>
      {/* 标题栏 */}
      <div className="flex items-center justify-between mb-3">
        <h4 className={`text-[11px] font-semibold ${styles.sidebarText} flex items-center gap-1.5`}>
          <BookOpen size={11} className={styles.muted} />
          {t("ow.glossaryBinding.title")}
          <span className={`text-[10px] font-normal ${styles.muted} ml-1`}>
            ({terms.length})
          </span>
        </h4>

        <button
          type="button"
          onClick={() => setSearchModalOpen(true)}
          className={`flex items-center gap-1 px-2.5 py-1 rounded-lg text-[10px] font-medium
            border transition
            ${styles.badgeBg} ${styles.accentText} ${styles.accentBorder}`}
        >
          <Plus size={10} />
          {t("ow.glossaryBinding.bind")}
        </button>
      </div>

      {/* 写入失败提示 */}
      {opError && (
        <p className={`text-[10px] mb-2 ${styles.dangerText}`}>{opError}</p>
      )}

      {/* 加载 / 错误 / 空态 / 术语列表 */}
      {loading ? (
        <div className={`flex items-center justify-center py-8 ${styles.muted}`}>
          <Loader2 size={16} className="animate-spin mr-2" />
          <span className="text-[11px]">{t("ow.glossaryBinding.loading")}</span>
        </div>
      ) : error !== null ? (
        <div className={`flex flex-col items-center justify-center py-8 ${styles.muted}`}>
          <p className={`text-[11px] ${styles.dangerText}`}>
            {error || t("ow.glossaryBinding.loadFailed")}
          </p>
          <button
            type="button"
            onClick={() => setReloadToken((token) => token + 1)}
            className={`mt-2 text-[10px] flex items-center gap-1 ${styles.accentText}`}
          >
            <RotateCw size={11} />
            {t("ow.glossaryBinding.retry")}
          </button>
        </div>
      ) : terms.length === 0 ? (
        <div className={`flex flex-col items-center justify-center py-10 ${styles.muted}`}>
          <BookOpen size={24} className="mb-2 opacity-20" />
          <p className="text-[11px]">{t("ow.glossaryBinding.empty")}</p>
          <p className="text-[9px] mt-0.5 opacity-50">
            {t("ow.glossaryBinding.emptyHint")}
          </p>
        </div>
      ) : (
        <div className="space-y-1.5">
          {terms.map((term) => (
            <TermChip
              key={term.id}
              term={term}
              pending={pendingTermId === term.id}
              onUnbind={() => handleUnbind(term.id)}
            />
          ))}
        </div>
      )}

      {/* 术语搜索弹窗 */}
      <TermSearchModal
        open={searchModalOpen}
        onClose={() => setSearchModalOpen(false)}
        onSelect={handleSelectTerms}
        alreadyBoundIds={boundTermIdSet}
      />
    </div>
  );
}
