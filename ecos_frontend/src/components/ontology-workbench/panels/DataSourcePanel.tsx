/**
 * DataSourcePanel — 数据源面板（右侧面板标签页）
 *
 * 显示当前选中实体已映射的物理表列表，支持：
 *   - 查看已映射物理表（TableChip + 解绑按钮）
 *   - [映射物理表] 按钮 → 打开 DataResourcePickerModal
 *   - [自动发现] 按钮 → 触发自动发现
 *
 * 深色主题，中文界面。
 * 对数据底座不可用的情况提示 "数据底座暂不可用"。
 *
 * Props: { entityId: string }
 *
 * @license Apache-2.0
 */

import React, { useState, useEffect, useCallback } from 'react';
import {
  Database,
  Plus,
  Zap,
  X,
  Trash2,
  Loader2,
  AlertTriangle,
  Table,
  Eye,
} from 'lucide-react';
import { useWorkbenchStore } from '../../../stores/useWorkbenchStore';
import { useTheme } from '../../ThemeContext';
import { useLanguage } from '../../LanguageContext';
import type { BulkResource } from '../../../types/workbench';
import DataResourcePickerModal from '../modals/DataResourcePickerModal';
import { DATA_CATALOG_UNAVAILABLE } from '../../../services/dataCatalogClient';

// ── 组件接口 ────────────────────────────────────────────────

interface DataSourcePanelProps {
  entityId: string;
}

// ── TableChip 子组件 ────────────────────────────────────────

interface TableChipProps {
  resource: BulkResource;
  onUnbind: () => void;
  onPreview: () => void;
}

