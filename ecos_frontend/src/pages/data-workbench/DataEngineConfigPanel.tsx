/**
 * Data Engine Configuration Panel
 * 数据引擎配置面板 — 左侧分层导航 + 右侧表单
 *
 * 分层归属（段 / 子分区）由后端 GET /api/v1/engine/data/settings/groups 决定，
 * 本组件负责：拉取分段配置 → 补齐单项元数据 → 扁平化取值 → 编辑与批量保存。
 * 拆分后：types/单项元数据移至 DataEngineConfigPanelTypes.tsx，
 * 导航移至 DataEngineConfigPanelGroupNav.tsx，
 * 表单移至 DataEngineConfigPanelForm.tsx，
 * 操作栏移至 DataEngineConfigPanelActions.tsx。
 * @license Apache-2.0
 */
import React, { useState, useEffect, useCallback, useRef } from 'react';
import { Settings } from 'lucide-react';
import { apiFetchData } from '../../api';
import { useTheme } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';
import {
  buildItemMeta, detectEdition, segmentIcon, groupLabelKey, subgroupLabelKey,
  itemLabelKey, itemDescriptionKey,
  type EcosEdition, type ConfigGroup, type ConfigItem, type ConfigSubGroup,
  type ConfigValues, type DefaultValues,
} from './DataEngineConfigPanelTypes';
import DataEngineConfigPanelGroupNav from './DataEngineConfigPanelGroupNav';
import DataEngineConfigPanelForm from './DataEngineConfigPanelForm';
import DataEngineConfigPanelActions from './DataEngineConfigPanelActions';

// Re-export types so existing `import { ConfigGroup } from './DataEngineConfigPanel'` keeps working
export type { ConfigGroup, ConfigValues, DefaultValues } from './DataEngineConfigPanelTypes';

interface Props {
  showToast?: (type: 'success' | 'info' | 'error', message: string) => void;
}

/** 首个段的 id（后端 SEGMENT_ORDER 首位），用作导航初值 */
const INITIAL_SEGMENT = 'near-source';

/** 单个子分区条目（后端 /groups 响应元素） */
interface SegmentSubGroupPayload {
  id: string;
  configs: Record<string, unknown>;
}

/** 单个段条目（后端 /groups 响应元素） */
interface SegmentPayload {
  id: string;
  subgroups: SegmentSubGroupPayload[];
}

