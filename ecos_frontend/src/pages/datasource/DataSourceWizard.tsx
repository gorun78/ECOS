/**
 * DataSourceWizard — 向导式数据源注册 (Step 1→2→3)
 * Step 1: 填连接信息 → Step 2: 测试连接 → Step 3: 确认导入
 * @license Apache-2.0
 */

import React, { useState } from "react";
import {
  Database, X, AlertCircle,
  Loader2, CheckCircle, ChevronRight, ChevronLeft, ArrowRight,
} from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import { useDict } from "../../hooks/useDict";
import { createDataSource } from "../../api";
import ConnectionTest from "./ConnectionTest";

const DB_TYPES = ["Oracle", "MySQL", "PostgreSQL", "SQLServer", "达梦", "金仓"] as const;
type DbType = typeof DB_TYPES[number];

interface FormState { name: string; jdbcUrl: string; username: string; password: string; databaseType: DbType; }
const EMPTY_FORM: FormState = { name: "", jdbcUrl: "", username: "", password: "", databaseType: "PostgreSQL" };

interface DataSourceWizardProps { onClose: () => void; onSuccess: () => void; }

const STEPS = [
  { zh: "连接信息", en: "Connection Info" },
  { zh: "测试连接", en: "Test Connection" },
  { zh: "导入表结构", en: "Import Tables" },
];

