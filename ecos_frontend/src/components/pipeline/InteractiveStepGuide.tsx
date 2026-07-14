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
 * @license Apache-2.0
 */

import React, { useState, useEffect, useCallback } from 'react';
import {
  Database, Play, Sliders, ShieldAlert, CheckCircle2, AlertTriangle,
  Code, GitBranch, GitPullRequest, Plus, Clock, Layers, Check, Sparkles,
  RefreshCw, X, FileCode, Wifi,
} from 'lucide-react';
import { commit as gitCommit } from '../../services/gitService';

// ── Props ───────────────────────────────────────────────────

interface PipelineBuilderOutput {
  datasetPath: string;
  columns: string[];
  rowCount: number;
  lastCompiled: string;
  expressionsCount: number;
}

interface InteractiveStepGuideProps {
  /** 当前激活的步骤 1..5 */
  activeStep: number;
  /** Pipeline Builder 物理产物；未传时使用 stub 占位（产品演示） */
  pipelineBuilderOutput?: PipelineBuilderOutput | null;
  /** 全局 Git 提交历史（保留接口兼容，演示态可不传） */
  globalGitHistory?: any[];
  /** 自定义提交回调；未传时组件内部直接调用 Git commit API */
  onCommitToGit?: (message: string, filesChanged: string[]) => void;
  /** Toast 通知回调；未传时静默 */
  showToast?: (type: 'success' | 'error' | 'info', message: string) => void;
  /** Git 仓库 ID，用于 POST /api/v1/ecos/git/commit */
  repoId?: string;
}

// Pipeline 后端未就绪时的占位产物
const STUB_PIPELINE_OUTPUT: PipelineBuilderOutput = {
  datasetPath: '/aviation/silver/ds_flights_clean',
  columns: ['flight_id', 'carrier', 'origin', 'dest', 'delay_minutes', 'pilot_id', 'pilot_name'],
  rowCount: 15000,
  lastCompiled: new Date().toISOString(),
  expressionsCount: 6,
};

// ── 算子定义 ─────────────────────────────────────────────────

interface Operator {
  id: string;
  name: string;
  desc: string;
  icon: string;
  color: string;
  type: 'filter' | 'regex' | 'nulls' | 'join' | 'cast';
}

const STATIC_OPERATORS: Operator[] = [
  { id: 'op-filter', name: 'Row Filter (行过滤算子)', desc: '筛选 delay_minutes 大于特定数值的异常飞行记录。', icon: 'Sliders', color: 'bg-blue-100 text-blue-700 border-blue-200', type: 'filter' },
  { id: 'op-regex', name: 'Regex Clean (正则清洗)', desc: '自动剔除航司 carrier 名称首尾的空白字符并转大写。', icon: 'Code', color: 'bg-indigo-100 text-indigo-700 border-indigo-200', type: 'regex' },
  { id: 'op-nulls', name: 'Null Coalesce (空值填充)', desc: '发现 pilot_name 为 Null 时，自动填充为默认值。', icon: 'AlertTriangle', color: 'bg-amber-100 text-amber-700 border-amber-200', type: 'nulls' },
  { id: 'op-join', name: 'Hash Join (主外键关联)', desc: '将 flights 表与 pilots 维度表通过 pilot_id 进行物理 Hash Join。', icon: 'GitBranch', color: 'bg-purple-100 text-purple-700 border-purple-200', type: 'join' },
  { id: 'op-cast', name: 'Type Cast (类型强转)', desc: '将 delay_minutes 的 String 类型转为 Integer 强类型。', icon: 'RefreshCw', color: 'bg-emerald-100 text-emerald-700 border-emerald-200', type: 'cast' },
];

// ── Mock 入湖数据 ────────────────────────────────────────────

interface IngressRow {
  flight_id: string;
  carrier: string;
  origin: string;
  dest: string;
  delay_minutes: any; // could be raw string or null
  pilot_id: string;
  pilot_name?: string;
}

const INGEST_RAW_DATA: IngressRow[] = [
  { flight_id: 'FL-102', carrier: '  airchina  ', origin: 'pek', dest: 'sha', delay_minutes: '0', pilot_id: 'PL-001' },
  { flight_id: 'FL-224', carrier: 'chinaeastern ', origin: 'pvg', dest: 'can', delay_minutes: '24', pilot_id: 'PL-002' },
  { flight_id: 'FL-509', carrier: ' sichuanair', origin: 'tfu', dest: 'pek', delay_minutes: '5', pilot_id: 'PL-001' },
  { flight_id: 'FL-771', carrier: 'chinasouthern', origin: 'can', dest: 'hkg', delay_minutes: '45', pilot_id: 'PL-003' },
  { flight_id: 'FL-088', carrier: 'springair', origin: 'sha', dest: 'szx', delay_minutes: '0', pilot_id: 'PL-004' },
  { flight_id: 'FL-912', carrier: ' hainanair ', origin: 'hak', dest: 'pek', delay_minutes: '12', pilot_id: 'PL-002' },
];

