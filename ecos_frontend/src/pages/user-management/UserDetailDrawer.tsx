/**
 * UserDetailDrawer — 用户详情右侧抽屉
 * 4 区块: 基本信息 / 角色 / 安全Profile / 最近操作
 * @license Apache-2.0
 */

import React, { useState, useEffect } from "react";
import { X, User, Shield, Key, Clock, Loader2 } from "lucide-react";
import { useLanguage } from "../../components/LanguageContext";
import { useTheme } from "../../components/ThemeContext";
import { fetchUserRoles, apiFetchData } from "../../api";
import type { IamUser, IamRole } from "../../api";

interface Props {
  visible: boolean;
  userId: string | null;
  onClose: () => void;
}

interface AuditLogEntry {
  logId?: string;
  action?: string;
  target?: string;
  details?: string;
  status?: string;
  ipAddress?: string;
  createdTime?: string;
}

export default function UserDetailDrawer({ visible, userId, onClose }: Props) {
  const { locale, t } = useLanguage();
  const { styles } = useTheme();
  const isZh = locale === "zh";

  const [user, setUser] = useState<IamUser | null>(null);
  const [roleIds, setRoleIds] = useState<string[] | null>(null);
  const [auditLogs, setAuditLogs] = useState<AuditLogEntry[]>([]);
  const [loading, setLoading] = useState(false);
  const [logsLoading, setLogsLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!visible || !userId) return;
    setLoading(true);
    setError("");

    // Fetch user detail
    apiFetchData<IamUser>(`/api/v1/users/${userId}`)
      .then(setUser)
      .catch((e) => {
        setError(e.message || "Failed to load user");
        setUser(null);
      })
      .finally(() => setLoading(false));

    // Fetch roles
    fetchUserRoles(userId)
      .then(setRoleIds)
      .catch(() => setRoleIds([]));

    // Fetch audit logs
    setLogsLoading(true);
    apiFetchData<AuditLogEntry[]>(`/api/v1/audit/logs?userId=${userId}&pageSize=10`)
      .then((data) => setAuditLogs(Array.isArray(data) ? data : []))
      .catch(() => setAuditLogs([]))
      .finally(() => setLogsLoading(false));
  }, [visible, userId]);

  if (!visible) return null;

  // ── Compute strength ──────────────────────────────────
  const loginFails = "(N/A)";
  const lockStatus = user?.locked === "1" ? (isZh ? "已锁定" : "Locked") : (isZh ? "正常" : "Normal");

  // ── Section Card ──────────────────────────────────────
  const SectionCard: React.FC<{
    icon: React.FC<{ size?: number }>;
    title: string;
    children: React.ReactNode;
  }> = ({ icon: Icon, title, children }) => (
    <div className={`mb-4 rounded-lg border p-4 ${styles.cardBorder}`}>
      <div className="flex items-center gap-2 mb-3">
        <span className="opacity-50"><Icon size={14} /></span>
        <span className="text-xs font-semibold opacity-60 uppercase tracking-wider">{title}</span>
      </div>
      {children}
    </div>
  );

  // ── KV Row ───────────────────────────────────────────
  const KV: React.FC<{ label: string; value: React.ReactNode }> = ({ label, value }) => (
    <div className="flex justify-between items-center py-1.5 border-b border-gray-100 dark:border-gray-700/10 last:border-0">
      <span className="text-[11px] opacity-45">{label}</span>
      <span className="text-xs font-medium ml-4 text-right break-all">{value}</span>
    </div>
  );

  return (
    <>
      {/* Overlay */}
      <div className="fixed inset-0 z-40 bg-black/30" onClick={onClose} />

      {/* Drawer */}
      <div
        className={`fixed right-0 top-0 h-full w-full max-w-[420px] z-50 shadow-2xl flex flex-col ${styles.cardBg} border-l ${styles.cardBorder}`}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-200 dark:border-gray-700/30 shrink-0">
          <div>
            <h3 className={`text-sm font-semibold ${styles.cardText}`}>
              {user?.username || t("user.detail.title")}
            </h3>
            <p className="text-xs opacity-40">{user?.realName || user?.email || "-"}</p>
          </div>
          <button onClick={onClose} className="opacity-50 hover:opacity-100 transition-opacity">
            <X size={16} />
          </button>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto px-5 py-4 min-h-0">
          {loading && (
            <div className="flex items-center justify-center py-12">
              <span className="animate-spin opacity-30 inline-block"><Loader2 size={24} /></span>
            </div>
          )}

          {error && !loading && (
            <div className="p-4 rounded bg-red-500/10 border border-red-500/30 text-red-400 text-xs text-center">
              {error}
            </div>
          )}

          {user && !loading && (
            <>
              {/* 1. 基本信息 */}
              <SectionCard icon={User} title={t("user.detail.basic")}>
                <KV label={isZh ? "用户名" : "Username"} value={user.username} />
                <KV label={isZh ? "姓名" : "Name"} value={user.realName || "-"} />
                <KV label={isZh ? "邮箱" : "Email"} value={user.email || "-"} />
                <KV label={isZh ? "手机" : "Phone"} value={user.phone || "-"} />
                <KV
                  label={isZh ? "状态" : "Status"}
                  value={
                    <span
                      className={`px-2 py-0.5 rounded text-[11px] font-medium ${
                        user.status === "ACTIVE"
                          ? "bg-green-50 dark:bg-green-900/20 text-green-600 dark:text-green-400"
                          : "bg-red-50 dark:bg-red-900/20 text-red-500 dark:text-red-400"
                      }`}
                    >
                      {user.status === "ACTIVE"
                        ? isZh ? "活跃" : "Active"
                        : isZh ? "禁用" : "Disabled"}
                    </span>
                  }
                />
                <KV
                  label={isZh ? "创建时间" : "Created"}
                  value={user.createdTime || "-"}
                />
              </SectionCard>

              {/* 2. 角色 (badge形式) */}
              <SectionCard icon={Shield} title={t("user.detail.roles")}>
                {roleIds === null ? (
                  <div className="flex gap-1">
                    {[1, 2, 3].map((i) => (
                      <div key={i} className="h-5 w-16 bg-gray-200 dark:bg-gray-700 rounded animate-pulse" />
                    ))}
                  </div>
                ) : roleIds.length === 0 ? (
                  <span className="text-xs opacity-30">{isZh ? "无角色" : "No roles"}</span>
                ) : (
                  <div className="flex flex-wrap gap-1.5">
                    {roleIds.map((rid, i) => (
                      <span
                        key={i}
                        className="text-[11px] bg-indigo-50 dark:bg-indigo-900/20 text-indigo-600 dark:text-indigo-400 px-2 py-1 rounded-full font-medium"
                      >
                        {rid}
                      </span>
                    ))}
                  </div>
                )}
              </SectionCard>

              {/* 3. 安全Profile */}
              <SectionCard icon={Key} title={t("user.detail.security")}>
                <KV label={isZh ? "登录失败次数" : "Login Failures"} value={loginFails} />
                <KV label={isZh ? "锁定状态" : "Lock Status"} value={lockStatus} />
                <KV
                  label={isZh ? "上次登录时间" : "Last Login"}
                  value={user.lastLoginTime || "-"}
                />
                <KV
                  label={isZh ? "登录IP" : "Login IP"}
                  value={auditLogs.length > 0 ? auditLogs[0]?.ipAddress || "-" : "-"}
                />
              </SectionCard>

              {/* 4. 最近操作 */}
              <SectionCard icon={Clock} title={t("user.detail.recent")}>
                {logsLoading ? (
                  <div className="text-center py-4">
                    <Loader2 size={16} className="animate-spin opacity-30 mx-auto" />
                  </div>
                ) : auditLogs.length === 0 ? (
                  <span className="text-xs opacity-30">{isZh ? "暂无操作记录" : "No recent operations"}</span>
                ) : (
                  <div className="space-y-2 max-h-48 overflow-y-auto">
                    {auditLogs.map((log, i) => (
                      <div
                        key={log.logId || i}
                        className={`p-2 rounded text-xs border ${styles.cardBorder}`}
                      >
                        <div className="flex justify-between items-start mb-0.5">
                          <span className="font-medium opacity-70">{log.action || "-"}</span>
                          <span className="text-[10px] opacity-35">{log.createdTime || ""}</span>
                        </div>
                        <div className="text-[11px] opacity-40 truncate">
                          {log.target || log.details || "-"}
                        </div>
                        {log.ipAddress && (
                          <div className="text-[10px] opacity-25 mt-0.5">IP: {log.ipAddress}</div>
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </SectionCard>
            </>
          )}
        </div>
      </div>
    </>
  );
}
