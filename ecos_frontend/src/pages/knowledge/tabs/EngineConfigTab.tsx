// 引擎配置 Tab（PMO-55 批次 D）
// 4 子 Tab: pgvector / neo4j / llm / task
// 统一走 /api/v1/knowledge/engine-config?scope=（fallback /api/v1/cognitive/config sys_config）
import { useCallback, useEffect, useMemo, useState } from "react";
import {
  ArrowUpFromLine, Cpu, Database, FileJson, ListChecks, Loader2, RefreshCw, Save,
} from "lucide-react";
import { useTheme } from "../../../components/ThemeContext";
import { useLanguage } from "../../../components/LanguageContext";
import { knowledgeApi } from "../services/knowledgeApi";

type Scope = "pgvector" | "neo4j" | "llm" | "task" | "extract";

interface EngineConfigWrapper {
  config: Record<string, unknown>;
  version: number;
  updatedAt: string;
}

interface TabProps { showToast?: (type: 'success' | 'info' | 'error', msg: string) => void }

const SCOPES: { scope: Scope; Icon: typeof Database; i18nKey: string }[] = [
  { scope: "pgvector", Icon: Database, i18nKey: "knowledge.engine_config.scope.pgvector" },
  { scope: "neo4j", Icon: FileJson, i18nKey: "knowledge.engine_config.scope.neo4j" },
  { scope: "llm", Icon: Cpu, i18nKey: "knowledge.engine_config.scope.llm" },
  { scope: "task", Icon: ListChecks, i18nKey: "knowledge.engine_config.scope.task" },
  { scope: "extract", Icon: ArrowUpFromLine, i18nKey: "knowledge.engine_config.scope.extract" },
];

// 各 scope 的默认 schema 字段（用于编辑 hint + 类型校验）
const CONFIG_FIELDS: Record<Scope, { key: string; i18nKey: string; type: "number" | "string" | "boolean" }[]> = {
  pgvector: [
    { key: "dimensions", i18nKey: "knowledge.engine_config.field.dimensions", type: "number" },
    { key: "indexType", i18nKey: "knowledge.engine_config.field.indexType", type: "string" },
    { key: "maintenanceWorkMem", i18nKey: "knowledge.engine_config.field.maintenanceWorkMem", type: "number" },
    { key: "hnswEfConstruction", i18nKey: "knowledge.engine_config.field.hnswEfConstruction", type: "number" },
    { key: "hnswM", i18nKey: "knowledge.engine_config.field.hnswM", type: "number" },
  ],
  neo4j: [
    { key: "uri", i18nKey: "knowledge.engine_config.field.uri", type: "string" },
    { key: "db", i18nKey: "knowledge.engine_config.field.db", type: "string" },
    { key: "maxConnectionPoolSize", i18nKey: "knowledge.engine_config.field.maxConnectionPoolSize", type: "number" },
    { key: "readOnlyTimeoutSeconds", i18nKey: "knowledge.engine_config.field.readOnlyTimeoutSeconds", type: "number" },
    { key: "resultNodeLimit", i18nKey: "knowledge.engine_config.field.resultNodeLimit", type: "number" },
  ],
  llm: [
    { key: "provider", i18nKey: "knowledge.engine_config.field.provider", type: "string" },
    { key: "model", i18nKey: "knowledge.engine_config.field.model", type: "string" },
    { key: "temperature", i18nKey: "knowledge.engine_config.field.temperature", type: "number" },
    { key: "maxTokens", i18nKey: "knowledge.engine_config.field.maxTokens", type: "number" },
    { key: "timeoutMs", i18nKey: "knowledge.engine_config.field.timeoutMs", type: "number" },
  ],
  task: [
    { key: "threadPoolSize", i18nKey: "knowledge.engine_config.field.threadPoolSize", type: "number" },
    { key: "maxConcurrency", i18nKey: "knowledge.engine_config.field.maxConcurrency", type: "number" },
    { key: "retryCount", i18nKey: "knowledge.engine_config.field.retryCount", type: "number" },
    { key: "deadLetterRetentionDays", i18nKey: "knowledge.engine_config.field.deadLetterRetentionDays", type: "number" },
    { key: "heartbeatIntervalSeconds", i18nKey: "knowledge.engine_config.field.heartbeatIntervalSeconds", type: "number" },
  ],
  // K1 知识抽取 — key 与后端 extract 配置键名一致（snake_case）
  extract: [
    { key: "allow_direct_upload", i18nKey: "knowledge.engine_config.field.allowDirectUpload", type: "boolean" },
    { key: "page_limit", i18nKey: "knowledge.engine_config.field.pageLimit", type: "number" },
    { key: "max_pages", i18nKey: "knowledge.engine_config.field.maxPages", type: "number" },
    { key: "periodic_enabled", i18nKey: "knowledge.engine_config.field.periodicEnabled", type: "boolean" },
  ],
};

