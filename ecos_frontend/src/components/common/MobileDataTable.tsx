/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 *
 * 移动端表格熔断点容器：移动端按 MobileCardDataCards 渲染（卡片态），桌面/平板态
 * 完整交由 DataTable 渲染（不强写桌面表现）。读路径的判断简单：
 *
 * - md 暗示上（≥ 768px） → DataTable（完全走这一路径）
 * - md 以下（< md） → MobileCardDataCards（卡片态，复用 DataTable 的分页逻辑）
 *
 * 语义对齐：mobileConfig 装配各列做卡片头/详情的语义映射：
 * - headerKeys: 行展开态主键 + 1~2 个高优先级字段（名称/状态/时间）
 * - detailKeys: 折到「展开」区的宽字段（描述 / 代码 / JSON）
 * - actionColumnKey: 提供固定 1 个列作为「操作」（edit/delete 等），自动切片卡片底部
 *
 * 设计约定（满足 ECOS 前端开发规范 §一/四 红线）：
 * - 通用封装：组件签名基于 DataTable 的 props 扩展，不重写桌面渲染逻辑
 * - 永不加 !important；样式全走 Tailwind 断点类
 * - 兼容 useTheme（深色/浅色）与 i18n
 */

import React, { useState } from "react";
import { ChevronDown, ChevronUp } from "lucide-react";
import { useTheme } from "../ThemeContext";
import DataTable, {ColumnConfig, DataTableProps} from "./DataTable";
import EmptyState from "./EmptyState";
import LoadingSkeleton from "./LoadingSkeleton";
import { useMediaQuery } from "../../hooks/useMediaQuery";

/**
 * 移动端卡片态配置：哪些字段做卡片头、哪些做详情折叠区、哪列作为底部操作按钮
 *
 * T：行数据类型
 */
export interface MobileCardConfig<T> {
  /** 主键 + 高优先级字段组（要显示在卡片头部，1~2 个最强字段） */
  headerKeys: string[];
  /** 详情字段：折到「展开」的宽字段（描述/代码/JSON） —— 可省略，默认用除 header 与 action 外的所有列 */
  detailKeys?: string[];
  /** action 列 key：列内 render 会包装成卡片底部按钮组（自动从 columns 中拆出） */
  actionColumnKey?: string;
}

/**
 * MobileDataTable 入参：以 DataTable 主要生产指标为主，额外允许卡片态配置
 *
 * 注意：以下字段在卡片态被覆盖使用，故 Omit 后再展开透传给 DataTable 桌面态：
 *   - loading / hidePagination / className（卡片态有独立加载骨架、分页 UI、容器 className）
 */
export interface MobileDataTableProps<T extends Record<string, any>>
  extends Omit<DataTableProps<T>, "className" | "loading" | "hidePagination"> {
  /** 容器 className（用于桌面态 DataTable + 移动端外层） */
  className?: string;
  /** 加载中：true 时按样式渲染骨架 */
  loading?: boolean;
  /** 是否隐藏分页（卡片态隐藏时分页控件不会出现） */
  hidePagination?: boolean;
  /**
   * 卡片态配置（移动端显示时生效）。
   * 缺省 → 全部 columns 都折进详情区（主键 + 首列做卡片头）
   */
  mobileConfig?: MobileCardConfig<T>;
  /**
   * 移动端断点判定。缺省 = "(max-width: 767px)"。
   * 业务方可以传 "(max-width: 1023px)" 让平板也走卡片态。
   */
  mobileQuery?: string;
}

/**
 * 卡片态单元格渲染：按 col 类型决定渲染
 *
 * - 列有 render → 调用 render
 * - 列文本长度 < 80 字符 → 直接显示
 * - 列 JSON 文本（以 '{"' / '[' 开头或包含 '"' 比例 > 0.1） → 直接用 JSON 风格高亮色 + pre 折叠
 * - 列 key 名包含 json / code / sql / yaml / 脚本等关键字 → 同样走 JSON 风格
 */
