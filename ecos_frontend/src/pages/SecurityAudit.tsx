/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useMemo, useState, useEffect } from "react";
import { Shield, Key, Lock, Server, Clock, Users, FileText, Activity } from "lucide-react";
import { AuditEvent } from "../types";
import { fetchAuditLogs } from "../api";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";

export default function SecurityAudit() {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();
  const [auditLogs, setAuditLogs] = useState<AuditEvent[]>([]);

  useEffect(() => {
    fetchAuditLogs().then(res => setAuditLogs(res.data || []));
  }, []);

  // Derive security insights from real audit log data
  const insights = useMemo(() => {
    const actorMap = new Map<string, number>();
    const actionMap = new Map<string, number>();
    const typeMap = new Map<string, number>();
    auditLogs.forEach((log) => {
      actorMap.set(log.userId, (actorMap.get(log.userId) || 0) + 1);
      actionMap.set(log.action, (actionMap.get(log.action) || 0) + 1);
      typeMap.set(log.resource, (typeMap.get(log.resource) || 0) + 1);
    });
    const topActors = [...actorMap.entries()].sort((a, b) => b[1] - a[1]).slice(0, 4);
    const topActions = [...actionMap.entries()].sort((a, b) => b[1] - a[1]).slice(0, 4);
    const recentEvents = auditLogs.slice(-3).reverse();
    return { topActors, topActions, recentEvents, total: auditLogs.length };
  }, [auditLogs]);

  // Bilingual dynamic helpers for audit event types
  const getActionLabel = (action: string) => {
    if (locale !== "zh") return action;
    const dict: Record<string, string> = {
      "SCHEMA_MODIFY": "元数据及字段Schema变更保护",
      "READ_DATASET": "数据集高维机密只读鉴权",
      "ROLE_GRANT": "全局安全角色及控制流授权发布",
      "ACCESS_REVOKE": "特权账号越权审计主动注销",
      "API_QUERY": "微服务对外公开接口鉴权准入"
    };
    return dict[action] || action;
  };

  const getObjectTypeLabel = (objType: string) => {
    if (locale !== "zh") return objType;
    const dict: Record<string, string> = {
      "dataset": "物理物理数据库表 (Dataset)",
      "ontology_class": "概念本体语义绑定 (Ontology)",
      "role": "访问控制 RBAC 角色域 (Role)",
      "user_contract": "合规脱敏防护隔离契约 (Contract)"
    };
    return dict[objType] || objType;
  };

  const getDetailsText = (id: string, originalDetails: string) => {
    if (locale !== "zh") return originalDetails;
    const dict: Record<string, string> = {
      "evt_01": "超级管理员成功执行了 customer_360 数据表字段追加修订。追加字段 churn_risk 触发密码学级账本多链路 Trace 遥测签名登记校验。",
      "evt_02": "高级风控专员对 ds_customer360 数据集启动了天级高吞吐预测性批量检索。该操作受行级条件 region = 'APAC' 规则严格过滤。",
      "evt_03": "数据架构师向 api_executor 注入了全局 ontology.action 并重置系统元数据鉴权令牌。哈希凭证校验结果: 无漏报、无篡改漏洞点。",
      "evt_04": "平台自动审计安全卫士检测到部分过期 API 轮询请求，安全拦截并拦截异常请求者，防止多重报送进而污染。强制吊销其令牌权限。",
      "evt_05": "智能外部代理（Antigravity Agent）安全调用了 24 层本体交互行动面板，执行合规准入检查并且执行对账单数据校验。"
    };
    return dict[id] || originalDetails;
  };

  const getRoleLabel = (role: string) => {
    if (locale !== "zh") return role;
    const dict: Record<string, string> = {
      "admin": "超级管理员 (admin)",
      "analyst": "商业分析师 (analyst)",
      "operator": "现场作业操盘手 (operator)",
      "guest": "临时访客账号 (guest)"
    };
    return dict[role] || role;
  };

  const getPermissionLabel = (perm: string) => {
    if (locale !== "zh") return perm;
    const dict: Record<string, string> = {
      "read_write": "允许读写与变更结构 (read_write)",
      "read_selected_apac": "限制仅读亚太行与合规列 (read_selected)",
      "read_only": "单纯只读访问资产 (read_only)",
      "no_access": "绝对严禁任何接入 (no_access)"
    };
    return dict[perm] || perm;
  };

  const getConditionLabel = (cond: string) => {
    if (!cond) return t("sec.directives.unlimited");
    if (locale !== "zh") return cond;
    const dict: Record<string, string> = {
      "region == 'APAC' && is_masked(email)": "仅限亚太销售辖区 (region == 'APAC') 且对私人邮箱字段 email 强制进行高维哈希脱敏掩码保护",
      "has_fault == 'false'": "仅限当前运行状态处于物理安全平稳期的设备机组记录 (has_fault == 'false')"
    };
    return dict[cond] || cond;
  };

  return (
    <div className={`flex-1 overflow-y-auto p-5 flex flex-col h-full font-sans ${styles.appBg} ${styles.appText}`}>
      
      {/* 1. Header Section */}
      <div className="flex justify-between items-center mb-6 shrink-0">
        <div>
          <h1 className={`text-xl font-bold tracking-tight flex items-center gap-2 ${styles.cardText}`}>
            <Shield className="text-blue-500 w-5 h-5 shrink-0" />
            {t("sec.title")}
          </h1>
          <p className={`text-xs mt-0.5 ${styles.cardTextMuted}`}>{t("sec.desc")}</p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-5 flex-1 min-h-0">
        
        {/* COL 1 & 2: IMMUTABLE AUDIT LEDGER TIMELINE */}
        <div className={`lg:col-span-2 rounded-md p-4 flex flex-col overflow-hidden max-h-full border ${styles.cardBg} ${styles.cardBorder}`}>
          <div className="mb-4 shrink-0 flex items-center justify-between select-none leading-none">
            <span className={`text-[10px] uppercase font-mono tracking-wider flex items-center gap-1.5 ${styles.cardTextMuted}`}>
              <Server className="w-3.5 h-3.5 text-blue-500" />
              {t("sec.ledger.title")}
            </span>
            <span className={`text-[9px] font-mono ${styles.cardTextMuted}`}>{t("sec.ledger.subtitle")}</span>
          </div>

          <div className="flex-1 overflow-y-auto scrollbar-thin space-y-3 font-sans pr-1">
            {auditLogs.map((log) => {
              const detailsStr = typeof log.details === 'string' ? log.details : (log.details ? JSON.stringify(log.details) : '');
              return (
                <div key={log.eventId} className={`p-3 border rounded-md bg-black/20 ${styles.cardBorder}`}>
                  <div className="flex items-center justify-between gap-3 flex-wrap mb-1.5 text-xs text-[11px] font-mono leading-none">
                    <span className={`font-bold uppercase ${styles.cardText}`}>{getActionLabel(log.action)}</span>
                    <span className={`flex items-center gap-1.5 ${styles.cardTextMuted}`}>
                      <Clock className="w-3 h-3" />
                      {log.timestamp}
                    </span>
                  </div>

                  <p className={`text-xs leading-normal ${styles.cardTextMuted}`}>{getDetailsText(log.eventId, detailsStr)}</p>

                  <div className={`flex flex-wrap items-center gap-x-4 gap-y-1.5 mt-2.5 pt-2 border-t text-[10px] font-mono ${styles.cardBorder} ${styles.cardTextMuted}`}>
                    <span>
                      {locale === "zh" ? "指令发出账号: " : "Actor: "}<span className="text-blue-400 font-semibold">{log.userId}</span>
                    </span>
                    <span>•</span>
                    <span>
                      {locale === "zh" ? "资源类别: " : "Type: "}<span className={`font-sans ${styles.cardText}`}>{getObjectTypeLabel(log.resource)}</span>
                    </span>
                    <span>•</span>
                    <span>
                      {t("sec.ledger.voucher")}<span className={`font-bold select-all px-1 py-0.5 rounded-sm bg-black/10 ${styles.cardText}`}>{log.eventId}</span>
                    </span>
                    <span>•</span>
                    <span className="flex items-center gap-1">
                      <span className={`w-1.5 h-1.5 rounded-full ${
                        log.result === "SUCCESS" ? "bg-green-500" : "bg-amber-500 animate-pulse"
                      }`}></span>
                      <span className={log.result === "SUCCESS" ? "text-green-400 font-semibold" : "text-amber-400 font-semibold"}>
                        {log.result === "SUCCESS" ? t("sec.ledger.committed") : t("sec.ledger.pending")}
                      </span>
                    </span>
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* COL 3: SECURITY INSIGHTS FROM AUDIT LOGS */}
        <div className={`rounded-md p-4 flex flex-col justify-between overflow-y-auto scrollbar-thin shadow-xs max-h-full border ${styles.cardBg} ${styles.cardBorder}`}>
          <div className="space-y-4">
            <div>
              <span className={`text-[9px] uppercase font-mono tracking-wider block leading-none ${styles.cardTextMuted}`}>{t("sec.directives.title")}</span>
              <h3 className={`text-xs font-bold mt-1 uppercase font-mono ${styles.cardText}`}>{t("sec.directives.subtitle")}</h3>
              <p className={`text-xs mt-1 leading-normal ${styles.cardTextMuted}`}>
                {insights.total === 0
                  ? (locale === "zh" ? "暂无审计日志数据。" : "No audit log data available yet.")
                  : (locale === "zh" ? `基于 ${insights.total} 条真实审计记录的安全洞察。` : `Security insights derived from ${insights.total} real audit records.`)}
              </p>
            </div>

            {/* Top Actors */}
            {insights.topActors.length > 0 && (
              <div>
                <span className={`text-[10px] uppercase font-mono tracking-wider block mb-2 flex items-center gap-1.5 ${styles.cardTextMuted}`}>
                  <Users className="w-3 h-3 text-blue-400" />
                  {locale === "zh" ? "活跃参与者" : "Top Actors"}
                </span>
                <div className="space-y-1.5">
                  {insights.topActors.map(([actor, count]) => (
                    <div key={actor} className={`flex items-center justify-between text-xs rounded-md px-2.5 py-1.5 border bg-black/20 ${styles.cardBorder}`}>
                      <span className="text-blue-400 font-semibold text-[11px] truncate">{actor}</span>
                      <span className={`font-mono text-[10px] ${styles.cardTextMuted}`}>{count} {locale === "zh" ? "次" : "ops"}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Action Summary */}
            {insights.topActions.length > 0 && (
              <div>
                <span className={`text-[10px] uppercase font-mono tracking-wider block mb-2 flex items-center gap-1.5 ${styles.cardTextMuted}`}>
                  <Activity className="w-3 h-3 text-emerald-400" />
                  {locale === "zh" ? "操作类型" : "Action Types"}
                </span>
                <div className="space-y-1.5">
                  {insights.topActions.map(([action, count]) => (
                    <div key={action} className={`flex items-center justify-between text-xs rounded-md px-2.5 py-1.5 border bg-black/20 ${styles.cardBorder}`}>
                      <span className={`font-medium text-[11px] truncate ${styles.cardText}`}>{action}</span>
                      <span className={`font-mono text-[10px] ${styles.cardTextMuted}`}>{count}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Recent Activity */}
            {insights.recentEvents.length > 0 && (
              <div>
                <span className={`text-[10px] uppercase font-mono tracking-wider block mb-2 flex items-center gap-1.5 ${styles.cardTextMuted}`}>
                  <Clock className="w-3 h-3 text-amber-400" />
                  {locale === "zh" ? "最近活动" : "Recent Events"}
                </span>
                <div className="space-y-1.5">
                  {insights.recentEvents.map((evt) => {
                    const evtDetails = typeof evt.details === 'string' ? evt.details : (evt.details ? JSON.stringify(evt.details) : '');
                    return (
                    <div key={evt.eventId} className={`text-[10px] rounded-md px-2.5 py-1.5 leading-relaxed border bg-black/20 ${styles.cardBorder}`}>
                      <span className="text-blue-400 font-semibold">{evt.userId}</span>
                      <span className={styles.cardTextMuted}> — </span>
                      <span className={styles.cardText}>{evtDetails.length > 60 ? evtDetails.substring(0, 60) + "..." : evtDetails}</span>
                    </div>
                    );
                  })}
                </div>
              </div>
            )}
          </div>

          <div className={`pt-3 mt-4 border-t text-[10px] leading-normal font-mono select-none ${styles.cardBorder} ${styles.cardTextMuted}`}>
            <span className="flex items-center gap-1.5 text-[9px] uppercase tracking-wider text-amber-500 font-bold mb-1 leading-none">
              <Key className="w-3.5 h-3.5 text-amber-500" /> {t("sec.bounds.title")}
            </span>
            <span>{t("sec.bounds.desc")}</span>
          </div>
        </div>

      </div>

    </div>
  );
}