interface FieldChange {
  key: string;
  value: string; // input 值，保存时按字段类型转换
}

export default function EngineConfigTab({ showToast }: TabProps = {}) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [scope, setScope] = useState<Scope>("pgvector");
  const [cfg, setCfg] = useState<Record<string, unknown>>({});
  const [meta, setMeta] = useState<{ version: number; updatedAt: string } | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [changes, setChanges] = useState<FieldChange[]>([]);
  const [banner, setBanner] = useState<{ type: "info" | "success" | "error"; text: string } | null>(null);
  const [afterSaveCheck, setAfterSaveCheck] = useState(false); // 是否已保存过（false 时 dirty 检测不触发）

  // 加载当前 scope 的 config
  const loadScope = useCallback(async (s: Scope) => {
    setLoading(true);
    setChanges([]);
    setAfterSaveCheck(false);
    try {
      const res: EngineConfigWrapper = await knowledgeApi.fetchEngineConfig(s);
      setCfg((res.config as Record<string, unknown>) || {});
      setMeta({ version: res.version ?? 1, updatedAt: res.updatedAt ?? "" });
      setBanner(null);
    } catch (e) {
      setBanner({ type: "error", text: `${t("knowledge.engine_config.load_failed")} ${String(e)}` });
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    void loadScope(scope);
  }, [scope, loadScope]);

  const fields = useMemo(() => CONFIG_FIELDS[scope], [scope]);

  const displayFor = (k: string, v: unknown): string => {
    if (v === null || v === undefined) return "";
    return String(v);
  };

  /** 按字段类型把 input 原始值转回真实配置值（number → Number，boolean → 布尔，其余原样） */
  const toConfigValue = (type: "number" | "string" | "boolean", raw: string): unknown => {
    if (type === "number") return Number(raw);
    if (type === "boolean") return raw === "true";
    return raw;
  };

  const changedValue = (k: string): string => {
    const c = changes.find(x => x.key === k);
    return c?.value ?? "";
  };
  const dirty = changes.length > 0;

  const updateField = (k: string, raw: string) => {
    const original = displayFor(k, cfg[k]);
    setChanges(prev => {
      const next = prev.filter(c => c.key !== k);
      if (raw !== original) next.push({ key: k, value: raw });
      return next;
    });
  };

  const resetField = (k: string) => setChanges(prev => prev.filter(c => c.key !== k));

  const save = async () => {
    setSaving(true);
    try {
      // 按字段类型反序列化（number 强制 number，boolean 转布尔）
      const newCfg: Record<string, unknown> = { ...cfg };
      for (const chg of changes) {
        const f = fields.find(fd => fd.key === chg.key);
        newCfg[chg.key] = toConfigValue(f?.type ?? "string", chg.value);
      }
      const updated: EngineConfigWrapper = await knowledgeApi.saveEngineConfig(scope, newCfg);
      setCfg((updated?.config as Record<string, unknown>) || {});
      setMeta({ version: updated?.version ?? 1, updatedAt: updated?.updatedAt ?? "" });
      setChanges([]);
      setAfterSaveCheck(true);
      setBanner({
        type: "success",
        text: `${t("knowledge.engine_config.save_success")} v${updated?.version ?? 1}`,
      });
    } catch (e) {
      // 失败 toast 替 500 白屏（C-3 要求）
      showToast?.('error', `${t("knowledge.engine_config.save_failed")} ${String(e)}`);
      setBanner({ type: "error", text: String(e) });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="h-full overflow-y-auto">
      <div className="space-y-6 p-6">
        {/* 头部 */}
        <div>
          <h2 className={`text-xl font-bold ${styles.cardText}`}>{t("knowledge.engine_config.title")}</h2>
          <p className="text-xs mt-1 opacity-70">{t("knowledge.engine_config.subtitle")}</p>
        </div>

        {/* scope 切换栏 — 4 scope sub-section 用 i18nKey 渲染 */}
        <div className={`border rounded-lg flex items-center gap-1 p-1 ${styles.cardBg} ${styles.cardBorder}`}>
          {SCOPES.map(({ scope: s, Icon, i18nKey }) => (
            <button
              key={s}
              onClick={() => setScope(s)}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium transition-colors ${
                scope === s
                  ? `${styles.accentBg} text-white`
                  : `border ${styles.inputBorder} hover:opacity-80`
              }`}
            >
              <Icon className="w-3.5 h-3.5" />
              {t(i18nKey)}
            </button>
          ))}
          <div className="flex-1" />
          {loading
            ? <Loader2 className="w-4 h-4 animate-spin opacity-60" />
            : null}
          <button
            onClick={() => void loadScope(scope)}
            className={`px-2 py-1 rounded border text-[11px] ${styles.cardBorder} hover:opacity-70`}
            title={t("knowledge.common.refresh")}
          >
            <RefreshCw className="w-3.5 h-3.5" />
          </button>
        </div>

        {banner && (
          <div
            className={`rounded-md border px-3 py-2 text-xs ${
              banner.type === "error"
                ? "border-red-300 bg-red-100 text-red-800"
                : "border-green-300 bg-green-100 text-green-800"
            }`}
          >
            {banner.text}
          </div>
        )}

        {/* 配置面板 */}
        <div className={`border rounded-lg overflow-hidden ${styles.cardBg} ${styles.cardBorder}`}>
          <div className="flex items-center justify-between px-4 py-2.5 border-b" style={{ borderColor: styles.cardBorder }}>
            <div className="text-sm font-medium">{t("knowledge.engine_config.field_list_hint")}</div>
            {meta && (
              <div className="text-[10px] font-mono opacity-60">
                v{meta.version} · {new Date(meta.updatedAt).toLocaleString()}
              </div>
            )}
          </div>
          <div className="divide-y" style={{ borderColor: styles.cardBorder }}>
            {fields.map(f => {
              const isChanged = changes.some(c => c.key === f.key);
              return (
                  <div key={f.key} className="grid grid-cols-1 md:grid-cols-3 gap-3 items-center px-4 py-2.5">
                  <div className="text-xs">
                    <div>{t(f.i18nKey)}</div>
                    <div className="text-[10px] font-mono opacity-50">{f.key}</div>
                  </div>
                  <div className="col-span-2 flex items-center gap-2">
                    {f.type === "boolean" ? (
                      <label className="flex items-center gap-2 cursor-pointer select-none">
                        <input
                          type="checkbox"
                          checked={
                            isChanged
                              ? changedValue(f.key) === "true"
                              : cfg[f.key] === true || cfg[f.key] === "true"
                          }
                          onChange={e => updateField(f.key, e.target.checked ? "true" : "false")}
                          className="w-4 h-4 cursor-pointer"
                        />
                        <span className="text-[11px] font-mono opacity-70">
                          {isChanged
                            ? changedValue(f.key)
                            : String(cfg[f.key] ?? "")}
                        </span>
                      </label>
                    ) : (
                      <input
                        type={f.type === "number" ? "number" : "text"}
                        value={isChanged ? changedValue(f.key) : displayFor(f.key, cfg[f.key])}
                        onChange={e => updateField(f.key, e.target.value)}
                        className={`w-full px-2.5 py-1.5 text-xs rounded-md border focus:outline-none ${styles.inputBg} ${styles.inputBorder}`}
                      />
                    )}
                    {isChanged && (
                      <button
                        onClick={() => resetField(f.key)}
                        className="text-[11px] px-2 py-1 rounded border hover:opacity-70"
                        style={{ borderColor: styles.cardBorder }}
                        title={t("knowledge.engine_config.reset")}
                      >
                        {t("knowledge.engine_config.reset")}
                      </button>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* 操作栏 */}
        <div className="flex flex-wrap items-center justify-end gap-2">
          {dirty && !saving && (
            <span className="text-[11px] font-mono opacity-70">
              {t("knowledge.engine_config.dirty_hint")} ({changes.length})
            </span>
          )}
          <button
            onClick={() => void save()}
            disabled={!dirty || saving || !afterSaveCheck && !dirty}
            className={`flex items-center gap-1.5 px-4 py-2 rounded-md text-xs font-semibold text-white disabled:opacity-40 transition-opacity ${styles.accentBg}`}
          >
            {saving
              ? <Loader2 className="w-3.5 h-3.5 animate-spin" />
              : <Save className="w-3.5 h-3.5" />}
            {t("knowledge.engine_config.save")}
          </button>
        </div>
      </div>
    </div>
  );
}
