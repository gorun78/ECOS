/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 *
 * Databridge C2EOS Cognitive Decision Suite & Blueprint Analyzer
 *
 * 数据来源约束（前端检视报告 §4.1）：
 * - 蓝图层数据只来自 GET /api/v1/cognitive/blueprint；接口不可达时整页降级为「示意数据」
 *   （降级横幅 + DemoBadge + 重试按钮），不再静默使用硬编码数据。
 * - 层级审计（triggerLayerCheck）真实调用 apiCognitiveBlueprint(layerId)；
 *   仅在 API 成功时标记「已核验」，失败时如实记录，绝不合成 100%。
 * - 帕累托寻优约束（预算/延迟）为用户可编辑输入，非页面伪造入参。
 */

import React, { useState, useEffect, useCallback } from "react";
import { AlertTriangle, RefreshCw } from "lucide-react";
import { apiCognitiveBlueprint, apiCognitiveReason, apiCognitiveOptimize, apiCognitiveHealth } from "../api";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";
import { logger } from "../services/logger";
import { mapApiBlueprintLayer, buildFallbackBlueprintLayers } from "./cos/blueprintData";
import type { BlueprintLayer } from "./cos/blueprintData";
import MetricsCards from "./cos/MetricsCards";
import BlueprintLayerStack from "./cos/BlueprintLayerStack";
import LayerDetailPanel from "./cos/LayerDetailPanel";
import DiagnosticConsole from "./cos/DiagnosticConsole";
import ParetoOptimizerCard from "./cos/ParetoOptimizerCard";
import RuleReasoningCard from "./cos/RuleReasoningCard";
import DemoBadge from "./cos/DemoBadge";