function TableChip({ resource, onUnbind, onPreview }: TableChipProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const typeLabel = resource.resourceType === 'VIEW' ? t('ow.ds.view') : t('ow.ds.table');

  return (
    <div className="flex items-center gap-2 px-3 py-2 rounded-lg bg-[#0b0e14] border border-[#1E293B]
      hover:border-[#2a3040] transition group">
      {/* 图标 */}
      <div className="p-1 rounded bg-indigo-500/10 shrink-0">
        <Table size={14} className="text-indigo-400" />
      </div>

      {/* 信息 */}
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-1.5">
          <span className={`text-xs font-medium ${styles.cardText} truncate`}>
            {resource.resourceName}
          </span>
          <span className={`text-[9px] px-1.5 py-0.5 rounded ${styles.badgeBg} ${styles.cardTextMuted} shrink-0`}>
            {typeLabel}
          </span>
        </div>
        <div className={`flex items-center gap-2 mt-0.5 text-[10px] ${styles.muted}`}>
          <span className="truncate">{resource.datasourceName}</span>
          <span>·</span>
          <span>{t('ow.ds.fields', { n: resource.fieldCount })}</span>
        </div>
      </div>

      {/* 操作按钮 */}
      <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition">
        <button
          type="button"
          onClick={(e) => { e.stopPropagation(); onPreview(); }}
          title={t('ow.ds.previewTitle')}
          className={`p-1 rounded hover:bg-indigo-500/10 ${styles.muted} hover:text-indigo-400 transition`}
        >
          <Eye size={13} />
        </button>
        <button
          type="button"
          onClick={(e) => { e.stopPropagation(); onUnbind(); }}
          title={t('ow.ds.unbindTitle')}
          className={`p-1 rounded hover:bg-red-500/10 ${styles.muted} hover:text-red-400 transition`}
        >
          <X size={13} />
        </button>
      </div>
    </div>
  );
}

// ── 主组件 ──────────────────────────────────────────────────

export default function DataSourcePanel({ entityId }: DataSourcePanelProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const store = useWorkbenchStore();
  const {
    dataResources,
    dataResourcesLoading,
    entityTableMappings,
    dataPreview,
    fetchDataResources,
    bindEntityToTable,
    unbindEntityFromTable,
    fetchDataPreview,
  } = store;

  const [pickerOpen, setPickerOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [autoDiscovering, setAutoDiscovering] = useState(false);

  // 当前实体已映射的 resourceId 列表
  const mappedResourceIds = entityTableMappings[entityId] || [];

  // 从 store 的 dataResources 中查找已映射资源的详细信息
  const mappedResources = dataResources.filter(
    (r) => mappedResourceIds.includes(r.resourceId)
  );

  // 如果 store 中数据为空，尝试加载
  useEffect(() => {
    if (dataResources.length === 0 && !dataResourcesLoading) {
      fetchDataResources().catch((err) => {
        setError(err?.message || DATA_CATALOG_UNAVAILABLE);
      });
    }
  }, [dataResources.length, dataResourcesLoading, fetchDataResources]);

  // ── 解绑 ──
  const handleUnbind = useCallback(
    (resourceId: string) => {
      unbindEntityFromTable(entityId, resourceId);
    },
    [entityId, unbindEntityFromTable]
  );

  // ── 预览 ──
  const handlePreview = useCallback(
    (resourceId: string) => {
      // 切换右侧面板到数据预览 tab（通过 store 触发预览加载）
      fetchDataPreview(entityId).catch((err) => {
        setError(err?.message || DATA_CATALOG_UNAVAILABLE);
      });
    },
    [entityId, fetchDataPreview]
  );

  // ── 选择资源确认 ──
  const handlePickerSelect = useCallback(
    async (resourceIds: string[]) => {
      setPickerOpen(false);
      setError(null);

      // 绑定每个选中的资源
      for (const rid of resourceIds) {
        try {
          await bindEntityToTable(entityId, rid);
        } catch (err: unknown) {
          setError((err as { message?: string } | undefined)?.message || t('ow.ds.bindFail', { rid }));
        }
      }
    },
    [entityId, bindEntityToTable, t]
  );

  // ── 自动发现 ──
  const handleAutoDiscover = useCallback(async () => {
    setAutoDiscovering(true);
    setError(null);

    try {
      // 确保资源列表已加载
      if (dataResources.length === 0) {
        await fetchDataResources();
      }

      // 简单的自动发现逻辑：按资源名称模糊匹配实体
      // 实际生产环境中应调用后端自动发现 API
      const entity = store.entities.find((e) => e.id === entityId);
      if (!entity) {
        setError(t('ow.ds.noEntity'));
        setAutoDiscovering(false);
        return;
      }

      const entityName = entity.name || entity.code || '';
      const entityCode = entity.code || '';

      // 模糊匹配：资源名包含实体名或实体编码
      const candidates = store.dataResources.filter((r) => {
        const name = r.resourceName.toLowerCase();
        return (
          name.includes(entityName.toLowerCase()) ||
          name.includes(entityCode.toLowerCase())
        );
      });

      if (candidates.length === 0) {
        setError(t('ow.ds.noMatch'));
        setAutoDiscovering(false);
        return;
      }

      // 绑定所有候选资源
      for (const cand of candidates) {
        try {
          await bindEntityToTable(entityId, cand.resourceId);
        } catch {
          // 跳过绑定失败的
        }
      }
    } catch (err: unknown) {
      setError((err as { message?: string } | undefined)?.message || DATA_CATALOG_UNAVAILABLE);
    } finally {
      setAutoDiscovering(false);
    }
  }, [entityId, dataResources.length, fetchDataResources, bindEntityToTable, store.entities, store.dataResources, t]);

  // ── 加载态 ──
  if (dataResourcesLoading && mappedResources.length === 0) {
    return (
      <div className="flex items-center justify-center py-12">
        <Loader2 size={20} className={`animate-spin ${styles.muted}`} />
        <span className={`ml-2 text-xs ${styles.muted}`}>{t('ow.ds.loading')}</span>
      </div>
    );
  }

  // ── 错误提示 ──
  if (error && mappedResources.length === 0) {
    return (
      <div className="space-y-4">
        <div className="flex items-start gap-2 px-3 py-3 rounded-lg bg-amber-500/10 border border-amber-500/20">
          <AlertTriangle size={14} className="text-amber-400 mt-0.5 shrink-0" />
          <div className="flex-1 min-w-0">
            <p className="text-xs text-amber-300 font-medium">{t('ow.ds.unavail')}</p>
            <p className="text-[10px] text-amber-500/70 mt-1">{error}</p>
          </div>
        </div>

        <button
          type="button"
          onClick={() => { setError(null); fetchDataResources().catch(() => {}); }}
          className="w-full py-2 rounded-lg text-xs text-indigo-400 border border-indigo-500/20
            hover:bg-indigo-500/10 transition"
        >
          {t('ow.ds.retry')}
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {/* ── 操作按钮行 ── */}
      <div className="flex items-center gap-2">
        <button
          type="button"
          onClick={() => setPickerOpen(true)}
          className="flex items-center gap-1.5 px-3 py-2 rounded-lg text-[11px] font-medium
            bg-indigo-600 hover:bg-indigo-500 text-white transition"
        >
          <Plus size={12} />
          {t('ow.ds.mapPhysical')}
        </button>
        <button
          type="button"
          onClick={handleAutoDiscover}
          disabled={autoDiscovering}
          className={`flex items-center gap-1.5 px-3 py-2 rounded-lg text-[11px] font-medium
            border border-[#2a3040] ${styles.cardText} hover:border-indigo-500/30 hover:text-indigo-300
            disabled:opacity-50 disabled:cursor-not-allowed transition`}
        >
          {autoDiscovering ? (
            <Loader2 size={12} className="animate-spin" />
          ) : (
            <Zap size={12} />
          )}
          {autoDiscovering ? t('ow.ds.discovering') : t('ow.ds.autoDiscover')}
        </button>
      </div>

      {/* ── 错误提示（已有映射时仍显示） ── */}
      {error && (
        <div className="flex items-start gap-2 px-2.5 py-2 rounded-lg bg-amber-500/10 border border-amber-500/20">
          <AlertTriangle size={12} className="text-amber-400 mt-0.5 shrink-0" />
          <p className="text-[10px] text-amber-400/80">{error}</p>
        </div>
      )}

      {/* ── 空状态 ── */}
      {mappedResources.length === 0 && !error && (
        <div className={`flex flex-col items-center justify-center py-8 ${styles.muted}`}>
          <Database size={28} className="mb-2 opacity-20" />
          <p className="text-xs">{t('ow.ds.unmapped')}</p>
          <p className="text-[10px] mt-1 opacity-60">
            {t('ow.ds.unmappedHint')}
          </p>
        </div>
      )}

      {/* ── 已映射表列表 ── */}
      {mappedResources.length > 0 && (
        <div className="space-y-1.5">
          <div className="flex items-center justify-between mb-2">
            <span className={`text-[10px] font-medium ${styles.muted}`}>
              {t('ow.ds.mapped', { n: mappedResources.length })}
            </span>
          </div>
          {mappedResources.map((res) => (
            <TableChip
              key={res.resourceId}
              resource={res}
              onUnbind={() => handleUnbind(res.resourceId)}
              onPreview={() => handlePreview(res.resourceId)}
            />
          ))}
        </div>
      )}

      {/* ── 数据资源选择器弹窗 ── */}
      <DataResourcePickerModal
        open={pickerOpen}
        onClose={() => setPickerOpen(false)}
        onSelect={handlePickerSelect}
        excludeIds={mappedResourceIds}
      />
    </div>
  );
}
