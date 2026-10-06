/**
 * SecurityConfigPanel — 右上角高阶安全面板
 * ECOS Phase 1 P1-1: Security profile configuration
 * @license Apache-2.0
 */

import React, { useState, useEffect } from "react";
import {
  Shield,
  Lock,
  Monitor,
  FileSearch,
  Box,
  Save,
  Loader2,
  AlertTriangle,
  CheckCircle2,
  Key,
  Clock,
  Users,
  Globe,
} from "lucide-react";
import { fetchSecurityProfile, updateSecurityProfile, SecurityProfile } from "../api";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";
import { useDict } from "../hooks/useDict";

export default function SecurityConfigPanel() {
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const { getLabel: getClLabel, getColor: getClColor } = useDict("clearance_level", locale);
  const { getLabel: getAuditLabel, getOptions: auditOptions } = useDict("audit_mode", locale);

  const [profile, setProfile] = useState<SecurityProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  // Form state
  const [clearanceLevel, setClearanceLevel] = useState(3);
  const [workstation, setWorkstation] = useState("");
  const [auditMode, setAuditMode] = useState<string>("detailed");
  const [sandboxMandatory, setSandboxMandatory] = useState(false);
  // Password policy
  const [passwordMinLength, setPasswordMinLength] = useState(8);
  const [mfaEnabled, setMfaEnabled] = useState(false);
  const [passwordExpireDays, setPasswordExpireDays] = useState(90);
  // Session management
  const [sessionTimeout, setSessionTimeout] = useState(30);
  const [maxConcurrentSessions, setMaxConcurrentSessions] = useState(3);

  // Scope state
  const [scopeType, setScopeType] = useState<string>("GLOBAL");
  const [scopeId, setScopeId] = useState<string>("");

  // Load profile on mount
  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        setLoading(true);
        setError(null);
        const params = scopeType !== "GLOBAL" && scopeId
          ? `?scopeType=${scopeType}&scopeId=${scopeId}`
          : "";
        const data = await fetchSecurityProfile(params);
        if (cancelled) return;
        setProfile(data);
        setClearanceLevel(data.clearanceLevel ?? 3);
        setWorkstation(data.linkedWorkstation ?? "");
        setAuditMode((data.auditMode as string) ?? "detailed");
        setSandboxMandatory(data.sandboxMandatory ?? false);
        // Password policy
        setPasswordMinLength(data.passwordMinLength ?? 8);
        setMfaEnabled(data.mfaEnabled ?? false);
        setPasswordExpireDays(data.passwordExpireDays ?? 90);
        // Session management
        setSessionTimeout(data.sessionTimeout ?? 30);
        setMaxConcurrentSessions(data.maxConcurrentSessions ?? 3);
      } catch (e: any) {
        if (!cancelled) {
          setError(e.message || "加载安全配置失败");
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    load();
    return () => { cancelled = true; };
  }, [scopeType, scopeId]);

  const handleSave = async () => {
    try {
      setSaving(true);
      setError(null);
      setSuccessMsg(null);
      const updated = await updateSecurityProfile({
        clearanceLevel,
        linkedWorkstation: workstation,
        auditMode,
        sandboxMandatory,
        // Password policy
        passwordMinLength,
        mfaEnabled,
        passwordExpireDays,
        // Session management
        sessionTimeout,
        maxConcurrentSessions,
        // Scope
        scopeType: scopeType !== "GLOBAL" ? scopeType : undefined,
        tenantId: scopeType === "TENANT" ? scopeId : undefined,
        orgId: scopeType === "ORG" ? scopeId : undefined,
      });
      setProfile(updated);
      setSuccessMsg(locale === "zh" ? "安全配置已保存，通信通道双向热重连完成！" : "Security configuration saved. Connection hot-reloaded successfully!");
      setTimeout(() => setSuccessMsg(null), 4000);
    } catch (e: any) {
      setError(e.message || "保存安全配置失败");
    } finally {
      setSaving(false);
    }
  };

  // ── Loading State ──────────────────────────────────────────
  if (loading) {
    return (
      <div className={`flex items-center justify-center h-full ${styles.appBg}`}>
        <div className="flex flex-col items-center gap-4">
          <Loader2 className="w-10 h-10 text-indigo-500 animate-spin" />
          <span className={`text-sm ${styles.muted}`}>
            {locale === "zh" ? "正在加载安全配置..." : "Loading security configuration..."}
          </span>
        </div>
      </div>
    );
  }

  // ── Render ─────────────────────────────────────────────────
  return (
    <div className={`h-full overflow-y-auto ${styles.appBg} ${styles.appText}`}>
      <div className="max-w-3xl mx-auto p-6 space-y-6">

        {/* Header */}
        <div className="space-y-1">
          <h1 className="text-xl font-bold flex items-center gap-2.5">
            <Shield className="w-6 h-6 text-indigo-500" />
            {locale === "zh" ? "高阶安全配置" : "Advanced Security Configuration"}
          </h1>
          <p className={`text-sm ${styles.muted}`}>
            {locale === "zh"
              ? "配置访问准入等级、审计模式与沙箱保护策略。所有变更将实时写入不可篡改审计账本。"
              : "Configure access clearance level, audit mode, and sandbox protection policies. All changes are immutably logged."}
          </p>
        </div>

        {/* Error Banner */}
        {error && (
          <div className="flex items-start gap-3 p-4 rounded-lg bg-red-500/10 border border-red-500/30 text-red-400">
            <AlertTriangle className="w-5 h-5 shrink-0 mt-0.5" />
            <div>
              <p className="font-semibold text-sm">{locale === "zh" ? "操作失败" : "Operation Failed"}</p>
              <p className="text-sm opacity-80">{error}</p>
            </div>
          </div>
        )}

        {/* Success Banner */}
        {successMsg && (
          <div className="flex items-start gap-3 p-4 rounded-lg bg-green-500/10 border border-green-500/30 text-green-400">
            <CheckCircle2 className="w-5 h-5 shrink-0 mt-0.5" />
            <p className="text-sm">{successMsg}</p>
          </div>
        )}

        {/* ── Scope Selector ──────────────────────────── */}
        <div className={`p-5 rounded-xl border ${styles.cardBorder} ${styles.cardBg} space-y-4`}>
          <div className="flex items-center gap-2.5">
            <Globe className="w-5 h-5 text-emerald-500" />
            <h2 className="font-semibold text-base">
              {locale === "zh" ? "作用域" : "Scope"}
            </h2>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className={`text-xs font-medium ${styles.muted}`}>
                {locale === "zh" ? "作用域类型" : "Scope Type"}
              </label>
              <select value={scopeType} onChange={(e) => { setScopeType(e.target.value); setScopeId(""); }}
                className={`w-full px-3 py-2 rounded-lg border ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}>
                <option value="GLOBAL">{locale==="zh"?"全局":"Global"}</option>
                <option value="TENANT">{locale==="zh"?"租户":"Tenant"}</option>
                <option value="ORG">{locale==="zh"?"机构":"Organization"}</option>
                <option value="ROLE">{locale==="zh"?"角色":"Role"}</option>
                <option value="USER">{locale==="zh"?"用户":"User"}</option>
              </select>
            </div>
            {scopeType !== "GLOBAL" && (
              <div>
                <label className={`text-xs font-medium ${styles.muted}`}>
                  {locale === "zh" ? "作用域ID" : "Scope ID"}
                </label>
                <input type="text" value={scopeId}
                  onChange={(e) => setScopeId(e.target.value)}
                  placeholder={scopeType === "TENANT" ? "tenant-id" : scopeType === "ORG" ? "org-id" : scopeType === "ROLE" ? "role-id" : "user-id"}
                  className={`w-full px-3 py-2 rounded-lg border ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`} />
              </div>
            )}
          </div>
          {scopeType !== "GLOBAL" && !scopeId && (
            <p className={`text-xs ${styles.muted}`}>{locale==="zh"?"请输入作用域ID后加载配置":"Enter scope ID to load config"}</p>
          )}
        </div>

        {/* ── Clearance Level Slider ────────────────────────── */}
        <div className={`p-5 rounded-xl border ${styles.cardBorder} ${styles.cardBg} space-y-4`}>
          <div className="flex items-center gap-2.5">
            <Lock className="w-5 h-5 text-amber-500" />
            <h2 className="font-semibold text-base">
              {locale === "zh" ? "安全准入等级" : "Security Clearance Level"}
            </h2>
          </div>

          <div className="space-y-3">
            {/* Slider */}
            <input
              type="range"
              min={1}
              max={5}
              value={clearanceLevel}
              onChange={(e) => setClearanceLevel(Number(e.target.value))}
              className="w-full h-2 bg-gray-700 rounded-lg appearance-none cursor-pointer accent-indigo-500"
            />

            {/* Labels */}
            <div className="flex justify-between text-xs">
              {[1, 2, 3, 4, 5].map((level) => (
                <button
                  key={level}
                  onClick={() => setClearanceLevel(level)}
                  className={`px-2 py-1 rounded transition-colors ${
                    clearanceLevel === level
                      ? "bg-indigo-500/20 text-indigo-400 font-bold"
                      : `${styles.muted} hover:text-indigo-400`
                  }`}
                >
                  {level}
                </button>
              ))}
            </div>

            {/* Current level description */}
            <div className={`text-sm ${styles.muted} flex items-center gap-2`}>
              <span className="px-2 py-0.5 rounded bg-indigo-500/15 text-indigo-400 font-mono text-xs">
                Lv.{clearanceLevel}
              </span>
              <span>
                {getClLabel(String(clearanceLevel))}
              </span>
            </div>
          </div>
        </div>

        {/* ── Linked Workstation ─────────────────────────────── */}
        <div className={`p-5 rounded-xl border ${styles.cardBorder} ${styles.cardBg} space-y-4`}>
          <div className="flex items-center gap-2.5">
            <Monitor className="w-5 h-5 text-blue-500" />
            <h2 className="font-semibold text-base">
              {locale === "zh" ? "绑定物理工作站" : "Linked Workstation"}
            </h2>
          </div>

          <div className="space-y-2">
            <input
              type="text"
              value={workstation}
              onChange={(e) => setWorkstation(e.target.value)}
              placeholder={locale === "zh" ? "输入工作站主机名或IP地址..." : "Enter workstation hostname or IP..."}
              className={`w-full px-4 py-2.5 rounded-lg border focus:outline-none focus:ring-2 focus:ring-indigo-500/50 transition-all text-sm
                ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}
            />
            <p className={`text-xs ${styles.muted}`}>
              {locale === "zh"
                ? "仅允许从已绑定的物理工作站发起高危操作请求。"
                : "High-risk operations are only permitted from the bound physical workstation."}
            </p>
          </div>
        </div>

        {/* ── Audit Mode Radio Group ─────────────────────────── */}
        <div className={`p-5 rounded-xl border ${styles.cardBorder} ${styles.cardBg} space-y-4`}>
          <div className="flex items-center gap-2.5">
            <FileSearch className="w-5 h-5 text-purple-500" />
            <h2 className="font-semibold text-base">
              {locale === "zh" ? "双写审计力度" : "Dual-Write Audit Mode"}
            </h2>
          </div>

          <div className="space-y-3">
            {auditOptions().map(({ value: mode }) => {
              const isActive = auditMode === mode;
              const label = getAuditLabel(mode);
              const desc = mode === 'basic' ? {zh:'基础审计',en:'Basic Audit'} : mode === 'detailed' ? {zh:'详细审计',en:'Detailed Audit'} : {zh:'全面审计',en:'Comprehensive Audit'};
              return (
                <label
                  key={mode}
                  className={`flex items-start gap-3 p-3 rounded-lg border cursor-pointer transition-all ${
                    isActive
                      ? "border-indigo-500/50 bg-indigo-500/10"
                      : `${styles.cardBorder} hover:border-indigo-500/30`
                  }`}
                >
                  <input
                    type="radio"
                    name="auditMode"
                    value={mode}
                    checked={isActive}
                    onChange={() => setAuditMode(mode)}
                    className="mt-0.5 accent-indigo-500"
                  />
                  <div className="flex-1 min-w-0">
                    <span className={`text-sm font-medium block ${isActive ? "text-indigo-400" : ""}`}>
                      {label}
                    </span>
                    {desc && (
                      <span className={`text-xs ${isActive ? styles.muted : styles.muted} block mt-0.5`}>
                        {locale === "zh" ? desc.zh : desc.en}
                      </span>
                    )}
                  </div>
                  {isActive && (
                    <CheckCircle2 className="w-4 h-4 text-indigo-400 shrink-0 mt-0.5" />
                  )}
                </label>
              );
            })}
          </div>
        </div>

        {/* ── Sandbox Mandatory Toggle ──────────────────────── */}
        <div className={`p-5 rounded-xl border ${styles.cardBorder} ${styles.cardBg} space-y-4`}>
          <div className="flex items-center gap-2.5">
            <Box className="w-5 h-5 text-orange-500" />
            <h2 className="font-semibold text-base">
              {locale === "zh" ? "强制沙盒审查" : "Mandatory Sandbox Review"}
            </h2>
          </div>

          <div className="flex items-center justify-between">
            <div className="space-y-1">
              <span className={`text-sm ${sandboxMandatory ? "text-orange-400" : styles.muted}`}>
                {sandboxMandatory
                  ? (locale === "zh" ? "已启用 — 所有高危指令将在沙盒中预执行审查" : "Enabled — All high-risk commands will be pre-executed in sandbox")
                  : (locale === "zh" ? "已禁用 — 高危指令将直接执行" : "Disabled — High-risk commands execute directly")}
              </span>
              <p className={`text-xs ${styles.muted}`}>
                {locale === "zh"
                  ? "启用后，所有高危操作将先在隔离沙盒中模拟执行并经过安全审查后方可放行。"
                  : "When enabled, all high-risk operations are simulated in an isolated sandbox and reviewed before execution."}
              </p>
            </div>

            {/* Toggle Switch */}
            <button
              onClick={() => setSandboxMandatory(!sandboxMandatory)}
              className={`relative w-12 h-6 rounded-full transition-colors duration-200 shrink-0 ${
                sandboxMandatory ? "bg-indigo-500" : "bg-gray-600"
              }`}
              role="switch"
              aria-checked={sandboxMandatory}
            >
              <span
                className={`absolute top-0.5 w-5 h-5 rounded-full bg-white shadow transition-transform duration-200 ${
                  sandboxMandatory ? "translate-x-6" : "translate-x-0.5"
                }`}
              />
            </button>
          </div>
        </div>

        {/* ── Password Policy ────────────────────────────────── */}
        <div className={`p-5 rounded-xl border ${styles.cardBorder} ${styles.cardBg} space-y-4`}>
          <div className="flex items-center gap-2.5">
            <Key className="w-5 h-5 text-cyan-500" />
            <h2 className="font-semibold text-base">
              {locale === "zh" ? "密码策略" : "Password Policy"}
            </h2>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {/* Password Min Length */}
            <div className="space-y-1.5">
              <label className={`text-xs font-medium ${styles.muted}`}>
                {locale === "zh" ? "密码最小长度" : "Minimum Password Length"}
              </label>
              <input
                type="number"
                min={4}
                max={64}
                value={passwordMinLength}
                onChange={(e) => setPasswordMinLength(Number(e.target.value))}
                className={`w-full px-4 py-2.5 rounded-lg border focus:outline-none focus:ring-2 focus:ring-indigo-500/50 transition-all text-sm
                  ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}
              />
            </div>

            {/* Password Expire Days */}
            <div className="space-y-1.5">
              <label className={`text-xs font-medium ${styles.muted}`}>
                {locale === "zh" ? "密码过期天数" : "Password Expiry (Days)"}
              </label>
              <input
                type="number"
                min={1}
                max={365}
                value={passwordExpireDays}
                onChange={(e) => setPasswordExpireDays(Number(e.target.value))}
                className={`w-full px-4 py-2.5 rounded-lg border focus:outline-none focus:ring-2 focus:ring-indigo-500/50 transition-all text-sm
                  ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}
              />
            </div>
          </div>

          {/* MFA Toggle */}
          <div className="flex items-center justify-between pt-1">
            <div className="space-y-1">
              <span className={`text-sm ${mfaEnabled ? "text-cyan-400" : styles.muted}`}>
                {locale === "zh" ? "多因素认证 (MFA)" : "Multi-Factor Authentication (MFA)"}
              </span>
              <p className={`text-xs ${styles.muted}`}>
                {locale === "zh"
                  ? "启用后要求用户通过第二因素验证身份。"
                  : "When enabled, users must verify identity with a second factor."}
              </p>
            </div>
            <button
              onClick={() => setMfaEnabled(!mfaEnabled)}
              className={`relative w-12 h-6 rounded-full transition-colors duration-200 shrink-0 ${
                mfaEnabled ? "bg-indigo-500" : "bg-gray-600"
              }`}
              role="switch"
              aria-checked={mfaEnabled}
            >
              <span
                className={`absolute top-0.5 w-5 h-5 rounded-full bg-white shadow transition-transform duration-200 ${
                  mfaEnabled ? "translate-x-6" : "translate-x-0.5"
                }`}
              />
            </button>
          </div>
        </div>

        {/* ── Session Management ──────────────────────────────── */}
        <div className={`p-5 rounded-xl border ${styles.cardBorder} ${styles.cardBg} space-y-4`}>
          <div className="flex items-center gap-2.5">
            <Clock className="w-5 h-5 text-teal-500" />
            <h2 className="font-semibold text-base">
              {locale === "zh" ? "会话管理" : "Session Management"}
            </h2>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {/* Session Timeout */}
            <div className="space-y-1.5">
              <label className={`text-xs font-medium ${styles.muted}`}>
                {locale === "zh" ? "会话超时 (分钟)" : "Session Timeout (Minutes)"}
              </label>
              <input
                type="number"
                min={1}
                max={1440}
                value={sessionTimeout}
                onChange={(e) => setSessionTimeout(Number(e.target.value))}
                className={`w-full px-4 py-2.5 rounded-lg border focus:outline-none focus:ring-2 focus:ring-indigo-500/50 transition-all text-sm
                  ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}
              />
            </div>

            {/* Max Concurrent Sessions */}
            <div className="space-y-1.5">
              <label className={`text-xs font-medium ${styles.muted}`}>
                {locale === "zh" ? "最大并发会话数" : "Max Concurrent Sessions"}
              </label>
              <input
                type="number"
                min={1}
                max={100}
                value={maxConcurrentSessions}
                onChange={(e) => setMaxConcurrentSessions(Number(e.target.value))}
                className={`w-full px-4 py-2.5 rounded-lg border focus:outline-none focus:ring-2 focus:ring-indigo-500/50 transition-all text-sm
                  ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}
              />
            </div>
          </div>

          <div className="flex items-center gap-2 pt-1">
            <Users className="w-4 h-4 text-teal-400" />
            <p className={`text-xs ${styles.muted}`}>
              {locale === "zh"
                ? "配置用户会话的超时时间与并发限制，增强账户安全。"
                : "Configure session timeout and concurrency limits to enhance account security."}
            </p>
          </div>
        </div>

        {/* ── Save Button ────────────────────────────────────── */}
        <div className="flex items-center justify-end gap-3 pt-2">
          <button
            onClick={handleSave}
            disabled={saving}
            className={`flex items-center gap-2 px-6 py-2.5 rounded-lg font-medium text-sm transition-all
              bg-indigo-500 hover:bg-indigo-600 text-white
              disabled:opacity-50 disabled:cursor-not-allowed
              shadow-lg shadow-indigo-500/25 hover:shadow-indigo-500/40`}
          >
            {saving ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin" />
                {locale === "zh" ? "保存中..." : "Saving..."}
              </>
            ) : (
              <>
                <Save className="w-4 h-4" />
                {locale === "zh" ? "保存并重连核准通道" : "Verify & Save Profile"}
              </>
            )}
          </button>
        </div>

      </div>
    </div>
  );
}
