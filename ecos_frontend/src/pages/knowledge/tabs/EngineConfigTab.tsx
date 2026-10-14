// 引擎配置 Tab — scope 词表单源对齐后端 KnowledgeConfigAggregator.SCOPES {knowledge, cognitive, all}
// GET  /api/v1/knowledge/engine-config?scope= → {scope, config: Map<String,String>, count}
// PUT  /api/v1/knowledge/engine-config        → body {scope, entries}；all 为读侧聚合视图，写侧后端直接拒绝
// 宿主是 workspace.knowledge.KnowledgeWorkbenchController（gateway :8080 fat-jar 内），不是 kb-engine :18086，
// dev 直连前缀已在 config/route-manifest.ts 归到 gateway，否则命中 kb-engine 必 404。
import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Cpu, Database, Layers, Loader2, RefreshCw, Save,
} from "lucide-react";
import { useTheme } from "../../../components/ThemeContext";
import { useLanguage } from "../../../components/LanguageContext";
import { knowledgeApi } from "../services/knowledgeApi";
import type {
  EngineConfig, EngineConfigScope, WritableEngineConfigScope,
} from "../typesAndConstants";

interface TabProps { showToast?: (type: 'success' | 'info' | 'error', msg: string) => void }

/** 后端读侧上游取数失败时注入的标记键 — 不是配置项，须从列表剔除并转成降级提示 */
const REMOTE_FAILED_KEY = "_remote_failed";

type FieldType = "boolean" | "number" | "string";

/** 显示元数据，key 与后端 sys_config 实键名逐字一致；未登记的键回落裸键名，不伪造字段 */
const FIELD_META: Record<string, { i18nKey: string; type: FieldType }> = {
  // scope=knowledge ← ecos_control.sys_config group 'knowledge'
  "extract.allow_direct_upload": { i18nKey: "knowledge.engine_config.field.allowDirectUpload", type: "boolean" },
  "extract.page_limit": { i18nKey: "knowledge.engine_config.field.pageLimit", type: "number" },
  "extract.max_pages": { i18nKey: "knowledge.engine_config.field.maxPages", type: "number" },
  "extract.periodic_enabled": { i18nKey: "knowledge.engine_config.field.periodicEnabled", type: "boolean" },
  "knowledge.graph.defaultDomain": { i18nKey: "knowledge.engine_config.field.defaultDomain", type: "string" },
  "knowledge.graph.maxNeighborDegree": { i18nKey: "knowledge.engine_config.field.maxNeighborDegree", type: "number" },
  "knowledge.index.autoSyncEnabled": { i18nKey: "knowledge.engine_config.field.autoSyncEnabled", type: "boolean" },
  "knowledge.index.batchSize": { i18nKey: "knowledge.engine_config.field.batchSize", type: "number" },
  "knowledge.lineage.maxDepth": { i18nKey: "knowledge.engine_config.field.lineageMaxDepth", type: "number" },
  "knowledge.rag.model": { i18nKey: "knowledge.engine_config.field.model", type: "string" },
  "knowledge.rag.similarityThreshold": { i18nKey: "knowledge.engine_config.field.similarityThreshold", type: "number" },
  "knowledge.rag.topK": { i18nKey: "knowledge.engine_config.field.ragTopK", type: "number" },
  // scope=cognitive ← aiming CognitiveConfigController DEFAULTS（group 'cognitive-engine'）
  "prompt-compiler.topK": { i18nKey: "knowledge.engine_config.field.promptTopK", type: "number" },
  "prompt-compiler.vectorModel": { i18nKey: "knowledge.engine_config.field.vectorModel", type: "string" },
  "agent-mesh.routingStrategy": { i18nKey: "knowledge.engine_config.field.routingStrategy", type: "string" },
  "guardrails.piiDetectionEnabled": { i18nKey: "knowledge.engine_config.field.piiDetection", type: "boolean" },
  "guardrails.hallucinationCheckEnabled": { i18nKey: "knowledge.engine_config.field.hallucinationCheck", type: "boolean" },
  "action-bridge.autoExecuteEnabled": { i18nKey: "knowledge.engine_config.field.autoExecute", type: "boolean" },
  "action-bridge.matchConfidenceThreshold": { i18nKey: "knowledge.engine_config.field.matchConfidence", type: "number" },
};

