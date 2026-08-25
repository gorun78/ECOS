/**
 * Data Engine Configuration Panel
 * 数据引擎配置面板 — 左侧分组导航 + 右侧表单
 * 拆分后：types/分组定义移至 DataEngineConfigPanelTypes.tsx，
 * 导航移至 DataEngineConfigPanelGroupNav.tsx，
 * 表单移至 DataEngineConfigPanelForm.tsx，
 * 操作栏移至 DataEngineConfigPanelActions.tsx。逻辑不变。
 * @license Apache-2.0
 */
import React, { useState, useEffect, useCallback, useRef } from 'react';
import { Settings } from 'lucide-react';
import { apiFetchData } from '../../api';
import { useTheme } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';
import {
  buildConfigGroups, detectEdition, type EcosEdition,
  type ConfigGroup, type ConfigValues, type DefaultValues,
} from './DataEngineConfigPanelTypes';
import DataEngineConfigPanelGroupNav from './DataEngineConfigPanelGroupNav';
import DataEngineConfigPanelForm from './DataEngineConfigPanelForm';
import DataEngineConfigPanelActions from './DataEngineConfigPanelActions';

// Re-export types so existing `import { ConfigGroup } from './DataEngineConfigPanel'` keeps working
export type { ConfigGroup, ConfigValues, DefaultValues } from './DataEngineConfigPanelTypes';

interface Props {
  showToast?: (type: 'success' | 'info' | 'error', message: string) => void;
}

export default function DataEngineConfigPanel({ showToast }: Props) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const edition = useRef<EcosEdition>(detectEdition()).current;
  const allGroups = useRef(buildConfigGroups(edition)).current;
  const [groups, setGroups] = useState<ConfigGroup[]>(allGroups);
  const [activeGroup, setActiveGroup] = useState<string>('execution');
  const [values, setValues] = useState<ConfigValues>({});
  const [defaults, setDefaults] = useState<DefaultValues>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [revealedPasswords, setRevealedPasswords] = useState<Set<string>>(new Set());
  const [originalValues, setOriginalValues] = useState<ConfigValues>({});
  const [loadError, setLoadError] = useState<string | null>(null);

  // 从 API 返回的分组数据中提取扁平配置值
  const flattenConfig = useCallback((data: any): ConfigValues => {
    const result: ConfigValues = {};
    if (!data || typeof data !== 'object') return result;
    for (const groupKey of Object.keys(data)) {
      const group = data[groupKey];
      if (!group || typeof group !== 'object') continue;
      for (const subgroupKey of Object.keys(group)) {
        const subgroup = group[subgroupKey];
        if (!subgroup || typeof subgroup !== 'object') continue;
        for (const key of Object.keys(subgroup)) {
          const fullKey = `dw.${subgroupKey}.${key}`;
          result[fullKey] = subgroup[key];
        }
      }
    }
    return result;
  }, []);

  // 加载配置
  const loadConfig = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const [configResp, defaultsResp] = await Promise.all([
        apiFetchData<{ code: number; data: any }>('/api/v1/engine/data/settings'),
        apiFetchData<{ code: number; data: any }>('/api/v1/engine/data/settings/defaults'),
      ]);

      const configData = configResp?.data ?? configResp;
      const defaultsData = defaultsResp?.data ?? defaultsResp;

      const configValues = flattenConfig(configData);
      const defaultValuesFromApi = flattenConfig(defaultsData);
      const builtInDefaults: DefaultValues = {};
      allGroups.forEach(g => {
        g.items.forEach(item => {
          builtInDefaults[item.key] = item.defaultValue;
        });
      });
      const mergedDefaults: DefaultValues = { ...builtInDefaults, ...defaultValuesFromApi };
      const merged: ConfigValues = { ...mergedDefaults, ...configValues };

      setDefaults(mergedDefaults);
      setValues(merged);
      setOriginalValues({ ...merged });
    } catch (e: any) {
      console.warn('[EngineConfig] Failed to load config:', e);
      setLoadError(t('dw.cfg.toast.loadFailed'));
      const fallback: ConfigValues = {};
      allGroups.forEach(g => {
        g.items.forEach(item => {
          fallback[item.key] = item.defaultValue;
        });
      });
      setDefaults({ ...fallback });
      setValues({ ...fallback });
      setOriginalValues({ ...fallback });
    } finally {
      setLoading(false);
    }
  }, [flattenConfig, allGroups, t]);

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
  const currentGroup = groups.find(g => g.id === activeGroup)!;

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