export default function DataSourceWizard({ onClose, onSuccess }: DataSourceWizardProps) {
  const { styles } = useTheme();
  const { locale } = useLanguage();
  const { getLabel: getDsTypeLabel } = useDict("datasource_type", locale);

  const [step, setStep] = useState(0);
  const [form, setForm] = useState<FormState>({ ...EMPTY_FORM });
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [importResult, setImportResult] = useState<{ success: boolean; message: string } | null>(null);

  const canGoStep2 = () => {
    if (!form.name.trim()) { setFormError(locale === "zh" ? "请输入数据源名称" : "Please enter data source name"); return false; }
    if (!form.jdbcUrl.trim()) { setFormError(locale === "zh" ? "请输入 JDBC URL" : "Please enter JDBC URL"); return false; }
    setFormError(null); return true;
  };

  const goNext = () => { if (step === 0 && !canGoStep2()) return; if (step < 2) setStep(step + 1); };
  const goPrev = () => { if (step > 0) setStep(step - 1); setFormError(null); };

  const handleImport = async () => {
    setSubmitting(true); setFormError(null); setImportResult(null);
    try {
      await createDataSource({
        datasourceName: form.name.trim(),
        datasourceType: "JDBC",
        connectionConfig: JSON.stringify({ jdbcUrl: form.jdbcUrl.trim(), username: form.username.trim(), password: form.password }),
      });
      setImportResult({
        success: true,
        message: locale === "zh" ? "数据源注册成功！请关闭向导后在列表中采集元数据。" : "Registered! Close wizard and collect metadata from the list.",
      });
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : (locale === "zh" ? "导入失败" : "Import failed");
      setImportResult({ success: false, message: msg }); setFormError(msg);
    } finally { setSubmitting(false); }
  };

  // ── Step indicator ─────────────────────────────────────
  const stepIndicator = (
    <div className="flex items-center justify-center gap-2 mb-5">
      {STEPS.map((s, idx) => {
        const active = idx === step, done = idx < step;
        return (
          <React.Fragment key={idx}>
            <div className={`flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[10px] font-bold transition ${done ? "bg-green-50 text-green-700 border border-green-200" : active ? `${styles.accentBg} text-white` : "bg-slate-100 text-slate-400"}`}>
              {done ? <CheckCircle className="w-3 h-3" /> : <span className="w-4 h-4 rounded-full border border-current flex items-center justify-center text-[9px]">{idx + 1}</span>}
              <span className="hidden sm:inline">{locale === "zh" ? s.zh : s.en}</span>
            </div>
            {idx < 2 && <ChevronRight className={`w-3 h-3 ${idx < step ? "text-green-400" : styles.muted}`} />}
          </React.Fragment>
        );
      })}
    </div>
  );

  // ── Form field factory ─────────────────────────────────
  const inputClass = `w-full border ${styles.cardBorder} rounded-lg px-3 py-2 text-xs outline-hidden focus:border-blue-400 ${styles.inputBg} ${styles.inputText}`;

  const Field = ({ label, required, children }: { label: string; required?: boolean; children: React.ReactNode }) => (
    <div>
      <label className={`text-xs font-semibold ${styles.cardTextMuted} block mb-1`}>{label}{required && <span className="text-red-400"> *</span>}</label>
      {children}
    </div>
  );

  // ── Step contents ──────────────────────────────────────
  const renderStep1 = () => (
    <div className="space-y-3">
      <Field label={locale === "zh" ? "数据源名称" : "Data Source Name"} required>
        <input type="text" value={form.name} onChange={(e) => setForm(p => ({ ...p, name: e.target.value }))} className={inputClass} placeholder={locale === "zh" ? "如: 生产数据库" : "e.g. Production DB"} />
      </Field>
      <Field label={locale === "zh" ? "数据库类型" : "Database Type"}>
        <select value={form.databaseType} onChange={(e) => setForm(p => ({ ...p, databaseType: e.target.value as DbType }))} className={`${inputClass} cursor-pointer`}>
          {DB_TYPES.map(db => <option key={db} value={db}>{getDsTypeLabel(db)}</option>)}
        </select>
      </Field>
      <Field label="JDBC URL" required>
        <input type="text" value={form.jdbcUrl} onChange={(e) => setForm(p => ({ ...p, jdbcUrl: e.target.value }))} className={`${inputClass} font-mono`} placeholder="jdbc:postgresql://localhost:5432/mydb" />
      </Field>
      <Field label={locale === "zh" ? "用户名" : "Username"}>
        <input type="text" value={form.username} onChange={(e) => setForm(p => ({ ...p, username: e.target.value }))} className={inputClass} placeholder="root" />
      </Field>
      <Field label={locale === "zh" ? "密码" : "Password"}>
        <input type="password" value={form.password} onChange={(e) => setForm(p => ({ ...p, password: e.target.value }))} className={inputClass} placeholder="••••••••" />
      </Field>
    </div>
  );

  const renderStep2 = () => (
    <div>
      <p className={`text-xs ${styles.muted} mb-4`}>{locale === "zh" ? "测试当前填写的连接配置是否可用" : "Test whether the current connection configuration is valid"}</p>
      <ConnectionTest jdbcUrl={form.jdbcUrl} username={form.username} password={form.password} datasourceType={form.databaseType} />
    </div>
  );

  const renderStep3 = () => (
    <div className="space-y-4">
      {!importResult ? (
        <div className="text-center py-8">
          <Database className={`w-12 h-12 mx-auto ${styles.muted} mb-3`} />
          <p className={`text-sm font-bold ${styles.cardText}`}>{locale === "zh" ? "确认导入" : "Confirm Import"}</p>
          <p className={`text-xs ${styles.muted} mt-1.5 max-w-xs mx-auto`}>{locale === "zh" ? `将注册数据源「${form.name}」并保存连接配置` : `Register data source "${form.name}" and save connection config`}</p>
          <p className={`text-[10px] ${styles.muted} mt-1`}>{locale === "zh" ? "注册完成后可在列表中采集元数据" : "After registration you can collect metadata from the list"}</p>
        </div>
      ) : (
        <div className={`rounded-lg p-4 flex items-start gap-3 text-sm ${importResult.success ? "bg-green-50 text-green-700 border border-green-200" : "bg-red-50 text-red-700 border border-red-200"}`}>
          {importResult.success ? <CheckCircle className="w-5 h-5 shrink-0 mt-0.5" /> : <AlertCircle className="w-5 h-5 shrink-0 mt-0.5" />}
          <div className="flex-1 min-w-0">
            <p className="font-bold">{importResult.success ? (locale === "zh" ? "注册成功" : "Registration Successful") : (locale === "zh" ? "注册失败" : "Registration Failed")}</p>
            <p className="mt-1 text-xs whitespace-pre-wrap break-all">{importResult.message}</p>
          </div>
        </div>
      )}
      {formError && !importResult?.success && (
        <div className="text-xs text-red-600 bg-red-50 border border-red-200 rounded-lg p-2 flex items-center gap-1.5"><AlertCircle className="w-3.5 h-3.5 shrink-0" />{formError}</div>
      )}
    </div>
  );

  // ── Main render ────────────────────────────────────────
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
      <div className={`${styles.cardBg} rounded-xl shadow-2xl w-full max-w-lg mx-4 p-6 border ${styles.cardBorder}`}>
        {/* Header */}
        <div className="flex items-center justify-between mb-4">
          <h2 className={`text-lg font-bold ${styles.cardText} flex items-center gap-2`}><Database className={`w-5 h-5 ${styles.accentText}`} />{locale === "zh" ? "注册数据源" : "Register Data Source"}</h2>
          <button onClick={onClose} className="p-1 hover:bg-slate-100 rounded transition cursor-pointer"><X className={`w-5 h-5 ${styles.muted}`} /></button>
        </div>

        {stepIndicator}

        <div className="min-h-[200px]">
          {step === 0 && renderStep1()}
          {step === 1 && renderStep2()}
          {step === 2 && renderStep3()}
        </div>

        {formError && step === 0 && (
          <div className="text-xs text-red-600 bg-red-50 border border-red-200 rounded-lg p-2 flex items-center gap-1.5 mt-3"><AlertCircle className="w-3.5 h-3.5 shrink-0" />{formError}</div>
        )}

        {/* Navigation */}
        <div className="flex justify-between gap-2 mt-5">
          <div>{step > 0 && <button onClick={goPrev} disabled={submitting} className="px-4 py-2 text-xs font-semibold text-slate-500 hover:bg-slate-100 rounded-lg transition flex items-center gap-1 cursor-pointer"><ChevronLeft className="w-3.5 h-3.5" />{locale === "zh" ? "上一步" : "Back"}</button>}</div>
          <div className="flex gap-2">
            <button onClick={onClose} className="px-4 py-2 text-xs font-semibold text-slate-500 hover:bg-slate-100 rounded-lg transition cursor-pointer">{locale === "zh" ? "取消" : "Cancel"}</button>
            {step < 2 ? (
              <button onClick={goNext} className={`px-4 py-2 text-xs font-semibold ${styles.accentBg} ${styles.accentHover} text-white rounded-lg transition flex items-center gap-1.5 cursor-pointer`}>{locale === "zh" ? "下一步" : "Next"}<ChevronRight className="w-3.5 h-3.5" /></button>
            ) : importResult?.success ? (
              <button onClick={() => { onSuccess(); onClose(); }} className={`px-4 py-2 text-xs font-semibold ${styles.accentBg} ${styles.accentHover} text-white rounded-lg transition cursor-pointer`}>{locale === "zh" ? "完成" : "Finish"}</button>
            ) : (
              <button onClick={handleImport} disabled={submitting} className={`px-4 py-2 text-xs font-semibold ${styles.accentBg} ${styles.accentHover} disabled:opacity-50 text-white rounded-lg transition flex items-center gap-1.5 cursor-pointer`}>{submitting && <Loader2 className="w-3 h-3 animate-spin" />}<ArrowRight className="w-3.5 h-3.5" />{locale === "zh" ? "注册并导入" : "Register & Import"}</button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
