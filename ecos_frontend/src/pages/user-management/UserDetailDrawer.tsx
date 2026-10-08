/**
 * UserDetailDrawer — 用户详情右侧抽屉（从 UserManagement.tsx 抽取，逐字搬迁）
 * @license Apache-2.0
 */

import React, { useState, useEffect } from "react";
import { X, Eye, Shield, KeyRound, LogOut } from "lucide-react";
import { fetchUserRoles } from "../../api";
import type { IamUser, IamRole } from "../../api";
import { useLanguage } from "../../components/LanguageContext";
import { useTheme } from "../../components/ThemeContext";

type DrawerTab = "basic" | "roles" | "login";

interface UserDetailDrawerProps {
  user: IamUser;
  allRoles: IamRole[];
  orgMap: Record<string, string>;
  onForceLogout: (userId: string) => void;
  onResetPassword: (userId: string) => void;
  onClose: () => void;
}

export default function UserDetailDrawer({ user, allRoles, orgMap, onForceLogout, onResetPassword, onClose }: UserDetailDrawerProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const [tab, setTab] = useState<DrawerTab>("basic");
  const [userRoles, setUserRoles] = useState<string[]>([]);
  const [rolesLoaded, setRolesLoaded] = useState(false);

  useEffect(() => {
    fetchUserRoles(user.userId)
      .then(r => setUserRoles(r))
      .catch(() => setUserRoles([]))
      .finally(() => setRolesLoaded(true));
  }, [user.userId]);

  const roleNames = userRoles
    .map(id => allRoles.find(r => r.roleId === id)?.roleName)
    .filter(Boolean) as string[];

  const drawerTabs: { id: DrawerTab; label: string; icon: React.FC<any> }[] = [
    { id: "basic", label: t("platform.user.detail.tabBasic"), icon: Eye },
    { id: "roles", label: t("platform.user.detail.tabRoles"), icon: Shield },
    { id: "login", label: t("platform.user.detail.tabLogin"), icon: KeyRound },
  ];

  return (
    <>
      <div className="fixed inset-0 z-40 bg-black/30" onClick={onClose} />
      <div className={`fixed right-0 top-0 h-full w-full max-w-[400px] z-50 shadow-2xl flex flex-col ${styles.cardBg} border-l ${styles.cardBorder}`}>
        <div className={`flex items-center justify-between px-5 py-4 border-b ${styles.cardBorder}`}>
          <div>
            <h3 className={`text-sm font-semibold ${styles.cardText}`}>{user.username}</h3>
            <p className={`text-xs opacity-50`}>{user.realName || user.email || "-"}</p>
          </div>
          <button onClick={onClose} className="opacity-60 hover:opacity-100"><X className="w-4 h-4" /></button>
        </div>

        <div className={`flex gap-1 px-4 pt-3 border-b ${styles.appBorder}`}>
          {drawerTabs.map(t => {
            const Icon = t.icon;
            return (
              <button key={t.id} onClick={() => setTab(t.id)}
                className={`flex items-center gap-1.5 px-3 py-1.5 text-xs rounded-t transition-colors ${
                  tab === t.id
                    ? `${styles.accentBg} text-white`
                    : `${styles.sidebarHoverBg} ${styles.muted}`
                }`}>
                <Icon size={12} />{t.label}
              </button>
            );
          })}
        </div>

        <div className="flex-1 overflow-y-auto px-5 py-4">
          {tab === "basic" && (
            <div className="space-y-3">
              {[
                [t("platform.user.detail.username"), user.username],
                [t("platform.user.detail.realName"), user.realName || "-"],
                [t("platform.user.detail.email"), user.email || "-"],
                [t("platform.user.detail.phone"), user.phone || "-"],
                [t("platform.user.detail.org"), orgMap[user.orgId || ""] || (user as any).orgName || user.orgId || "-"],
                [t("platform.user.detail.status"), user.status === "ACTIVE" ? t("platform.user.detail.statusActive") : t("platform.user.detail.statusLocked")],
                [t("platform.user.detail.locked"), user.locked === "1" ? t("platform.user.detail.yes") : t("platform.user.detail.no")],
                [t("platform.user.detail.lastLogin"), user.lastLoginTime || "-"],
                [t("platform.user.detail.created"), user.createdTime || "-"],
              ].map(([label, value], i) => (
                <div key={i} className={`flex justify-between items-center py-1.5 border-b ${styles.cardBorder} last:border-0`}>
                  <span className="text-xs opacity-50">{label}</span>
                  <span className="text-xs font-medium ml-4 text-right break-all">{value}</span>
                </div>
              ))}
            </div>
          )}

          {tab === "roles" && (
            <div>
              {!rolesLoaded ? (
                <div className="space-y-2 py-4">
                  {[1,2,3].map(i => <div key={i} className="h-8 bg-gray-100 dark:bg-gray-800 rounded animate-pulse" />)}
                </div>
              ) : roleNames.length === 0 ? (
                <div className="text-center py-8 text-xs opacity-40">
                  {t("platform.user.detail.noRoles")}
                </div>
              ) : (
                <div className="space-y-1">
                  {roleNames.map((name, i) => (
                    <div key={i} className="px-3 py-2 rounded text-xs bg-indigo-50 dark:bg-indigo-900/20 text-indigo-600 dark:text-indigo-400">
                      <Shield className="w-3 h-3 inline mr-1.5" />
                      {name}
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {tab === "login" && (
            <div className="space-y-3">
              <div className={`p-4 rounded-lg border ${styles.cardBorder} text-center`}>
                <KeyRound className="w-8 h-8 opacity-20 mx-auto mb-2" />
                <p className={`text-xs ${styles.cardTextMuted}`}>
                  {t("platform.user.detail.loginReady")}
                </p>
                <p className="text-[10px] opacity-30 mt-1">
                  {t("platform.user.detail.loginBackend")}
                </p>
              </div>
            </div>
          )}
        </div>

        <div className={`px-5 py-4 border-t ${styles.cardBorder} space-y-2`}>
          <button
            type="button"
            onClick={() => onForceLogout(user.userId)}
            className={`w-full px-3 py-2 rounded text-xs font-medium border ${styles.warningBorder} ${styles.warningText} ${styles.warningBg} hover:opacity-80 flex items-center justify-center gap-1.5`}
          >
            <LogOut className="w-3.5 h-3.5" />
            {t("platform.user.detail.forceLogout")}
          </button>
          <button
            type="button"
            onClick={() => onResetPassword(user.userId)}
            className={`w-full px-3 py-2 rounded text-xs font-medium border border-indigo-200 dark:border-indigo-800 ${styles.badgeText} ${styles.badgeBg} hover:opacity-80 flex items-center justify-center gap-1.5`}
          >
            <KeyRound className="w-3.5 h-3.5" />
            {t("platform.user.detail.resetPassword")}
          </button>
        </div>
      </div>
    </>
  );
}