const SCOPES: { scope: EngineConfigScope; Icon: typeof Database; i18nKey: string }[] = [
  { scope: "knowledge", Icon: Database, i18nKey: "knowledge.engine_config.scope.knowledge" },
  { scope: "cognitive", Icon: Cpu, i18nKey: "knowledge.engine_config.scope.cognitive" },
  { scope: "all", Icon: Layers, i18nKey: "knowledge.engine_config.scope.all" },
];

type BannerType = "error" | "warning" | "success" | "info";

export default function EngineConfigTab({ showToast }: TabProps = {}) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [scope, setScope] = useState<EngineConfigScope>("knowledge");
  const [cfg, setCfg] = useState<EngineConfig>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [changes, setChanges] = useState<Record<string, string>>({});
  const [banner, setBanner] = useState<{ type: BannerType; text: string } | null>(null);

  const readOnly = scope === "all";

  const bannerClass = (type: BannerType): string => {
    switch (type) {
      case "error": return `${styles.dangerBorder} ${styles.dangerBg} ${styles.dangerText}`;
      case "warning": return `${styles.warningBorder} ${styles.warningBg} ${styles.warningText}`;
      case "success": return `${styles.successBorder} ${styles.successBg} ${styles.successText}`;
      default: return `${styles.infoBorder} ${styles.infoBg} ${styles.infoText}`;
    }
  };

  // 加载当前 scope — 失败即显式报错并清空，绝不以空配置冒充"没有项"
  const loadScope = useCallback(async (s: EngineConfigScope) => {
    setLoading(true);
    setChanges({});
    try {
      const res = await knowledgeApi.fetchEngineConfig(s);
      const next = (res.config ?? {}) as EngineConfig;
      setCfg(next);
      const remoteFailed = next[REMOTE_FAILED_KEY];
      setBanner(remoteFailed === undefined
        ? null
        : { type: "warning", text: `${t("knowledge.engine_config.remote_failed")} ${String(remoteFailed)}` });
    } catch (e) {
      setCfg({});
      setBanner({ type: "error", text: `${t("knowledge.engine_config.load_failed")} ${String(e)}` });
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => {
    void loadScope(scope);
  }, [scope, loadScope]);

  // 后端返回什么键就渲染什么键（服务端驱动），按字典序稳定排列
  const rows = useMemo(
    () => Object.keys(cfg).filter(k => k !== REMOTE_FAILED_KEY).sort(),
    [cfg],
  );

  const displayFor = (k: string): string => {
    const v = cfg[k];
    return v === null || v === undefined ? "" : String(v);
  };

  const dirty = Object.keys(changes).length > 0;

  const updateField = (k: string, raw: string) => {
    const original = displayFor(k);
    setChanges(prev => {
      const next = { ...prev };
      if (raw === original) delete next[k];
      else next[k] = raw;
      return next;
    });
  };

  const save = async () => {
    if (readOnly || !dirty) return;
    setSaving(true);
    try {
      // 后端只回 {updated}，不含版本/时间戳，前端不得合成
      const { updated } = await knowledgeApi.saveEngineConfig(
        scope as WritableEngineConfigScope,
        { ...changes },
      );
      setChanges({});
      await loadScope(scope);
      if (updated <= 0) {
        // 0 行落库不是成功：后端语义上没写入任何东西
        setBanner({ type: "warning", text: t("knowledge.engine_config.save_noop") });
        showToast?.('info', t("knowledge.engine_config.save_noop"));
        return;
      }
      setBanner({ type: "success", text: `${t("knowledge.engine_config.save_success")} ${updated}` });
      showToast?.('success', t("knowledge.engine_config.save_success"));
    } catch (e) {
      showToast?.('error', `${t("knowledge.engine_config.save_failed")} ${String(e)}`);
      setBanner({ type: "error", text: `${t("knowledge.engine_config.save_failed")} ${String(e)}` });
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
          <p className={`text-xs mt-1 ${styles.cardTextMuted}`}>{t("knowledge.engine_config.subtitle")}</p>
        </div>

        {/* scope 切换栏 — 与后端 SCOPES 同集合 */}
        <div className={`border rounded-lg flex items-center gap-1 p-1 ${styles.cardBg} ${styles.cardBorder}`}>
          {SCOPES.map(({ scope: s, Icon, i18nKey }) => (
            <button type="button"
              key={s}
              onClick={() => setScope(s)}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium transition-colors ${
                scope === s
                  ? `${styles.accentBg} ${styles.accentSolidText}`
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
          <button type="button"
            onClick={() => void loadScope(scope)}
            className={`px-2 py-1 rounded border text-[11px] ${styles.cardBorder} hover:opacity-70`}
            title={t("knowledge.common.refresh")}
          >
            <RefreshCw className="w-3.5 h-3.5" />
          </button>
        </div>

        {banner && (
          <div className={`rounded-md border px-3 py-2 text-xs ${bannerClass(banner.type)}`}>
            {banner.text}
          </div>
        )}

        {/* 配置面板 — 行集来自服务端响应 */}
        <div className={`border rounded-lg overflow-hidden ${styles.cardBg} ${styles.cardBorder}`}>
          <div className="flex items-center justify-between px-4 py-2.5 border-b" style={{ borderColor: styles.cardBorder }}>
            <div className="text-sm font-medium">{t("knowledge.engine_config.field_list_hint")}</div>
            <div className={`text-[10px] font-mono ${styles.cardTextMuted}`}>
              {t("knowledge.engine_config.item_count", { count: rows.length })}
              {readOnly ? ` · ${t("knowledge.engine_config.readonly_hint")}` : ""}
            </div>
          </div>
          {loading ? (
            <div className="flex items-center gap-2 px-4 py-6 text-xs opacity-70">
              <Loader2 className="w-3.5 h-3.5 animate-spin" />
              {t("knowledge.engine_config.loading")}
            </div>
          ) : rows.length === 0 ? (
            <div className="px-4 py-6 text-xs opacity-70">{t("knowledge.engine_config.empty_hint")}</div>
          ) : (
            <div className="divide-y" style={{ borderColor: styles.cardBorder }}>
              {rows.map(k => {
                const meta = FIELD_META[k];
                const type = meta?.type ?? "string";
                const original = displayFor(k);
                const isChanged = k in changes;
                const current = isChanged ? changes[k] : original;
                return (
                  <div key={k} className="grid grid-cols-1 md:grid-cols-3 gap-3 items-center px-4 py-2.5">
                    <div className="text-xs">
                      <div>{meta ? t(meta.i18nKey) : k}</div>
                      <div className={`text-[10px] font-mono ${styles.cardTextMuted}`}>{k}</div>
                    </div>
                    <div className="col-span-2 flex items-center gap-2">
                      {type === "boolean" ? (
                        <label className="flex items-center gap-2 cursor-pointer select-none">
                          <input
                            type="checkbox"
                            disabled={readOnly}
                            checked={current === "true"}
                            onChange={e => updateField(k, e.target.checked ? "true" : "false")}
                            className="w-4 h-4 cursor-pointer disabled:cursor-not-allowed"
                          />
                          <span className={`text-[11px] font-mono ${styles.cardTextMuted}`}>{current}</span>
                        </label>
                      ) : (
                        <input
                          type={type === "number" ? "number" : "text"}
                          step={type === "number" ? "any" : undefined}
                          disabled={readOnly}
                          value={current}
                          onChange={e => updateField(k, e.target.value)}
                          className={`w-full px-2.5 py-1.5 text-xs rounded-md border focus:outline-none disabled:opacity-60 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}
                        />
                      )}
                      {isChanged && !readOnly && (
                        <button type="button"
                          onClick={() => updateField(k, original)}
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
          )}
        </div>

        {/* 操作栏 */}
        <div className="flex flex-wrap items-center justify-end gap-2">
          {dirty && !saving && (
            <span className={`text-[11px] font-mono ${styles.cardTextMuted}`}>
              {t("knowledge.engine_config.dirty_hint")} ({Object.keys(changes).length})
            </span>
          )}
          <button type="button"
            onClick={() => void save()}
            disabled={readOnly || !dirty || saving}
            className={`flex items-center gap-1.5 px-4 py-2 rounded-md text-xs font-semibold disabled:opacity-40 transition-opacity ${styles.accentBg} ${styles.accentSolidText}`}
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
