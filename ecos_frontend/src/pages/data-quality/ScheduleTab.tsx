/**
 * PMO-48-C T14 — 数据质量中心 · 监控调度 Tab
 * @license
 * SPDX-License-Identifier: Apache-2.0
 *
 * 后端 T11 已就绪：
 *   GET    /api/v1/dq/schedules
 *   POST   /api/v1/dq/schedules  (create)
 *   PUT    /api/v1/dq/schedules/{id}  (update)
 *   DELETE /api/v1/dq/schedules/{id}  (logical delete)
 *   POST   /api/v1/dq/schedules/{id}/trigger  (manual trigger)
 *
 * 功能：
 * - 调度列表（name / triggerType 徽章 / cron / rule 数 / enabled 开关 / 最近执行占位）
 * - [+新建] 按钮 → Modal 创建表单（name / triggerType / cron / rule 占位）
 * - [trigger] 按钮调 runDqSchedule
 */

import React, { useCallback, useEffect, useState } from "react";
import {
  CalendarClock,
  Circle,
  FilePlus2,
  Loader2,
  Play,
  RefreshCw,
  Settings2,
  X,
} from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import { showToastGlobal } from "../../components/common/Toast";
import {
  DqScheduleVO,
  createDqSchedule,
  deleteDqSchedule,
  fetchDqSchedules,
  runDqSchedule,
  updateDqSchedule,
} from "./api";

/** trigger 类型选项 */
const TRIGGER_OPTIONS = ["SCHEDULE", "EVENT", "MANUAL"] as const;

/** trigger 徽章样式 */
function triggerBadgeClass(type: string): string {
  switch (type) {
    case "SCHEDULE":
      return "bg-blue-500/15 text-blue-600 dark:text-blue-400 border border-blue-500/30";
    case "EVENT":
      return "bg-purple-500/15 text-purple-600 dark:text-purple-400 border border-purple-500/30";
    case "MANUAL":
      return "bg-slate-500/15 text-slate-600 dark:text-slate-400 border border-slate-400/30";
    default:
      return "bg-slate-500/15 text-slate-500 border border-slate-400/20";
  }
}

/** 从 localStorage 取某调度最近一次触发时间（本波后端无 last_run_at） */
function getLastRunLocal(scheduleId: string): string | null {
  try {
    return localStorage.getItem(`dq_schedule_last_run_${scheduleId}`) ?? null;
  } catch {
    return null;
  }
}

/** 格式化时间 */
function fmtTime(iso?: string | null): string {
  if (!iso) return "—";
  return iso.replace("T", " ").slice(0, 19);
}

