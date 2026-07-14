/**
 * Data Engine Configuration Panel
 * 数据引擎配置面板 — 左侧分组导航 + 右侧表单
 * @license Apache-2.0
 */
import React, { useState, useEffect, useCallback, useRef } from 'react';
import {
  Settings, RotateCcw, Save, RefreshCw, Check, AlertCircle,
  Shield, Eye, EyeOff, Cpu, Database, HardDrive, Workflow,
  ShieldCheck, GitBranch, Wrench,
} from 'lucide-react';
import { apiFetch, apiFetchData } from '../../api';

// ── Types ────────────────────────────────────────────────────

interface ConfigItem {
  key: string;
  label: string;
  type: 'string' | 'int' | 'float' | 'bool' | 'enum' | 'password';
  defaultValue: string | number | boolean;
  description: string;
  options?: string[]; // for enum type
  sensitive?: boolean;
  subgroup: string;
}

interface ConfigGroup {
  id: string;       // subgroup key, e.g. "execution"
  label: string;    // Chinese label, e.g. "执行引擎"
  icon: React.ReactNode;
  items: ConfigItem[];
  modified: boolean;
}

interface ConfigValues {
  [key: string]: string | number | boolean;
}

interface DefaultValues {
  [key: string]: string | number | boolean;
}

// ── Config Item Definitions ──────────────────────────────────