const MOCK_PILOTS_DIM: Record<string, string> = {
  'PL-001': '张伟 (经验:12年)',
  'PL-002': '王芳 (经验:8年)',
  'PL-003': '李杰 (经验:15年)',
  'PL-004': '赵敏 (经验:5年)',
};

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

  // --- STEP 1: INGEST STATE ---
  const [ingestLogs, setIngestLogs] = useState<string[]>([]);
  const [isIngesting, setIsIngesting] = useState<boolean>(false);
  const [ingestProgress, setIngestProgress] = useState<number>(0);
  const [ingestSuccess, setIngestSuccess] = useState<boolean>(false);

  const startIngest = () => {
    setIsIngesting(true);
    setIngestSuccess(false);
    setIngestProgress(0);
    setIngestLogs([
      '[KMS-AGENT] 🔑 正在检索安全库 Vault 凭证 [postgres_prod_db_key]...',
      '[INGRESS-CONTROLLER] 📡 启动 Magritte 安全网关，建立 TLS 隧道...',
      '[INGRESS-CONTROLLER] SUCCESS: 连接至 10.22.4.91:5432 (PostgreSQL 生产库)',
    ]);

    const steps = [
      '[METADATA-PROBE] 🔍 探查目标物理 schema: flights_raw_archive',
      '[INGEST-JOB] 📥 下推全量 SNAPSHOT 数据拉取，限制单分区并发量 <= 8',
      '[INGEST-JOB] 🔄 正在写盘至 DFS Bronze 分区: /aviation/bronze/flights_raw/dt=2026-07-03/',
      '[DATA-COMPRESS] 🗄️ 启用 Snappy 块内压缩，平均压缩率 4.2x',
      '[SUCCESS] 🎉 Ingest 完成！成功拉取 15,000 条物理航班存根报文，大小: 24.5 MB',
    ];

    let count = 0;
    const interval = setInterval(() => {
      count += 20;
      setIngestProgress(count);

      const logIdx = Math.floor(count / 20) - 1;
      if (steps[logIdx]) {
        setIngestLogs((prev) => [...prev, steps[logIdx]]);
      }

      if (count >= 100) {
        clearInterval(interval);
        setIsIngesting(false);
        setIngestSuccess(true);
        toast('success', '步骤 1 (Ingest) 物理源拉取完毕，已落入 Bronze 层！');
        handleCommitToGit('chore(ingest): 物理源拉取完成，更新 /aviation/bronze/flights_raw 数据存根', ['flights_raw.parquet']);
      }
    }, 400);
  };

  // --- STEP 2: TRANSFORM DRAG-AND-DROP STATE ---
  const [appliedOperators, setAppliedOperators] = useState<Operator[]>([
    { id: 'op-filter', name: 'Row Filter (行过滤算子)', desc: '筛选 delay_minutes 大于特定数值的异常飞行记录。', icon: 'Sliders', color: 'bg-blue-100 text-blue-700 border-blue-200', type: 'filter' },
    { id: 'op-regex', name: 'Regex Clean (正则清洗)', desc: '自动剔除航司 carrier 名称首尾的空白字符并转大写。', icon: 'Code', color: 'bg-indigo-100 text-indigo-700 border-indigo-200', type: 'regex' },
  ]);
  const [filterMinutes, setFilterMinutes] = useState<number>(10);
  const [nullFillerValue, setNullFillerValue] = useState<string>('未分配飞行员');
  const [draggingOpId, setDraggingOpId] = useState<string | null>(null);

  const addOperator = (op: Operator) => {
    if (appliedOperators.some((o) => o.id === op.id)) {
      toast('info', `${op.name} 已经存在于当前变换流中`);
      return;
    }
    setAppliedOperators((prev) => [...prev, op]);
    toast('success', `已添加算子: ${op.name}`);
  };

  const removeOperator = (id: string) => {
    setAppliedOperators((prev) => prev.filter((o) => o.id !== id));
    toast('info', '算子已移除');
  };

  const handleDragStart = (id: string) => {
    setDraggingOpId(id);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    if (!draggingOpId) return;
    const matched = STATIC_OPERATORS.find((o) => o.id === draggingOpId);
    if (matched) {
      addOperator(matched);
    }
    setDraggingOpId(null);
  };

  // 实时响应式数据预览
  const getTransformedData = () => {
    let result = [...INGEST_RAW_DATA];

    appliedOperators.forEach((op) => {
      if (op.type === 'filter') {
        result = result.filter((r) => {
          const val = parseInt(r.delay_minutes?.toString() || '0', 10);
          return val >= filterMinutes;
        });
      } else if (op.type === 'regex') {
        result = result.map((r) => ({ ...r, carrier: r.carrier.trim().toUpperCase() }));
      } else if (op.type === 'nulls') {
        result = result.map((r) => ({ ...r, pilot_name: r.pilot_name || nullFillerValue }));
      } else if (op.type === 'join') {
        result = result.map((r) => ({ ...r, pilot_name: MOCK_PILOTS_DIM[r.pilot_id] || nullFillerValue }));
      } else if (op.type === 'cast') {
        result = result.map((r) => ({ ...r, delay_minutes: parseInt(r.delay_minutes?.toString() || '0', 10) }));
      }
    });

    return result;
  };

  // --- STEP 3: LINEAGE & GIT VERIFY STATE ---
  const [activeDiffFile, setActiveDiffFile] = useState<string>('clean_flights.py');
  const [ciStatus, setCiStatus] = useState<'idle' | 'running' | 'success' | 'failed'>('idle');
  const [ciProgress] = useState<number>(0);
  const [ciLogs, setCiLogs] = useState<string[]>([]);
  const [prMerged, setPrMerged] = useState<boolean>(false);

  const mockFileDiffs: Record<string, { original: string; current: string }> = {
    'clean_flights.py': {
      original: `def clean_and_enrich_flights(ctx, flights_raw):\n    flights_df = flights_raw.read()\n    # 简易物理过滤\n    return flights_df.filter(flights_df.delay_minutes > 15)`,
      current: `def clean_and_enrich_flights(ctx, flights_raw, pilots_raw):\n    flights_df = flights_raw.read()\n    pilots_df = pilots_raw.read()\n    \n    # 1. 动态过滤，当前阈值: delay >= ${filterMinutes} 分钟\n    filtered_df = flights_df.filter(flights_df.delay_minutes >= ${filterMinutes})\n    \n    # 2. 规范化航司 carrier 首尾去空格转大写\n    cleaned_df = filtered_df.withColumn("carrier", F.upper(F.trim(F.col("carrier"))))\n    \n    # 3. 关联飞行员表，缺失填充: '${nullFillerValue}'\n    enriched_df = cleaned_df.join(pilots_df, "pilot_id", "left")\n    return enriched_df.fillna({"pilot_name": "${nullFillerValue}"})`,
    },
    'metadata.json': {
      original: `{\n  "name": "ds_flights_clean",\n  "columns": ["flight_id", "delay_minutes"]\n}`,
      current: `{\n  "name": "ds_flights_clean",\n  "columns": ["flight_id", "carrier", "origin", "dest", "delay_minutes", "pilot_id", "pilot_name"],\n  "has_ontology_mapping": true\n}`,
    },
    'test_enrich.py': {
      original: `def test_filter():\n    # TODO: 编写完整断言`,
      current: `def test_clean_and_enrich_flights():\n    # 验证延误过滤和航司格式清洗断言\n    mock_row = Row(flight_id="FL-101", carrier=" airchina ", delay_minutes=20, pilot_id="PL-01")\n    result = clean_and_enrich_flights(mock_row)\n    assert result.carrier == "AIRCHINA"\n    assert result.delay_minutes >= ${filterMinutes}`,
    },
  };

  const runCiChecks = () => {
    setCiStatus('running');
    setCiLogs([
      '[CI-RUNNER] 🚀 启动 Doris-Branch 内存自动化双控校验管道 (CI Daemon Active)...',
      '[CI-RUNNER] 🔍 检测到变更文件: 3 件. 分支: dev/flight-weather-enrichment',
      '[CI-RUNNER] ⚙️ 开始第 1 阶段: Doris SQL 静态语法与物理算子检查...',
    ]);

    const steps = [
      '[AST-CHECK] SUCCESS: Syntax valid. Check Doris Logical plan & In-Memory DAG. -> OK',
      '[LINEAGE-PROBE] ⚙️ 开始第 2 阶段: 拓扑无环依赖探查 (DAG Acyclic Verify)...',
      '[LINEAGE-PROBE] 检测到下游 4 个消费数据集，评估时效 SLA 影响... 评估通过 (无延迟溢出)',
      '[UNIT-TESTS] ⚙️ 开始第 3 阶段: 运行 test_enrich_doris.sql 单元测试及断言...',
      '[UNIT-TESTS] 🧪 Running test_clean_and_enrich_flights()... SUCCESS ✅',
      '[UNIT-TESTS] 🧪 Running test_null_coalesce_fill()... SUCCESS ✅',
      '[CI-RUNNER] 🎉 所有 3 轮物理校验及单元断言测试通过 (Total: 4.2s)!',
    ];

    let count = 0;
    const interval = setInterval(() => {
      count += 15;
      if (count > 100) count = 100;

      const logIdx = Math.floor(count / 15) - 1;
      if (steps[logIdx]) {
        setCiLogs((prev) => [...prev, steps[logIdx]]);
      }

      if (count >= 100) {
        clearInterval(interval);
        setCiStatus('success');
        toast('success', '所有 Git 分支 CI 合规质检测试已全部通过，可以安全合并至主分支！');
      }
    }, 450);
  };

  const mergeBranch = () => {
    if (ciStatus !== 'success') {
      toast('error', '请先运行并通过 CI 校验测试，才可进行物理合并！');
      return;
    }
    setPrMerged(true);
    toast('success', '🏆 PR 合并成功！代码及血缘已全量发布至 main 主分支，即将重算生产银牌表！');
    handleCommitToGit(`Merge pull request #115 from dev/flight-weather-enrichment (过滤阈值:${filterMinutes}m)`, ['clean_flights.py', 'metadata.json']);
  };

  // --- STEP 4: SCHEDULE & DATA HEALTH STATE ---
  const [scheduleTrigger] = useState<string>('ON_DATASET_UPDATE');
  const [nullTolerance, setNullTolerance] = useState<number>(1); // 1%
  const [minRowCount, setMinRowCount] = useState<number>(1000);
  const [freshnessDelay, setFreshnessDelay] = useState<number>(120); // 120 mins
  const [streamActive, setStreamActive] = useState<boolean>(false);
  const [streamDataRows, setStreamDataRows] = useState<number>(4500);
  const [incidentActive, setIncidentActive] = useState<boolean>(false);
  const [isMelted, setIsMelted] = useState<boolean>(false); // 熔断状态
  const [healthLogs, setHealthLogs] = useState<string[]>([]);

  useEffect(() => {
    let interval: ReturnType<typeof setInterval> | undefined;
    if (streamActive && !isMelted) {
      interval = setInterval(() => {
        setStreamDataRows((prev) => {
          const added = Math.floor(Math.random() * 80) + 20;
          const next = prev + added;

          if (incidentActive) {
            setHealthLogs((curr) => [
              `[STREAM-LISTENER] 📥 实时捕获流式数据批次 ${Date.now().toString().slice(-4)}: 注入 ${added} 行`,
              `[DHC-PROBE] 📡 评估空值占比: 监控 [pilot_id] 的 Null 发生率为: 8.42% (警告: 规则上限为 ${nullTolerance}%) ❌`,
              `[DHC-ALERT] 🚨 CRITICAL ERROR: 空值溢出指标阈值已报警！当前状态严重。`,
              `[CIRCUIT-BREAKER] 🛑 [熔断机制触发] 自动停止流式调度 Pipeline，避免污染 downstream ontology!`,
              `[WEBHOOK] 📡 SLA警报发送成功！飞书/SLACK: "调度任务 flights_enrich 发生异常熔断：空值占比(8.42%)突破${nullTolerance}%"`,
            ]);
            setIsMelted(true);
            setStreamActive(false);
            toast('error', '⚠️ 步骤 4 Data Health 报警：发现大量空值，安全断言失败，物理管道自动熔断！');
            handleCommitToGit('fix(health-breaker): 自动触发调度生命周期熔断逻辑，终止下游构建，保护物理 Gold 表', ['dhc-telemetry.log']);
          } else {
            setHealthLogs((curr) => [
              `[STREAM-LISTENER] 📥 实时拉取流式批次 ${Date.now().toString().slice(-4)}: 注入 ${added} 行 (总计 ${next} 行)`,
              `[DHC-PROBE] 📡 评估行数下限: 监控 ${next} 行 (阈值: >${minRowCount}) -> PASSED ✅`,
              `[DHC-PROBE] 📡 评估 [pilot_id] 空值率: 实际 0.11% (阈值: <${nullTolerance}%) -> PASSED ✅`,
              `[DHC-PROBE] 📡 评估时效延迟: 实际延时 15 分钟 (阈值: <${freshnessDelay}m) -> PASSED ✅`,
            ]);
          }
          return next;
        });
      }, 1000);
    }
    return () => {
      if (interval) clearInterval(interval);
    };
  }, [streamActive, incidentActive, nullTolerance, minRowCount, freshnessDelay, isMelted, toast, handleCommitToGit]);

  const toggleStream = () => {
    if (isMelted) {
      setIsMelted(false);
      setIncidentActive(false);
      setStreamDataRows(4500);
      setHealthLogs(['[SCHEDULER] 🔄 系统熔断已人工复位，清除 Data Health 异常事件，正在准备重新启动流监控。']);
      toast('info', '熔断警报已手动复位！');
      return;
    }
    setStreamActive(!streamActive);
    if (!streamActive) {
      setHealthLogs([
        `[SCHEDULER] 📅 注册自动调度策略: ${scheduleTrigger} [ds_flights_raw]`,
        `[SCHEDULER] 🛡️ 挂载 ${nullTolerance}% 字段空值限制、>${minRowCount} 行数判定、<${freshnessDelay}m 延迟判定...`,
        '[STREAM-LISTENER] 📡 高并发实时接收通道启动，开始周期监控...',
      ]);
    }
  };

  const triggerIncident = () => {
    if (!streamActive) {
      toast('error', '请先开启实时高并发调度，才可模拟注入脏数据！');
      return;
    }
    setIncidentActive(true);
    toast('info', '脏数据已注入流！等待下一轮 Data Health 断言核验...');
  };

  // --- STEP 5: ONTOLOGY STATE ---
  const [publishedOntology, setPublishedOntology] = useState<boolean>(false);
  const [isPublishing, setIsPublishing] = useState<boolean>(false);

  const publishOntology = () => {
    setIsPublishing(true);
    setTimeout(() => {
      setIsPublishing(false);
      setPublishedOntology(true);
      toast('success', '🏆 发布成功！业务实体 Flight 和 Link [Flight_to_Pilot] 已同步到全局 Ontology 中！');
      handleCommitToGit('feat(ontology): 全量发布并更新逻辑航空业务实体 Object Type: Flight (Silver-To-Ontology映射)', ['ontology_schema.json']);
    }, 1500);
  };

  return (
    <div className="bg-white border border-slate-200 rounded-xl p-5 flex-1 flex flex-col justify-between overflow-hidden select-text">
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

        <div className="flex justify-between items-center border-b border-slate-100 pb-3">
          <div className="flex items-center gap-2">
            <span className="bg-slate-900 text-amber-400 font-mono text-xs font-black h-6 w-6 rounded-full flex items-center justify-center">
              {activeStep}
            </span>
            <div>
              <h4 className="font-extrabold text-slate-900 text-xs">
                {activeStep === 1 && '步骤 1: 物理异构源拉取与轻量入湖 (Ingest)'}
                {activeStep === 2 && '步骤 2: 算子表达式与级联物理关联 (Transform)'}
                {activeStep === 3 && '步骤 3: 管道血缘分支控制与 Git PR 协同 (Verify)'}
                {activeStep === 4 && '步骤 4: 调度生命周期与 Data Health 熔断控制 (Schedule)'}
                {activeStep === 5 && '步骤 5: Ontology 逻辑实体绑定与全栈发布 (Publish)'}
              </h4>
              <p className="text-[10px] text-slate-400 mt-0.5 font-sans">
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
          <div className="space-y-4 font-sans">
            <div className="p-4 bg-slate-50 border border-slate-200 rounded-xl space-y-3">
              <div className="flex justify-between items-center">
                <span className="text-[10px] font-extrabold text-slate-500 uppercase tracking-wider font-mono">物理连接凭证托管 (Credential Custody)</span>
                <span className="h-2 w-2 rounded-full bg-emerald-500" title="连接可用" />
              </div>

              <div className="grid grid-cols-2 gap-3 text-xs">
                <div className="bg-white p-2.5 rounded-lg border border-slate-200 flex flex-col gap-0.5">
                  <span className="text-slate-400 text-[9px] font-mono">CONNECTION NAME</span>
                  <span className="font-bold text-slate-700">postgres_prod_db</span>
                </div>
                <div className="bg-white p-2.5 rounded-lg border border-slate-200 flex flex-col gap-0.5">
                  <span className="text-slate-400 text-[9px] font-mono">DRIVER GATEWAY</span>
                  <span className="font-bold text-slate-700">Foundry JDBC Agent v3</span>
                </div>
              </div>
            </div>

            <div className="flex gap-4 items-stretch">
              <div className="flex-1 border border-slate-200 rounded-xl p-4 bg-white flex flex-col justify-between gap-3">
                <div className="space-y-1">
                  <div className="text-xs font-bold text-slate-700">物理入湖动作模拟 (Magritte Ingest Sandbox)</div>
                  <p className="text-[10px] text-slate-500 leading-relaxed">
                    点击右侧按钮测试握手提取，查看分布式 Ingress 将外部 DB 或 AWS S3 数据包进行块级分割、断点校验、并写入 Bronze 文件夹的底层全套物理日志。
                  </p>
                </div>

                <div className="flex items-center gap-3">
                  <button
                    onClick={startIngest}
                    disabled={isIngesting}
                    className="flex-1 py-2 bg-slate-900 hover:bg-slate-800 text-white font-bold text-xs rounded-lg transition-all flex items-center justify-center gap-1.5 cursor-pointer shadow-xs disabled:bg-slate-200 disabled:text-slate-400"
                  >
                    <Wifi size={13} className={isIngesting ? 'animate-ping' : ''} />
                    <span>{isIngesting ? `提取拉取中... (${ingestProgress}%)` : '启动物理 Ingest 提取'}</span>
                  </button>

                  {ingestSuccess && (
                    <div className="flex items-center gap-1 text-emerald-600 text-xs font-bold animate-pulse">
                      <CheckCircle2 size={14} />
                      <span>已成功落湖！</span>
                    </div>
                  )}
                </div>
              </div>

              <div className="w-[280px] bg-slate-950 rounded-xl border border-slate-900 p-3 font-mono text-[9px] text-slate-300 leading-relaxed max-h-40 overflow-y-auto">
                {ingestLogs.length === 0 ? (
                  <div className="text-slate-500 italic h-full flex items-center justify-center text-center">
                    等待触发 Ingest Ingress 仿真提取运行日志...
                  </div>
                ) : (
                  <div className="space-y-1">
                    {ingestLogs.map((log, idx) => (
                      <div
                        key={idx}
                        className={
                          log.startsWith('[SUCCESS]') || log.includes('SUCCESS')
                            ? 'text-emerald-400 font-bold'
                            : log.startsWith('[KMS')
                            ? 'text-amber-400'
                            : 'text-slate-300'
                        }
                      >
                        {log}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </div>
        )}

        {/* ==================== STEP 2 ==================== */}
        {activeStep === 2 && (
          <div className="space-y-4 font-sans select-none">
            <div className="grid grid-cols-12 gap-4">
              {/* Operators Left (cols 4) */}
              <div className="col-span-4 border border-slate-200 rounded-xl p-3 bg-slate-50 space-y-2">
                <span className="text-[9px] font-extrabold text-slate-400 uppercase tracking-wider font-mono">拖拽或点击添加清洗算子 (Operators)</span>

                <div className="space-y-2 max-h-72 overflow-y-auto">
                  {STATIC_OPERATORS.map((op) => {
                    const isApplied = appliedOperators.some((o) => o.id === op.id);
                    return (
                      <div
                        key={op.id}
                        draggable
                        onDragStart={() => handleDragStart(op.id)}
                        onClick={() => addOperator(op)}
                        className={`p-2 rounded-lg border text-[10px] flex items-start gap-2 cursor-grab active:cursor-grabbing transition-all ${op.color} ${
                          isApplied ? 'opacity-55' : 'hover:scale-102 hover:shadow-2xs'
                        }`}
                      >
                        <span className="p-0.5 bg-white/80 rounded mt-0.5">
                          {op.type === 'filter' && <Sliders size={11} />}
                          {op.type === 'regex' && <Code size={11} />}
                          {op.type === 'nulls' && <AlertTriangle size={11} />}
                          {op.type === 'join' && <GitBranch size={11} />}
                          {op.type === 'cast' && <RefreshCw size={11} />}
                        </span>
                        <div className="flex-1">
                          <div className="font-extrabold flex justify-between items-center">
                            <span>{op.name.split(' ')[0]}</span>
                            <Plus size={10} className="text-slate-500 cursor-pointer hover:scale-120" />
                          </div>
                          <p className="text-[8px] text-slate-500 mt-0.5 font-sans leading-tight">{op.desc}</p>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>

              {/* Drop canvas right (cols 8) */}
              <div
                onDragOver={(e) => e.preventDefault()}
                onDrop={handleDrop}
                className="col-span-8 border border-slate-200 rounded-xl p-3 bg-white flex flex-col justify-between min-h-[220px]"
              >
                <div>
                  <div className="flex justify-between items-center mb-2 pb-1.5 border-b border-slate-100">
                    <span className="text-[9px] font-extrabold text-slate-400 uppercase tracking-wider font-mono">
                      活动加工拓扑管道 (Applied Transformations)
                    </span>
                    <span className="text-[8px] text-slate-400">支持 HTML5 拖放算子入仓，或直接在算子内微调属性</span>
                  </div>

                  {appliedOperators.length === 0 ? (
                    <div className="border-2 border-dashed border-slate-200 rounded-lg p-6 flex flex-col items-center justify-center text-slate-400 gap-1.5 text-xs text-center min-h-[140px]">
                      <Sliders size={20} className="text-slate-300 animate-bounce" />
                      <span>请从左侧拖拽或点击算子加入此处物理建模管道</span>
                    </div>
                  ) : (
                    <div className="flex flex-wrap gap-2 max-h-[160px] overflow-y-auto p-1 bg-slate-50/50 rounded-lg">
                      {appliedOperators.map((op) => (
                        <div key={op.id} className="p-2 bg-white rounded-lg border border-slate-200 flex flex-col gap-1.5 shadow-2xs text-[10px] w-[180px] shrink-0 animate-in zoom-in-95">
                          <div className="flex justify-between items-center border-b border-slate-100 pb-1">
                            <span className="font-extrabold text-slate-700 truncate pr-2">{op.name.split(' ')[0]}</span>
                            <button onClick={() => removeOperator(op.id)} className="text-slate-400 hover:text-slate-600 cursor-pointer">
                              <X size={10} />
                            </button>
                          </div>

                          {op.type === 'filter' && (
                            <div className="space-y-1">
                              <div className="flex justify-between text-[8px] text-slate-400">
                                <span>过滤延误 &gt;= </span>
                                <span className="font-bold text-blue-600">{filterMinutes} 分钟</span>
                              </div>
                              <input
                                type="range"
                                min={0}
                                max={40}
                                value={filterMinutes}
                                onChange={(e) => setFilterMinutes(parseInt(e.target.value))}
                                className="w-full h-1 bg-slate-200 rounded-lg appearance-none cursor-pointer accent-blue-600"
                              />
                            </div>
                          )}

                          {op.type === 'nulls' && (
                            <div className="space-y-1">
                              <span className="text-[8px] text-slate-400 block">空值默认填充值:</span>
                              <input
                                type="text"
                                value={nullFillerValue}
                                onChange={(e) => setNullFillerValue(e.target.value)}
                                className="w-full text-[9px] px-1.5 py-0.5 border border-slate-200 rounded focus:outline-none focus:border-indigo-500 bg-slate-50"
                              />
                            </div>
                          )}

                          {op.type === 'regex' && (
                            <span className="text-[8px] text-slate-500 leading-normal bg-indigo-50/50 p-1 rounded font-mono">
                              regex: s.strip().upper()
                            </span>
                          )}

                          {op.type === 'join' && (
                            <div className="flex items-center gap-1">
                              <span className="text-[8px] text-slate-400">关联维度表:</span>
                              <span className="text-[8px] bg-purple-100 text-purple-700 font-bold px-1 rounded font-mono">pilots_raw</span>
                            </div>
                          )}

                          {op.type === 'cast' && (
                            <span className="text-[8px] text-slate-500 font-mono">delay_minutes: String ➔ Double</span>
                          )}
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                <div className="mt-2 text-slate-400 text-[8px] font-mono leading-none bg-slate-900 text-slate-300 p-1.5 rounded flex justify-between">
                  <span>Optimizer Pipeline Expression Target:</span>
                  <span className="text-indigo-400">DorisCatalystPushdownBuilder()</span>
                </div>
              </div>
            </div>

            {/* Reactive Output Preview Table */}
            <div className="border border-slate-200 rounded-xl overflow-hidden bg-white">
              <div className="bg-slate-50 px-3 py-1.5 border-b border-slate-200 flex justify-between items-center">
                <span className="text-[10px] font-extrabold text-slate-700 flex items-center gap-1 font-sans">
                  <Database size={11} className="text-slate-500" />
                  <span>实时过滤计算动态数据预览 (Reactive In-Memory/Doris Preview)</span>
                </span>
                <span className="text-[8px] bg-slate-200 text-slate-600 px-1.5 py-0.2 rounded font-mono">
                  Rows count: {getTransformedData().length}
                </span>
              </div>

              <div className="overflow-x-auto max-h-36">
                <table className="w-full text-left text-[9px] font-mono border-collapse select-text">
                  <thead>
                    <tr className="bg-slate-100/50 border-b border-slate-200 text-slate-500 font-bold">
                      <th className="p-2 border-r border-slate-200">flight_id</th>
                      <th className="p-2 border-r border-slate-200">carrier</th>
                      <th className="p-2 border-r border-slate-200">origin</th>
                      <th className="p-2 border-r border-slate-200">dest</th>
                      <th className="p-2 border-r border-slate-200">delay_minutes</th>
                      <th className="p-2 border-r border-slate-200">pilot_id</th>
                      {appliedOperators.some((op) => op.type === 'join' || op.type === 'nulls') && (
                        <th className="p-2 text-indigo-700 font-extrabold">pilot_name (关联字段)</th>
                      )}
                    </tr>
                  </thead>
                  <tbody>
                    {getTransformedData().map((row, idx) => (
                      <tr key={idx} className="border-b border-slate-100 last:border-0 hover:bg-slate-50/50">
                        <td className="p-2 border-r border-slate-100">{row.flight_id}</td>
                        <td className="p-2 border-r border-slate-100">{row.carrier}</td>
                        <td className="p-2 border-r border-slate-100 font-bold">{row.origin}</td>
                        <td className="p-2 border-r border-slate-100 font-bold">{row.dest}</td>
                        <td className="p-2 border-r border-slate-100">{row.delay_minutes}</td>
                        <td className="p-2 border-r border-slate-100">{row.pilot_id}</td>
                        {appliedOperators.some((op) => op.type === 'join' || op.type === 'nulls') && (
                          <td className="p-2 text-indigo-600 font-bold bg-indigo-50/20">{row.pilot_name || nullFillerValue}</td>
                        )}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}

        {/* ==================== STEP 3 ==================== */}
        {activeStep === 3 && (
          <div className="space-y-4 font-sans">
            <div className="grid grid-cols-12 gap-4">
              {/* Files list Left (cols 3) */}
              <div className="col-span-3 border border-slate-200 rounded-xl p-3 bg-slate-50 space-y-2 select-none">
                <span className="text-[9px] font-extrabold text-slate-400 uppercase tracking-wider font-mono block">修改待PR代码文件</span>
                <div className="space-y-1">
                  {Object.keys(mockFileDiffs).map((filename) => (
                    <button
                      key={filename}
                      onClick={() => setActiveDiffFile(filename)}
                      className={`w-full text-left px-2.5 py-1.5 rounded-lg text-[10px] font-mono flex items-center gap-1.5 transition-all cursor-pointer ${
                        activeDiffFile === filename
                          ? 'bg-slate-900 text-white shadow-xs font-bold'
                          : 'bg-white hover:bg-slate-100 text-slate-600 border border-slate-150'
                      }`}
                    >
                      <FileCode size={11} className={activeDiffFile === filename ? 'text-purple-400' : 'text-slate-400'} />
                      <span className="truncate">{filename}</span>
                    </button>
                  ))}
                </div>
              </div>

              {/* Side by side diff (cols 9) */}
              <div className="col-span-9 border border-slate-200 rounded-xl overflow-hidden bg-slate-950 text-[#c9d1d9] font-mono text-[9px] leading-relaxed flex flex-col justify-between">
                <div className="bg-[#161b22] px-3 py-1.5 border-b border-[#30363d] flex justify-between items-center shrink-0">
                  <span className="font-extrabold text-slate-300 flex items-center gap-1">
                    <GitPullRequest size={11} className="text-[#58a6ff]" />
                    <span>
                      代码分支对比 Diff: <code>{activeDiffFile}</code>
                    </span>
                  </span>
                  <span className="text-[8px] bg-[#21262d] text-[#8b949e] px-1.5 py-0.2 rounded">PR #115 (dev/flight-enrichment)</span>
                </div>

                <div className="p-3 grid grid-cols-2 gap-4 divide-x divide-slate-800 h-36 overflow-y-auto select-text bg-[#0d1117]">
                  <div>
                    <span className="text-[8px] text-red-400 uppercase tracking-wider block mb-1 font-sans">--- main (主生产分支)</span>
                    <pre className="whitespace-pre-wrap text-slate-400 opacity-60 font-mono">{mockFileDiffs[activeDiffFile].original}</pre>
                  </div>
                  <div className="pl-4">
                    <span className="text-[8px] text-emerald-400 uppercase tracking-wider block mb-1 font-sans">+++ dev/enrichment (修改后草稿)</span>
                    <pre className="whitespace-pre-wrap text-[#e6edf3] font-mono">{mockFileDiffs[activeDiffFile].current}</pre>
                  </div>
                </div>

                <div className="bg-[#161b22] px-3 py-2 border-t border-[#30363d] flex justify-between items-center select-none">
                  <span className="text-[8px] text-slate-400 font-sans">分支保护已激活：必须运行静态 CI 与 3 组单元断言，方可解锁合并</span>

                  <div className="flex gap-2">
                    <button
                      onClick={runCiChecks}
                      disabled={ciStatus === 'running'}
                      className="px-2.5 py-1 bg-indigo-600 hover:bg-indigo-700 text-white font-bold rounded text-[9px] transition-all flex items-center gap-1 cursor-pointer shadow-xs disabled:bg-slate-700"
                    >
                      <Play size={9} />
                      <span>{ciStatus === 'running' ? '运行CI中...' : '运行自动化 CI 校验'}</span>
                    </button>

                    <button
                      onClick={mergeBranch}
                      disabled={ciStatus !== 'success' || prMerged}
                      className="px-2.5 py-1 bg-emerald-600 hover:bg-emerald-700 text-white font-bold rounded text-[9px] transition-all flex items-center gap-1 cursor-pointer shadow-xs disabled:bg-slate-800 disabled:text-slate-500"
                    >
                      <Check size={10} />
                      <span>{prMerged ? '已合并成功' : '批准并合并 (PR Merge)'}</span>
                    </button>
                  </div>
                </div>
              </div>
            </div>

            {/* Run Logs simulation console */}
            <div className="bg-[#0d1117] rounded-xl border border-slate-900 p-3 h-28 overflow-y-auto font-mono text-[9px] leading-relaxed select-text">
              {ciLogs.length === 0 ? (
                <div className="text-slate-500 italic h-full flex items-center justify-center text-center">
                  等待启动自动化 CI 校验... (将评估 DAG 血缘圈环、静态 Doris SQL 算子及内存依赖计划并触发 unit tests)
                </div>
              ) : (
                <div className="space-y-0.5">
                  {ciLogs.map((log, lidx) => (
                    <div
                      key={lidx}
                      className={
                        log.includes('SUCCESS') || log.includes('✅')
                          ? 'text-[#3fb950] font-bold'
                          : log.includes('ERROR') || log.includes('警告')
                          ? 'text-[#f85149] font-bold'
                          : log.startsWith('[CI-')
                          ? 'text-[#58a6ff]'
                          : 'text-[#c9d1d9]'
                      }
                    >
                      {log}
                    </div>
                  ))}
                  {ciStatus === 'success' && (
                    <div className="text-[#3fb950] font-extrabold text-[10px] border-t border-[#30363d] pt-1 mt-1 flex items-center gap-1 animate-pulse">
                      <CheckCircle2 size={12} />
                      <span>[COMPILE METRICS] CI CHECKS: PASSED. STAGE DEPLOYMENT READY FOR PRODUCTION RE-BUILD.</span>
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>
        )}

        {/* ==================== STEP 4 ==================== */}
        {activeStep === 4 && (
          <div className="space-y-4 font-sans select-none">
            <div className="p-4 bg-slate-50 border border-slate-200 rounded-xl space-y-3">
              <div className="flex justify-between items-center pb-1.5 border-b border-slate-200">
                <span className="text-[10px] font-extrabold text-slate-700 uppercase tracking-wider font-mono">
                  高时效调度与异常熔断安全设定 (SLA Rules)
                </span>
                <span className={`text-[9px] font-bold px-2 py-0.5 rounded ${isMelted ? 'bg-rose-100 text-rose-800 animate-pulse' : 'bg-emerald-100 text-emerald-800'}`}>
                  {isMelted ? '🛑 物理管道处于熔断保护中' : '🟢 质量监控引擎运作中'}
                </span>
              </div>

              {/* Threshold control sliders */}
              <div className="grid grid-cols-3 gap-4 text-xs">
                <div className="bg-white p-2.5 rounded-lg border border-slate-200 flex flex-col gap-1.5">
                  <div className="flex justify-between font-mono text-[9px] text-slate-500">
                    <span>空值上限 (Null Limit)</span>
                    <span className="font-bold text-slate-800">{nullTolerance}%</span>
                  </div>
                  <input
                    type="range"
                    min={1}
                    max={10}
                    value={nullTolerance}
                    onChange={(e) => setNullTolerance(parseInt(e.target.value))}
                    disabled={isMelted}
                    className="w-full h-1 bg-slate-200 rounded-lg appearance-none cursor-pointer accent-slate-800"
                  />
                  <p className="text-[8px] text-slate-400">若 pilot_id 缺漏率超出此上限，触发自动熔断保护。</p>
                </div>

                <div className="bg-white p-2.5 rounded-lg border border-slate-200 flex flex-col gap-1.5">
                  <div className="flex justify-between font-mono text-[9px] text-slate-500">
                    <span>行数底限 (Min Rows)</span>
                    <span className="font-bold text-slate-800">{minRowCount} 行</span>
                  </div>
                  <input
                    type="range"
                    min={500}
                    max={2000}
                    step={100}
                    value={minRowCount}
                    onChange={(e) => setMinRowCount(parseInt(e.target.value))}
                    disabled={isMelted}
                    className="w-full h-1 bg-slate-200 rounded-lg appearance-none cursor-pointer accent-slate-800"
                  />
                  <p className="text-[8px] text-slate-400">输出结果行数低于此阀值时报错，发送SLA警报。</p>
                </div>

                <div className="bg-white p-2.5 rounded-lg border border-slate-200 flex flex-col gap-1.5">
                  <div className="flex justify-between font-mono text-[9px] text-slate-500">
                    <span>时效延迟 (Freshness)</span>
                    <span className="font-bold text-slate-800">{freshnessDelay} 分钟</span>
                  </div>
                  <input
                    type="range"
                    min={60}
                    max={300}
                    step={30}
                    value={freshnessDelay}
                    onChange={(e) => setFreshnessDelay(parseInt(e.target.value))}
                    disabled={isMelted}
                    className="w-full h-1 bg-slate-200 rounded-lg appearance-none cursor-pointer accent-slate-800"
                  />
                  <p className="text-[8px] text-slate-400">入湖至 Gold 延迟超出时启动 SLA 故障分级。</p>
                </div>
              </div>
            </div>

            <div className="flex gap-4 items-stretch">
              <div className="flex-1 border border-slate-200 rounded-xl p-4 bg-white flex flex-col justify-between gap-3">
                <div className="space-y-1">
                  <div className="text-xs font-bold text-slate-700">高频流式微批接收测试沙箱 (Scheduler Monitor)</div>
                  <p className="text-[10px] text-slate-500 leading-relaxed">
                    点击开启流接收，模拟高吞吐量写入。你可以模拟<strong>注入脏数据异常事件</strong>，查看 Data Health Checks 探针是如何敏锐拦截 Null 并实施微毫秒级
                    <strong className="text-rose-600 font-extrabold ml-1">自动熔断 (Circuit Breaker)</strong>。
                  </p>
                </div>

                <div className="flex items-center gap-2">
                  <button
                    onClick={toggleStream}
                    className={`flex-1 py-2 font-bold text-xs rounded-lg transition-all flex items-center justify-center gap-1 cursor-pointer shadow-xs ${
                      isMelted
                        ? 'bg-rose-600 hover:bg-rose-700 text-white animate-pulse'
                        : streamActive
                        ? 'bg-amber-600 hover:bg-amber-700 text-white'
                        : 'bg-slate-900 hover:bg-slate-800 text-white'
                    }`}
                  >
                    <RefreshCw size={12} className={streamActive ? 'animate-spin' : ''} />
                    <span>{isMelted ? '⚠️ 熔断已生效：点击一键报警复位' : streamActive ? '暂停实时流写入' : '开启高频流式调度'}</span>
                  </button>

                  <button
                    onClick={triggerIncident}
                    disabled={!streamActive || isMelted}
                    className="py-2 px-3 bg-red-100 hover:bg-red-200 text-red-700 font-bold text-xs rounded-lg transition-all flex items-center gap-1 cursor-pointer disabled:bg-slate-100 disabled:text-slate-400"
                  >
                    <ShieldAlert size={12} />
                    <span>注入脏数据</span>
                  </button>
                </div>
              </div>

              {/* Simulated alerting logs terminal */}
              <div
                className={`w-[320px] rounded-xl border p-3 font-mono text-[9px] leading-relaxed max-h-40 overflow-y-auto transition-colors ${
                  isMelted ? 'bg-rose-950/95 border-rose-800 text-rose-200 shadow-lg' : 'bg-slate-950 border-slate-900 text-slate-300'
                }`}
              >
                {healthLogs.length === 0 ? (
                  <div className="text-slate-500 italic h-full flex items-center justify-center text-center">
                    等待开启高频流式调度以注入监测流日志...
                  </div>
                ) : (
                  <div className="space-y-1">
                    {healthLogs.map((log, lidx) => (
                      <div
                        key={lidx}
                        className={
                          log.includes('熔断') || log.includes('🚨') || log.includes('监控 [')
                            ? 'text-rose-400 font-bold'
                            : log.includes('Passed ✅') || log.includes('PASSED ✅')
                            ? 'text-emerald-400'
                            : log.startsWith('[SCHEDULER]')
                            ? 'text-blue-400'
                            : 'text-slate-300'
                        }
                      >
                        {log}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </div>
        )}

        {/* ==================== STEP 5 ==================== */}
        {activeStep === 5 && (
          <div className="space-y-4 font-sans">
            <div className="p-4 bg-slate-50 border border-slate-200 rounded-xl space-y-3">
              <span className="text-[10px] font-extrabold text-slate-500 uppercase tracking-wider font-mono block">
                Ontology 物理-逻辑属性映射引擎 (Ontology Mapping Core)
              </span>

              <div className="grid grid-cols-12 gap-4 items-center">
                {/* Physical dataset */}
                <div className="col-span-4 bg-white p-3 rounded-lg border border-slate-200 text-[11px] space-y-1.5 shadow-2xs">
                  <span className="text-[9px] bg-slate-100 text-slate-500 font-mono font-bold px-1.5 py-0.2 rounded uppercase">PHYSICAL SILVER VIEW</span>
                  <div className="font-mono text-slate-700 text-xs truncate">ds_flights_clean</div>
                  <div className="text-[9px] text-slate-400 space-y-0.5 font-mono">
                    <div>• flight_id (PK)</div>
                    <div>• carrier (String)</div>
                    <div>• delay_minutes (Double)</div>
                    <div>• pilot_id (FK)</div>
                  </div>
                </div>

                {/* Mapping line */}
                <div className="col-span-4 flex flex-col items-center justify-center text-slate-400 text-xs">
                  <span className="bg-slate-200 text-slate-600 px-2 py-0.5 rounded text-[8px] font-mono mb-1 font-bold animate-pulse">SCHEMA SYNC</span>
                  <div className="w-full h-0.5 bg-gradient-to-r from-slate-200 via-indigo-400 to-slate-200 relative">
                    <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 bg-indigo-500 text-white rounded-full p-0.5 animate-ping">
                      <Layers size={8} />
                    </div>
                  </div>
                </div>

                {/* Ontology logic model */}
                <div className="col-span-4 bg-indigo-950 text-indigo-100 p-3 rounded-lg border border-indigo-800 text-[11px] space-y-1.5 shadow-md">
                  <span className="text-[9px] bg-indigo-800 text-indigo-100 font-mono font-bold px-1.5 py-0.2 rounded uppercase">ONTOLOGY LOGIC OBJECT</span>
                  <div className="font-extrabold text-white text-xs flex items-center gap-1">
                    <Layers size={11} className="text-amber-400 animate-spin" style={{ animationDuration: '4s' }} />
                    <span>Flight (航班实体)</span>
                  </div>
                  <div className="text-[9px] text-indigo-300 space-y-0.5 font-mono">
                    <div>• Primary ID ➔ Object UUID</div>
                    <div>• Title ➔ title_display</div>
                    <div>• Status ➔ delay_status_sla</div>
                    <div>• Link ➔ [Flight_to_Pilot]</div>
                  </div>
                </div>
              </div>
            </div>

            <div className="p-4 bg-white border border-slate-200 rounded-xl flex flex-col md:flex-row justify-between items-center gap-4">
              <div className="space-y-1">
                <div className="text-xs font-bold text-slate-800">将物理银/金牌数据集物化同步至业务 Ontology</div>
                <p className="text-[10px] text-slate-500 leading-relaxed max-w-xl">
                  发布完成后，业务决策人员可在 Foundry Object Explorer 或 Workshop 中，完全绕过 SQL 代码，直接检索到以面向对象的形式封装的 <code>Flight</code> 航班对象。
                </p>
              </div>

              <div className="flex gap-3 items-center shrink-0">
                <button
                  onClick={publishOntology}
                  disabled={isPublishing || publishedOntology}
                  className="px-5 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white font-bold text-xs rounded-xl transition-all shadow-xs flex items-center gap-1.5 cursor-pointer disabled:bg-slate-200 disabled:text-slate-400"
                >
                  {isPublishing ? (
                    <>
                      <span className="h-3.5 w-3.5 border-2 border-slate-400 border-t-transparent rounded-full animate-spin"></span>
                      <span>元数据同步中...</span>
                    </>
                  ) : publishedOntology ? (
                    <>
                      <CheckCircle2 size={13} className="text-emerald-400 animate-bounce" />
                      <span>已全量发布成功</span>
                    </>
                  ) : (
                    <>
                      <Layers size={13} />
                      <span>编译并一键发布至 Ontology 实体层</span>
                    </>
                  )}
                </button>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* 3. STEP FOOTER / SUBMIT ACTION */}
      <div className="border-t border-slate-100 pt-3 flex justify-between items-center select-none text-[10px]">
        <div className="flex items-center gap-1.5 text-slate-500 font-mono">
          <Clock size={11} className="text-slate-400" />
          <span>最新状态时间: 刚刚</span>
        </div>

        {activeStep === 2 && <div className="text-[10px] text-slate-400">算子联动机制: 改变延误限度，下方 In-Memory/Doris 预览数据立刻响应</div>}
        {activeStep === 3 && <div className="text-[10px] text-slate-400">版本管理规范: 强制 CI 单元校验保障生产分支的绝对高可用</div>}
        {activeStep === 4 && <div className="text-[10px] text-slate-400">质量控制标准: 高容错 Data Health 校验，自动熔断异常下游</div>}
      </div>
    </div>
  );
}