export default function ScheduleTab() {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [schedules, setSchedules] = useState<DqScheduleVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [showModal, setShowModal] = useState(false);
  const [modalBusy, setModalBusy] = useState(false);
  // Modal 表单状态
  const [formName, setFormName] = useState("");
  const [formTrigger, setFormTrigger] = useState("SCHEDULE");
  const [formCron, setFormCron] = useState("0 0 * * *");
  const [formEnabled, setFormEnabled] = useState(true);
  const [formMaxRuntime, setFormMaxRuntime] = useState(300);

  const load = useCallback(async () => {
    try {
      const r = await fetchDqSchedules();
      setSchedules(r ?? []);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  /** 立即触发 */
  const handleTrigger = useCallback(
    async (id: string) => {
      setBusyId(id);
      try {
        const r = await runDqSchedule(id);
        if (r.success) {
          localStorage.setItem(`dq_schedule_last_run_${id}`, new Date().toISOString());
          showToastGlobal("success", t("dw.dqRule.schedules.triggerSuccess"));
          await load();
        } else {
          showToastGlobal("error", r.error ?? t("dw.dqRule.schedules.triggerFailed"));
        }
      } finally {
        setBusyId(null);
      }
    },
    [load, t],
  );

  /** enabled 开关切换 */
  const handleToggleEnabled = useCallback(
    async (sched: DqScheduleVO) => {
      setBusyId(sched.id);
      try {
        const r = await updateDqSchedule(sched.id, { enabled: !sched.enabled });
        if (r.success) await load();
      } finally {
        setBusyId(null);
      }
    },
    [load],
  );

  /** 删除（逻辑删除） */
  const handleDelete = useCallback(
    async (id: string) => {
      setBusyId(id);
      try {
        const r = await deleteDqSchedule(id);
        if (r.success) {
          showToastGlobal("success", t("dw.dqRule.schedules.deleteSuccess"));
          await load();
        } else {
          showToastGlobal("error", r.error ?? t("dw.dqRule.schedules.deleteFailed"));
        }
      } finally {
        setBusyId(null);
      }
    },
    [load, t],
  );

  /** Modal 保存 */
  const handleCreate = useCallback(async () => {
    const name = formName.trim();
    if (!name) {
      showToastGlobal("error", t("dw.dqRule.schedules.nameRequired"));
      return;
    }
    setModalBusy(true);
    try {
      const dto: Parameters<typeof createDqSchedule>[0] = {
        name,
        triggerType: formTrigger,
        enabled: formEnabled,
        maxRuntimeSeconds: formMaxRuntime,
        ruleIds: [],
        scopeType: "SYSTEM",
        scopeId: "default",
      };
      if (formTrigger === "SCHEDULE" && formCron.trim()) {
        dto.cronExpression = formCron.trim();
      }
      const r = await createDqSchedule(dto);
      if (r.success) {
        showToastGlobal("success", t("dw.dqRule.schedules.createSuccess"));
        setShowModal(false);
        setFormName("");
        await load();
      } else {
        showToastGlobal("error", r.error ?? t("dw.dqRule.schedules.createFailed"));
      }
    } finally {
      setModalBusy(false);
    }
  }, [formName, formTrigger, formCron, formEnabled, formMaxRuntime, load, t]);

  return (
    <div className="flex-1 min-h-0 flex flex-col space-y-4">
      {/* 头部操作 */}
      <div className="flex items-center gap-2 flex-wrap">
        <button
          onClick={() => {
            setLoading(true);
            void load();
          }}
          className="h-8 px-3 rounded-md flex items-center gap-1.5 text-xs border cursor-pointer transition hover:opacity-80"
          style={{ borderColor: styles.cardBorder, color: styles.cardTextMuted }}
        >
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? "animate-spin" : ""}`} />
          {t("dw.dqRule.refresh")}
        </button>

        <button
          onClick={() => {
            setFormName("");
            setFormTrigger("SCHEDULE");
            setFormCron("0 0 * * *");
            setFormEnabled(true);
            setFormMaxRuntime(300);
            setShowModal(true);
          }}
          className="h-8 px-3 rounded-md flex items-center gap-1.5 text-xs font-medium cursor-pointer transition
            bg-blue-600 text-white hover:bg-blue-700"
        >
          <FilePlus2 className="w-3.5 h-3.5" />
          {t("dw.dqRule.schedules.newSchedule")}
        </button>
      </div>

      {/* 调度列表 */}
      <div className={`flex-1 min-h-0 overflow-y-auto rounded-md border ${styles.cardBorder} ${styles.cardBg}`}>
        {loading && schedules.length === 0 ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin opacity-50" />
          </div>
        ) : schedules.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 gap-2">
            <Settings2 className="w-8 h-8 opacity-30" />
            <span className={`text-xs ${styles.cardTextMuted}`}>{t("dw.dqRule.schedules.noSchedules")}</span>
          </div>
        ) : (
          <div>
            {schedules.map((s) => {
              const isBusy = busyId === s.id;
              const lastRun = getLastRunLocal(s.id);
              const ruleCount = s.ruleIds?.length ?? 0;
              return (
                <div
                  key={s.id}
                  className={`flex items-center gap-3 px-4 py-3 border-b ${styles.cardBorder}
                    hover:${styles.sidebarBg} transition`}
                >
                  {/* triggerType 徽章 */}
                  <span
                    className={`shrink-0 inline-flex items-center gap-1 px-2 py-1 rounded text-[10px] font-bold tracking-wide ${triggerBadgeClass(s.triggerType)}`}
                  >
                    {s.triggerType === "SCHEDULE" && <CalendarClock className="w-3 h-3" />}
                    {s.triggerType === "EVENT" && <Circle className="w-3 h-3 fill-current" />}
                    {t(`dw.dqRule.schedules.trigger.${s.triggerType}`)}
                  </span>

                  {/* 名称 + cron */}
                  <div className="flex-1 min-w-0">
                    <div className={`text-xs font-medium truncate ${styles.cardText}`} title={s.name}>
                      {s.name}
                    </div>
                    {s.triggerType === "SCHEDULE" && s.cronExpression && (
                      <div className="text-[10px] font-mono opacity-50 mt-0.5">{s.cronExpression}</div>
                    )}
                  </div>

                  {/* rule 数 */}
                  <span className="shrink-0 text-[10px] font-mono opacity-50 whitespace-nowrap">
                    {ruleCount} {t("dw.dqRule.schedules.ruleCount")}
                  </span>

                  {/* 最近执行 */}
                  <span className="shrink-0 hidden md:table-cell text-[10px] font-mono opacity-50 whitespace-nowrap">
                    {lastRun ? `↻ ${fmtTime(lastRun)}` : "—"}
                  </span>

                  {/* enabled 开关 */}
                  <button
                    onClick={() => (isBusy ? null : handleToggleEnabled(s))}
                    disabled={isBusy}
                    className="shrink-0 cursor-pointer disabled:opacity-50"
                    title={s.enabled ? t("dw.dqRule.schedules.disable") : t("dw.dqRule.schedules.enable")}
                  >
                    <div
                      className={`w-8 h-4 rounded-full relative transition-colors ${
                        s.enabled ? "bg-emerald-500" : styles.appBg
                      }`}
                    >
                      <div
                        className={`w-3 h-3 rounded-full bg-white absolute top-0.5 transition-all ${
                          s.enabled ? "left-4" : "left-0.5"
                        }`}
                      />
                    </div>
                  </button>

                  {/* trigger 按钮 */}
                  <button
                    disabled={isBusy || !s.enabled}
                    onClick={() => handleTrigger(s.id)}
                    className="shrink-0 h-7 px-2.5 rounded-md flex items-center gap-1 text-[11px] font-medium border cursor-pointer transition
                      border-emerald-500/40 text-emerald-600 dark:text-emerald-400 hover:bg-emerald-500/10
                      disabled:opacity-40 disabled:cursor-not-allowed"
                  >
                    {isBusy ? (
                      <Loader2 className="w-3.5 h-3.5 animate-spin" />
                    ) : (
                      <Play className="w-3.5 h-3.5" />
                    )}
                    {t("dw.dqRule.schedules.trigger")}
                  </button>

                  {/* 删除 */}
                  <button
                    disabled={isBusy}
                    onClick={() => handleDelete(s.id)}
                    className="shrink-0 h-7 px-2 rounded-md text-[11px] border cursor-pointer transition
                      border-red-500/30 text-red-500 dark:text-red-400 hover:bg-red-500/10 disabled:opacity-40"
                    title={t("dw.dqRule.schedules.delete")}
                  >
                    <X className="w-3.5 h-3.5" />
                  </button>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* 新建 Modal */}
      {showModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" onClick={() => !modalBusy && setShowModal(false)}>
          <div
            onClick={(e) => e.stopPropagation()}
            className={`rounded-lg border ${styles.cardBorder} ${styles.cardBg} w-[420px] max-w-full p-5 shadow-xl`}
          >
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-sm font-bold">{t("dw.dqRule.schedules.newSchedule")}</h3>
              <button
                onClick={() => !modalBusy && setShowModal(false)}
                className="opacity-50 hover:opacity-100 cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="space-y-3">
              {/* 名称 */}
              <div>
                <label className="text-[11px] font-medium opacity-60 block mb-1">
                  {t("dw.dqRule.schedules.name")} *
                </label>
                <input
                  autoFocus
                  value={formName}
                  onChange={(e) => setFormName(e.target.value)}
                  placeholder={t("dw.dqRule.schedules.name")}
                  className={`w-full h-9 rounded-md px-3 text-xs border outline-none ${styles.cardBorder} ${styles.inputBg} ${styles.cardText}`}
                />
              </div>

              {/* triggerType */}
              <div>
                <label className="text-[11px] font-medium opacity-60 block mb-1">
                  {t("dw.dqRule.schedules.trigger")}
                </label>
                <select
                  value={formTrigger}
                  onChange={(e) => setFormTrigger(e.target.value)}
                  className={`w-full h-9 rounded-md px-2 text-xs border ${styles.cardBorder} ${styles.inputBg} ${styles.cardText}`}
                >
                  {TRIGGER_OPTIONS.map((tp) => (
                    <option key={tp} value={tp}>
                      {t(`dw.dqRule.schedules.trigger.${tp}`)}
                    </option>
                  ))}
                </select>
              </div>

              {/* cron (仅 SCHEDULE) */}
              {formTrigger === "SCHEDULE" && (
                <div>
                  <label className="text-[11px] font-medium opacity-60 block mb-1">
                    {t("dw.dqRule.schedules.cron")}
                  </label>
                  <input
                    value={formCron}
                    onChange={(e) => setFormCron(e.target.value)}
                    placeholder="0 0 * * *"
                    className={`w-full h-9 rounded-md px-3 text-xs font-mono border outline-none ${styles.cardBorder} ${styles.inputBg} ${styles.cardText}`}
                  />
                </div>
              )}

              {/* maxRuntimeSeconds */}
              <div>
                <label className="text-[11px] font-medium opacity-60 block mb-1">
                  {t("dw.dqRule.schedules.maxRuntime")} (s)
                </label>
                <input
                  type="number"
                  min={60}
                  max={3600}
                  value={formMaxRuntime}
                  onChange={(e) => setFormMaxRuntime(Number(e.target.value) || 300)}
                  className={`w-full h-9 rounded-md px-3 text-xs border outline-none ${styles.cardBorder} ${styles.inputBg} ${styles.cardText}`}
                />
              </div>

              {/* rule 选择（占位） */}
              <div>
                <label className="text-[11px] font-medium opacity-60 block mb-1">
                  {t("dw.dqRule.schedules.ruleCount")}
                </label>
                <div className="h-9 rounded-md px-3 text-xs opacity-40 flex items-center font-mono">
                  ---{t("dw.dqRule.schedules.rulePlaceholder")}---
                </div>
              </div>

              {/* enabled */}
              <div className="flex items-center gap-2">
                <input
                  type="checkbox"
                  id="schedule-enabled"
                  checked={formEnabled}
                  onChange={(e) => setFormEnabled(e.target.checked)}
                  className="w-3.5 h-3.5"
                />
                <label htmlFor="schedule-enabled" className={`text-xs ${styles.cardText}`}>
                  {t("dw.dqRule.schedules.enabled")}
                </label>
              </div>
            </div>

            {/* 按钮 */}
            <div className="flex items-center justify-end gap-2 mt-5">
              <button
                onClick={() => setShowModal(false)}
                disabled={modalBusy}
                className="h-8 px-3 rounded-md text-xs border cursor-pointer transition hover:opacity-80"
                style={{ borderColor: styles.cardBorder, color: styles.cardTextMuted }}
              >
                {t("dw.dqRule.cancel")}
              </button>
              <button
                onClick={() => void handleCreate()}
                disabled={modalBusy || !formName.trim()}
                className="h-8 px-4 rounded-md text-xs font-medium cursor-pointer transition
                  bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-40 disabled:cursor-not-allowed flex items-center gap-1.5"
              >
                {modalBusy && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
                {t("dw.dqRule.schedules.create")}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
