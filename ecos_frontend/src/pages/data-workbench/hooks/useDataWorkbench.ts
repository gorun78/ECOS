/**
 * useDataWorkbench — extracted from DataWorkbenchLayout
 * Owns the 6 CRUD/test handlers + associated form state + data state.
 * Pure extraction: logic unchanged, signatures preserved.
 * @license Apache-2.0
 */

import { useState, useEffect, useCallback } from 'react';
import type { DataConnection, DataSyncTask, DataPipeline, DataHealthCheck } from '../types';

type ShowToast = (type: 'success' | 'info' | 'error', message: string) => void;
type TFn = (key: string, params?: Record<string, unknown>) => string;

export type ComputeEngine = 'memory' | 'doris';

export interface PbOutput {
  datasetPath: string;
  columns: string[];
  rowCount: number;
  lastCompiled: string;
  expressionsCount: number;
}

export function useDataWorkbench(showToast: ShowToast, t: TFn) {
  // ── Data loaded from API ──
  const [connections, setConnections] = useState<DataConnection[]>([]);
  const [syncTasks, setSyncTasks] = useState<DataSyncTask[]>([]);
  const [pipelines, setPipelines] = useState<DataPipeline[]>([]);
  const [healthChecks, setHealthChecks] = useState<DataHealthCheck[]>([]);

  useEffect(() => {
    import('../api').then(({ fetchDataConnections, fetchDataSyncTasks, fetchDataPipelines, fetchDataHealthChecks }) => {
      fetchDataConnections().then(setConnections).catch(console.error);
      fetchDataSyncTasks().then(setSyncTasks).catch(console.error);
      fetchDataPipelines().then(setPipelines).catch(console.error);
      fetchDataHealthChecks().then(setHealthChecks).catch(console.error);
    }).catch(console.error);
  }, []);

  // ── Selected IDs ──
  const [selConnId, setSelConnId] = useState<string>('');
  const [selTaskId, setSelTaskId] = useState<string>('');
  const [selCheckId, setSelCheckId] = useState<string>('');
  const [editingPipelineId, setEditingPipelineId] = useState<string | null>(null);
  const [computeEngine, setComputeEngine] = useState<ComputeEngine>('memory');

  // ── Pipeline builder output ──
  const [pbOutput, setPbOutput] = useState<PbOutput | null>(null);

  // ── Connection testing ──
  const [testingConnId, setTestingConnId] = useState<string | null>(null);
  const [testingLogs, setTestingLogs] = useState<string[]>([]);

  // ── Modal toggles ──
  const [showAddConn, setShowAddConn] = useState(false);
  const [showAddSync, setShowAddSync] = useState(false);
  const [showAddCheck, setShowAddCheck] = useState(false);

  // ── Connection form ──
  const [ncName, setNcName] = useState('');
  const [ncType, setNcType] = useState<'postgresql' | 's3' | 'rest_api' | 'sftp' | 'sap'>('postgresql');
  const [ncHost, setNcHost] = useState('');
  const [ncPort, setNcPort] = useState(5432);
  const [ncUser, setNcUser] = useState('');

  // ── Sync form ──
  const [nsName, setNsName] = useState('');
  const [nsConn, setNsConn] = useState('');
  const [nsTable, setNsTable] = useState('');
  const [nsMode, setNsMode] = useState<'snapshot' | 'incremental' | 'append'>('snapshot');
  const [nsSched, setNsSched] = useState<'manual' | 'hourly' | 'daily' | 'cron'>('hourly');

  // ── Health check form ──
  const [nhName, setNhName] = useState('');
  const [nhDs, setNhDs] = useState('');
  const [nhType, setNhType] = useState<'row_count' | 'null_check' | 'schema_check' | 'freshness'>('row_count');
  const [nhThr, setNhThr] = useState('1000');

  // ── Handlers ──
  const testConnection = useCallback(async (connId: string) => {
    setTestingConnId(connId); setTestingLogs([]);
    const conn = connections.find(c => c.id === connId);
    if (!conn) return;
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
    } catch (e: any) {
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
        username: ncUser || 'anonymous',
      });
      if (conn) {
        setConnections(p => [...p, conn]); setSelConnId(conn.id); setShowAddConn(false);
        showToast('success', t('databench.layout.toast.connCreated', { name: ncName }));
        setTimeout(() => testConnection(conn.id), 500);
      } else {
        showToast('error', t('databench.layout.toast.connTestFailed', { name: ncName }));
      }
    } catch (e: any) {
      showToast('error', t('databench.layout.toast.connTestFailed', { name: ncName }));
    }
  }, [ncName, ncType, ncHost, ncPort, ncUser, showToast, t, testConnection]);

  const triggerSync = useCallback(async (taskId: string) => {
    setSyncTasks(p => p.map(tk => tk.id === taskId ? { ...tk, status: 'running' as const } : tk));
    showToast('info', t('databench.layout.toast.syncRunning'));
    try {
      const { triggerSyncRun } = await import('../api');
      const result = await triggerSyncRun(taskId);
      const tn = new Date().toISOString().replace('T', ' ').substring(0, 19);
      if (result && result.status !== 'failed') {
        setSyncTasks(p => p.map(tk => tk.id === taskId ? { ...tk, status: 'success' as const, lastRunTime: tn, errorMessage: undefined } : tk));
        const task = syncTasks.find(tk => tk.id === taskId);
        showToast('success', t('databench.layout.toast.syncSuccess', { name: task?.name }));
      } else {
        setSyncTasks(p => p.map(tk => tk.id === taskId ? { ...tk, status: 'failed' as const, lastRunTime: tn, errorMessage: 'Execution failed' } : tk));
        const task = syncTasks.find(tk => tk.id === taskId);
        showToast('error', t('databench.layout.toast.syncFailed', { name: task?.name }));
      }
    } catch (e: any) {
      const tn = new Date().toISOString().replace('T', ' ').substring(0, 19);
      setSyncTasks(p => p.map(tk => tk.id === taskId ? { ...tk, status: 'failed' as const, lastRunTime: tn, errorMessage: e.message } : tk));
      const task = syncTasks.find(tk => tk.id === taskId);
      showToast('error', t('databench.layout.toast.syncFailed', { name: task?.name }));
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
    } catch (e: any) {
      showToast('error', t('databench.layout.toast.syncFormIncomplete'));
    }
  }, [nsName, nsTable, nsConn, nsMode, nsSched, connections, showToast, t]);

  const createHealth = useCallback(async () => {
    if (!nhName.trim()) { showToast('error', t('databench.layout.toast.healthNameRequired')); return; }
    try {
      const { createHealthCheck } = await import('../api');
      const ruleType = nhType === 'row_count' ? 'COMPLETENESS'
        : nhType === 'null_check' ? 'COMPLETENESS'
        : nhType === 'freshness' ? 'TIMELINESS'
        : nhType === 'schema_check' ? 'VALIDITY'
        : 'ACCURACY';
      const check = await createHealthCheck({
        name: nhName,
        ruleType,
        targetEntity: nhDs,
        ruleExpression: nhThr,
        description: `${nhType} threshold: ${nhThr}`,
      });
      if (check) {
        setHealthChecks(p => [...p, check]); setSelCheckId(check.id); setShowAddCheck(false);
        showToast('success', t('databench.layout.toast.healthCreated', { name: nhName }));
      } else {
        showToast('error', t('databench.layout.toast.healthNameRequired'));
      }
    } catch (e: any) {
      showToast('error', t('databench.layout.toast.healthNameRequired'));
    }
  }, [nhName, nhType, nhDs, nhThr, showToast, t]);

  const runCheck = useCallback(async (checkId: string) => {
    setHealthChecks(p => p.map(c => c.id === checkId ? { ...c, status: 'pending' as const, message: computeEngine === 'doris' ? t('databench.layout.health.dorisCheck') : t('databench.layout.health.memoryCheck') } : c));
    try {
      const { runHealthCheck } = await import('../api');
      await runHealthCheck();
      const tn = new Date().toISOString().replace('T', ' ').substring(0, 19);
      setHealthChecks(p => p.map(c => {
        if (c.id !== checkId) return c;
        const finalStatus = c.checkType === 'freshness' ? 'warning' as const : 'passed' as const;
        const msg = c.checkType === 'freshness' ? t('databench.layout.health.warningFreshness') : t('databench.layout.health.passed');
        return { ...c, status: finalStatus, lastChecked: tn, message: msg };
      }));
      showToast('success', t('databench.layout.toast.healthCheckComplete'));
    } catch (e: any) {
      const tn = new Date().toISOString().replace('T', ' ').substring(0, 19);
      setHealthChecks(p => p.map(c => c.id === checkId ? { ...c, status: 'failed' as const, lastChecked: tn, message: e.message } : c));
      showToast('error', t('databench.layout.toast.healthCheckComplete'));
    }
  }, [computeEngine, showToast, t]);

  return {
    // data
    connections, setConnections,
    syncTasks, setSyncTasks,
    pipelines, setPipelines,
    healthChecks, setHealthChecks,
    // selected ids
    selConnId, setSelConnId,
    selTaskId, setSelTaskId,
    selCheckId, setSelCheckId,
    editingPipelineId, setEditingPipelineId,
    computeEngine, setComputeEngine,
    // pb output
    pbOutput, setPbOutput,
    // testing
    testingConnId, setTestingConnId,
    testingLogs,
    // modal toggles
    showAddConn, setShowAddConn,
    showAddSync, setShowAddSync,
    showAddCheck, setShowAddCheck,
    // connection form
    ncName, setNcName,
    ncType, setNcType,
    ncHost, setNcHost,
    ncPort, setNcPort,
    ncUser, setNcUser,
    // sync form
    nsName, setNsName,
    nsConn, setNsConn,
    nsTable, setNsTable,
    nsMode, setNsMode,
    nsSched, setNsSched,
    // health form
    nhName, setNhName,
    nhDs, setNhDs,
    nhType, setNhType,
    nhThr, setNhThr,
    // handlers
    testConnection,
    createConnection,
    triggerSync,
    createSync,
    createHealth,
    runCheck,
  };
}