export default function CognitiveOperatingSystem() {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();

  // ── Cognitive Engine API states ──────────────────────
  const [cogHealth, setCogHealth] = useState<{ activeStreams?: number; status?: string; uptime?: string } | null>(null);
  const [cogHealthFailed, setCogHealthFailed] = useState(false);
  const [cogBlueprintLayers, setCogBlueprintLayers] = useState<BlueprintLayer[] | null>(null);
  const [blueprintApiLoading, setBlueprintApiLoading] = useState(true);
  // 蓝图 bulk 接口不可达标记：true 时页面数据为「示意数据」，必须上屏告知用户
  const [blueprintDegraded, setBlueprintDegraded] = useState(false);

  // 单层审计成功后的真实数据覆盖层（覆盖 fallback/API 初值）
  const [verifiedLayers, setVerifiedLayers] = useState<Record<string, BlueprintLayer>>({});

  // Pareto optimizer（约束为用户输入，默认值仅为输入框初值）
  const [optimizeResult, setOptimizeResult] = useState<any>(null);
  const [optimizeLoading, setOptimizeLoading] = useState(false);
  const [optBudget, setOptBudget] = useState<number>(1000);
  const [optLatency, setOptLatency] = useState<number>(200);

  // Rule reasoning
  const [reasonResult, setReasonResult] = useState<any>(null);
  const [reasonLoading, setReasonLoading] = useState(false);
  const [showReasonPanel, setShowReasonPanel] = useState(false);

  // Diagnostics interactive states
  const [diagnosticActive, setDiagnosticActive] = useState<string | null>(null);
  const [diagnosticLogs, setDiagnosticLogs] = useState<string[]>([]);
  const [completedDiagnostics, setCompletedDiagnostics] = useState<Record<string, boolean>>({});
  const [selectedInfoLayerId, setSelectedInfoLayerId] = useState<string | null>("strategic");

  const loadBlueprint = useCallback(() => {
    setBlueprintApiLoading(true);
    apiCognitiveBlueprint()
      .then((data: any) => {
        if (data && Array.isArray(data)) {
          setCogBlueprintLayers(data.map(mapApiBlueprintLayer));
        } else if (data && data.layers) {
          setCogBlueprintLayers(data.layers.map(mapApiBlueprintLayer));
        } else {
          // 接口可达但无有效数据：与不可达同等降级处理，不假装成功
          setCogBlueprintLayers(null);
          setBlueprintDegraded(true);
        }
        setBlueprintApiLoading(false);
      })
      .catch((e: any) => {
        logger.warn("COS blueprint API unavailable, page degraded to illustrative data:", e);
        setCogBlueprintLayers(null);
        setBlueprintDegraded(true);
        setBlueprintApiLoading(false);
      });

    setCogHealthFailed(false);
    apiCognitiveHealth()
      .then((data: any) => {
        if (data) {
          setCogHealth({
            activeStreams: data.activeStreams ?? data.active_streams ?? data.streamCount,
            status: data.status ?? data.engineStatus,
            uptime: data.uptime,
          });
        } else {
          setCogHealth(null);
          setCogHealthFailed(true);
        }
      })
      .catch((e: any) => {
        logger.warn("COS cognitive health API unavailable:", e);
        setCogHealth(null);
        setCogHealthFailed(true);
      });
  }, []);

  useEffect(() => {
    loadBlueprint();
  }, [loadBlueprint]);

  // Use API data when available; fall back to explicitly-labeled illustrative blueprint
  const baseLayers: BlueprintLayer[] = cogBlueprintLayers ?? buildFallbackBlueprintLayers();
  const blueprintLayers: BlueprintLayer[] = baseLayers.map(
    (l) => (verifiedLayers[l.id] ? { ...l, ...verifiedLayers[l.id] } : l)
  );

  const activeInfoLayer = blueprintLayers.find((b) => b.id === selectedInfoLayerId) || blueprintLayers[0];

  // ── Handler: Pareto optimizer（入参来自用户输入） ──────────
  const handleOptimize = async () => {
    setOptimizeLoading(true);
    setOptimizeResult(null);
    try {
      const data = await apiCognitiveOptimize({
        problem: {
          name: 'ECOS Multi-Objective Optimization',
          constraints: { budget: optBudget, latency: optLatency },
        },
        params: { populationSize: 50, generations: 100 },
      });
      setOptimizeResult(data);
    } catch (e: any) {
      logger.error("cos-optimize", 'Pareto optimizer failed:', e);
      setOptimizeResult({ error: e.message || 'Optimization failed' });
    } finally {
      setOptimizeLoading(false);
    }
  };

  // ── Handler: Rule reasoning（上下文使用真实层 id，不写死版本号） ──
  const handleReason = async () => {
    setReasonLoading(true);
    setReasonResult(null);
    setShowReasonPanel(true);
    try {
      const data = await apiCognitiveReason({
        mode: 'rule',
        facts: { system: 'ECOS', layer: activeInfoLayer?.id ?? '' },
      });
      setReasonResult(data);
    } catch (e: any) {
      logger.error("cos-reason", 'Rule reasoning failed:', e);
      setReasonResult({ error: e.message || 'Reasoning failed' });
    } finally {
      setReasonLoading(false);
    }
  };

  // ── 真实审计：调用认知引擎蓝图接口，成功才标记「已核验」，绝不合成结果 ──
  const triggerLayerCheck = async (layerId: string) => {
    const layer = blueprintLayers.find((b) => b.id === layerId);
    if (!layer || diagnosticActive) return;

    const layerName = locale === "zh" ? layer.nameZh : layer.nameEn;
    setDiagnosticActive(layerId);
    setDiagnosticLogs([`[AUDIT] ${t("cognition.cos.diag.fetching", { name: layerName })}`]);

    try {
      const data: any = await apiCognitiveBlueprint(layerId);
      const rec: any = Array.isArray(data)
        ? data[0]
        : (Array.isArray(data?.layers) ? data.layers[0] : data);

      if (!rec || typeof rec !== "object") {
        setDiagnosticLogs((prev) => [
          ...prev,
          `[DIAGNOSTIC_RESULT] ${t("cognition.cos.diag.failed", { message: "empty API response" })}`,
        ]);
        return;
      }

      const fetched = mapApiBlueprintLayer(rec);
      const merged: BlueprintLayer = {
        ...layer,
        ...fetched,
        id: layer.id,
        icon: layer.icon,
        diagnosticTime: layer.diagnosticTime,
      };
      const itemCount = (locale === "zh" ? merged.blueprintItemsZh : merged.blueprintItemsEn).length;

      setVerifiedLayers((prev) => ({ ...prev, [layerId]: merged }));
      setCompletedDiagnostics((prev) => ({ ...prev, [layerId]: true }));
      setDiagnosticLogs((prev) => [
        ...prev,
        `[DIAGNOSTIC_RESULT] ${t("cognition.cos.diag.result", { coverage: merged.coverage, count: itemCount })}`,
      ]);
    } catch (e: any) {
      logger.warn("COS layer audit failed:", e);
      setDiagnosticLogs((prev) => [
        ...prev,
        `[DIAGNOSTIC_RESULT] ${t("cognition.cos.diag.failed", { message: e?.message ?? String(e) })}`,
      ]);
    } finally {
      setDiagnosticActive(null);
    }
  };

  // 全局一致性 = 各层真实 coverage 均值（降级时为示意数据均值，横幅已明示）
  const globalComplianceVal = parseFloat(
    (blueprintLayers.reduce((s, l) => s + (l.coverage || 0), 0) / Math.max(blueprintLayers.length, 1)).toFixed(1)
  );

  const componentsCount = blueprintLayers.reduce(
    (s, l) => s + (locale === "zh" ? l.blueprintItemsZh : l.blueprintItemsEn).length,
    0
  );

  return (
    <div className={`flex-grow overflow-y-auto p-6 font-sans ${styles.appBg} ${styles.appText} transition-colors duration-150`}>
      <div className="max-w-7xl mx-auto space-y-6">

        {/* 降级横幅：bulk 蓝图接口不可达时上屏告知「示意数据」，并提供重试 */}
        {blueprintDegraded && (
          <div className={`border ${styles.dangerBorder} ${styles.dangerBg} rounded-lg p-3 flex items-center gap-2 ${styles.dangerText} text-sm`}>
            <AlertTriangle className="w-4 h-4 shrink-0" />
            <span>{t("cognition.cos.degraded.banner")}</span>
            <DemoBadge />
            <button
              type="button"
              onClick={loadBlueprint}
              className={`ml-auto shrink-0 px-2 py-1 rounded-md border text-[11px] font-bold flex items-center gap-1 cursor-pointer transition ${styles.cardBorder} ${styles.cardBg}`}
            >
              <RefreshCw className="w-3 h-3" />
              <span>{t("cognition.cos.degraded.retry")}</span>
            </button>
          </div>
        )}

        {/* Global Blueprint Metrics Cards row */}
        <MetricsCards
          globalComplianceVal={globalComplianceVal}
          completedDiagnostics={completedDiagnostics}
          totalLayers={blueprintLayers.length}
          componentsCount={componentsCount}
          cogHealth={cogHealth}
          cogHealthFailed={cogHealthFailed}
          degraded={blueprintDegraded}
        />

        {/* ECOS 20-Point System Blueprint Alignment Monitor (系统蓝图对齐分析仪) */}
        <div className="space-y-6">

          {/* Interactive Visual Blueprint diagram block */}
          <BlueprintLayerStack
            blueprintLayers={blueprintLayers}
            selectedInfoLayerId={selectedInfoLayerId}
            completedDiagnostics={completedDiagnostics}
            degraded={blueprintDegraded}
            onSelect={setSelectedInfoLayerId}
          />

          {/* Comprehensive details of selected Blueprint Layer and Interactive Sweeper Diagnostics */}
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">

            {/* Box A: Detailed Specifications & Matching Code Page (8 columns) */}
            <LayerDetailPanel activeInfoLayer={activeInfoLayer} degraded={blueprintDegraded} />

            {/* Box B: Interactive Diagnostics Sweep Console (4 columns) */}
            <DiagnosticConsole
              activeInfoLayer={activeInfoLayer}
              diagnosticActive={diagnosticActive}
              diagnosticLogs={diagnosticLogs}
              loading={blueprintApiLoading}
              onRun={triggerLayerCheck}
            />

          </div>

          {/* ── Cognitive Engine: Pareto Optimizer + Rule Reasoning ── */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">

            {/* Pareto Optimizer Card */}
            <ParetoOptimizerCard
              optimizeLoading={optimizeLoading}
              optimizeResult={optimizeResult}
              budget={optBudget}
              latency={optLatency}
              onBudgetChange={setOptBudget}
              onLatencyChange={setOptLatency}
              onOptimize={handleOptimize}
            />

            {/* Rule Reasoning Card */}
            <RuleReasoningCard
              reasonLoading={reasonLoading}
              reasonResult={reasonResult}
              showReasonPanel={showReasonPanel}
              onReason={handleReason}
            />

          </div>

        </div>
      </div>
    </div>
  );
}
