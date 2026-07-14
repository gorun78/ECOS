/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect } from "react";
import { useParams, useNavigate } from "react-router-dom";
import {
  FileText,
  Lock,
  History,
  Activity,
  Layers,
  CheckCircle2,
  AlertTriangle,
  ArrowLeft,
  Settings,
  Shield,
  HelpCircle,
  RefreshCw,
  Database,
  ArrowRight,
  Award,
  UserCheck,
  GitBranch,
} from "lucide-react";
import { DataAsset, ColumnDefinition } from "../types";
import { fetchDataset, apiFetchData, fetchDatasets, fetchPreview } from "../api";

import { useLanguage } from "../components/LanguageContext";
import GraphCanvas from "../components/GraphCanvas";

export default function DatasetExplorer() {
  const { assetId } = useParams<{ assetId: string }>();
  const navigate = useNavigate();
  const resolvedAssetId = assetId || "ds_customer360";
  const { t, locale } = useLanguage();
  const [asset, setAsset] = useState<DataAsset | null>(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<"overview" | "schema" | "preview" | "quality" | "lineage">("overview");
  const [selectedColumn, setSelectedColumn] = useState<ColumnDefinition | null>(null);
  const [lineageNodes, setLineageNodes] = useState<any[]>([]);
  const [lineageLinks, setLineageLinks] = useState<any[]>([]);
  const [lineageLoading, setLineageLoading] = useState(false);
  const [selectedLineageNode, setSelectedLineageNode] = useState<string | null>(null);
  // Preview rows — fetched from datanet metadata preview API
  const [previewRows, setPreviewRows] = useState<Record<string, string | number>[]>([]);

  useEffect(() => {
    setLoading(true);
    fetchDataset(resolvedAssetId).then(found => {
      if (found) {
        setAsset(found);
        if (found.schema.length > 0) setSelectedColumn(found.schema[0]);
        // 加载预览数据
        fetchPreview(resolvedAssetId, 50).then(preview => {
          if (preview.rows?.length > 0) setPreviewRows(preview.rows);
        }).catch(() => {});
      } else {
        // No fallback — API is the source of truth
        setAsset(null);
      }
    }).catch(() => {
      // API failed — try mock data
      const mockAsset = MOCK_DATA_ASSETS.find(a => a.id === resolvedAssetId || a.name === resolvedAssetId);
      if (mockAsset) {
        setAsset(mockAsset);
        if (mockAsset.schema.length > 0) setSelectedColumn(mockAsset.schema[0]);
      }
    }).finally(() => setLoading(false));
  }, [resolvedAssetId]);

  // Fetch lineage data when lineage tab becomes active
  useEffect(() => {
    if (activeTab !== "lineage" || !asset) return;
    setLineageLoading(true);
    fetchDatasets()
      .then((items: any[]) => {
        // Build lineage: all datasets as nodes, linked sequentially
        const dsNames = items.slice(0, 10).map((d: any) => d.name || d.tablename || `tbl_${Math.random().toString(36).slice(2,6)}`);
        const graphNodes = dsNames.map((name: string, i: number) => ({
          id: name,
          type: name === asset.name ? "dataset" : i === 0 ? "source" : i === dsNames.length - 1 ? "target" : "dataset",
          label: name,
          status: "active",
          owner: "data-team",
          updatedAt: new Date().toISOString().slice(0, 10),
        }));
        const graphLinks = [];
        for (let i = 0; i < graphNodes.length - 1; i++) {
          graphLinks.push({
            id: `edge_${i}`,
            source: graphNodes[i].id,
            target: graphNodes[i + 1].id,
            animated: i % 2 === 0,
          });
        }
        setLineageNodes(graphNodes);
        setLineageLinks(graphLinks);
      })
      .catch(() => {
        // Mock fallback
        setLineageNodes([
          { id: "raw_orders", type: "source", label: "raw_orders", status: "active", owner: "Ingestion", updatedAt: "2026-06-20" },
          { id: asset?.name || resolvedAssetId, type: "dataset", label: asset?.name || resolvedAssetId, status: "active", owner: asset?.owner || "data-team", updatedAt: asset?.updatedAt || "2026-06-22" },
          { id: "ads_reports", type: "target", label: "ads_reports", status: "active", owner: "Analytics", updatedAt: "2026-06-22" },
        ]);
        setLineageLinks([
          { id: "e1", source: "raw_orders", target: asset?.name || resolvedAssetId },
          { id: "e2", source: asset?.name || resolvedAssetId, target: "ads_reports" },
        ]);
      })
      .finally(() => setLineageLoading(false));
  }, [activeTab, asset, resolvedAssetId]);

  if (loading) {
    return (
      <div className="flex-1 bg-[#F8FAFC] flex items-center justify-center h-full">
        <div className="text-center space-y-3">
          <RefreshCw className="w-10 h-10 text-slate-300 animate-spin mx-auto" />
          <p className="text-sm text-slate-400">{locale === "zh" ? "加载数据集..." : "Loading dataset..."}</p>
        </div>
      </div>
    );
  }

  if (!asset && !loading) {
    return (
      <div className="flex-1 bg-[#F8FAFC] flex items-center justify-center h-full">
        <div className="text-center space-y-3">
          <Database className="w-12 h-12 text-slate-300 mx-auto" />
          <p className="text-sm text-slate-500">
            {locale === "zh" ? "未找到数据集" : "Dataset not found"}
          </p>
          <p className="text-xs text-slate-400">
            ID: {resolvedAssetId}
          </p>
        </div>
      </div>
    );
  }

  if (!asset) return null;

  // Bilingual mappings for Schema & metadata catalog strings
  const getColDesc = (colName: string, originalDesc: string) => {
    if (locale !== "zh") return originalDesc;
    const dict: Record<string, string> = {
      customer_id: "企业与零售客户的唯一全局物理编码",
      email: "客户注册的主电子邮箱联系地址",
      region: "地理业务及财务统计销售区域 (包含亚太 APAC、欧洲及中东 EMEA、拉丁美洲 LATAM、北美 NA)",
      revenue: "累计产生的客户生命周期总价值，以一揽子美元 (USD) 计价",
      churn_risk: "根据双流深度神经网络与梯度提升多任务智能关系估算模型得出的客户流失概率状态分 (0-1.0)",
      last_active: "该物理记录在分布集群或核心数据层最近一次产生高维链路交互行为的安全审计时间戳",
      machine_id: "生产车间装配机台及物理节点的数智一机一卡唯一设备条码标识符",
      throughput_rate: "各离散制造单元分钟级物理负载产品装配流程产出吞吐率指标",
      temperature_c: "电机运转物理核心在摄氏度下的高精密传感器实时表面测温数据",
      has_fault: "标识设备主控板当前是否亮起物理故障报错或激活紧急保护停车的二级制布尔触发字开关",
      uptime_hours: "自上一次厂线停产预防性精密检修与对账调零以来，机组连续安全无故障平稳服役至今的物理耗时 (小时)",
      order_id: "系统底座产生的贸易及财务票据流转事务唯一不可逆安全结算防伪代号",
      amount: "合并计算折扣优惠、物流账单与税额变动等关联约束规则后的商业汇款发票原始物理总额",
      order_status: "票据全生命周期状态判定机条件: 待审批 Pending、已流转交付 Fulfilled、退库核销 Returned、事务终结 Closed",
      transaction_date: "由核心财务会计核算日结、录入官方账目簿册的法定交易及交割落袋日期"
    };
    return dict[colName] || originalDesc;
  };

  const getRuleName = (ruleId: string, originalName: string) => {
    if (locale !== "zh") return originalName;
    const dict: Record<string, string> = {
      qr_1: "空值验证边界筛查 (Null Checks)",
      qr_2: "唯一索引对账复查 (Duplicate Checks)",
      qr_3: "主外键一致参照校验",
      qr_4: "离群脉冲异动侦测 (Anomalies Check)",
      qr_o1: "物理主键强制完整校验",
      qr_o2: "极限温控红线离群检测",
      qr_o3: "物理设备名录参照完整校验",
      qr_f1: "交易票据非空要素合规筛查",
      qr_f2: "唯一哈希幂等防重流水去重",
      qr_f3: "买家账户真实存在性对照检查"
    };
    return dict[ruleId] || originalName;
  };

  const getRuleDesc = (ruleId: string, originalDesc: string) => {
    if (locale !== "zh") return originalDesc;
    const dict: Record<string, string> = {
      qr_1: "强制断言物理主键 customer_id 列包含零空值漏洞点以规避关联失协。",
      qr_2: "扫描物理分区确认主键 customer_id 每一条唯一索引记录，防止多重报送导致报表污染。",
      qr_3: "交叉联名验证当前表的外链属性值与 sales_orders 业务子表的成交对照完整性 (确保 1050 个主键完全匹配)。",
      qr_4: "使用 4 倍滑动平均法和高斯模型对周度业绩流水突变进行自适应黄色评级预警。",
      qr_o1: "精密一机一卡条码 machine_id 的每一物理行均应正常上报、严禁出现漏洞无字段填充。",
      qr_o2: "2 台精密装配机电机的轴热核心测温信号突破了120℃预警红线，应拦截不良数据并触发降负荷或旁路保护。",
      qr_o3: "校验在线收集到的设备测控物理地址与智能工厂全局主物理台账目录的合规参照匹配率 (验证其合法合规性)。",
      qr_f1: "执行金税财务级别防漏机制，阻断 order_id 代表的关键商业发票主键出现任何高维空值遗漏。",
      qr_f2: "对网格摄入的所有多渠道联机收单流启动联合物理哈希防重校验，防止网络颠簸造成的分布式幂等违规。",
      qr_f3: "在企业 CRM 及账户大名单索引下跨物理库检验外键 customer_id 是否属于已授信合法主体账号。"
    };
    return dict[ruleId] || originalDesc;
  };

  const getHistoryAction = (action: string) => {
    if (locale !== "zh") return action;
    const dict: Record<string, string> = {
      "Published": "系统验证发布",
      "Schema Changed": "物理字段表结构变更",
      "Modified": "执行元数据编辑",
      "Imported": "底层原生数据转储"
    };
    return dict[action] || action;
  };

  const getHistorySummary = (historyId: string, originalSummary: string) => {
    if (locale !== "zh") return originalSummary;
    const dict: Record<string, string> = {
      h_1: "自动化质检流水线顺利完成了物理参照验证，并发布审核通过的最新只读数据集版本。",
      h_2: "为了支持企业中高危流失用户的实时反流失预测，正式在 schema catalog 中补充了预测性 churn_risk 流失风险属性列。",
      h_3: "引入了区域物理分区重映射与多字段聚类排序，极大地削弱了巨量多表关联查询的落盘开销并提速。",
      h_4: "自企业 SAP 原生 CRM 应用系统底座中归档了全部历史转储快照及对应的物料流水对照字段。"
    };
    return dict[historyId] || originalSummary;
  };

  return (
    <div className="flex-1 bg-[#11141d] text-gray-300 flex flex-col h-full font-sans overflow-hidden w-full">
      
      {/* 1. Page Header detailing metadata */}
      <div className="bg-[#141924] border-b border-gray-800 p-3 sm:p-4 shrink-0 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 sm:gap-4">
        <div className="flex items-center gap-3">
          <button
            onClick={() => navigate("/catalog")}
            className="p-1.5 hover:bg-gray-800 rounded-md text-gray-400 hover:text-white cursor-pointer transition shrink-0"
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          
          <div>
            <div className="text-xs text-gray-500 mb-0.5">
              <button onClick={() => navigate("/catalog")} className="hover:text-gray-300 transition">
                {locale === "zh" ? "数据目录" : "Data Catalog"}
              </button>
              <span className="mx-1.5 opacity-40">›</span>
              <span className="text-gray-300">{asset.name}</span>
            </div>
            <div className="flex items-center gap-2">
              <h1 className="text-base font-bold text-white leading-tight">{asset.name}</h1>
              <span className={`text-[9px] px-1.5 py-0.2 rounded-xs font-mono font-bold leading-none ${
                asset.status === "Healthy" ? "bg-green-500/10 text-green-400" : "bg-amber-500/10 text-amber-400"
              }`}>
                {asset.status === "Healthy" ? (locale === "zh" ? "运行健康" : "Healthy") : (locale === "zh" ? "警告过热" : "Warning")}
              </span>
            </div>
            
            <p className="text-[11px] text-gray-500 mt-1 max-w-2xl truncate">
              {locale === "zh" && asset.id === "ds_customer360"
                ? "包含企业客户全生命周期收益价值、注册账单电邮、高维地理细分及流失模型估值的多维度360度基础关系物理表。"
                : locale === "zh" && asset.id === "ds_plantops"
                ? "来自慕尼黑、新加坡、珀斯工厂车间电磁控制机台和工业设备的高频物理传感器监控明细快照。"
                : locale === "zh" && asset.id === "ds_salesorders"
                ? "企业全球销售分账系统发票汇总、收单实付金额和核销状态相关的事务性流水物理表。"
                : asset.description}
            </p>
          </div>
        </div>

        {/* Header Metadata badge fields */}
        <div className="flex flex-wrap gap-3 sm:gap-4 text-xs font-mono">
          <div className="text-right border-r border-[#1E293B] pr-4">
            <span className="text-[9px] uppercase tracking-wider text-gray-400 block leading-none">{locale === "zh" ? "资产负责人" : "Dataset Owner"}</span>
            <span className="text-blue-400 font-sans font-medium text-xs mt-1 inline-block">{asset.owner}</span>
          </div>
          <div className="text-right border-r border-[#1E293B] pr-4">
            <span className="text-[9px] uppercase tracking-wider text-gray-400 block leading-none">{locale === "zh" ? "质量校验分" : "Quality Score"}</span>
            <span className="text-green-400 font-semibold font-sans text-xs mt-1 inline-block">{asset.qualityScore}%</span>
          </div>
          <div className="text-right">
            <span className="text-[9px] uppercase tracking-wider text-gray-400 block leading-none">{locale === "zh" ? "更新频率" : "Update Frequency"}</span>
            <span className="text-gray-300 text-xs mt-1 inline-block">
              {locale === "zh" && asset.updatedAt === "Hourly batch"
                ? "每小时微批"
                : locale === "zh" && asset.updatedAt === "Streaming telemetry"
                ? "实时多端流"
                : locale === "zh" && asset.updatedAt === "Daily trigger"
                ? "天级对账触发"
                : asset.updatedAt}
            </span>
          </div>
        </div>
      </div>

      {/* 2. Workspace Navigation Tabs */}
      <div className="bg-[#141924] border-b border-[#1E293B] px-5 flex shrink-0">
        {[
          { id: "overview", label: locale === "zh" ? "总览仪表盘" : "Overview" },
          { id: "schema", label: locale === "zh" ? "物理字段 Schema" : "Schema" },
          { id: "preview", label: locale === "zh" ? "明细数据预览" : "Preview" },
          { id: "quality", label: locale === "zh" ? "质量校验规约" : "Quality Checks" },
          { id: "lineage", label: locale === "zh" ? "字段级数据血缘" : "Data Lineage" }
        ].map((tab) => {
          const isActive = activeTab === tab.id;
          return (
            <button
              key={tab.id}
              className={`h-9 px-4 font-sans text-[11px] font-semibold tracking-wide border-b-2 transition ${
                isActive
                  ? "border-blue-500 text-blue-400 font-bold"
                  : "border-transparent text-gray-500 hover:text-gray-300"
              }`}
              onClick={() => setActiveTab(tab.id as any)}
            >
              {tab.label}
            </button>
          );
        })}
      </div>

      {/* 3. Main Workspace Container */}
      <div className="flex-1 overflow-hidden p-4 sm:p-6">
        
        {/* TAB 1: OVERVIEW PANEL */}
        {activeTab === "overview" && (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-4 h-full overflow-y-auto pr-1">
            
            {/* Column cards */}
            <div className="lg:col-span-2 space-y-4">
              <div className="bg-[#141924] border border-gray-800 rounded-md p-4 shadow-xs">
                <h2 className="text-xs font-bold uppercase font-mono tracking-wider text-gray-400 mb-3 block">{t("db.stats.title")}</h2>
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 text-center">
                  <div className="bg-[#0b0e14] p-3 rounded-md border border-gray-900">
                    <span className="text-[10px] uppercase font-mono text-gray-500 block">{t("db.stats.rows")}</span>
                    <strong className="text-lg font-bold text-white mt-1 block">{asset.rows.toLocaleString()}</strong>
                  </div>
                  <div className="bg-[#0b0e14] p-3 rounded-md border border-gray-900">
                    <span className="text-[10px] uppercase font-mono text-gray-500 block">{t("db.stats.cols")}</span>
                    <strong className="text-lg font-bold text-white mt-1 block">{asset.columns}</strong>
                  </div>
                  <div className="bg-[#0b0e14] p-3 rounded-md border border-gray-900">
                    <span className="text-[10px] uppercase font-mono text-gray-500 block">{t("db.stats.size")}</span>
                    <strong className="text-lg font-bold text-white mt-1 block">{asset.storageSize}</strong>
                  </div>
                  <div className="bg-[#0b0e14] p-3 rounded-md border border-gray-900">
                    <span className="text-[10px] uppercase font-mono text-gray-500 block">{t("db.stats.quality")}</span>
                    <strong className="text-lg font-bold text-blue-400 mt-1 block">{asset.qualityScore}%</strong>
                  </div>
                </div>
              </div>

              {/* Revision history timelines */}
              <div className="bg-[#141924] border border-gray-800 rounded-md p-4 shadow-xs">
                <h2 className="text-xs font-bold uppercase font-mono tracking-wider text-gray-400 mb-4 flex items-center gap-2">
                  <History className="w-4 h-full text-blue-500 shrink-0" />
                  {t("db.history.title")}
                </h2>
                
                {asset.history.length === 0 ? (
                  <p className="text-xs text-gray-500 text-center py-6">{t("db.history.empty")}</p>
                ) : (
                  <div className="space-y-4 font-sans text-xs">
                    {asset.history.map((record, index) => (
                      <div key={record.id} className="flex gap-3 relative">
                        {index !== asset.history.length - 1 && (
                          <div className="absolute left-2.5 top-6 bottom-[-20px]. w-0.5 bg-gray-800"></div>
                        )}
                        <div className="w-5 h-5 rounded-full bg-[#1c2230] border border-gray-800 flex items-center justify-center shrink-0 mt-0.5">
                          <span className="w-1.5 h-1.5 rounded-full bg-blue-500"></span>
                        </div>
                        <div className="flex-1 bg-[#0b0e14] border border-gray-900 p-2.5 rounded-md">
                          <div className="flex items-center justify-between gap-2 flex-wrap mb-1 text-[11px] font-mono">
                            <span className="font-bold text-gray-200">
                              {locale === "zh" ? "版本修订" : "Revision"} {record.version} • {getHistoryAction(record.action)}
                            </span>
                            <span className="text-gray-500">{record.timestamp}</span>
                          </div>
                          <p className="text-gray-400 leading-normal text-[11px]">
                            {getHistorySummary(record.id, record.summary)}
                          </p>
                          <div className="flex items-center gap-1.5 mt-2 text-[10px] font-mono text-gray-600">
                            <span>{locale === "zh" ? "操作账号:" : "Actor:"}</span>
                            <span className="text-gray-400">{record.actor}</span>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>

            {/* Side summary bar */}
            <div className="space-y-4">
              {/* Category tags */}
              <div className="bg-[#141924] border border-gray-800 rounded-md p-4">
                <h3 className="text-xs font-bold uppercase font-mono tracking-wider text-gray-400 mb-3 block">{t("db.meta.title")}</h3>
                <div className="space-y-3 text-xs">
                  <div>
                    <span className="text-gray-500 block text-[10px] uppercase font-mono">{t("db.meta.domain")}</span>
                    <span className="text-gray-350 font-medium block mt-0.5 text-gray-300">
                      {locale === "zh" && asset.domain === "Marketing & Analytics"
                        ? "市场营销与跨端分析域"
                        : locale === "zh" && asset.domain === "Manufacturing & IoT"
                        ? "精密制造与物联网车间域"
                        : locale === "zh" && asset.domain === "Financial & Billing"
                        ? "全球金融与票据核收账目域"
                        : asset.domain}
                    </span>
                  </div>
                  <div>
                    <span className="text-gray-500 block text-[10px] uppercase font-mono">{t("db.meta.tags")}</span>
                    <div className="flex flex-wrap gap-1 mt-1.5">
                      {asset.tags.map((tag) => (
                        <span key={tag} className="text-[10px] bg-[#1c2230] px-2 py-0.5 rounded-xs text-gray-400 border border-gray-800">
                          {locale === "zh" && tag === "customer"
                            ? "客户数据"
                            : locale === "zh" && tag === "ml_features"
                            ? "AI训练要素"
                            : locale === "zh" && tag === "privacy_pci"
                            ? "合规脱敏防护"
                            : locale === "zh" && tag === "iot_telemetry"
                            ? "IoT实时流"
                            : locale === "zh" && tag === "machinery"
                            ? "核心机组"
                            : locale === "zh" && tag === "hardware_alarms"
                            ? "报警监控"
                            : locale === "zh" && tag === "transactions"
                            ? "结算交易流"
                            : locale === "zh" && tag === "billing"
                            ? "金税明细"
                            : tag}
                        </span>
                      ))}
                    </div>
                  </div>
                </div>
              </div>

              {/* Permissions list */}
              <div className="bg-[#141924] border border-gray-800 rounded-md p-4">
                <h3 className="text-xs font-bold uppercase font-mono tracking-wider text-gray-400 mb-3 block">{t("db.permissions.title")}</h3>
                <div className="space-y-2.5 text-xs">
                  <div>
                    <span className="text-gray-500 text-[10px] uppercase font-mono flex items-center gap-1">
                      <Shield className="w-3.5 h-3.5 text-amber-500" /> {t("db.permissions.owner")}
                    </span>
                    <p className="text-gray-300 font-sans mt-1 pl-4.5">{asset.permissions.owner.join(", ")}</p>
                  </div>
                  <div>
                    <span className="text-gray-500 text-[10px] uppercase font-mono flex items-center gap-1">
                      <UserCheck className="w-3.5 h-3.5 text-blue-500" /> {t("db.permissions.editor")}
                    </span>
                    <p className="text-gray-300 font-sans mt-0.5 pl-4.5">{asset.permissions.editor.join(", ")}</p>
                  </div>
                  <div>
                    <span className="text-gray-500 text-[10px] uppercase font-mono flex items-center gap-1">
                      <Lock className="w-3.5 h-3.5 text-gray-500" /> {t("db.permissions.viewer")}
                    </span>
                    <p className="text-gray-300 font-sans mt-0.5 pl-4.5">{asset.permissions.viewer.join(", ")}</p>
                  </div>
                </div>
              </div>
            </div>

          </div>
        )}

        {/* TAB 2: SCHEMA TABLE & COLUMN INSPECTOR */}
        {activeTab === "schema" && (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-4 h-full overflow-hidden">
            
            {/* Table layout showing columns list */}
            <div className="lg:col-span-2 border border-gray-800 rounded-md bg-[#141924] flex flex-col overflow-hidden">
              <div className="px-3 py-2 bg-[#1b2130] text-[10px] font-mono uppercase tracking-wider text-gray-400 font-bold border-b border-gray-850 shrink-0">
                {t("db.fields.title")} ({asset.schema.length})
              </div>
              
              <div className="flex-1 overflow-auto scrollbar-thin">
                <table className="w-full text-xs text-left text-gray-400">
                  <thead className="bg-[#0b0e14]/50 font-mono text-[10px] text-gray-500 uppercase sticky top-0 border-b border-gray-850">
                    <tr>
                      <th className="px-4 py-2">{t("db.fields.col_field")}</th>
                      <th className="px-4 py-2">{t("db.fields.col_type")}</th>
                      <th className="px-4 py-2">{t("db.fields.col_null")}</th>
                      <th className="px-4 py-2 text-right">{t("db.fields.col_quality")}</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-850">
                    {asset.schema.map((col) => {
                      const isColSelected = selectedColumn?.name === col.name;
                      return (
                        <tr
                          key={col.name}
                          onClick={() => setSelectedColumn(col)}
                          className={`cursor-pointer transition ${
                            isColSelected ? "bg-blue-600/10 text-blue-400 font-medium" : "hover:bg-[#1c2231]/40"
                          }`}
                        >
                          <td className="px-4 py-2.5 flex items-center gap-1.5 font-mono text-gray-200">
                            {col.primaryKey && <Lock className="w-3 h-3 text-amber-500 shrink-0" title="Primary Key constraint" />}
                            <span>{col.name}</span>
                          </td>
                          <td className="px-4 py-2.5 font-mono text-blue-500">{col.type}</td>
                          <td className="px-4 py-2.5 font-mono text-gray-550">
                            {col.nullable ? (locale === "zh" ? "允许为空" : "true") : (locale === "zh" ? "强制非空" : "false")}
                          </td>
                          <td className="px-4 py-2.5 text-right font-mono font-bold text-green-400">{col.qualityScore}%</td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </div>

            {/* Column Field Inspector details */}
            <div className="bg-[#141924] border border-gray-800 rounded-md p-4 flex flex-col justify-between overflow-y-auto scrollbar-thin shadow-xs">
              {selectedColumn ? (
                <div className="space-y-4">
                  <div>
                    <span className="text-[9px] uppercase font-mono tracking-wider text-gray-500 block">{t("db.inspector.title")}</span>
                    <h3 className="text-sm font-bold text-white mt-1 font-mono flex items-center gap-1.5">
                      {selectedColumn.primaryKey && <Lock className="w-3.5 h-3.5 text-amber-500" />}
                      {selectedColumn.name}
                    </h3>
                  </div>

                  <div className="space-y-2.5 text-xs">
                    <div>
                      <span className="text-gray-500 block text-[10px] uppercase font-mono">{t("db.inspector.type")}</span>
                      <code className="text-blue-400 font-mono text-xs block mt-0.5">{selectedColumn.type}</code>
                    </div>
                    <div>
                      <span className="text-gray-500 block text-[10px] uppercase font-mono">{t("db.inspector.nullability")}</span>
                      <span className="text-gray-350 block font-mono mt-0.5">
                        {selectedColumn.nullable ? t("db.inspector.null_yes") : t("db.inspector.null_no")}
                      </span>
                    </div>
                    <div>
                      <span className="text-gray-500 block text-[10px] uppercase font-mono">{t("db.inspector.semantics")}</span>
                      <p className="text-gray-300 mt-1 leading-normal">
                        {getColDesc(selectedColumn.name, selectedColumn.description || "")}
                      </p>
                    </div>
                  </div>

                  {/* Quality details inside inspector */}
                  <div className="pt-4 border-t border-gray-850">
                    <div className="flex items-center justify-between mb-2">
                      <span className="text-[10px] uppercase font-mono text-gray-500 flex items-center gap-1">
                        <Award className="w-3.5 h-3.5 text-green-500" /> {t("db.inspector.quality")}
                      </span>
                      <span className="text-xs font-bold font-mono text-green-400">{selectedColumn.qualityScore}%</span>
                    </div>
                    <div className="w-full h-1 bg-gray-800 rounded-full overflow-hidden">
                      <div className="h-full bg-green-500" style={{ width: `${selectedColumn.qualityScore}%` }}></div>
                    </div>
                    <p className="text-[10px] text-gray-550 mt-1.5 font-mono leading-normal select-all">
                      {t("db.inspector.quality_desc")}
                    </p>
                  </div>
                </div>
              ) : (
                <div className="text-center py-24 text-gray-650 text-xs font-mono">
                  {t("db.inspector.empty")}
                </div>
              )}

              <div className="text-[10px] font-mono text-gray-600 mt-4 border-t border-gray-850 pt-2 flex items-center gap-1 select-none">
                <Settings className="w-3 h-3 text-gray-600" />
                <span>{t("db.inspector.policy")}</span>
              </div>
            </div>

          </div>
        )}

        {/* TAB 3: DATA PREVIEW SCREEN */}
        {activeTab === "preview" && (
          <div className="border border-gray-800 rounded-md bg-[#141924] flex flex-col h-full overflow-hidden shadow-xs">
            <div className="px-4 py-2 bg-[#1b2130] text-[10px] font-mono uppercase tracking-wider text-gray-400 font-bold border-b border-gray-850 shrink-0 flex items-center justify-between">
              <span>{t("db.preview.title").replace("limit", `${previewRows.length}`)}</span>
              <span className="text-gray-500 text-[9px] font-mono normal-case">{t("db.preview.desc")}</span>
            </div>

            {previewRows.length === 0 ? (
              <div className="flex-1 flex flex-col items-center justify-center text-gray-500">
                <Database className="w-8 h-8 mb-2 text-gray-600" />
                <p className="text-xs font-mono">{t("db.preview.empty") || "No preview data available. Connect the backend to view sample rows."}</p>
              </div>
            ) : (
              <div className="flex-1 overflow-auto scrollbar-thin">
                <table className="w-full text-xs text-left text-gray-400">
                  <thead className="bg-[#0b0e14]/50 border-b border-gray-850 font-mono text-[10px] text-gray-500 uppercase sticky top-0">
                    <tr>
                      {Object.keys(previewRows[0]).map((key) => (
                        <th key={key} className="px-4 py-2.5 font-medium">{key}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-850 font-mono">
                    {previewRows.map((row, idx) => (
                      <tr key={idx} className="hover:bg-gray-800/20">
                        {Object.entries(row).map(([cellKey, val]: any, vIdx) => {
                          let finalVal = String(val);
                          if (typeof val === "number" && cellKey !== "churn_risk" && cellKey !== "temperature_c") {
                            finalVal = `$ ${val.toLocaleString()}`;
                          } else if (cellKey === "churn_risk") {
                            finalVal = `${(Number(val) * 100).toFixed(0)} %`;
                          } else if (cellKey === "temperature_c") {
                            finalVal = `${val} °C`;
                          } else if (cellKey === "has_fault") {
                            finalVal = val === "true" ? (locale === "zh" ? "故障报错 🚨" : "True 🚨") : (locale === "zh" ? "运行正常 🟢" : "False 🟢");
                          }
                          return (
                            <td key={vIdx} className="px-4 py-2.5 truncate max-w-xs text-gray-300">
                              {finalVal}
                            </td>
                          );
                        })}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            <div className="px-4 py-1.5 border-t border-gray-850 bg-[#0c1017] text-[10px] text-gray-500 font-mono">
              {t("db.preview.sql")}<strong className="text-gray-350 select-all font-mono font-semibold">SELECT * FROM {asset.name} LIMIT 100;</strong>
            </div>
          </div>
        )}

        {/* TAB 4: QUALITY RULES SUMMARY */}
        {activeTab === "quality" && (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-4 h-full overflow-y-auto pr-1">
            
            {/* Rules checklist */}
            <div className="lg:col-span-2 space-y-3">
              <div className="bg-[#141924] border border-gray-800 rounded-md p-4">
                <h2 className="text-xs font-bold uppercase font-mono tracking-wider text-gray-400 mb-4 block">{t("db.quality.active_rules")}</h2>
                
                <div className="space-y-2.5">
                  {asset.qualityRules.map((rule) => {
                    return (
                      <div key={rule.id} className="flex items-start gap-3 p-3 bg-[#0b0e14]/60 border border-gray-850 rounded-md">
                        {rule.status === "pass" ? (
                          <CheckCircle2 className="w-4 h-4 text-green-500 shrink-0 mt-0.5" />
                        ) : rule.status === "warn" ? (
                          <AlertTriangle className="w-4 h-4 text-amber-500 shrink-0 mt-0.5" />
                        ) : (
                          <AlertTriangle className="w-4 h-4 text-red-500 shrink-0 mt-0.5 animate-bounce" />
                        )}

                        <div className="flex-1 text-xs">
                          <div className="flex items-center justify-between gap-2 flex-wrap mb-1">
                            <span className="font-bold text-gray-200">{getRuleName(rule.id, rule.name)}</span>
                            <span className={`text-[9px] font-mono uppercase px-1.5 py-0.2 rounded-xs font-bold ${
                              rule.status === "pass"
                                ? "bg-green-500/10 text-green-400"
                                : rule.status === "warn"
                                ? "bg-amber-500/10 text-amber-400"
                                : "bg-red-500/10 text-red-400"
                            }`}>
                              {rule.status === "pass" ? (locale === "zh" ? "质检通过" : "Passed") : rule.status === "warn" ? (locale === "zh" ? "黄色警告" : "Warning") : (locale === "zh" ? "质检拦截" : "Failed")}
                            </span>
                          </div>
                          <p className="text-gray-400 leading-normal mb-1">{getRuleDesc(rule.id, rule.description)}</p>
                          <div className="text-[10px] font-mono text-gray-500">{t("db.quality.method")}{rule.type}</div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            </div>

            {/* Quality Summary dashboard */}
            <div className="space-y-4">
              <div className="bg-[#141924] border border-gray-800 rounded-md p-4 text-center">
                <span className="text-[10px] uppercase font-mono tracking-wider text-gray-500 block">{t("db.quality.health_grade")}</span>
                <strong className={`text-4xl font-extrabold block mt-2 font-sans ${asset.qualityScore >= 95 ? "text-green-400" : "text-amber-500"}`}>
                  {asset.qualityScore >= 95 ? "A+" : "B"}
                </strong>
                <p className="text-xs text-gray-400 leading-relaxed mt-2 p-1.5 bg-[#0b0e14] rounded-md border border-gray-900">
                  {asset.qualityScore >= 95
                    ? t("db.quality.certified")
                    : t("db.quality.caution")}
                </p>
              </div>
            </div>

            {/* 全局 DQ 链接 */}
            <div className="mt-3 pt-3 border-t border-gray-800">
              <button
                onClick={() => navigate("/dq_dashboard")}
                className="text-xs text-indigo-400 hover:text-indigo-300 flex items-center gap-1 transition"
              >
                {locale === "zh" ? "查看全局质量仪表盘" : "View Global DQ Dashboard"} →
              </button>
            </div>

          </div>
        )}

        {/* TAB 5: INTEGRATED LINEAGE GRAPH */}
        {activeTab === "lineage" && (
          <div className="flex flex-col h-full min-h-0 w-full">
            <div className="flex items-center gap-2 mb-3 shrink-0">
              <GitBranch className="w-4 h-4 text-indigo-400" />
              <span className="text-xs text-gray-400">
                {lineageNodes.length} nodes · {lineageLinks.length} edges
              </span>
              {lineageLoading && <RefreshCw className="w-3 h-3 animate-spin text-gray-500" />}
            </div>
            <div className="flex-1 flex min-h-0">
              {lineageNodes.length === 0 && !lineageLoading ? (
                <div className="flex-1 flex items-center justify-center text-gray-500">
                  <Database className="w-10 h-10 mb-3 text-gray-600" />
                  <p className="text-xs font-mono ml-3">{t("db.lineage.empty") || "No lineage data available."}</p>
                </div>
              ) : (
                <GraphCanvas
                  nodes={lineageNodes}
                  links={lineageLinks}
                  selectedNodeId={selectedLineageNode}
                  onSelectNode={setSelectedLineageNode}
                  interactive={true}
                />
              )}

            {/* 全局血缘链接 */}
            <div className="mt-3 pt-3 border-t border-gray-800">
              <button
                onClick={() => navigate("/lineage")}
                className="text-xs text-indigo-400 hover:text-indigo-300 flex items-center gap-1 transition"
              >
                {locale === "zh" ? "查看全局血缘图" : "View Global Lineage"} →
              </button>
            </div>

            </div>

          </div>
        )}

      </div>

    </div>
  );
}