function buildConfigGroups(): ConfigGroup[] {
  return [
    {
      id: 'execution',
      label: '执行引擎',
      icon: <Cpu size={15} />,
      items: [
        { key: 'dw.execution.mode', label: '执行模式', type: 'enum', defaultValue: 'memory', description: 'memory=内存模式, doris=Apache Doris', options: ['memory', 'doris'], subgroup: 'execution' },
        { key: 'dw.execution.memory.max_rows', label: '内存最大处理行数', type: 'int', defaultValue: 100000, description: '内存模式最大处理行数', subgroup: 'execution' },
        { key: 'dw.execution.memory.threads', label: '并行线程数', type: 'int', defaultValue: 4, description: '内存模式并行线程数', subgroup: 'execution' },
        { key: 'dw.execution.doris.host', label: 'Doris FE 地址', type: 'string', defaultValue: 'localhost', description: 'Doris FE 地址', subgroup: 'execution' },
        { key: 'dw.execution.doris.port', label: 'Doris 端口', type: 'int', defaultValue: 9030, description: 'Doris MySQL 协议端口', subgroup: 'execution' },
        { key: 'dw.execution.doris.user', label: 'Doris 用户名', type: 'string', defaultValue: 'root', description: 'Doris 用户名', subgroup: 'execution' },
        { key: 'dw.execution.doris.database', label: 'Doris 默认库', type: 'string', defaultValue: 'ecos_dw', description: 'Doris 默认库', subgroup: 'execution' },
        { key: 'dw.execution.doris.batch_size', label: 'Doris 批量写入行数', type: 'int', defaultValue: 10000, description: 'Doris 批量写入行数', subgroup: 'execution' },
        { key: 'dw.execution.timeout', label: '任务超时(秒)', type: 'int', defaultValue: 600, description: '任务超时秒数', subgroup: 'execution' },
      ],
      modified: false,
    },
    {
      id: 'data-lake',
      label: '数据湖',
      icon: <Database size={15} />,
      items: [
        { key: 'dw.lake.enabled', label: '启用数据湖', type: 'bool', defaultValue: false, description: '是否启用数据湖', subgroup: 'data-lake' },
        { key: 'dw.lake.datasource_id', label: '目标数据源 ID', type: 'string', defaultValue: '', description: '数据湖目标数据源 ID', subgroup: 'data-lake' },
        { key: 'dw.lake.storage_format', label: '存储格式', type: 'enum', defaultValue: 'parquet', description: 'parquet/orc/avro', options: ['parquet', 'orc', 'avro'], subgroup: 'data-lake' },
        { key: 'dw.lake.partition_by', label: '默认分区字段', type: 'string', defaultValue: 'dt', description: '默认分区字段', subgroup: 'data-lake' },
        { key: 'dw.lake.retention_days', label: '数据保留天数', type: 'int', defaultValue: 90, description: '数据保留天数', subgroup: 'data-lake' },
      ],
      modified: false,
    },
    {
      id: 'object-storage',
      label: '对象存储',
      icon: <HardDrive size={15} />,
      items: [
        { key: 'dw.storage.type', label: '存储类型', type: 'enum', defaultValue: 'minio', description: 'minio/s3/oss', options: ['minio', 's3', 'oss'], subgroup: 'object-storage' },
        { key: 'dw.storage.minio.endpoint', label: 'MinIO 地址', type: 'string', defaultValue: 'http://localhost:9000', description: 'MinIO 服务地址', subgroup: 'object-storage' },
        { key: 'dw.storage.minio.access_key', label: 'Access Key', type: 'string', defaultValue: 'minioadmin', description: 'MinIO Access Key', subgroup: 'object-storage' },
        { key: 'dw.storage.minio.secret_key', label: 'Secret Key', type: 'password', defaultValue: 'minioadmin', description: 'MinIO Secret Key（加密存储）', sensitive: true, subgroup: 'object-storage' },
        { key: 'dw.storage.minio.bucket', label: '默认 Bucket', type: 'string', defaultValue: 'ecos-data', description: '默认 Bucket 名称', subgroup: 'object-storage' },
        { key: 'dw.storage.minio.region', label: '区域', type: 'string', defaultValue: 'us-east-1', description: '区域标识', subgroup: 'object-storage' },
        { key: 'dw.storage.minio.ssl', label: '启用 SSL', type: 'bool', defaultValue: false, description: '是否启用 SSL', subgroup: 'object-storage' },
      ],
      modified: false,
    },
    {
      id: 'pipeline',
      label: '管道',
      icon: <Workflow size={15} />,
      items: [
        { key: 'dw.pipeline.max_steps', label: '最大步骤数', type: 'int', defaultValue: 20, description: '单个管道最大步骤数', subgroup: 'pipeline' },
        { key: 'dw.pipeline.parallel_steps', label: '并行步骤数', type: 'int', defaultValue: 4, description: '允许的并行步骤数', subgroup: 'pipeline' },
        { key: 'dw.pipeline.default_chunk_size', label: '分块行数', type: 'int', defaultValue: 10000, description: '默认分块行数', subgroup: 'pipeline' },
        { key: 'dw.pipeline.temp_table_prefix', label: '临时表前缀', type: 'string', defaultValue: 'ecos_tmp_', description: '临时表前缀', subgroup: 'pipeline' },
        { key: 'dw.pipeline.temp_table_ttl_hours', label: '临时表过期(小时)', type: 'int', defaultValue: 24, description: '临时表过期时间（小时）', subgroup: 'pipeline' },
        { key: 'dw.pipeline.retry_max', label: '重试次数', type: 'int', defaultValue: 3, description: '步骤默认重试次数', subgroup: 'pipeline' },
        { key: 'dw.pipeline.retry_backoff_ms', label: '重试间隔(毫秒)', type: 'int', defaultValue: 5000, description: '重试间隔（毫秒）', subgroup: 'pipeline' },
      ],
      modified: false,
    },
    {
      id: 'quality',
      label: '数据质量',
      icon: <ShieldCheck size={15} />,
      items: [
        { key: 'dw.quality.sample_rate', label: '采样率', type: 'float', defaultValue: 1.0, description: '采样率（0.0–1.0）', subgroup: 'quality' },
        { key: 'dw.quality.sample_max_rows', label: '采样最大行数', type: 'int', defaultValue: 1000000, description: '采样最大行数', subgroup: 'quality' },
        { key: 'dw.quality.stale_threshold_hours', label: '过期阈值(小时)', type: 'int', defaultValue: 24, description: '数据过期阈值（小时）', subgroup: 'quality' },
        { key: 'dw.quality.default_alert_score', label: '告警分数阈值', type: 'int', defaultValue: 80, description: '默认告警分数阈值', subgroup: 'quality' },
        { key: 'dw.quality.concurrent_checks', label: '并发检查数', type: 'int', defaultValue: 2, description: '并发检查任务数', subgroup: 'quality' },
        { key: 'dw.quality.check_timeout', label: '检查超时(秒)', type: 'int', defaultValue: 300, description: '单次检查超时秒数', subgroup: 'quality' },
      ],
      modified: false,
    },
    {
      id: 'lineage',
      label: '血缘',
      icon: <GitBranch size={15} />,
      items: [
        { key: 'dw.lineage.enabled', label: '启用血缘', type: 'bool', defaultValue: true, description: '是否启用血缘采集', subgroup: 'lineage' },
        { key: 'dw.lineage.parser', label: '解析引擎', type: 'enum', defaultValue: 'sql', description: '血缘解析引擎', options: ['sql', 'spark', 'dbt'], subgroup: 'lineage' },
        { key: 'dw.lineage.max_depth', label: '最大追溯深度', type: 'int', defaultValue: 10, description: '最大追溯深度', subgroup: 'lineage' },
        { key: 'dw.lineage.cache_ttl_minutes', label: '缓存时间(分钟)', type: 'int', defaultValue: 30, description: '血缘缓存时间', subgroup: 'lineage' },
        { key: 'dw.lineage.neo4j_enabled', label: 'Neo4j 图存储', type: 'bool', defaultValue: false, description: '是否启用 Neo4j 图存储', subgroup: 'lineage' },
      ],
      modified: false,
    },
    {
      id: 'general',
      label: '通用',
      icon: <Wrench size={15} />,
      items: [
        { key: 'dw.sync.batch_size', label: '同步批次大小', type: 'int', defaultValue: 5000, description: '同步任务默认批次大小', subgroup: 'general' },
        { key: 'dw.sync.max_retries', label: '同步最大重试', type: 'int', defaultValue: 3, description: '同步任务最大重试', subgroup: 'general' },
        { key: 'dw.query.max_rows', label: '查询最大行数', type: 'int', defaultValue: 10000, description: 'SQL 查询最大返回行数', subgroup: 'general' },
        { key: 'dw.query.timeout', label: '查询超时(秒)', type: 'int', defaultValue: 30, description: 'SQL 查询超时秒数', subgroup: 'general' },
        { key: 'dw.cache.ttl_seconds', label: '缓存时间(秒)', type: 'int', defaultValue: 300, description: '元数据缓存时间', subgroup: 'general' },
        { key: 'dw.engine.auto_start', label: '自动启动引擎', type: 'bool', defaultValue: true, description: '启动时自动启动数据引擎', subgroup: 'general' },
      ],
      modified: false,
    },
  ];
}