export default function DataEngineConfigPanel({ showToast }: Props) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const edition = useRef<EcosEdition>(detectEdition()).current;
  const itemMeta = useRef(buildItemMeta(edition)).current;
  const [groups, setGroups] = useState<ConfigGroup[]>([]);
  const [activeGroup, setActiveGroup] = useState<string>(INITIAL_SEGMENT);
  const [values, setValues] = useState<ConfigValues>({});
  const [defaults, setDefaults] = useState<DefaultValues>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [revealedPasswords, setRevealedPasswords] = useState<Set<string>>(new Set());
  const [originalValues, setOriginalValues] = useState<ConfigValues>({});
  const [loadError, setLoadError] = useState<string | null>(null);

  /** 从 /groups 响应的 segments 中提取扁平配置值（key 即完整 config_key，无需反拼） */
  const flattenSegments = useCallback((segments: SegmentPayload[]): ConfigValues => {
    const result: ConfigValues = {};
    for (const segment of segments) {
      for (const subgroup of segment.subgroups ?? []) {
        const configs = subgroup?.configs;
        if (!configs || typeof configs !== 'object') continue;
        for (const key of Object.keys(configs)) {
          result[key] = configs[key] as string | number | boolean;
        }
      }
    }
    return result;
  }, []);

  /** /settings/defaults 返回扁平 Map，直接取原始类型值 */
  const flattenFlatMap = useCallback((data: unknown): ConfigValues => {
    const result: ConfigValues = {};
    if (!data || typeof data !== 'object') return result;
    for (const key of Object.keys(data as Record<string, unknown>)) {
      const value = (data as Record<string, unknown>)[key];
      if (value !== null && typeof value === 'object') continue;
      result[key] = value as string | number | boolean;
    }
    return result;
  }, []);

  /** 后端 segments + 本地单项元数据 → 面板分组结构 */
  const buildGroupsFromSegments = useCallback((segments: SegmentPayload[]): ConfigGroup[] => {
    const result: ConfigGroup[] = [];
    for (const segment of segments) {
      if (!segment || typeof segment.id !== 'string') continue;
      const subgroups: ConfigSubGroup[] = [];
      const items: ConfigItem[] = [];
      for (const subgroup of segment.subgroups ?? []) {
        if (!subgroup || typeof subgroup.id !== 'string') continue;
        subgroups.push({ id: subgroup.id, labelKey: subgroupLabelKey(subgroup.id) });
        const configs = subgroup.configs && typeof subgroup.configs === 'object' ? subgroup.configs : {};
        for (const key of Object.keys(configs)) {
          const meta = itemMeta[key];
          if (!meta) {
            // 后端新增了未登记元数据的 key —— 不丢弃，按只读字符串兜底渲染
            console.warn('[EngineConfig] 未定义元数据的配置项，按字符串处理:', key);
          }
          items.push({
            key,
            labelKey: itemLabelKey(key),
            descriptionKey: itemDescriptionKey(key),
            subgroup: subgroup.id,
            ...(meta ?? { type: 'string' as const, defaultValue: String(configs[key] ?? '') }),
          });
        }
      }
      result.push({
        id: segment.id,
        labelKey: groupLabelKey(segment.id),
        icon: segmentIcon(segment.id),
        subgroups,
        items,
        modified: false,
      });
    }
    return result;
  }, [itemMeta]);

  // 加载配置
  const loadConfig = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const [groupsResp, defaultsResp] = await Promise.all([
        apiFetchData<{ code: number; data: { segments?: SegmentPayload[] } }>(
          '/api/v1/engine/data/settings/groups'),
        apiFetchData<{ code: number; data: Record<string, unknown> }>(
          '/api/v1/engine/data/settings/defaults'),
      ]);

      const segments = ((groupsResp?.data ?? groupsResp) as { segments?: SegmentPayload[] })
        ?.segments ?? [];
      const builtGroups = buildGroupsFromSegments(segments);
      setGroups(builtGroups);
      // 保持当前选中段；其不存在时回退首个段
      setActiveGroup(prev =>
        builtGroups.some(g => g.id === prev) ? prev : (builtGroups[0]?.id ?? prev));

      const configValues = flattenSegments(segments);
      const defaultValuesFromApi = flattenFlatMap(defaultsResp?.data ?? defaultsResp);
      const builtInDefaults: DefaultValues = {};
      for (const key of Object.keys(itemMeta)) {
        builtInDefaults[key] = itemMeta[key].defaultValue;
      }
      const mergedDefaults: DefaultValues = { ...builtInDefaults, ...defaultValuesFromApi };
      const merged: ConfigValues = { ...mergedDefaults, ...configValues };

      setDefaults(mergedDefaults);
      setValues(merged);
      setOriginalValues({ ...merged });
    } catch (e: any) {
      console.warn('[EngineConfig] Failed to load config:', e);
      setLoadError(t('dw.cfg.toast.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [buildGroupsFromSegments, flattenSegments, flattenFlatMap, itemMeta, t]);

  useEffect(() => {
    loadConfig();
  }, [loadConfig]);

  // 更新修改标记
  const updateModifiedFlags = useCallback((newValues: ConfigValues) => {
    setGroups(prev =>
      prev.map(g => {
        const anyModified = g.items.some(
          item => String(newValues[item.key] ?? '') !== String(originalValues[item.key] ?? '')
        );
        return { ...g, modified: anyModified };
      })
    );
  }, [originalValues]);

  // 设置单个值
  const handleValueChange = useCallback((key: string, value: string | number | boolean) => {
    setValues(prev => {
      const next = { ...prev, [key]: value };
      updateModifiedFlags(next);
      return next;
    });
  }, [updateModifiedFlags]);

  // 恢复默认
  const handleRestoreDefaults = useCallback(() => {
    const restored = { ...defaults };
    setValues(restored);
    updateModifiedFlags(restored);
    showToast?.('info', t('dw.cfg.toast.restored'));
  }, [defaults, updateModifiedFlags, showToast, t]);

  // 全部保存
  const handleSaveAll = useCallback(async () => {
    setSaving(true);
    try {
      const changedItems: { config_key: string; config_value: string }[] = [];
      for (const key of Object.keys(values)) {
        const currentVal = values[key];
        const origVal = originalValues[key];
        if (String(currentVal ?? '') !== String(origVal ?? '')) {
          changedItems.push({
            config_key: key,
            config_value: String(currentVal ?? ''),
          });
        }
      }

      if (changedItems.length === 0) {
        showToast?.('info', t('dw.cfg.toast.noChanges'));
        setSaving(false);
        return;
      }

      await apiFetchData('/api/v1/engine/data/settings', {
        method: 'PUT',
        body: JSON.stringify(changedItems),
      });

      setOriginalValues({ ...values });
      setGroups(prev => prev.map(g => ({ ...g, modified: false })));
      showToast?.('success', t('dw.cfg.toast.saved', { count: changedItems.length }));
    } catch (e: any) {
      showToast?.('error', t('dw.cfg.toast.saveFailed', { error: e?.message || '' }));
    } finally {
      setSaving(false);
    }
  }, [values, originalValues, showToast, t]);

  // 刷新缓存
  const handleRefreshCache = useCallback(async () => {
    setRefreshing(true);
    try {
      await apiFetchData('/api/v1/engine/data/settings/refresh', { method: 'POST' });
      showToast?.('success', t('dw.cfg.toast.cacheRefreshed'));
    } catch (e: any) {
      showToast?.('error', t('dw.cfg.toast.refreshFailed', { error: e?.message || '' }));
    } finally {
      setRefreshing(false);
    }
  }, [showToast, t]);

  // 切换密码可见性
  const togglePasswordReveal = useCallback((key: string) => {
    setRevealedPasswords(prev => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  }, []);

  // Ctrl+S 键盘快捷键
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 's') {
        e.preventDefault();
        handleSaveAll();
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [handleSaveAll]);

  // ── Current group data ──
  // 段 id 变更后 activeGroup 可能失配；loading/错误态下 groups 为空，Form 会提前返回
  const currentGroup = groups.find(g => g.id === activeGroup) ?? groups[0];

  // ── Render ──
  return (
    <div className={`flex-1 flex flex-col min-h-0 ${styles.cardBg}`}>
      {/* Header */}
      <div className={`flex items-center gap-2 px-4 py-2.5 border-b ${styles.cardBorder} ${styles.cardBg} shrink-0`}>
        <Settings size={16} className={styles.cardTextMuted} />
        <span className={`text-sm font-bold ${styles.cardText}`}>{t('dw.cfg.header.title')}</span>
        <span className={`text-[10px] ${styles.cardTextMuted} ml-auto`}>
          {t('dw.cfg.header.shortcut')}
        </span>
      </div>

      <div className="flex flex-1 min-h-0">
        <DataEngineConfigPanelGroupNav
          groups={groups}
          activeGroup={activeGroup}
          onSelect={setActiveGroup}
          styles={styles}
        />
        <DataEngineConfigPanelForm
          loading={loading}
          loadError={loadError}
          currentGroup={currentGroup}
          values={values}
          defaults={defaults}
          originalValues={originalValues}
          revealedPasswords={revealedPasswords}
          onValueChange={handleValueChange}
          onRetry={loadConfig}
          onTogglePassword={togglePasswordReveal}
          styles={styles}
        />
      </div>

      <DataEngineConfigPanelActions
        groups={groups}
        saving={saving}
        refreshing={refreshing}
        onRestoreDefaults={handleRestoreDefaults}
        onSaveAll={handleSaveAll}
        onRefreshCache={handleRefreshCache}
        styles={styles}
      />
    </div>
  );
}