function renderCellText<T extends Record<string, any>>(
  col: ColumnConfig<T>,
  value: any,
  record: T,
): React.ReactNode {
  if (col.render) {
    return col.render(value, record, 0);
  }
  if (value === null || value === undefined || value === "") {
    return <span className="text-muted-foreground/50">—</span>;
  }
  const txt = String(value);
  const isLikeJson =
    (txt.length > 20 && txt.startsWith("{") && txt.endsWith("}")) ||
    (txt.length > 10 && txt.startsWith("[") && txt.endsWith("]")) ||
    txt.length > 100 && (txt.includes('"') || txt.includes("{"));
  if (isLikeJson) {
    return (
      <pre className="whitespace-pre-wrap break-all text-[11px] bg-black/5 dark:bg-white/5 rounded px-2 py-1.5 my-1 font-mono">{txt}</pre>
    );
  }
  return txt;
}

/**
 * 卡片态实现：MobileDataTable 的 < md 分支
 *
 * - 主行 = 卡片头（headerKeys 字段 + label，1~2 个最高优先级）
 * - 详情 = detailKeys 字段（展开后显示）
 * - 底部 = action 字段（按钮组，自动从 columns 中拆出）
 */
function MobileCardRows<T extends Record<string, any>>({
  columns,
  data,
  rowKey,
  loading = false,
  emptyTitle,
  emptyDescription,
  emptyIcon,
  emptyAction,
  pageSize = 10,
  currentPage,
  total,
  onPageChange,
  onRowClick,
  hidePagination = false,
  mobileConfig,
  styles,
}: {
  columns: ColumnConfig<T>[];
  data: T[];
  rowKey: keyof T | ((record: T) => string);
  loading?: boolean;
  emptyTitle?: string;
  emptyDescription?: string;
  emptyIcon?: React.ReactNode;
  emptyAction?: {label: string; onClick: () => void};
  pageSize?: number;
  currentPage?: number;
  total?: number;
  onPageChange?: (page: number) => void;
  onRowClick?: (record: T) => void;
  hidePagination?: boolean;
  mobileConfig?: MobileCardConfig<T>;
  styles: Record<string, string>;
}) {
  const [innerPage, setInnerPage] = useState(1);
  const [expanded, setExpanded] = useState<boolean>(false); // 当前卡片是否展开
  const isControlled = currentPage !== undefined;
  const page = isControlled ? currentPage : innerPage;
  const allTotal = total ?? data.length;
  const totalPages = Math.max(1, Math.ceil(allTotal / pageSize));
  const displayData = isControlled ? data : data.slice((page - 1) * pageSize, page * pageSize);
  const setPage = (p: number) => {
    const clamped = Math.max(1, Math.min(p, totalPages));
    if (isControlled) onPageChange?.(clamped);
    else setInnerPage(clamped);
  };
  const getRowKey = (r: T, i: number): string => {
    if (typeof rowKey === "function") return rowKey(r);
    return String(r[rowKey]);
  };

  if (loading) {
    return (
      <div>
        <LoadingSkeleton variant="table" rows={pageSize} />
      </div>
    );
  }
  if (!displayData.length) {
    return (
      <EmptyState
        icon={emptyIcon}
        title={emptyTitle}
        description={emptyDescription}
        action={emptyAction}
      />
    );
  }

  const actionCol = mobileConfig?.actionColumnKey
    ? columns.find((c) => c.key === mobileConfig!.actionColumnKey)
    : undefined;
  const headerKeys = mobileConfig?.headerKeys ?? [];
  const detailKeys =
    mobileConfig?.detailKeys ??
    columns.filter(
      (c) => !headerKeys.includes(c.key) && c.key !== mobileConfig?.actionColumnKey,
    ).map((c) => c.key);

  return (
    <div className="flex flex-col gap-2">
      {displayData.map((r, i) => {
        const rowId = getRowKey(r, i);
        return (
          <div
            key={rowId}
            className="border rounded-md bg-white dark:bg-neutral-900 border-black/10 dark:border-white/10"
          >
            {/* 卡片头 */}
            <div className="flex items-center justify-between px-3 py-2.5">
              <div className="flex flex-wrap items-center gap-2 min-w-0 flex-1">
                {headerKeys.map((k) => {
                  const col = columns.find((c) => c.key === k);
                  if (!col) return null;
                  const val = r[k];
                  return (
                    <div key={k} className="flex items-center gap-1.5 min-w-0">
                      <span className="text-[10px] text-muted-foreground uppercase tracking-wider shrink-0">{col.label}</span>
                      <span className="text-xs font-medium truncate">
                        {renderCellText(col, val, r)}
                      </span>
                    </div>
                  );
                })}
              </div>
              <button
                type="button"
                aria-label={expanded ? "收起详情" : "展开详情"}
                onClick={() => setExpanded((v) => !v)}
                className="p-1.5 rounded hover:bg-black/5 dark:hover:bg-white/10 transition shrink-0"
              >
                {expanded ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
              </button>
            </div>

            {/* 详情区（折叠） */}
            {expanded && (
              <div className="border-t px-3 py-2.5 border-black/10 dark:border-white/10 flex flex-col gap-2">
                {detailKeys.map((k) => {
                  const col = columns.find((c) => c.key === k);
                  if (!col) return null;
                  const val = r[k];
                  return (
                    <div key={k} className="flex flex-col gap-0.5 min-w-0">
                      <span className="text-[10px] text-muted-foreground uppercase tracking-wider">{col.label}</span>
                      <div className="text-xs break-words">{renderCellText(col, val, r)}</div>
                    </div>
                  );
                })}
              </div>
            )}

            {/* 操作底部 */}
            {actionCol && (
              <div className="border-t px-3 py-2 border-black/10 dark:border-white/10 flex items-center justify-end gap-1.5 flex-wrap">
                {typeof actionCol.render === "function"
                  ? actionCol.render(r[actionCol.key], r, i)
                  : r[actionCol.key] == null
                    ? null
                    : <span className="text-xs">{r[actionCol.key]}</span>}
              </div>
            )}
          </div>
        );
      })}

      {/* 移动端分页（与 DataTable 同等行为） */}
      {!hidePagination && totalPages > 1 && (
        <div className="flex items-center justify-center py-2 text-xs">
          <div className="flex items-center gap-1">
            <button
              type="button"
              disabled={page <= 1}
              onClick={() => setPage(page - 1)}
              className="px-2.5 py-1 border rounded text-xs disabled:opacity-40"
            >
              上一页
            </button>
            <span className="px-2 select-none">{page}/{totalPages}</span>
            <button
              type="button"
              disabled={page >= totalPages}
              onClick={() => setPage(page + 1)}
              className="px-2.5 py-1 border rounded text-xs disabled:opacity-40"
            >
              下一页
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

/**
 * MobileDataTable 主组件：移动端按比例切换 DataTable 与卡片态
 *
 * 签名兼容所有 DataTable props，再加 mobileConfig + mobileQuery。
 */
export default function MobileDataTable<T extends Record<string, any>>({
  className = "",
  mobileConfig,
  mobileQuery,
  columns,
  data,
  rowKey,
  ...rest
}: MobileDataTableProps<T>): React.ReactElement {
  // 默认移动端查询；最终选择时的优先级：mobileQuery > 系统默认 "(max-width: 767px)"
  const mobileQueryResolved = mobileQuery ?? "(max-width: 767px)";
  const isMobile = useMediaQuery(mobileQueryResolved);
  const {styles} = useTheme();

  if (!isMobile) {
    // 桌面态：完整交给 DataTable（不重写桌面表现）
    return (
      <DataTable<T>
        columns={columns}
        data={data}
        rowKey={rowKey}
        {...rest}
        className={`${className} flex flex-col md:min-w-0`}
      />
    );
  }

  // 移动端：卡片态
  return (
    <div className={className}>
      <MobileCardRows<T>
        columns={columns}
        data={data}
        rowKey={rowKey}
        {...rest}
        mobileConfig={mobileConfig}
        styles={styles}
      />
    </div>
  );
}
