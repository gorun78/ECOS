/**
 * InteractiveStepGuide — 数据管道交互向导 (c2eos 移植版)
 *
 * 5 步交互式数据管道演示：Ingest / Transform (5 种算子拖拽) / Verify (Git PR+CI) /
 * Schedule (Data Health 熔断) / Publish (Ontology)。
 *
 * 移植自 ceos_new，适配 c2eos：
 *  - 算子列表保留 mock（过滤 / 正则 / 空值 / Join / 类型转换）
 *  - 管道输出采用 stub 占位（Pipeline 后端未就绪时作为产品演示）
 *  - Git 提交对接 POST /api/v1/ecos/git/commit（经 services/gitService），
 *    端点不可用时自动降级为 stub，演示流程不中断
 *  - 去掉对 ceos_new 其它组件的 import 依赖；图标统一从 lucide-react 导入
 *
 * H6-T4 组件行数治理：五个步骤面板与其 state 逻辑已机械抽取至
 * ./StepIngest ./StepTransform ./StepVerify ./StepSchedule ./StepPublish，
 * 静态定义与 mock 数据见 ./stepGuideData；本文件保留为组合根 + 步骤头尾。
 *
 * @license Apache-2.0
 */

import React, { useCallback } from 'react';
import { Clock, Sparkles } from 'lucide-react';
import { commit as gitCommit } from '../../services/gitService';
import { useTheme } from '../ThemeContext';
import { PipelineBuilderOutput, STUB_PIPELINE_OUTPUT } from './stepGuideData';
import StepIngest, { useIngestStep } from './StepIngest';
import StepTransform, { useTransformStep } from './StepTransform';
import StepVerify, { useVerifyStep } from './StepVerify';
import StepSchedule, { useScheduleStep } from './StepSchedule';
import StepPublish, { usePublishStep } from './StepPublish';

// ── Props ───────────────────────────────────────────────────

interface InteractiveStepGuideProps {
  /** 当前激活的步骤 1..5 */
  activeStep: number;
  /** Pipeline Builder 物理产物；未传时使用 stub 占位（产品演示） */
  pipelineBuilderOutput?: PipelineBuilderOutput | null;
  /** 全局 Git 提交历史（保留接口兼容，演示态可不传） */
  globalGitHistory?: unknown[];
  /** 自定义提交回调；未传时组件内部直接调用 Git commit API */
  onCommitToGit?: (message: string, filesChanged: string[]) => void;
  /** Toast 通知回调；未传时静默 */
  showToast?: (type: 'success' | 'error' | 'info', message: string) => void;
  /** Git 仓库 ID，用于 POST /api/v1/ecos/git/commit */
  repoId?: string;
}

// ── 组件 ─────────────────────────────────────────────────────

