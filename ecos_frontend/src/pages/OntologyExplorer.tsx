/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect } from "react";
import { Globe, Plus, ArrowRight, Lock, AlertCircle, X } from "lucide-react";
import { EntityDefinition, ActionDefinition, EntityInstance } from "../types";
import { fetchOntology } from "../api";
import GraphCanvas from "../components/GraphCanvas";
import { useTheme } from "../components/ThemeContext";
import { useLanguage } from "../components/LanguageContext";

export default function OntologyExplorer() {
  const [entities, setEntities] = useState<EntityDefinition[]>([]);
  const [selectedEntityId, setSelectedEntityId] = useState<string>("Customer");
  const [instances, setInstances] = useState<Record<string, EntityInstance[]>>({});
  const [activeAction, setActiveAction] = useState<ActionDefinition | null>(null);
  const [actionFormValues, setActionFormValues] = useState<Record<string, any>>({});
  const [successMessage, setSuccessMessage] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const { t, locale } = useLanguage();
  useTheme();

  useEffect(() => {
    fetchOntology().then(data => {
      setEntities(data.entities);
      setInstances(data.instances);
      setLoading(false);
      if (data.entities.length > 0 && !data.entities.find(e => e.id === selectedEntityId)) {
        setSelectedEntityId(data.entities[0].id);
      }
    }).catch((e: any) => { setError(e.message || t("ont.load_error")); setLoading(false); });
  }, []);

  if (loading || entities.length === 0) {
    return (
      <div className="flex-1 flex items-center justify-center bg-slate-50">
        <div className="text-center">
          <Globe className="w-10 h-10 text-indigo-400 mx-auto mb-3 animate-pulse" />
          <p className="text-sm text-slate-500">{t("ont.title")} — {t("ont.loading")}</p>
        </div>
      </div>
    );
  }

  const selectedEntity = entities.find((e) => e.id === selectedEntityId) || entities[0];
  const currentInstances = instances[selectedEntityId] || [];

  const handleTriggerAction = (action: ActionDefinition) => {
    setActiveAction(action);
    const defaults: Record<string, any> = {};
    action.fields.forEach((f) => {
      defaults[f.name] = f.defaultValue !== undefined ? f.defaultValue : "";
    });
    setActionFormValues(defaults);
    setSuccessMessage("");
  };

  const handleFieldChange = (name: string, val: any) => {
    setActionFormValues((prev) => ({ ...prev, [name]: val }));
  };

  const handleActionSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!activeAction) return;

    const isHighImpact = activeAction.impactLevel === "high";

    // State transition logging (audit handled by SecurityAudit internally)

    // Apply outcomes in-memory (e.g. lock customer 123 if suspending account)
    if (activeAction.id === "suspend_account") {
      setInstances((prev) => {
        const list = prev[selectedEntityId] || [];
        return {
          ...prev,
          [selectedEntityId]: list.map((item) => {
            if (item.id === "customer_123") {
              return { ...item, properties: { ...item.properties, isActive: false } };
            }
            return item;
          })
        };
      });
    }

    setSuccessMessage(
      isHighImpact
        ? t("ont.workbench.toast_fail")
        : t("ont.workbench.toast_success")
    );

    setTimeout(() => {
      setActiveAction(null);
      setSuccessMessage("");
    }, 4500);
  };

  // Ontological Entity & properties dictionary mapper
  const getEntityName = (id: string) => {
    if (locale !== "zh") return id;
    const dict: Record<string, string> = {
      Customer: "企业核心客商实体 (Customer)",
      Order: "贸易及结算订单实体 (Order)",
      Facility: "智能厂区物理设备实体 (Facility)"
    };
    return dict[id] || id;
  };

  const getEntityDesc = (id: string, originalDesc: string) => {
    if (locale !== "zh") return originalDesc;
    const dict: Record<string, string> = {
      Customer: "代表全局企业与零售客户的统一属性大名单，可绑定风控流失决策逻辑及账单支付信息。",
      Order: "代表销售单据及结算核扣事件的单据模型，负责维系订单状态转移、去重去漏和级联血缘检验。",
      Facility: "工业互联网背景下对物理厂房电磁装配单元及高频测温机组在元数据层的数字孪生定义。"
    };
    return dict[id] || originalDesc;
  };

  const getPropLabel = (propName: string) => {
    if (locale !== "zh") return propName;
    const dict: Record<string, string> = {
      customerId: "customerId (账号唯一码)",
      email: "email (主注册电邮)",
      revenue: "revenue (累计产生营收)",
      isActive: "isActive (签约生命周期状态)",
      orderId: "orderId (票证唯一哈希)",
      amount: "amount (财务发票总币额)",
      facilityId: "facilityId (厂线设备条码)",
      tempC: "tempC (机组实时测向温度)",
      hzRate: "hzRate (分钟级装配产出率)"
    };
    return dict[propName] || propName;
  };

  const getActionLabel = (actId: string, originalLabel: string) => {
    if (locale !== "zh") return originalLabel;
    const dict: Record<string, string> = {
      suspend_account: "🔒 暂停客户服务链路",
      adjust_credit: "💳 变更综合授信额度",
      process_order: "📦 执行跨境物流对账",
      calibrate_sensor: "🔬 精密仪器测温标定"
    };
    return dict[actId] || originalLabel;
  };

  const getRelLabel = (relName: string) => {
    if (locale !== "zh") return relName;
    const dict: Record<string, string> = {
      placed_orders: "发起了贸易采购单",
      located_at: "部署物理位置归属"
    };
    return dict[relName] || relName;
  };

  const getRelationTargetSuffix = (target: string, cardinality: string) => {
    const localizedTarget = getEntityName(target);
    if (locale === "zh") {
      return `关联目标 ${localizedTarget} (${cardinality === "one-to-many" ? "一对多" : "多对一"})`;
    }
    return `Target ${target} (${cardinality})`;
  };

  // Build standard mock EKG nodes for ontology relationship mapping graph
  const ekgGraphNodes = [
    { id: "Customer", type: "dataset", label: locale === "zh" ? "客商概念实体" : "Customer Object Schema", status: "Healthy" },
    { id: "Order", type: "source", label: locale === "zh" ? "订单概念实体" : "Order Object Schema", status: "Healthy" },
    { id: "Facility", type: "dashboard", label: locale === "zh" ? "物理车间实体" : "Facility Object Schema", status: "Healthy" }
  ];

  const ekgGraphLinks = [
    { id: "ekgl_1", source: "Customer", target: "Order", animated: true },
    { id: "ekgl_2", source: "Facility", target: "Order" }
  ];

  return (
    <div className="flex-1 bg-slate-50 text-slate-800 flex flex-col h-full font-sans overflow-hidden animate-fade-in">
      
      {/* Error Banner */}
      {error && (
        <div className="bg-red-50 border border-red-200 p-3 flex items-center gap-2 text-red-700 text-sm shrink-0">
          <AlertCircle className="w-4 h-4 shrink-0" />
          <span>{error}</span>
          <button onClick={() => setError(null)} className="ml-auto text-red-400 hover:text-red-600">&times;</button>
        </div>
      )}

      {/* 1. Header Information */}
      <div className="bg-white border-b border-slate-200 p-5 shrink-0 flex items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold text-slate-800 flex items-center gap-2">
            <Globe className="text-indigo-650 w-5 h-5 shrink-0" />
            {t("ont.title")}
          </h1>
          <p className="text-xs text-slate-500 mt-1 max-w-2xl leading-relaxed">
            {t("ont.desc")}
          </p>
        </div>

        <button
          onClick={() => alert(t("ont.workbench.alert_prompt"))}
          className="bg-blue-600 hover:bg-blue-700 text-white rounded-lg px-4 py-2 text-xs font-semibold flex items-center gap-2 cursor-pointer transition shadow-xs shrink-0 font-sans border-0"
        >
          <Plus className="w-3.5 h-3.5" />
          {t("ont.btn.define")}
        </button>
      </div>

      {/* 2. Unified Grid Layout */}
      <div className="flex-1 grid grid-cols-1 lg:grid-cols-4 gap-5 p-6 min-h-0 overflow-hidden">
        
        {/* COL 1: LEFT SIDEBAR OBJECT LIST */}
        <div className="lg:col-span-1 bg-white border border-slate-200 rounded-xl p-4 flex flex-col overflow-hidden shadow-xs">
          <div className="text-[10px] font-mono font-bold uppercase tracking-wider text-slate-400 block mb-3 leading-none">{t("ont.sidebar.title")}</div>
          <div className="space-y-1.5 overflow-y-auto flex-1 scrollbar-none">
            {entities.map((entity) => {
              const isSelected = selectedEntityId === entity.id;
              return (
                <button
                  key={entity.id}
                  id={`ont-entity-${entity.id}`}
                  onClick={() => {
                    setSelectedEntityId(entity.id);
                    setActiveAction(null);
                    setSuccessMessage("");
                  }}
                  className={`w-full text-left flex items-center justify-between p-3 rounded-lg transition-all border outline-hidden ${
                    isSelected 
                      ? "bg-indigo-50 border-indigo-250 text-indigo-950 font-bold shadow-2xs" 
                      : "bg-transparent border-transparent hover:bg-slate-50 text-slate-500 hover:text-slate-800"
                  }`}
                >
                  <div className="min-w-0">
                    <span className="text-xs block font-bold truncate">{getEntityName(entity.id)}</span>
                    <span className="text-[10px] text-slate-400 block truncate mt-1">
                      {entity.properties.length} {locale === "zh" ? "项基础属性" : "Properties"}
                    </span>
                  </div>
                  <ArrowRight className={`w-3.5 h-3.5 ${isSelected ? "text-indigo-600" : "text-slate-300"}`} />
                </button>
              );
            })}
          </div>
        </div>

        {/* COL 2: MAIN DYNAMIC WORKBENCH CONTENT */}
        <div className="lg:col-span-2 border border-slate-200 rounded-xl p-5 bg-white flex flex-col overflow-y-auto scrollbar-thin min-h-0 shadow-xs">
          <div className="mb-5 shrink-0">
            <span className="text-[9px] uppercase font-bold font-mono tracking-wider text-slate-400">{t("ont.workbench.title")}</span>
            <h2 className="text-md font-bold text-slate-800 mt-1 uppercase font-mono">{getEntityName(selectedEntity.id)}</h2>
            <p className="text-xs text-slate-500 mt-1.5 leading-relaxed">{getEntityDesc(selectedEntity.id, selectedEntity.description)}</p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-2">
            
            {/* Custom Object Properties */}
            <div className="bg-slate-50 border border-slate-200 rounded-xl p-4">
              <h3 className="text-[10px] font-bold uppercase font-mono tracking-wider text-slate-400 mb-2 leading-none">{t("ont.workbench.props")}</h3>
              <div className="space-y-2 mt-3">
                {selectedEntity.properties.map((prop) => (
                  <div key={prop.name} className="flex items-center justify-between text-xs border-b border-slate-100 pb-1.5">
                    <span className="font-mono text-slate-750 font-medium">{getPropLabel(prop.name)}</span>
                    <div className="flex gap-1.5">
                      <span className="px-2 py-0.5 rounded bg-white border border-slate-200 text-slate-600 text-[9px] font-mono font-medium">
                        {prop.type}
                      </span>
                      {prop.required && (
                        <span className="px-1.5 py-0.5 rounded bg-red-100 border border-red-250 text-red-700 text-[9px] font-semibold leading-none">
                          {t("ont.workbench.required")}
                        </span>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Relations specs card */}
            <div className="bg-slate-50 border border-slate-200 rounded-xl p-4 flex flex-col justify-between gap-4">
              <div>
                <h3 className="text-[10px] font-bold uppercase font-mono tracking-wider text-slate-400 mb-2 leading-none">{t("ont.workbench.rel")}</h3>
                <div className="space-y-2 mt-3">
                  {selectedEntity.relationships.map((rel) => (
                    <div key={rel.name} className="flex items-center justify-between text-xs border-b border-slate-100 pb-1.5">
                      <span className="font-mono text-slate-700 flex items-center gap-1">
                        <ArrowRight className="w-3.5 h-3.5 text-slate-450" />
                        {getRelLabel(rel.name)}
                      </span>
                      <span className="text-slate-500 font-mono text-[10px] font-medium">
                        {getRelationTargetSuffix(rel.targetEntity, rel.cardinality)}
                      </span>
                    </div>
                  ))}
                </div>
              </div>

              <div className="p-3.5 rounded-lg bg-indigo-50 border border-indigo-250 text-[10px] text-indigo-800 font-mono leading-relaxed select-all">
                {t("ont.workbench.constraint")}
              </div>
            </div>

          </div>

          {/* DYNAMIC ACTION TRIGGER GRID */}
          <div className="mt-6 pt-5 border-t border-slate-200">
            <h3 className="text-[10px] font-bold uppercase font-mono tracking-wider text-slate-400 mb-3 leading-none">{t("ont.workbench.actions")}</h3>
            <div className="flex flex-wrap gap-2.5">
              {selectedEntity.actions.map((act) => (
                <button
                  key={act.id}
                  id={`action-btn-${act.id}`}
                  onClick={() => handleTriggerAction(act)}
                  className={`px-4 py-2 rounded-lg border cursor-pointer text-xs font-semibold transition-all flex items-center gap-2 ${
                    act.impactLevel === "high"
                      ? "bg-red-100 hover:bg-red-200 border-red-250 text-red-800 shadow-2xs"
                      : "bg-indigo-100 hover:bg-indigo-200 border-indigo-250 text-indigo-800 shadow-2xs"
                  }`}
                >
                  <span>{getActionLabel(act.id, act.label)}</span>
                </button>
              ))}
            </div>

            {/* DYNAMIC FORM RENDERER DRAWER INTERACTION */}
            {activeAction && (
              <div className="bg-slate-50 border border-slate-200 rounded-xl p-5 mt-5 animate-fade-in text-xs font-sans shadow-2xs text-slate-800">
                <div className="flex justify-between items-center mb-4 border-b border-slate-250 pb-2.5">
                  <span className="font-bold text-slate-800 uppercase tracking-wide font-mono text-[11px] flex items-center gap-1.5">
                    {activeAction.impactLevel === "high" && <Lock className="w-3.5 h-3.5 text-red-600" />}
                    {t("ont.workbench.form_active")} {getActionLabel(activeAction.id, activeAction.label)}
                  </span>
                  <button onClick={() => setActiveAction(null)} className="text-slate-400 hover:text-slate-700 cursor-pointer select-none font-bold border-0 bg-transparent">
                    {t("ont.form.close")}
                  </button>
                </div>

                {successMessage ? (
                  <div
                    className={`p-4 rounded-xl border leading-relaxed text-xs font-semibold ${
                      activeAction.impactLevel === "high"
                        ? "bg-amber-50 border-amber-255 text-amber-800 shadow-2xs"
                        : "bg-emerald-50 border-emerald-255 text-emerald-800 shadow-2xs"
                    }`}
                  >
                    {successMessage}
                  </div>
                ) : (
                  <form onSubmit={handleActionSubmit} className="space-y-4">
                    {activeAction.fields.map((f) => (
                      <div key={f.name}>
                        <label className="block text-slate-600 font-bold mb-1.5 font-sans text-xs">
                          {locale === "zh" && f.label === "Credit Multiplier"
                            ? "授信计算乘率"
                            : locale === "zh" && f.label === "Fulfillment Mode"
                            ? "贸易履行结算模式"
                            : locale === "zh" && f.label === "Calibration Factor"
                            ? "精密温感纠偏校准因子"
                            : f.label} {f.required && <span className="text-red-500">*</span>}
                        </label>
                        {f.type === "string" ? (
                          <input
                            type="text"
                            required={f.required}
                            className="w-full bg-white border border-slate-300 p-2.5 text-xs rounded-lg focus:border-indigo-600 focus:ring-1 focus:ring-indigo-600/15 text-slate-850 outline-hidden focus:outline-hidden"
                            value={actionFormValues[f.name] || ""}
                            onChange={(e) => handleFieldChange(f.name, e.target.value)}
                          />
                        ) : f.type === "number" ? (
                          <input
                            type="number"
                            required={f.required}
                            className="w-full bg-white border border-slate-300 p-2.5 text-xs rounded-lg focus:border-indigo-600 focus:ring-1 focus:ring-indigo-600/15 text-slate-850 outline-hidden focus:outline-hidden"
                            value={actionFormValues[f.name] || ""}
                            onChange={(e) => handleFieldChange(f.name, Number(e.target.value))}
                          />
                        ) : (
                          <select
                            required={f.required}
                            className="w-full bg-white border border-slate-300 p-2.5 text-xs rounded-lg focus:border-indigo-500 text-slate-850 outline-hidden focus:outline-hidden"
                            value={actionFormValues[f.name] || ""}
                            onChange={(e) => handleFieldChange(f.name, e.target.value === "true")}
                          >
                            <option value="true">{t("ont.form.placeholder_true")}</option>
                            <option value="false">{t("ont.form.placeholder_false")}</option>
                          </select>
                        )}
                      </div>
                    ))}

                    <div className="flex justify-end gap-2.5 pt-3 border-t border-slate-200">
                      <button
                        type="button"
                        onClick={() => setActiveAction(null)}
                        className="px-4 py-2 border-0 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-600 font-semibold cursor-pointer"
                      >
                        {t("ont.form.cancel")}
                      </button>
                      <button
                        type="submit"
                        className="px-4.5 py-2 border-0 rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white font-semibold cursor-pointer shadow-sm"
                      >
                        {t("ont.workbench.submit_btn")}
                      </button>
                    </div>
                  </form>
                )}
              </div>
            )}
          </div>
        </div>

        {/* COL 3 & 4: RELATIONSHIP MAP LOCAL GRAPH RENDERING */}
        <div className="lg:col-span-1 bg-white border border-slate-200 rounded-xl p-4 flex flex-col overflow-hidden shadow-xs">
          <div className="text-[10px] font-mono font-bold uppercase tracking-wider text-slate-400 block mb-3 leading-none">{t("ont.workbench.topo")}</div>
          <div className="flex-1 flex overflow-hidden rounded-xl border border-slate-250 bg-slate-50">
            <GraphCanvas nodes={ekgGraphNodes} links={ekgGraphLinks} selectedNodeId={selectedEntityId} interactive={true} />
          </div>
        </div>

      </div>

    </div>
  );
}
