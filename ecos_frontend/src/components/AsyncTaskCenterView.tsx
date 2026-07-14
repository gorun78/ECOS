/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect, useRef } from 'react';
import { AlertCircle, AlertTriangle, Check, CheckCircle2, Clock, Compass, Cpu, Loader, Pause, Play, RefreshCw, Search, ShieldAlert, StopCircle, Trash2, Volume2, Bell } from 'lucide-react';
import LucideIcon from '../pages/data-workbench/LucideIcon'

// Task Category Type
export type TaskCategory = 'integration' | 'ontology' | 'knowledge' | 'aip' | 'security' | 'workshop';

// Task Status Type
export type TaskStatus = 'PENDING' | 'RUNNING' | 'PAUSED' | 'SUCCESS' | 'FAILED' | 'CANCELLED';

// Asynchronous Task Interface
export interface AsyncTask {
  id: string;
  name: string;
  category: TaskCategory;
  status: TaskStatus;
  progress: number; // 0 to 100
  triggerType: 'MANUAL' | 'SCHEDULED' | 'EVENT';
  createdAt: string;
  updatedAt: string;
  workerId: string;
  parameters: Record<string, any>;
  pipelineSteps: { name: string; status: 'pending' | 'running' | 'success' | 'failed' }[];
  logs: string[];
  targetPath: { viewMode: any; tab?: string; category?: string; id?: string }; // Navigation metadata
}

interface AsyncTaskCenterViewProps {
  showToast: (type: 'success' | 'info' | 'error', message: string) => void;
  onViewModeChange: (mode: any) => void;
}