// ── Component ─────────────────────────────────────────────────

interface Props {
  showToast?: (type: 'success' | 'info' | 'error', message: string) => void;
}

export default function DataEngineConfigPanel({ showToast }: Props) {
  const allGroups = useRef(buildConfigGroups()).current;
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
    // data 可能是 { "data-engine": { execution: {...}, ... } }
    for (const groupKey of Object.keys(data)) {
      const group = data[groupKey];
      if (!group || typeof group !== 'object') continue;
      for (const subgroupKey of Object.keys(group)) {
        const subgroup = group[subgroupKey];
        if (!subgroup || typeof subgroup !== 'object') continue;
        for (const key of Object.keys(subgroup)) {
          // Construct full key: dw.{subgroup}.{key}
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
        apiFetch<{ code: number; data: any }>('/api/v1/engine/data/settings'),
        apiFetch<{ code: number; data: any }>('/api/v1/engine/data/settings/defaults'),
      ]);

      const configData = configResp?.data ?? configResp;
      const defaultsData = defaultsResp?.data ?? defaultsResp;

      const configValues = flattenConfig(configData);
      // For defaults, also try to flatten; if that fails, use the built-in defaults from config groups
      const defaultValuesFromApi = flattenConfig(defaultsData);
      const builtInDefaults: DefaultValues = {};
      allGroups.forEach(g => {
        g.items.forEach(item => {
          builtInDefaults[item.key] = item.defaultValue;
        });
      });
      // Merge: API defaults override built-in where available
      const mergedDefaults: DefaultValues = { ...builtInDefaults, ...defaultValuesFromApi };

      // Merge loaded values with defaults (loaded takes priority)
      const merged: ConfigValues = { ...mergedDefaults, ...configValues };

      setDefaults(mergedDefaults);
      setValues(merged);
      setOriginalValues({ ...merged });
    } catch (e: any) {
      console.warn('[EngineConfig] Failed to load config:', e);
      setLoadError(e?.message || '加载配置失败，使用本地默认值');
      // Fallback: use built-in defaults
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
  }, [flattenConfig, allGroups]);

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
    showToast?.('info', '已恢复为默认值，尚未保存');
  }, [defaults, updateModifiedFlags, showToast]);

  // 全部保存
  const handleSaveAll = useCallback(async () => {
    setSaving(true);
    try {
      // 构建需要保存的配置项
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
        showToast?.('info', '没有需要保存的更改');
        setSaving(false);
        return;
      }

      await apiFetch('/api/v1/engine/data/settings', {
        method: 'PUT',
        body: JSON.stringify(changedItems),
      });

      setOriginalValues({ ...values });
      // 清除所有修改标记
      setGroups(prev => prev.map(g => ({ ...g, modified: false })));
      showToast?.('success', `已保存 ${changedItems.length} 项配置`);
    } catch (e: any) {
      showToast?.('error', `保存失败: ${e?.message || '未知错误'}`);
    } finally {
      setSaving(false);
    }
  }, [values, originalValues, showToast]);

  // 刷新缓存
  const handleRefreshCache = useCallback(async () => {
    setRefreshing(true);
    try {
      await apiFetch('/api/v1/engine/data/settings/refresh', { method: 'POST' });
      showToast?.('success', '缓存已刷新');
    } catch (e: any) {
      showToast?.('error', `刷新缓存失败: ${e?.message || '未知错误'}`);
    } finally {
      setRefreshing(false);
    }
  }, [showToast]);

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
    <div className="flex-1 flex flex-col min-h-0 bg-white">
      {/* Header */}
      <div className="flex items-center gap-2 px-4 py-2.5 border-b border-slate-200 bg-slate-50/80 shrink-0">
        <Settings size={16} className="text-slate-500" />
        <span className="text-sm font-bold text-slate-700">引擎配置</span>
        <span className="text-[10px] text-slate-400 ml-auto">
          Ctrl+S 保存当前修改
        </span>
      </div>

      <div className="flex flex-1 min-h-0">
        {/* ── Left: Group Navigation ── */}
        <div className="w-44 border-r border-slate-200 bg-slate-50/30 flex flex-col shrink-0 overflow-y-auto">
          {groups.map(g => (
            <button
              key={g.id}
              onClick={() => setActiveGroup(g.id)}
              className={`flex items-center gap-2 px-3 py-2.5 text-xs font-medium transition-all text-left border-l-2 ${
                activeGroup === g.id
                  ? 'bg-blue-50 text-blue-700 border-l-blue-600'
                  : 'text-slate-600 hover:bg-slate-100 border-l-transparent'
              }`}
            >
              <span className={activeGroup === g.id ? 'text-blue-600' : 'text-slate-400'}>
                {g.icon}
              </span>
              <span className="flex-1">{g.label}</span>
              {g.modified && (
                <span className="text-amber-500 text-[10px] font-bold" title="有未保存的修改">●</span>
              )}
            </button>
          ))}
        </div>

        {/* ── Right: Config Form ── */}
        <div className="flex-1 flex flex-col min-h-0 overflow-y-auto">
          {loading ? (
            <div className="flex items-center justify-center flex-1 text-slate-400 text-sm">
              正在加载配置...
            </div>
          ) : loadError ? (
            <div className="flex flex-col items-center justify-center flex-1 gap-2">
              <AlertCircle size={24} className="text-amber-500" />
              <span className="text-sm text-slate-500">{loadError}</span>
              <button
                onClick={loadConfig}
                className="px-3 py-1 text-xs text-blue-600 hover:bg-blue-50 rounded-md transition-colors"
              >
                重试
              </button>
            </div>
          ) : (
            <>
              {/* Group Title */}
              <div className="px-5 py-3 border-b border-slate-100 bg-white sticky top-0 z-10">
                <div className="flex items-center gap-2">
                  <span className="text-slate-400">
                    {currentGroup.icon}
                  </span>
                  <h3 className="text-sm font-bold text-slate-800">
                    {currentGroup.label}
                  </h3>
                  {currentGroup.modified && (
                    <span className="text-amber-500 text-[10px] font-bold">● 已修改</span>
                  )}
                </div>
                <p className="text-[10px] text-slate-400 mt-0.5 ml-7">
                  共 {currentGroup.items.length} 项配置
                </p>
              </div>

              {/* Form Fields */}
              <div className="px-5 py-4 space-y-4">
                {currentGroup.items.map(item => {
                  const rawValue = values[item.key];
                  const displayValue = rawValue !== undefined ? rawValue : item.defaultValue;
                  const defaultValue = defaults[item.key] ?? item.defaultValue;
                  const isModified = String(rawValue ?? '') !== String(originalValues[item.key] ?? '');
                  const isPassword = item.type === 'password';
                  const passwordRevealed = revealedPasswords.has(item.key);

                  return (
                    <div key={item.key} className="space-y-1.5">
                      {/* Label + Description */}
                      <div className="flex items-center gap-2">
                        <label className="text-xs font-semibold text-slate-700">
                          {item.label}
                        </label>
                        {isPassword && (
                          <Shield size={11} className="text-amber-500" title="敏感字段" />
                        )}
                        {isModified && (
                          <span className="w-1.5 h-1.5 rounded-full bg-amber-500" title="已修改" />
                        )}
                      </div>

                      {/* Input Control */}
                      {item.type === 'bool' ? (
                        <label className="flex items-center gap-2 cursor-pointer select-none">
                          <button
                            type="button"
                            role="switch"
                            aria-checked={displayValue === true || displayValue === 'true'}
                            onClick={() => handleValueChange(item.key, !(displayValue === true || displayValue === 'true'))}
                            className={`relative inline-flex h-5 w-9 items-center rounded-full transition-colors ${
                              displayValue === true || displayValue === 'true'
                                ? 'bg-blue-600'
                                : 'bg-slate-300'
                            }`}
                          >
                            <span
                              className={`inline-block h-3.5 w-3.5 transform rounded-full bg-white transition-transform shadow-sm ${
                                displayValue === true || displayValue === 'true' ? 'translate-x-[18px]' : 'translate-x-[3px]'
                              }`}
                            />
                          </button>
                          <span className="text-xs text-slate-500">
                            {displayValue === true || displayValue === 'true' ? '启用' : '禁用'}
                          </span>
                        </label>
                      ) : item.type === 'enum' && item.options ? (
                        <select
                          value={String(displayValue)}
                          onChange={e => handleValueChange(item.key, e.target.value)}
                          className="w-full max-w-sm px-2.5 py-1.5 text-xs border border-slate-300 rounded-md bg-white text-slate-700 focus:border-blue-400 focus:ring-1 focus:ring-blue-200 outline-none transition-colors"
                        >
                          {item.options.map(opt => (
                            <option key={opt} value={opt}>{opt}</option>
                          ))}
                        </select>
                      ) : isPassword ? (
                        <div className="relative max-w-sm">
                          <input
                            type={passwordRevealed ? 'text' : 'password'}
                            value={passwordRevealed ? String(displayValue ?? '') : '********'}
                            onChange={e => handleValueChange(item.key, e.target.value)}
                            className="w-full px-2.5 py-1.5 pr-16 text-xs border border-slate-300 rounded-md bg-white text-slate-700 font-mono focus:border-amber-400 focus:ring-1 focus:ring-amber-200 outline-none transition-colors"
                            placeholder="********"
                          />
                          <button
                            type="button"
                            onClick={() => togglePasswordReveal(item.key)}
                            className="absolute right-1 top-1/2 -translate-y-1/2 p-1 rounded hover:bg-slate-100 text-slate-400 hover:text-slate-600 transition-colors"
                            title={passwordRevealed ? '隐藏' : '显示'}
                          >
                            {passwordRevealed ? <EyeOff size={14} /> : <Eye size={14} />}
                          </button>
                        </div>
                      ) : item.type === 'int' ? (
                        <input
                          type="number"
                          step="1"
                          value={displayValue as number}
                          onChange={e => handleValueChange(item.key, parseInt(e.target.value, 10) || 0)}
                          className="w-full max-w-sm px-2.5 py-1.5 text-xs border border-slate-300 rounded-md bg-white text-slate-700 font-mono focus:border-blue-400 focus:ring-1 focus:ring-blue-200 outline-none transition-colors"
                        />
                      ) : item.type === 'float' ? (
                        <input
                          type="number"
                          step="0.1"
                          min="0"
                          max="1"
                          value={displayValue as number}
                          onChange={e => handleValueChange(item.key, parseFloat(e.target.value) || 0)}
                          className="w-full max-w-sm px-2.5 py-1.5 text-xs border border-slate-300 rounded-md bg-white text-slate-700 font-mono focus:border-blue-400 focus:ring-1 focus:ring-blue-200 outline-none transition-colors"
                        />
                      ) : (
                        <input
                          type="text"
                          value={String(displayValue ?? '')}
                          onChange={e => handleValueChange(item.key, e.target.value)}
                          className="w-full max-w-sm px-2.5 py-1.5 text-xs border border-slate-300 rounded-md bg-white text-slate-700 font-mono focus:border-blue-400 focus:ring-1 focus:ring-blue-200 outline-none transition-colors"
                        />
                      )}

                      {/* Description + Default hint */}
                      <p className="text-[10px] text-slate-400 flex items-center gap-1">
                        {item.description}
                        <span className="text-slate-300">|</span>
                        <span>默认: </span>
                        <code className="text-[10px] bg-slate-100 px-1 rounded text-slate-500">
                          {item.type === 'password' ? '****' : String(defaultValue)}
                        </code>
                      </p>
                    </div>
                  );
                })}
              </div>
            </>
          )}
        </div>
      </div>

      {/* ── Bottom Action Bar ── */}
      <div className="flex items-center justify-between px-4 py-2.5 border-t border-slate-200 bg-slate-50/80 shrink-0">
        <div className="text-[10px] text-slate-400">
          {groups.filter(g => g.modified).length > 0 && (
            <span className="text-amber-600">
              {groups.filter(g => g.modified).length} 个分组有未保存的修改
            </span>
          )}
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={handleRestoreDefaults}
            disabled={saving || refreshing}
            className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-slate-600 hover:bg-slate-200 rounded-md transition-colors disabled:opacity-50"
          >
            <RotateCcw size={13} />
            恢复默认
          </button>
          <button
            onClick={handleSaveAll}
            disabled={saving || refreshing}
            className="flex items-center gap-1.5 px-4 py-1.5 text-xs font-bold text-white bg-blue-600 hover:bg-blue-700 rounded-md transition-colors disabled:opacity-50 shadow-sm"
          >
            {saving ? (
              <>
                <RefreshCw size={13} className="animate-spin" />
                保存中...
              </>
            ) : (
              <>
                <Save size={13} />
                全部保存
              </>
            )}
          </button>
          <button
            onClick={handleRefreshCache}
            disabled={saving || refreshing}
            className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-slate-600 hover:bg-slate-200 rounded-md transition-colors disabled:opacity-50"
          >
            {refreshing ? (
              <>
                <RefreshCw size={13} className="animate-spin" />
                刷新中...
              </>
            ) : (
              <>
                <RefreshCw size={13} />
                刷新缓存
              </>
            )}
          </button>
        </div>
      </div>
    </div>
  );
}