export default function InteractiveStepGuide({
  activeStep,
  pipelineBuilderOutput,
  onCommitToGit,
  showToast,
  repoId = 'default',
}: InteractiveStepGuideProps) {
  // 占位产物：父组件未提供时使用 stub（产品演示态）
  const pipelineOutput: PipelineBuilderOutput = pipelineBuilderOutput ?? STUB_PIPELINE_OUTPUT;
  // Toast 静默降级
  const toast = showToast ?? (() => undefined);
  const { styles } = useTheme();

  // Git 提交：父组件提供回调则委托；否则直接对接 POST /api/v1/ecos/git/commit，
  // 端点不可用时降级为 stub（演示流程不中断）。
  const handleCommitToGit = useCallback(
    (message: string, filesChanged: string[]) => {
      if (onCommitToGit) {
        onCommitToGit(message, filesChanged);
        return;
      }
      // 对接后端 Git commit 端点；失败时 stub 降级
      gitCommit(repoId, { message, files: filesChanged }).catch((err: unknown) => {
        // 后端端点未就绪 —— stub 降级，仅记录日志，演示继续
        console.warn(
          '[InteractiveStepGuide] git commit 端点不可用，已降级为 stub：',
          message,
          err instanceof Error ? err.message : err,
        );
      });
    },
    [onCommitToGit, repoId],
  );

  const ingest = useIngestStep(toast, handleCommitToGit);
  const transform = useTransformStep(toast);
  const verify = useVerifyStep(toast, handleCommitToGit, transform.filterMinutes);
  const schedule = useScheduleStep(toast, handleCommitToGit);
  const publish = usePublishStep(toast, handleCommitToGit);

  return (
    <div className={`${styles.primaryBg} border ${styles.cardBorder} rounded-xl p-5 flex-1 flex flex-col justify-between overflow-hidden select-text`}>
      {/* 1. STEP HEADER */}
      <div className="mb-4">
        {activeStep === 2 && pipelineOutput && (
          <div className="mb-3 px-3 py-2 bg-emerald-50 border border-emerald-100 rounded-lg flex items-center justify-between text-xs animate-in slide-in-from-top duration-300">
            <div className="flex items-center gap-1.5 text-emerald-800">
              <Sparkles size={14} className="text-emerald-600 animate-spin" style={{ animationDuration: '3s' }} />
              <span className="font-semibold">
                已成功决策：无缝集成 Tool 1 (Pipeline Builder) 的物理产物 <code>/aviation/silver/ds_flights_clean</code>
              </span>
            </div>
            <div className="text-[10px] bg-emerald-100 text-emerald-700 font-mono font-bold px-2 py-0.5 rounded">
              已读取: {pipelineOutput.rowCount} 行 · {pipelineOutput.expressionsCount} 个公式
            </div>
          </div>
        )}

        <div className={`flex justify-between items-center border-b ${styles.appBorder} pb-3`}>
          <div className="flex items-center gap-2">
            <span className={`${styles.darkBg} text-amber-400 font-mono text-xs font-black h-6 w-6 rounded-full flex items-center justify-center`}>
              {activeStep}
            </span>
            <div>
              <h4 className={`font-extrabold ${styles.cardText} text-xs`}>
                {activeStep === 1 && '步骤 1: 物理异构源拉取与轻量入湖 (Ingest)'}
                {activeStep === 2 && '步骤 2: 算子表达式与级联物理关联 (Transform)'}
                {activeStep === 3 && '步骤 3: 管道血缘分支控制与 Git PR 协同 (Verify)'}
                {activeStep === 4 && '步骤 4: 调度生命周期与 Data Health 熔断控制 (Schedule)'}
                {activeStep === 5 && '步骤 5: Ontology 逻辑实体绑定与全栈发布 (Publish)'}
              </h4>
              <p className={`text-[10px] ${styles.cardTextMuted} mt-0.5 font-sans`}>
                {activeStep === 1 && 'Data Connections 凭证托管 + 分布式 Magritte Agent 增量拉取'}
                {activeStep === 2 && 'Pipeline Builder 无代码算子 + Doris 算子下推高速计算引擎'}
                {activeStep === 3 && 'Git-First 冷分支演练 + 自动化 CI/CD Doris/内存 单元测试流水线'}
                {activeStep === 4 && 'Job Scheduler 事务编排 + Data Health Checks 质量时效防污染断言'}
                {activeStep === 5 && 'Ontology Manager 逻辑属性绑定 + Functions on Objects 指标统一'}
              </p>
            </div>
          </div>
          <span className="text-[9px] bg-indigo-50 text-indigo-700 border border-indigo-100 font-mono font-bold px-2 py-0.5 rounded">
            Foundry 生产级原型
          </span>
        </div>
      </div>

      {/* 2. MAIN WORKSPACE ZONE */}
      <div className="flex-1 overflow-y-auto pr-1 min-h-[300px] max-h-[460px] space-y-4">
        {/* ==================== STEP 1 ==================== */}
        {activeStep === 1 && (
          <StepIngest
            ingestLogs={ingest.ingestLogs}
            isIngesting={ingest.isIngesting}
            ingestProgress={ingest.ingestProgress}
            ingestSuccess={ingest.ingestSuccess}
            startIngest={ingest.startIngest}
          />
        )}

        {/* ==================== STEP 2 ==================== */}
        {activeStep === 2 && (
          <StepTransform
            appliedOperators={transform.appliedOperators}
            filterMinutes={transform.filterMinutes}
            setFilterMinutes={transform.setFilterMinutes}
            nullFillerValue={transform.nullFillerValue}
            setNullFillerValue={transform.setNullFillerValue}
            addOperator={transform.addOperator}
            removeOperator={transform.removeOperator}
            handleDragStart={transform.handleDragStart}
            handleDrop={transform.handleDrop}
            getTransformedData={transform.getTransformedData}
          />
        )}

        {/* ==================== STEP 3 ==================== */}
        {activeStep === 3 && (
          <StepVerify
            filterMinutes={transform.filterMinutes}
            nullFillerValue={transform.nullFillerValue}
            activeDiffFile={verify.activeDiffFile}
            setActiveDiffFile={verify.setActiveDiffFile}
            ciStatus={verify.ciStatus}
            ciLogs={verify.ciLogs}
            prMerged={verify.prMerged}
            runCiChecks={verify.runCiChecks}
            mergeBranch={verify.mergeBranch}
          />
        )}

        {/* ==================== STEP 4 ==================== */}
        {activeStep === 4 && (
          <StepSchedule
            nullTolerance={schedule.nullTolerance}
            setNullTolerance={schedule.setNullTolerance}
            minRowCount={schedule.minRowCount}
            setMinRowCount={schedule.setMinRowCount}
            freshnessDelay={schedule.freshnessDelay}
            setFreshnessDelay={schedule.setFreshnessDelay}
            streamActive={schedule.streamActive}
            isMelted={schedule.isMelted}
            healthLogs={schedule.healthLogs}
            toggleStream={schedule.toggleStream}
            triggerIncident={schedule.triggerIncident}
          />
        )}

        {/* ==================== STEP 5 ==================== */}
        {activeStep === 5 && (
          <StepPublish
            publishedOntology={publish.publishedOntology}
            isPublishing={publish.isPublishing}
            publishOntology={publish.publishOntology}
          />
        )}
      </div>

      {/* 3. STEP FOOTER / SUBMIT ACTION */}
      <div className={`border-t ${styles.appBorder} pt-3 flex justify-between items-center select-none text-[10px]`}>
        <div className={`flex items-center gap-1.5 ${styles.cardTextMuted} font-mono`}>
          <Clock size={11} className={styles.cardTextMuted} />
          <span>最新状态时间: 刚刚</span>
        </div>

        {activeStep === 2 && <div className={`text-[10px] ${styles.cardTextMuted}`}>算子联动机制: 改变延误限度，下方 In-Memory/Doris 预览数据立刻响应</div>}
        {activeStep === 3 && <div className={`text-[10px] ${styles.cardTextMuted}`}>版本管理规范: 强制 CI 单元校验保障生产分支的绝对高可用</div>}
        {activeStep === 4 && <div className={`text-[10px] ${styles.cardTextMuted}`}>质量控制标准: 高容错 Data Health 校验，自动熔断异常下游</div>}
      </div>
    </div>
  );
}