export default function AsyncTaskCenterView({ showToast, onViewModeChange }: AsyncTaskCenterViewProps) {
  // Task state
  const [tasks, setTasks] = useState<AsyncTask[]>([
    {
      id: 'TASK-INT-1024',
      name: '航班到离港每日业务增量增量同步 (ds_flight_schedules)',
      category: 'integration',
      status: 'SUCCESS',
      progress: 100,
      triggerType: 'SCHEDULED',
      createdAt: '2026-07-06 04:00:00',
      updatedAt: '2026-07-06 04:08:12',
      workerId: 'k8s-pod-ingest-8b92',
      parameters: { sourceDataset: 'ds_flight_schedules_raw', targetOntology: 'Flight', batchSize: 50000, incremental: true },
      pipelineSteps: [
        { name: '读取数据源', status: 'success' },
        { name: '执行脏数据清洗', status: 'success' },
        { name: '契约实体映射转换', status: 'success' },
        { name: '写入本体对象存储', status: 'success' }
      ],
      logs: [
        '[INFO] [04:00:00] Initializing ingestion workflow for ds_flight_schedules...',
        '[INFO] [04:00:02] Connected successfully to Oracle CDC Gateway.',
        '[INFO] [04:01:15] Retrieved 48,291 incremental records since last watermark.',
        '[WARN] [04:02:30] Found 12 rows with invalid landing scheduled times. Replaced with flight plan fallback.',
        '[INFO] [04:04:45] Mapped raw relational records to Flight Ontology schemas.',
        '[INFO] [04:07:02] Committing 48,279 rows to Transactional Datastore...',
        '[SUCCESS] [04:08:12] Synchronization task finished successfully. Index updated.'
      ],
      targetPath: { viewMode: 'integration', tab: 'syncs' }
    },
    {
      id: 'TASK-INT-2048',
      name: '全局航班起降延误多维度批处理关联分析 (Delay Correlation Analyze)',
      category: 'integration',
      status: 'RUNNING',
      progress: 45,
      triggerType: 'MANUAL',
      createdAt: '2026-07-06 20:25:00',
      updatedAt: '2026-07-06 20:31:00',
      workerId: 'k8s-pod-spark-c122',
      parameters: { sparkCores: 16, memoryGb: 64, targetYear: 2026, runPruning: true },
      pipelineSteps: [
        { name: '载入多维轨迹集', status: 'success' },
        { name: '计算空域气象重叠', status: 'running' },
        { name: '构建关联特征矩阵', status: 'pending' },
        { name: '导出相关性网络', status: 'pending' }
      ],
      logs: [
        '[INFO] [20:25:00] SparkContext initialized. Executor pool: 8 active containers.',
        '[INFO] [20:25:12] Broad-casting airport geo-metadata matrix across partitions...',
        '[INFO] [20:26:45] Shuffling airport landing trajectories with weather grids...',
        '[INFO] [20:28:30] Aggregating congestion indicators. Processed 1.2M points.'
      ],
      targetPath: { viewMode: 'integration', tab: 'pipelines' }
    },
    {
      id: 'TASK-ONT-3011',
      name: '本体主版本 Schema 变更：重建 1250 万条航班-飞行员(Flight-Pilot)映射索引',
      category: 'ontology',
      status: 'RUNNING',
      progress: 12,
      triggerType: 'EVENT',
      createdAt: '2026-07-06 20:29:10',
      updatedAt: '2026-07-06 20:31:00',
      workerId: 'k8s-pod-indexer-77ff',
      parameters: { sourceLinkType: 'flight_pilot_link', chunkLimit: 100000, rebuildElasticsearch: true },
      pipelineSteps: [
        { name: '锁定本体映射拓扑', status: 'success' },
        { name: '逐块生成哈希联合键', status: 'running' },
        { name: '刷新 Elastic 宽表', status: 'pending' }
      ],
      logs: [
        '[INFO] [20:29:10] Received ontology publishing event. Schema change detected on Flight-Pilot link.',
        '[INFO] [20:29:15] DB write-lock enabled for link metadata table.',
        '[INFO] [20:29:40] Scanning primary keys in parallel. Total nodes: 12,501,892.',
        '[INFO] [20:30:15] Allocation finished. Starting indexing worker subthreads...'
      ],
      targetPath: { viewMode: 'ontology', category: 'overview' }
    },
    {
      id: 'TASK-KNO-4005',
      name: '第 4 版民航标准安全管理手册 (SMS) 文本大段落分块与知识库向量索引建立',
      category: 'knowledge',
      status: 'RUNNING',
      progress: 82,
      triggerType: 'MANUAL',
      createdAt: '2026-07-06 19:45:00',
      updatedAt: '2026-07-06 20:31:00',
      workerId: 'k8s-pod-gpu-embed-01',
      parameters: { modelName: 'text-embedding-004', chunkOverlap: 150, chunkSize: 800, collection: 'sms_manual_v4' },
      pipelineSteps: [
        { name: '解析 PDF 多重层级', status: 'success' },
        { name: '段落文本清理分块', status: 'success' },
        { name: '计算高维特征向量', status: 'success' },
        { name: '写入向量检索库', status: 'running' }
      ],
      logs: [
        '[INFO] [19:45:00] OCR processing for PDF input stream initialized.',
        '[INFO] [19:46:12] Extracted 420 chapters and 1,850 standard paragraphs.',
        '[INFO] [19:48:30] Deduplicating cross-references. Cleaned text ratio: 98.4%.',
        '[INFO] [19:50:00] Batch embedding requests dispatched to Vertex AI model endpoint.',
        '[INFO] [19:55:40] Embedded chunks size: 1,850 rows. Vector dimension: 768.',
        '[INFO] [20:10:00] Feeding vector indexing pipeline to pgvector database...',
        '[INFO] [20:28:15] Vector tree index HNSW created on sms_manual_v4.'
      ],
      targetPath: { viewMode: 'knowledge' }
    },
    {
      id: 'TASK-AIP-5022',
      name: 'Aviation Control Copilot 2.1 大模型参数微调与航班调度安全红线指令对齐训练',
      category: 'aip',
      status: 'PENDING',
      progress: 0,
      triggerType: 'MANUAL',
      createdAt: '2026-07-06 20:30:00',
      updatedAt: '2026-07-06 20:30:00',
      workerId: 'k8s-pod-gpu-a100-x8',
      parameters: { baseModel: 'gemini-2.5-pro', epochs: 5, learningRate: 0.00002, trainDataset: 'ds_atc_align_rules' },
      pipelineSteps: [
        { name: '加载预训练权重', status: 'pending' },
        { name: '验证训练微调集', status: 'pending' },
        { name: '梯度更新计算', status: 'pending' },
        { name: '模型红线对齐评估', status: 'pending' }
      ],
      logs: [
        '[INFO] [20:30:00] Job submitted to A100 GPU cluster queue.',
        '[INFO] [20:30:02] Allocated worker ID k8s-pod-gpu-a100-x8. Waiting for free resource slot (Estimated queue: 24s)...'
      ],
      targetPath: { viewMode: 'aip' }
    },
    {
      id: 'TASK-SEC-6080',
      name: '飞行员电子履历主档案 (ds_pilots_biography) 隐私字段全局行列哈希脱敏',
      category: 'security',
      status: 'PAUSED',
      progress: 30,
      triggerType: 'MANUAL',
      createdAt: '2026-07-06 18:00:00',
      updatedAt: '2026-07-06 18:15:20',
      workerId: 'k8s-pod-sec-mask-04',
      parameters: { columnsToMask: ['ssn', 'phone', 'address'], algorithm: 'sha256_salted', targetProject: 'HR_Biographics' },
      pipelineSteps: [
        { name: '解析安全组织规则', status: 'success' },
        { name: '扫描隐私字段标识', status: 'success' },
        { name: '执行SHA-256加盐转换', status: 'running' },
        { name: '写入高密隔离物理表', status: 'pending' }
      ],
      logs: [
        '[INFO] [18:00:00] Security Policy Engine matching. Project Scope: HR_Biographics.',
        '[INFO] [18:01:10] Discovered sensitive SSN headers in column index 3.',
        '[INFO] [18:02:40] Discovered sensitive crew mobile numbers in column index 5.',
        '[INFO] [18:15:20] Task paused by Administrator guorongxiao@gmail.com for manual salt review.'
      ],
      targetPath: { viewMode: 'security', tab: 'row_col' }
    },
    {
      id: 'TASK-WSH-7019',
      name: 'AIP 航空智能联合指挥控制中心 - 生产静态代码树摇压缩与低代码端到端打包编译',
      category: 'workshop',
      status: 'SUCCESS',
      progress: 100,
      triggerType: 'EVENT',
      createdAt: '2026-07-06 12:30:00',
      updatedAt: '2026-07-06 12:32:15',
      workerId: 'k8s-pod-build-nodejs',
      parameters: { compressJs: true, optimizeCSS: true, useViteWebpackPolyfill: false, targetAppId: 'aviation_ops' },
      pipelineSteps: [
        { name: '提取可视化组件配置', status: 'success' },
        { name: '打包 JS/CSS 资源', status: 'success' },
        { name: '验证低代码变量依赖', status: 'success' },
        { name: '推送至边缘发布网络', status: 'success' }
      ],
      logs: [
        '[INFO] [12:30:00] Building bundle for App ID: aviation_ops...',
        '[INFO] [12:30:15] Resolved 14 internal Low-code variables: v_flights_filtered, v_selected_flight...',
        '[INFO] [12:30:45] Bundling 24 widgets (Table, Pie Chart, Action Buttons, Details Panel)...',
        '[INFO] [12:31:30] Tree shaking successfully excluded 4.2MB of unreferenced boilerplate code.',
        '[INFO] [12:32:00] Compression optimized. Total asset size reduced to 842KB.',
        '[SUCCESS] [12:32:15] Code compilation successfully completed. Deployed to production instance.'
      ],
      targetPath: { viewMode: 'workshop' }
    }
  ]);

  // Selected task id
  const [selectedTaskId, setSelectedTaskId] = useState<string>('TASK-INT-2048');

  // Interactive filter states
  const [searchTerm, setSearchTerm] = useState<string>('');
  const [categoryFilter, setCategoryFilter] = useState<string>('all');
  const [statusFilter, setStatusFilter] = useState<string>('all');

  // Multi-selection states for batch actions
  const [selectedTaskIds, setSelectedTaskIds] = useState<string[]>([]);

  // Sound/Vibration / Alarm trigger rules states
  const [isAlertSoundEnabled, setIsAlertSoundEnabled] = useState<boolean>(true);
  const [alertConfig, setAlertConfig] = useState({
    onSuccess: true,
    onFailure: true,
    onQueueTimeout: false
  });

  // Persistent alerts array (消息通知中心)
  const [alerts, setAlerts] = useState<{ id: string; text: string; type: 'success' | 'error' | 'warning'; time: string }[]>([
    { id: '1', text: '任务 TASK-WSH-7019 已在 12:32:15 编译成功并推送生产环境。', type: 'success', time: '12:32:15' },
    { id: '2', text: '任务 TASK-SEC-6080 因需要核验安全加盐规则已被暂停运行。', type: 'warning', time: '18:15:20' }
  ]);
  const [showNotificationCenter, setShowNotificationCenter] = useState(false);

  // Auto-scroll logs ref
  const consoleEndRef = useRef<HTMLDivElement>(null);

  // Find currently active details task
  const activeTask = tasks.find(t => t.id === selectedTaskId);

  // Play audio alarm synthesized by Web Audio API for immersive feel
  const playAlertSound = (type: 'success' | 'error' | 'warning') => {
    if (!isAlertSoundEnabled) return;
    try {
      const AudioContextClass = window.AudioContext || (window as any).webkitAudioContext;
      if (!AudioContextClass) return;
      const ctx = new AudioContextClass();
      
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.connect(gain);
      gain.connect(ctx.destination);

      if (type === 'success') {
        // High-pitched chime
        osc.type = 'sine';
        osc.frequency.setValueAtTime(523.25, ctx.currentTime); // C5
        osc.frequency.setValueAtTime(659.25, ctx.currentTime + 0.15); // E5
        gain.gain.setValueAtTime(0.15, ctx.currentTime);
        gain.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.4);
        osc.start(ctx.currentTime);
        osc.stop(ctx.currentTime + 0.45);
      } else if (type === 'error') {
        // Low double-buzz
        osc.type = 'sawtooth';
        osc.frequency.setValueAtTime(120, ctx.currentTime);
        osc.frequency.setValueAtTime(90, ctx.currentTime + 0.2);
        gain.gain.setValueAtTime(0.2, ctx.currentTime);
        gain.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.5);
        osc.start(ctx.currentTime);
        osc.stop(ctx.currentTime + 0.5);
      } else {
        // Single neutral blip
        osc.type = 'triangle';
        osc.frequency.setValueAtTime(330, ctx.currentTime);
        gain.gain.setValueAtTime(0.15, ctx.currentTime);
        gain.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.3);
        osc.start(ctx.currentTime);
        osc.stop(ctx.currentTime + 0.3);
      }
    } catch (e) {
      console.warn('Web Audio synthesis failed:', e);
    }
  };

  // Add notification to list
  const addAlert = (text: string, type: 'success' | 'error' | 'warning') => {
    const timeStr = new Date().toTimeString().split(' ')[0];
    const newAlert = {
      id: Date.now().toString(),
      text,
      type,
      time: timeStr
    };
    setAlerts(prev => [newAlert, ...prev]);
    playAlertSound(type);
    showToast(type === 'success' ? 'success' : type === 'error' ? 'error' : 'info', text);
  };

  // Task processing loops (simulating asynchronous updates)
  useEffect(() => {
    const interval = setInterval(() => {
      setTasks(prevTasks => {
        let modified = false;
        const updated = prevTasks.map(task => {
          if (task.status === 'RUNNING') {
            modified = true;
            const newProgress = task.progress + Math.floor(Math.random() * 8) + 4;
            
            // Generate some random log statements associated with the task category
            let newLog = '';
            const progressPercentage = Math.min(newProgress, 100);
            
            if (task.category === 'integration') {
              newLog = `[INFO] Spark analysis partitioned data chunks at ${progressPercentage}%. Active executors: 8. Shuffle Read: 4.2GB.`;
            } else if (task.category === 'ontology') {
              newLog = `[INFO] Link-reindexing block mapping progress: ${progressPercentage}% (${Math.floor(progressPercentage * 125000)} / 12500000 records processed).`;
            } else if (task.category === 'knowledge') {
              newLog = `[INFO] Vector indices feeding pipeline: ${progressPercentage}%. Index nodes balanced. HNSW graph depth: 4.`;
            } else {
              newLog = `[INFO] Processing task chunk context pipeline at ${progressPercentage}%...`;
            }

            const logsCopy = [...task.logs, newLog];
            if (logsCopy.length > 50) logsCopy.shift(); // Keep logs buffer sane

            if (newProgress >= 100) {
              // Task finished!
              setTimeout(() => {
                if (alertConfig.onSuccess) {
                  addAlert(`🎉 异步任务 【${task.name.slice(0, 20)}...】 已顺利执行成功！`, 'success');
                }
              }, 50);

              return {
                ...task,
                progress: 100,
                status: 'SUCCESS' as const,
                updatedAt: new Date().toISOString().replace('T', ' ').slice(0, 19),
                pipelineSteps: task.pipelineSteps.map(step => ({ ...step, status: 'success' as const })),
                logs: [...logsCopy, `[SUCCESS] ${new Date().toLocaleTimeString()} Task processing successfully completed in node pool.`]
              };
            }

            // Also update pipeline steps status
            const updatedSteps = [...task.pipelineSteps];
            const activeStepIdx = Math.floor((newProgress / 100) * updatedSteps.length);
            for (let i = 0; i < updatedSteps.length; i++) {
              if (i < activeStepIdx) updatedSteps[i].status = 'success';
              else if (i === activeStepIdx) updatedSteps[i].status = 'running';
              else updatedSteps[i].status = 'pending';
            }

            return {
              ...task,
              progress: newProgress,
              pipelineSteps: updatedSteps,
              logs: logsCopy
            };
          } else if (task.status === 'PENDING') {
            // Pending tasks have a small 8% chance each interval to start running automatically
            if (Math.random() < 0.15) {
              modified = true;
              return {
                ...task,
                status: 'RUNNING' as const,
                updatedAt: new Date().toISOString().replace('T', ' ').slice(0, 19),
                pipelineSteps: task.pipelineSteps.map((step, idx) => idx === 0 ? { ...step, status: 'running' as const } : step),
                logs: [...task.logs, `[INFO] Assigned queue resource resolved. Task transitioned from PENDING to RUNNING state.`]
              };
            }
          }
          return task;
        });

        return modified ? updated : prevTasks;
      });
    }, 3000);

    return () => clearInterval(interval);
  }, [alertConfig]);

  // Autoscroll terminal when active task receives new logs
  useEffect(() => {
    if (consoleEndRef.current) {
      consoleEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [activeTask?.logs.length, selectedTaskId]);

  // Handle task state control actions (启动、暂停、终止、删除)
  const handleStartTask = (taskId: string) => {
    setTasks(prev => prev.map(t => {
      if (t.id === taskId) {
        addAlert(`任务 【${t.id}】 已被手动启动。`, 'success');
        return {
          ...t,
          status: 'RUNNING',
          updatedAt: new Date().toISOString().replace('T', ' ').slice(0, 19),
          logs: [...t.logs, `[INFO] Manual request received: Starting task execution thread.`]
        };
      }
      return t;
    }));
  };

  const handlePauseTask = (taskId: string) => {
    setTasks(prev => prev.map(t => {
      if (t.id === taskId) {
        addAlert(`任务 【${t.id}】 已暂停。`, 'warning');
        return {
          ...t,
          status: 'PAUSED',
          updatedAt: new Date().toISOString().replace('T', ' ').slice(0, 19),
          logs: [...t.logs, `[WARN] Manual request received: Pausing task execution thread. State frozen.`]
        };
      }
      return t;
    }));
  };

  const handleTerminateTask = (taskId: string) => {
    setTasks(prev => prev.map(t => {
      if (t.id === taskId) {
        addAlert(`任务 【${t.id}】 已被手动强制终止。`, 'error');
        return {
          ...t,
          status: 'CANCELLED',
          progress: t.progress,
          updatedAt: new Date().toISOString().replace('T', ' ').slice(0, 19),
          logs: [...t.logs, `[ERROR] Manual request received: SIGKILL dispatched. Job aborted by user.`]
        };
      }
      return t;
    }));
  };

  const handleDeleteTask = (taskId: string) => {
    if (window.confirm(`确定要从系统历史中删除任务 ${taskId} 吗？`)) {
      setTasks(prev => prev.filter(t => t.id !== taskId));
      showToast('info', `任务 ${taskId} 记录已被彻底移除。`);
      if (selectedTaskId === taskId) {
        setSelectedTaskId('');
      }
    }
  };

  // Batch operations (批量处理)
  const handleBatchStart = () => {
    if (selectedTaskIds.length === 0) return;
    setTasks(prev => prev.map(t => {
      if (selectedTaskIds.includes(t.id) && (t.status === 'PAUSED' || t.status === 'PENDING' || t.status === 'CANCELLED' || t.status === 'FAILED')) {
        return {
          ...t,
          status: 'RUNNING',
          updatedAt: new Date().toISOString().replace('T', ' ').slice(0, 19),
          logs: [...t.logs, `[INFO] Batch Operation received: Starting task execution thread.`]
        };
      }
      return t;
    }));
    addAlert(`已批量启动了 ${selectedTaskIds.length} 项任务！`, 'success');
    setSelectedTaskIds([]);
  };

  const handleBatchPause = () => {
    if (selectedTaskIds.length === 0) return;
    setTasks(prev => prev.map(t => {
      if (selectedTaskIds.includes(t.id) && t.status === 'RUNNING') {
        return {
          ...t,
          status: 'PAUSED',
          updatedAt: new Date().toISOString().replace('T', ' ').slice(0, 19),
          logs: [...t.logs, `[WARN] Batch Operation received: Pausing task.`]
        };
      }
      return t;
    }));
    addAlert(`已批量暂停了 ${selectedTaskIds.length} 项正在运行的任务。`, 'warning');
    setSelectedTaskIds([]);
  };

  const handleBatchTerminate = () => {
    if (selectedTaskIds.length === 0) return;
    setTasks(prev => prev.map(t => {
      if (selectedTaskIds.includes(t.id) && (t.status === 'RUNNING' || t.status === 'PAUSED')) {
        return {
          ...t,
          status: 'CANCELLED',
          updatedAt: new Date().toISOString().replace('T', ' ').slice(0, 19),
          logs: [...t.logs, `[ERROR] Batch Operation received: Terminating task workflow.`]
        };
      }
      return t;
    }));
    addAlert(`已批量强制终止了 ${selectedTaskIds.length} 项任务！`, 'error');
    setSelectedTaskIds([]);
  };

  const handleBatchDelete = () => {
    if (selectedTaskIds.length === 0) return;
    if (window.confirm(`确定要彻底删除这 ${selectedTaskIds.length} 个任务记录吗？`)) {
      setTasks(prev => prev.filter(t => !selectedTaskIds.includes(t.id)));
      showToast('success', '批量删除任务记录成功！');
      setSelectedTaskIds([]);
      setSelectedTaskId('');
    }
  };

  // Injected Fail Simulation (故障注入仿真)
  const injectFaultTask = () => {
    const id = `TASK-SEC-FAULT-${Date.now().toString().slice(-4)}`;
    const faultTask: AsyncTask = {
      id,
      name: '⚠️ [高危警报测试] 跨组织涉密隔离项目(HR_Project)访问日志深度穿透与外流安全审计统计',
      category: 'security',
      status: 'RUNNING',
      progress: 35,
      triggerType: 'MANUAL',
      createdAt: new Date().toISOString().replace('T', ' ').slice(0, 19),
      updatedAt: new Date().toISOString().replace('T', ' ').slice(0, 19),
      workerId: 'k8s-pod-sec-audit-fault',
      parameters: { auditScope: 'SystemLogs', sensitiveOnly: true, threshold: 100 },
      pipelineSteps: [
        { name: '扫描访问规则集', status: 'success' },
        { name: '并发匹配行为序列', status: 'running' },
        { name: '打包加密日志上报', status: 'pending' }
      ],
      logs: [
        '[INFO] Audit Scanner initialized with High Security Profile.',
        '[INFO] Querying system logs since last CAAC security gate watermark...',
        '[WARN] Heavy cross-site correlation detected: user analyst_li accessing ds_pilots_biography twice within 4ms.'
      ],
      targetPath: { viewMode: 'security', tab: 'audit' }
    };

    setTasks(prev => [faultTask, ...prev]);
    setSelectedTaskId(id);
    showToast('info', '已成功向系统注入故障测试进程！将在 6 秒后自动触发高危故障崩溃。');

    // Timeout to simulate crash
    setTimeout(() => {
      setTasks(currentTasks => {
        return currentTasks.map(t => {
          if (t.id === id) {
            // Update to FAILED
            setTimeout(() => {
              if (alertConfig.onFailure) {
                addAlert(`❌ 高危异常告警！检测到异步任务 【${t.id}】 执行崩溃 (错误码: OOM_SEGFAULT)`, 'error');
              }
            }, 50);

            return {
              ...t,
              status: 'FAILED' as const,
              progress: 58,
              pipelineSteps: t.pipelineSteps.map(s => s.status === 'running' ? { ...s, status: 'failed' as const } : s),
              logs: [
                ...t.logs,
                `[INFO] Memory utilization threshold breached: Heap memory exceeded physical resource limit (8.0GB).`,
                `[ERROR] Segmentation fault at heap instruction 0xca921f00. SIGSEGV code 139 dispatched.`,
                `[ERROR] Execution aborted prematurely. Dump file saved to bucket://failures/sec_fault_dump.bin.`
              ]
            };
          }
          return t;
        });
      });
    }, 6000);
  };

  // Helper mapping source categories to visual titles and icons
  const getCategoryMeta = (cat: TaskCategory) => {
    switch (cat) {
      case 'integration':
        return { label: '数据集成工作台', icon: 'Workflow', color: 'text-blue-500 bg-blue-50 border-blue-200' };
      case 'ontology':
        return { label: '本体建模工作台', icon: 'Settings', color: 'text-slate-600 bg-slate-50 border-slate-200' };
      case 'knowledge':
        return { label: '知识工作台', icon: 'BookOpen', color: 'text-indigo-500 bg-indigo-50 border-indigo-200' };
      case 'aip':
        return { label: 'AI 协同工作台', icon: 'Bot', color: 'text-purple-500 bg-purple-50 border-purple-200' };
      case 'security':
        return { label: '安全中心工作台', icon: 'ShieldAlert', color: 'text-rose-500 bg-rose-50 border-rose-200' };
      case 'workshop':
        return { label: '应用构建中心', icon: 'LayoutGrid', color: 'text-amber-500 bg-amber-50 border-amber-200' };
    }
  };

  const getStatusBadge = (status: TaskStatus) => {
    switch (status) {
      case 'PENDING':
        return <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-slate-100 text-slate-500 border border-slate-200">排队中</span>;
      case 'RUNNING':
        return <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-blue-50 text-blue-600 border border-blue-200 flex items-center gap-1"><span className="w-1.5 h-1.5 rounded-full bg-blue-500 animate-ping inline-block" />运行中</span>;
      case 'PAUSED':
        return <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-amber-50 text-amber-600 border border-amber-200">已暂停</span>;
      case 'SUCCESS':
        return <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-emerald-50 text-emerald-600 border border-emerald-200">执行成功</span>;
      case 'FAILED':
        return <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-red-50 text-red-600 border border-red-200">执行失败</span>;
      case 'CANCELLED':
        return <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-slate-100 text-slate-400 border border-slate-200">已终止</span>;
    }
  };

  // Perform filtering
  const filteredTasks = tasks.filter(task => {
    const matchesSearch = task.name.toLowerCase().includes(searchTerm.toLowerCase()) || task.id.toLowerCase().includes(searchTerm.toLowerCase());
    const matchesCategory = categoryFilter === 'all' || task.category === categoryFilter;
    const matchesStatus = statusFilter === 'all' || task.status === statusFilter;
    return matchesSearch && matchesCategory && matchesStatus;
  });

  // Toggle selection for bulk operations
  const toggleSelectTask = (taskId: string) => {
    setSelectedTaskIds(prev =>
      prev.includes(taskId) ? prev.filter(id => id !== taskId) : [...prev, taskId]
    );
  };

  const toggleSelectAll = () => {
    if (selectedTaskIds.length === filteredTasks.length) {
      setSelectedTaskIds([]);
    } else {
      setSelectedTaskIds(filteredTasks.map(t => t.id));
    }
  };

  return (
    <div className="flex-1 flex flex-col overflow-hidden bg-slate-50 h-full">
      {/* Dynamic Notification Banners / Live Alarms Indicator */}
      <div className="bg-slate-900 text-white px-4 py-2 border-b border-slate-800 flex items-center justify-between text-xs shrink-0 select-none">
        <div className="flex items-center gap-3">
          <span className="flex items-center gap-1.5 bg-slate-800 px-2.5 py-1 rounded text-[11px] font-mono border border-slate-700/60 text-slate-300">
            <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
            Backend Broker Active (集群连接就绪)
          </span>
          <span className="text-slate-400">|</span>
          <div className="flex items-center gap-1 text-slate-300">
            <Volume2 size={13} className="text-blue-400" />
            <span>智能警报：</span>
            <button
              onClick={() => setIsAlertSoundEnabled(!isAlertSoundEnabled)}
              className={`px-1.5 py-0.5 rounded text-[10px] font-bold ${isAlertSoundEnabled ? 'bg-blue-600 text-white' : 'bg-slate-800 text-slate-500 hover:text-slate-300'}`}
            >
              {isAlertSoundEnabled ? '语音及音效启用 (ON)' : '已静音 (MUTED)'}
            </button>
          </div>
        </div>

        {/* Floating Notification Center Dropdown Toggle */}
        <div className="relative">
          <button
            onClick={() => setShowNotificationCenter(!showNotificationCenter)}
            className="flex items-center gap-1.5 px-3 py-1 bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 rounded transition-colors text-[11px] font-semibold"
          >
            <Bell size={13} className={alerts.length > 0 ? 'text-amber-400 animate-bounce' : ''} />
            <span>系统警报中心</span>
            {alerts.length > 0 && (
              <span className="bg-red-500 text-white text-[9px] font-extrabold px-1 rounded-full">{alerts.length}</span>
            )}
          </button>

          {showNotificationCenter && (
            <div className="absolute top-8 right-0 bg-white text-slate-800 border border-slate-200 rounded-lg shadow-2xl py-2 z-50 w-80 divide-y divide-slate-100 max-h-96 overflow-y-auto">
              <div className="px-3 py-1.5 bg-slate-50 flex items-center justify-between">
                <span className="font-bold text-xs text-slate-700">消息通知面板</span>
                <button onClick={() => setAlerts([])} className="text-[10px] text-blue-600 hover:underline">清空记录</button>
              </div>
              <div className="py-1">
                {alerts.map(alert => (
                  <div key={alert.id} className="p-2 px-3 text-[11px] hover:bg-slate-50 transition-colors">
                    <div className="flex items-center justify-between mb-0.5">
                      <span className={`px-1.5 py-0.2 rounded text-[8px] font-bold ${
                        alert.type === 'success' ? 'bg-emerald-50 text-emerald-600 border border-emerald-200' :
                        alert.type === 'error' ? 'bg-red-50 text-red-600 border border-red-200' : 'bg-amber-50 text-amber-600'
                      }`}>
                        {alert.type.toUpperCase()}
                      </span>
                      <span className="text-[9px] text-slate-400 font-mono">{alert.time}</span>
                    </div>
                    <p className="text-slate-600 leading-tight">{alert.text}</p>
                  </div>
                ))}
                {alerts.length === 0 && (
                  <div className="py-8 text-center text-slate-400 text-xs">暂无未读系统预警消息</div>
                )}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Top statistics overview cards */}
      <div className="p-4 grid grid-cols-6 gap-3 shrink-0 select-none">
        <div className="bg-white p-3.5 rounded-xl border border-slate-200 shadow-sm flex items-center justify-between">
          <div className="space-y-1">
            <p className="text-slate-400 text-[10px] font-bold uppercase tracking-wider">总排班任务</p>
            <h3 className="text-2xl font-black text-slate-800 font-mono">{tasks.length}</h3>
          </div>
          <span className="p-2.5 rounded-lg bg-blue-50 text-blue-600 border border-blue-100">
            <Cpu size={18} />
          </span>
        </div>

        <div className="bg-white p-3.5 rounded-xl border border-slate-200 shadow-sm flex items-center justify-between">
          <div className="space-y-1">
            <p className="text-slate-400 text-[10px] font-bold uppercase tracking-wider">正在执行中</p>
            <h3 className="text-2xl font-black text-blue-600 font-mono">{tasks.filter(t => t.status === 'RUNNING').length}</h3>
          </div>
          <span className="p-2.5 rounded-lg bg-blue-50 text-blue-600 border border-blue-100">
            <RefreshCw size={18} className="animate-spin" />
          </span>
        </div>

        <div className="bg-white p-3.5 rounded-xl border border-slate-200 shadow-sm flex items-center justify-between">
          <div className="space-y-1">
            <p className="text-slate-400 text-[10px] font-bold uppercase tracking-wider">排队就绪数</p>
            <h3 className="text-2xl font-black text-slate-600 font-mono">{tasks.filter(t => t.status === 'PENDING').length}</h3>
          </div>
          <span className="p-2.5 rounded-lg bg-slate-50 text-slate-500 border border-slate-100">
            <Clock size={18} />
          </span>
        </div>

        <div className="bg-white p-3.5 rounded-xl border border-slate-200 shadow-sm flex items-center justify-between">
          <div className="space-y-1">
            <p className="text-slate-400 text-[10px] font-bold uppercase tracking-wider">顺利完成数</p>
            <h3 className="text-2xl font-black text-emerald-600 font-mono">{tasks.filter(t => t.status === 'SUCCESS').length}</h3>
          </div>
          <span className="p-2.5 rounded-lg bg-emerald-50 text-emerald-600 border border-emerald-100">
            <CheckCircle2 size={18} />
          </span>
        </div>

        <div className="bg-white p-3.5 rounded-xl border border-slate-200 shadow-sm flex items-center justify-between">
          <div className="space-y-1">
            <p className="text-slate-400 text-[10px] font-bold uppercase tracking-wider">崩溃故障数</p>
            <h3 className="text-2xl font-black text-red-600 font-mono">{tasks.filter(t => t.status === 'FAILED').length}</h3>
          </div>
          <span className="p-2.5 rounded-lg bg-red-50 text-red-600 border border-red-100">
            <AlertTriangle size={18} />
          </span>
        </div>

        {/* Fault simulation injector */}
        <div
          onClick={injectFaultTask}
          className="bg-rose-900 text-white p-3 rounded-xl border border-rose-800 shadow-md flex flex-col justify-between cursor-pointer hover:bg-rose-950 transition-all group"
        >
          <div className="flex items-center justify-between">
            <span className="text-[9px] font-bold tracking-widest text-rose-300 uppercase">异常仿真器</span>
            <ShieldAlert size={14} className="text-rose-200 animate-pulse" />
          </div>
          <div>
            <h4 className="text-xs font-black mb-0.5 group-hover:underline">🚨 注入崩溃故障测试</h4>
            <p className="text-[9px] text-rose-200">一键注入故障并在 6 秒后发出高危声音弹窗警报</p>
          </div>
        </div>
      </div>

      {/* Control bar */}
      <div className="mx-4 p-3 bg-white border border-slate-200 rounded-xl shadow-xs flex flex-wrap items-center justify-between shrink-0 gap-3 select-none">
        <div className="flex items-center gap-3">
          {/* Keyword search input */}
          <div className="relative w-64">
            <span className="absolute left-3 top-2.5 text-slate-400">
              <Search size={13} />
            </span>
            <input
              type="text"
              placeholder="搜索任务名称 / ID / 标识..."
              value={searchTerm}
              onChange={e => setSearchTerm(e.target.value)}
              className="w-full pl-8 pr-3 h-8 text-xs border border-slate-200 rounded-lg focus:border-blue-500 focus:outline-hidden bg-slate-50/50"
            />
          </div>

          {/* Workbench category filter */}
          <div className="flex items-center gap-1.5">
            <span className="text-[10px] font-bold text-slate-400 uppercase">来源:</span>
            <select
              value={categoryFilter}
              onChange={e => setCategoryFilter(e.target.value)}
              className="h-8 border border-slate-200 rounded-lg text-xs bg-slate-50/30 px-2.5 font-medium"
            >
              <option value="all">📁 全部工作台 (All Workbenches)</option>
              <option value="integration">数据集成 (Integration)</option>
              <option value="ontology">本体管理 (Ontology)</option>
              <option value="knowledge">知识库 (Knowledge)</option>
              <option value="aip">AI 智能协同 (AIP)</option>
              <option value="security">安全中心 (Security)</option>
              <option value="workshop">应用构建 (Workshop)</option>
            </select>
          </div>

          {/* Status filter */}
          <div className="flex items-center gap-1.5">
            <span className="text-[10px] font-bold text-slate-400 uppercase">状态:</span>
            <div className="flex bg-slate-100 p-0.5 rounded-lg border border-slate-200">
              {['all', 'RUNNING', 'PAUSED', 'PENDING', 'SUCCESS', 'FAILED'].map(st => (
                <button
                  key={st}
                  onClick={() => setStatusFilter(st)}
                  className={`h-6 px-2 text-[10px] font-bold rounded-md transition-all ${
                    statusFilter === st ? 'bg-white text-slate-900 shadow-xs' : 'text-slate-500 hover:text-slate-800'
                  }`}
                >
                  {st === 'all' ? '全部' : st === 'RUNNING' ? '运行中' : st === 'PAUSED' ? '已暂停' : st === 'PENDING' ? '排队' : st === 'SUCCESS' ? '完成' : '失败'}
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* Batch action operations buttons */}
        <div className="flex items-center gap-2">
          {selectedTaskIds.length > 0 && (
            <div className="flex items-center gap-1.5 bg-blue-50 border border-blue-200 p-1 px-2.5 rounded-lg text-xs text-blue-700 animate-fade-in font-medium">
              <span>已选 {selectedTaskIds.length} 项:</span>
              <button
                onClick={handleBatchStart}
                className="hover:underline text-[11px] font-bold px-1 text-emerald-600 flex items-center gap-0.5"
              >
                <Play size={10} /> 批量启动
              </button>
              <span className="text-blue-300">|</span>
              <button
                onClick={handleBatchPause}
                className="hover:underline text-[11px] font-bold px-1 text-amber-600 flex items-center gap-0.5"
              >
                <Pause size={10} /> 批量暂停
              </button>
              <span className="text-blue-300">|</span>
              <button
                onClick={handleBatchTerminate}
                className="hover:underline text-[11px] font-bold px-1 text-red-600 flex items-center gap-0.5"
              >
                <StopCircle size={10} /> 批量终止
              </button>
              <span className="text-blue-300">|</span>
              <button
                onClick={handleBatchDelete}
                className="hover:underline text-[11px] font-bold px-1 text-slate-500 hover:text-red-600 flex items-center gap-0.5"
              >
                <Trash2 size={10} /> 批量删除
              </button>
            </div>
          )}
          <span className="text-[10px] text-slate-400">符合过滤：{filteredTasks.length} 条</span>
        </div>
      </div>

      {/* Main split workbench layout */}
      <div className="flex-1 flex overflow-hidden p-4 gap-4">
        
        {/* Left Side: Tasks Table List */}
        <div className="flex-1 bg-white border border-slate-200 rounded-xl shadow-xs overflow-hidden flex flex-col h-full">
          <div className="flex-1 overflow-y-auto">
            <table className="w-full text-left border-collapse text-xs select-none">
              <thead className="bg-slate-50 border-b border-slate-200 sticky top-0 text-slate-500 font-bold text-[10px] uppercase tracking-wider z-10">
                <tr>
                  <th className="py-2.5 px-3 w-8">
                    <input
                      type="checkbox"
                      checked={filteredTasks.length > 0 && selectedTaskIds.length === filteredTasks.length}
                      onChange={toggleSelectAll}
                      className="rounded border-slate-300 text-blue-600 h-3 w-3 cursor-pointer"
                    />
                  </th>
                  <th className="py-2.5 px-2 w-32">任务标识 (ID)</th>
                  <th className="py-2.5 px-3">任务名称</th>
                  <th className="py-2.5 px-3 w-36">来源工作台</th>
                  <th className="py-2.5 px-3 w-28">当前状态</th>
                  <th className="py-2.5 px-3 w-40">执行进度</th>
                  <th className="py-2.5 px-3 w-24 text-right">管理操作</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 text-slate-600">
                {filteredTasks.map(task => {
                  const isSelected = selectedTaskId === task.id;
                  const isChecked = selectedTaskIds.includes(task.id);
                  const catMeta = getCategoryMeta(task.category);

                  return (
                    <tr
                      key={task.id}
                      onClick={() => setSelectedTaskId(task.id)}
                      className={`hover:bg-slate-50 cursor-pointer transition-colors ${
                        isSelected ? 'bg-blue-50/40 text-slate-900 font-semibold' : ''
                      }`}
                    >
                      <td className="py-3 px-3" onClick={e => e.stopPropagation()}>
                        <input
                          type="checkbox"
                          checked={isChecked}
                          onChange={() => toggleSelectTask(task.id)}
                          className="rounded border-slate-300 text-blue-600 h-3 w-3 cursor-pointer"
                        />
                      </td>
                      <td className="py-3 px-2 font-mono text-[10px] font-bold text-slate-500 uppercase">
                        {task.id}
                      </td>
                      <td className="py-3 px-3">
                        <div className="font-semibold text-slate-800 line-clamp-1 truncate" title={task.name}>
                          {task.name}
                        </div>
                        <div className="text-[9px] text-slate-400 flex items-center gap-1.5 mt-0.5">
                          <span>创建时间: {task.createdAt}</span>
                          <span>•</span>
                          <span>触发: {task.triggerType === 'MANUAL' ? '人工' : task.triggerType === 'SCHEDULED' ? '定时' : '事件触发'}</span>
                        </div>
                      </td>
                      <td className="py-3 px-3">
                        <div className={`px-2 py-1 rounded-md border text-[10px] inline-flex items-center gap-1.5 font-semibold ${catMeta.color}`}>
                          <LucideIcon name={catMeta.icon} size={11} />
                          <span>{catMeta.label}</span>
                        </div>
                      </td>
                      <td className="py-3 px-3">
                        {getStatusBadge(task.status)}
                      </td>
                      <td className="py-3 px-3">
                        <div className="space-y-1">
                          <div className="flex items-center justify-between text-[10px] font-mono text-slate-400">
                            <span>{task.progress}%</span>
                          </div>
                          <div className="w-full bg-slate-100 rounded-full h-1.5 overflow-hidden border border-slate-200/50">
                            <div
                              className={`h-full rounded-full transition-all duration-500 ${
                                task.status === 'SUCCESS' ? 'bg-emerald-500' :
                                task.status === 'FAILED' ? 'bg-red-500' :
                                task.status === 'PAUSED' ? 'bg-amber-400' : 'bg-blue-500'
                              }`}
                              style={{ width: `${task.progress}%` }}
                            />
                          </div>
                        </div>
                      </td>
                      <td className="py-3 px-3 text-right" onClick={e => e.stopPropagation()}>
                        <div className="flex items-center justify-end gap-1">
                          {/* Play/Pause control based on status */}
                          {task.status === 'RUNNING' ? (
                            <button
                              onClick={() => handlePauseTask(task.id)}
                              className="p-1 hover:bg-slate-200/60 rounded text-amber-600"
                              title="暂停执行"
                            >
                              <Pause size={12} />
                            </button>
                          ) : (task.status === 'PAUSED' || task.status === 'PENDING' || task.status === 'CANCELLED' || task.status === 'FAILED') ? (
                            <button
                              onClick={() => handleStartTask(task.id)}
                              className="p-1 hover:bg-slate-200/60 rounded text-emerald-600"
                              title="开始执行"
                            >
                              <Play size={12} />
                            </button>
                          ) : null}

                          {/* Terminate running/paused tasks */}
                          {(task.status === 'RUNNING' || task.status === 'PAUSED') && (
                            <button
                              onClick={() => handleTerminateTask(task.id)}
                              className="p-1 hover:bg-slate-200/60 rounded text-red-500"
                              title="终止任务"
                            >
                              <StopCircle size={12} />
                            </button>
                          )}

                          {/* Delete historical task */}
                          <button
                            onClick={() => handleDeleteTask(task.id)}
                            className="p-1 hover:bg-slate-200/60 rounded text-slate-400 hover:text-red-600"
                            title="删除任务记录"
                          >
                            <Trash2 size={12} />
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
                {filteredTasks.length === 0 && (
                  <tr>
                    <td colSpan={7} className="py-12 text-center text-slate-400">
                      没有找到符合条件的后台异步任务
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>

        {/* Right Side: Task Diagnostics & Positioning console */}
        <div className="w-[420px] bg-slate-900 rounded-xl overflow-hidden flex flex-col border border-slate-800 text-slate-300 h-full">
          {activeTask ? (
            <div className="flex-1 flex flex-col overflow-hidden">
              {/* Task Header info */}
              <div className="p-4 bg-slate-950/60 border-b border-slate-800 select-none">
                <div className="flex items-center justify-between gap-2 mb-2">
                  <span className="text-[10px] font-mono font-bold text-slate-500">{activeTask.id}</span>
                  <div className="flex items-center gap-1.5">
                    {getStatusBadge(activeTask.status)}
                    <span className="text-[10px] bg-slate-800 px-1.5 py-0.5 rounded text-slate-400 font-mono">
                      PID: {activeTask.workerId.split('-').pop()}
                    </span>
                  </div>
                </div>
                <h3 className="font-bold text-white text-xs leading-tight mb-2">
                  {activeTask.name}
                </h3>

                {/* Go to source workbench direct link */}
                <button
                  onClick={() => {
                    // Navigate to source workbench
                    onViewModeChange(activeTask.targetPath);
                    showToast('info', `已通过 AIP 自动定位并跳转至 【${getCategoryMeta(activeTask.category).label}】`);
                  }}
                  className="w-full mt-2.5 py-1.5 px-3 bg-blue-600 hover:bg-blue-500 text-white rounded font-bold text-[10px] flex items-center justify-center gap-1.5 transition-colors shadow-sm cursor-pointer"
                >
                  <Compass size={12} />
                  <span>🚀 跟踪定位：一键跳转到关联工作台 (Source Workbench)</span>
                </button>
              </div>

              {/* Central interactive segment for Node Pipeline Trace */}
              <div className="p-3 bg-slate-950/20 border-b border-slate-800 shrink-0 select-none">
                <span className="text-[9px] font-bold tracking-widest text-slate-500 uppercase mb-2 block">
                  执行管道拓扑诊断 (Pipeline Trace Topology)
                </span>
                
                {/* SVG styled flow diagram indicating pipeline steps */}
                <div className="flex items-center justify-between px-1.5 py-1 text-[9px] relative mt-1">
                  {/* Background connecting lines */}
                  <div className="absolute top-1/2 left-4 right-4 h-0.5 bg-slate-800 -translate-y-1/2 z-0" />
                  
                  {activeTask.pipelineSteps.map((step, idx) => (
                    <div key={idx} className="flex flex-col items-center z-10 relative space-y-1">
                      <div className={`w-5 h-5 rounded-full border-2 flex items-center justify-center font-bold text-[9px] ${
                        step.status === 'success' ? 'bg-emerald-950 border-emerald-500 text-emerald-400' :
                        step.status === 'running' ? 'bg-blue-950 border-blue-500 text-blue-400 animate-pulse' :
                        step.status === 'failed' ? 'bg-red-950 border-red-500 text-red-400' : 'bg-slate-900 border-slate-800 text-slate-600'
                      }`}>
                        {step.status === 'success' ? (
                          <Check size={10} />
                        ) : step.status === 'running' ? (
                          <Loader size={10} className="animate-spin" />
                        ) : step.status === 'failed' ? (
                          <AlertCircle size={10} />
                        ) : (
                          <span>{idx + 1}</span>
                        )}
                      </div>
                      <span className={`font-semibold tracking-tight ${
                        step.status === 'success' ? 'text-emerald-400' :
                        step.status === 'running' ? 'text-blue-400 font-bold' :
                        step.status === 'failed' ? 'text-red-400 font-bold' : 'text-slate-500'
                      }`} style={{ fontSize: '8px' }}>
                        {step.name}
                      </span>
                    </div>
                  ))}
                </div>
              </div>

              {/* Task parameter details metadata block */}
              <div className="p-3 bg-slate-950/40 border-b border-slate-800 text-[10px] space-y-1 font-mono">
                <span className="text-[9px] font-bold tracking-widest text-slate-500 uppercase block select-none">
                  计算负载载荷参数 (Payload Parameters)
                </span>
                <div className="grid grid-cols-2 gap-x-2 gap-y-0.5 text-slate-400">
                  {Object.entries(activeTask.parameters).map(([key, val]) => (
                    <div key={key} className="truncate">
                      <span className="text-slate-600">{key}:</span> {String(val)}
                    </div>
                  ))}
                </div>
              </div>

              {/* Live streaming dark Terminal Logs view */}
              <div className="flex-1 flex flex-col min-h-0 bg-slate-950 relative">
                <div className="px-3 py-1 bg-slate-900 text-slate-400 font-mono text-[9px] flex items-center justify-between border-b border-slate-950 select-none">
                  <span className="flex items-center gap-1">
                    <span className="w-1.5 h-1.5 rounded-full bg-blue-400 animate-pulse" />
                    容器标准输出控制台 (Stdout Stream)
                  </span>
                  <span>编码: UTF-8</span>
                </div>

                <div className="flex-1 p-3 font-mono text-[10px] overflow-y-auto space-y-1.5 leading-normal select-text">
                  {activeTask.logs.map((log, index) => {
                    let logColor = 'text-slate-300';
                    if (log.includes('[ERROR]')) logColor = 'text-red-400 font-bold';
                    else if (log.includes('[SUCCESS]')) logColor = 'text-emerald-400 font-bold';
                    else if (log.includes('[WARN]')) logColor = 'text-amber-400 font-semibold';
                    else if (log.includes('[INFO]')) logColor = 'text-slate-400';

                    return (
                      <div key={index} className={`whitespace-pre-wrap break-all ${logColor}`}>
                        {log}
                      </div>
                    );
                  })}
                  <div ref={consoleEndRef} />
                </div>
              </div>
            </div>
          ) : (
            <div className="flex-1 flex flex-col items-center justify-center text-slate-500 text-xs p-6 select-none space-y-2">
              <Cpu size={32} className="text-slate-700 animate-pulse" />
              <span>请在左侧列表中选择一个排班或进行中的后台异步任务</span>
              <span className="text-[10px] text-slate-600">选择后即可在此调取流式容器控制台日志和管线数据</span>
            </div>
          )}
        </div>

      </div>
    </div>
  );
}
