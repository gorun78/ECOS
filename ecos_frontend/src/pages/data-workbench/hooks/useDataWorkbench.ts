/**
 * useDataWorkbench — extracted from DataWorkbenchLayout
 * Owns the 6 CRUD/test handlers + associated form state + data state.
 * Pure extraction: logic unchanged, signatures preserved.
 * @license Apache-2.0
 */

import { useState, useEffect, useCallback, useRef, type SetStateAction } from 'react';
import type { DataConnection, DataSyncTask, DataPipeline } from '../types';
import type { ConnType } from '../types';
import { getErrorMessage } from '../helpers';

type ShowToast = (type: 'success' | 'info' | 'error', message: string) => void;
type TFn = (key: string, params?: Record<string, unknown>) => string;

export type ComputeEngine = 'memory' | 'doris';

export interface PbOutput {
  datasetPath: string;
  columns: string[];
  rowCount: number;
  lastCompiled: string;
  expressionsCount: number;
  /** Wave 3 (lower) 缺陷②: bumped by PipelineBuilderTab after save/create */
  refreshTick?: number;
}

export function useDataWorkbench(showToast: ShowToast, t: TFn) {
  // ── Data loaded from API ──
  const [connections, setConnections] = useState<DataConnection[]>([]);
  const [syncTasks, setSyncTasks] = useState<DataSyncTask[]>([]);
  const [pipelines, setPipelines] = useState<DataPipeline[]>([]);

  useEffect(() => {
    import('../api').then(({ fetchDataConnections, fetchDataSyncTasks, fetchDataPipelines }) => {
      fetchDataConnections().then(setConnections).catch((e) => { console.error(e); showToast('error', t('databench.layout.toast.loadConnectionsFailed')); });
      fetchDataSyncTasks().then(setSyncTasks).catch((e) => { console.error(e); showToast('error', t('databench.layout.toast.loadSyncTasksFailed')); });
      fetchDataPipelines().then(setPipelines).catch((e) => { console.error(e); showToast('error', t('databench.layout.toast.loadPipelinesFailed')); });
    }).catch(console.error);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // ── Selected IDs ──
  const [selConnId, setSelConnId] = useState<string>('');
  const [selTaskId, setSelTaskId] = useState<string>('');
  const [editingPipelineId, setEditingPipelineId] = useState<string | null>(null);
  const [computeEngine, setComputeEngine] = useState<ComputeEngine>('memory');

  // ── Pipeline builder output ──
  const [pbOutput, setPbOutputState] = useState<PbOutput | null>(null);
  // Local mirror of pbOutput.refreshTick: pbOutput may stay `null` forever
  // (its other fields are never populated) so we cannot read the tick from
  // it directly. We mirror the incoming tick whenever setPbOutput is called.
  const [refreshTick, setRefreshTick] = useState(0);
  const setPbOutput = useCallback((v: SetStateAction<PbOutput | null>) => {
    setPbOutputState(v);
    if (typeof v !== 'function' && v?.refreshTick) setRefreshTick(v.refreshTick);
  }, []);
  useEffect(() => {
    if (refreshTick === 0) return; // mount tick already covered above
    import('../api').then(({ fetchDataPipelines }) => fetchDataPipelines().then(setPipelines).catch(console.error))
      .catch(console.error);
  }, [refreshTick]);

  // ── Connection testing ──
  const [testingConnId, setTestingConnId] = useState<string | null>(null);
  const [testingLogs, setTestingLogs] = useState<string[]>([]);
  /** 切换连接时清空上一次测试日志（防止日志跨连接串台） */
  const clearTestingLogs = useCallback(() => setTestingLogs([]), []);

  // ── In-flight sync triggers (taskId 集合，防重复触发 + 支撑轮询) ──
  const inFlightSyncRef = useRef<Set<string>>(new Set());

  // ── Modal toggles ──
  const [showAddConn, setShowAddConn] = useState(false);
  const [showAddSync, setShowAddSync] = useState(false);

  // ── Connection form ──
  const [ncName, setNcName] = useState('');
  const [ncType, setNcType] = useState<ConnType>('postgresql');
  const [ncHost, setNcHost] = useState('');
  const [ncPort, setNcPort] = useState(5432);
  const [ncUser, setNcUser] = useState('');
  const [ncPassword, setNcPassword] = useState('');
  const [ncDatabase, setNcDatabase] = useState('');
  // PMO-47 Wave3: 类型特定字段统一存储
  const [ncExtra, setNcExtra] = useState<Record<string, string | number | boolean>>({});
  const setNcExtraField = useCallback((key: string, val: string | number | boolean) => {
    setNcExtra(prev => ({ ...prev, [key]: val }));
  }, []);
  // PMO-48-T5: 类型切换时按类型预填默认值 (extra 字段与 uiType 对齐)
  const handleNcTypeChange = useCallback((t: string) => {
    setNcType(t as ConnType);
    setNcExtra({});
    // 按类型重置 common 字段默认值
    setNcPort(t === 'oracle' ? 1521 : t === 'mssql' ? 1433 : t === 'dm' ? 5236
      : t === 'kingbase' ? 54321 : t === 'gaussdb' ? 5432
      : t === 'doris' ? 9030 : t === 'clickhouse' ? 8123 : t === 'mysql' ? 3306
      : t === 'minio' ? 9000 : t === 'mongodb' ? 27017
      : t === 'kafka' ? 9092 : t === 'sap' ? 3300
      : t === 'sftp' ? 22 : 5432);
  }, []);

  // ── Sync form ──
  const [nsName, setNsName] = useState('');
  const [nsConn, setNsConn] = useState('');
  const [nsTable, setNsTable] = useState('');
  const [nsMode, setNsMode] = useState<'snapshot' | 'incremental' | 'append'>('snapshot');
  const [nsSched, setNsSched] = useState<'manual' | 'hourly' | 'daily' | 'cron'>('hourly');

  // ── Handlers ──
  const testConnection = useCallback(async (connId: string) => {
    // 先解析连接：列表里查不到（如新建后本回调仍持有旧列表）直接返回，避免 testingConnId 永不复位
    const conn = connections.find(c => c.id === connId);
    if (!conn) return;
    setTestingConnId(connId); setTestingLogs([]);
    const addLog = (msg: string) => setTestingLogs(p => [...p, msg]);
    addLog(`${t('databench.layout.testLog.initDriver')}[${conn.type.toUpperCase()}]`);
    addLog(t('databench.layout.testLog.connecting'));
    try {
      const { testDataSource } = await import('../api');
      const result = await testDataSource(connId);
      if (result?.success) {
        const tn = new Date().toISOString().replace('T', ' ').substring(0, 19);
        addLog(t('databench.layout.testLog.connected'));
        addLog(t('databench.layout.testLog.metadataOk'));
        setConnections(p => p.map(c => c.id === connId ? { ...c, status: 'connected' as const, config: { ...c.config, lastTested: tn } } : c));
        showToast('success', t('databench.layout.toast.connTestPassed', { name: conn.name }));
      } else {
        addLog(t('databench.layout.testLog.sshFailed'));
        addLog(t('databench.layout.testLog.testFailed'));
        setConnections(p => p.map(c => c.id === connId ? { ...c, status: 'error' as const } : c));
        showToast('error', t('databench.layout.toast.connTestFailed', { name: conn.name }));
      }
    } catch (e) {
      addLog(t('databench.layout.testLog.testFailed'));
      setConnections(p => p.map(c => c.id === connId ? { ...c, status: 'error' as const } : c));
      showToast('error', t('databench.layout.toast.connTestFailed', { name: conn.name }));
    } finally {
      setTestingConnId(null);
    }
  }, [connections, showToast, t]);

  const createConnection = useCallback(async () => {
    if (!ncName.trim()) { showToast('error', t('databench.layout.toast.connNameRequired')); return; }
    try {
      const { createDataSource } = await import('../api');
      const conn = await createDataSource({
        name: ncName, type: ncType, host: ncHost || 'localhost', port: ncPort,
        username: ncUser || 'anonymous', password: ncPassword, database: ncDatabase,
        strategy: { trigger: 'ON_SAVE', countMethod: 'ESTIMATE' },
        extra: ncExtra,
      });
      if (conn) {
        setConnections(p => [...p, conn]); setSelConnId(conn.id); setShowAddConn(false);
        showToast('success', t('databench.layout.toast.connCreated', { name: ncName }));
        setTimeout(() => testConnection(conn.id), 500);
      } else {
        showToast('error', t('databench.layout.toast.connTestFailed', { name: ncName }));
      }
    } catch (e) {
      showToast('error', t('databench.layout.toast.connTestFailed', { name: ncName }));
    }
  }, [ncName, ncType, ncHost, ncPort, ncUser, ncPassword, ncDatabase, ncExtra, showToast, t, testConnection]);

  const testConnectionRaw = useCallback(async () => {
    if (!ncName.trim()) { showToast('error', t('databench.layout.toast.connNameRequired')); return; }
    try {
      const { testDataSourceRaw } = await import('../api');
      const result = await testDataSourceRaw({
        name: ncName, type: ncType, host: ncHost || 'localhost', port: ncPort,
        username: ncUser || 'anonymous', password: ncPassword, database: ncDatabase,
        extra: ncExtra,
      });
      if (result?.success) {
        showToast('success', t('dw.conn.testConnSuccess'));
      } else {
        showToast('error', t('dw.conn.testConnFailed'));
      }
    } catch (e) {
      showToast('error', t('dw.conn.testConnFailed'));
    }
  }, [ncName, ncType, ncHost, ncPort, ncUser, ncPassword, ncDatabase, ncExtra, showToast, t]);

  const triggerSync = useCallback(async (taskId: string) => {
    // 同一任务防止重复触发（轮询期间再次点击）
    if (inFlightSyncRef.current.has(taskId)) return;
    inFlightSyncRef.current.add(taskId);
    setSyncTasks(p => p.map(tk => tk.id === taskId ? { ...tk, status: 'running' as const } : tk));
    showToast('info', t('databench.layout.toast.syncRunning'));
    const stamp = () => new Date().toISOString().replace('T', ' ').substring(0, 19);
    const taskName = () => syncTasks.find(tk => tk.id === taskId)?.name;
    const finish = (ok: boolean, errMsg?: string) => {
      inFlightSyncRef.current.delete(taskId);
      if (ok) {
        setSyncTasks(p => p.map(tk => tk.id === taskId ? { ...tk, status: 'success' as const, lastRunTime: stamp(), errorMessage: undefined } : tk));
        showToast('success', t('databench.layout.toast.syncSuccess', { name: taskName() }));
      } else {
        setSyncTasks(p => p.map(tk => tk.id === taskId ? { ...tk, status: 'failed' as const, lastRunTime: stamp(), errorMessage: errMsg || 'Execution failed' } : tk));
        showToast('error', t('databench.layout.toast.syncFailed', { name: taskName() }));
      }
    };
    try {
      const { triggerSyncRun, fetchPipelineRunStatus } = await import('../api');
      const result = await triggerSyncRun(taskId);
      // api 层失败吞掉返回 null —— 无后端可达性证据时绝不能报成功
      if (!result) { finish(false, t('databench.layout.toast.syncTriggerUnreachable')); return; }
      const st = (result.status || '').toLowerCase();
      const OK_TERMINAL = ['success', 'succeeded', 'completed', 'finished', 'done'];
      const BAD_TERMINAL = ['failed', 'error', 'cancelled', 'canceled', 'timeout'];
      if (st && OK_TERMINAL.includes(st)) { finish(true); return; }
      if (st && BAD_TERMINAL.includes(st)) { finish(false, result.status); return; }
      // running / pending / 无状态 ⇒ 保持 running，轮询 run 终态后才上报
      const runKey = result.runId || taskId;
      const POLL_MS = 2000;
      const MAX_ROUNDS = 60;
      for (let round = 0; round < MAX_ROUNDS; round++) {
        await new Promise(r => setTimeout(r, POLL_MS));
        const rs = await fetchPipelineRunStatus(runKey);
        if (!rs) continue; // 单次查询失败不判死，继续轮询
        const rst = (rs.status || '').toLowerCase();
        if (OK_TERMINAL.includes(rst)) { finish(true); return; }
        if (BAD_TERMINAL.includes(rst)) { finish(false, rs.errorMsg || rs.status); return; }
      }
      // 超时仍非终态：结果未知，绝不谎报成功
      inFlightSyncRef.current.delete(taskId);
      setSyncTasks(p => p.map(tk => tk.id === taskId ? { ...tk, status: 'error' as const, errorMessage: t('databench.layout.toast.syncResultUnknown') } : tk));
      showToast('error', t('databench.layout.toast.syncResultUnknown', { name: taskName() }));
    } catch (e) {
      finish(false, getErrorMessage(e));
    }
  }, [syncTasks, showToast, t]);

  const createSync = useCallback(async () => {
    if (!nsName.trim() || !nsTable.trim()) { showToast('error', t('databench.layout.toast.syncFormIncomplete')); return; }
    try {
      const { createSyncTask } = await import('../api');
      const task = await createSyncTask({
        name: nsName,
        sourceConnectionId: nsConn || (connections[0]?.id || ''),
        sourceTable: nsTable,
        targetTable: nsTable,
        syncMode: nsMode,
        schedule: nsSched === 'cron' ? '' : nsSched,
      });
      if (task) {
        setSyncTasks(p => [...p, task]); setSelTaskId(task.id); setShowAddSync(false);
        showToast('success', t('databench.layout.toast.syncCreated', { name: nsName }));
      } else {
        showToast('error', t('databench.layout.toast.syncFormIncomplete'));
      }
    } catch (e) {
      showToast('error', t('databench.layout.toast.syncFormIncomplete'));
    }
  }, [nsName, nsTable, nsConn, nsMode, nsSched, connections, showToast, t]);

  return {
    // data
    connections, setConnections,
    syncTasks, setSyncTasks,
    pipelines, setPipelines,
    // selected ids
    selConnId, setSelConnId,
    selTaskId, setSelTaskId,
    editingPipelineId, setEditingPipelineId,
    computeEngine, setComputeEngine,
    // pb output
    pbOutput, setPbOutput,
    // testing
    testingConnId, setTestingConnId,
    testingLogs,
    clearTestingLogs,
    // modal toggles
    showAddConn, setShowAddConn,
    showAddSync, setShowAddSync,
    // connection form
    ncName, setNcName,
    ncType, setNcType, handleNcTypeChange,
    ncHost, setNcHost,
    ncPort, setNcPort,
    ncUser, setNcUser,
    ncPassword, setNcPassword,
    ncDatabase, setNcDatabase,
    ncExtra, setNcExtraField,
    testConnectionRaw,
    // sync form
    nsName, setNsName,
    nsConn, setNsConn,
    nsTable, setNsTable,
    nsMode, setNsMode,
    nsSched, setNsSched,
    // handlers
    testConnection,
    createConnection,
    triggerSync,
    createSync,
  };
}
