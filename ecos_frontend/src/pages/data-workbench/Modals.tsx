/**
 * Modals — Add Connection / Add Sync panels / External Interfaces drawer
 * Extracted from DataWorkbenchLayout.tsx
 * PMO-47 Wave3: type-specific ConfigForms + connection test
 * @license Apache-2.0
 */
import React, { useState, useRef, useEffect } from 'react';
import LucideIcon from './LucideIcon';
import { useTheme } from '../../components/ThemeContext';
import type { DataConnection, ConnType } from './types';
import { CONNECTION_TYPES, STANDARD_DISABLED_TYPES } from './types';
import { detectEdition, type EcosEdition } from './DataEngineConfigPanelTypes';
import {
  JdbcConfigForm, ObjectStorageConfigForm, FileSourceConfigForm,
  SftpConfigForm, SapConfigForm, RestApiConfigForm, KafkaConfigForm, MongoConfigForm,
  MinioConfigForm, FsConfigForm,
  type JdbcProps, type ObjStorageProps, type FileSourceProps,
  type SftpProps, type SapProps, type RestApiProps,
  type KafkaProps, type MongoProps, type MinioProps, type FsProps,
} from './ConfigForms';

interface AddConnectionModalProps {
  t: (key: string) => string;
  locale: string;
  newConnName: string; setNewConnName: (v: string) => void;
  newConnType: string; setNewConnType: (v: string) => void;
  newConnHost: string; setNewConnHost: (v: string) => void;
  newConnPort: number; setNewConnPort: (v: number) => void;
  newConnUser: string; setNewConnUser: (v: string) => void;
  newConnPassword: string; setNewConnPassword: (v: string) => void;
  newConnDatabase: string; setNewConnDatabase: (v: string) => void;
  ncExtra: Record<string, string | number | boolean>;
  setNcExtraField: (key: string, val: string | number | boolean) => void;
  onClose: () => void;
  onCreate: () => void;
  onTestConnection: () => void;
}

export function AddConnectionModal({
  t, locale,
  newConnName, setNewConnName,
  newConnType, setNewConnType,
  newConnHost, setNewConnHost,
  newConnPort, setNewConnPort,
  newConnUser, setNewConnUser,
  newConnPassword, setNewConnPassword,
  newConnDatabase, setNewConnDatabase,
  ncExtra, setNcExtraField,
  onClose, onCreate, onTestConnection,
}: AddConnectionModalProps) {
  const { styles } = useTheme();
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<null | { ok: boolean; msg: string }>(null);
  const [previewVisible, setPreviewVisible] = useState(false);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewResult, setPreviewResult] = useState<{ fields: { name: string; type: string; required: boolean; comment?: string }[]; tableNames: string[] } | null>(null);

  // PMO-48-T5: Edition 检测
  const edition = useRef<EcosEdition>(detectEdition()).current;
  const isStandard = edition === 'standard';

  // PMO-48-T5: P-2 — 新建路径不调 catalog，不卡死
  // CLR 1/3 整卡高亮渲染由上层 ConnectionsTab 负责

  const isJdbc = ['postgresql', 'mysql', 'doris', 'clickhouse', 'oracle', 'mssql', 'dm', 'kingbase', 'gaussdb'].includes(newConnType);
  const isMongo = newConnType === 'mongodb';
  const isKafka = newConnType === 'kafka';
  const isMinio = newConnType === 'minio';
  const isFs = newConnType === 'fs';

  const commonJdbc: Omit<JdbcProps, 't'> = {
    host: newConnHost, setHost: setNewConnHost,
    port: newConnPort, setPort: setNewConnPort,
    database: newConnDatabase, setDatabase: setNewConnDatabase,
    username: newConnUser, setUsername: setNewConnUser,
    password: newConnPassword, setPassword: setNewConnPassword,
    ssl: ncExtra.ssl === true, setSsl: (v: boolean) => setNcExtraField('ssl', v),
    schema: (ncExtra.schema as string) ?? '', setSchema: (v: string) => setNcExtraField('schema', v),
    warehouse: (ncExtra.warehouse as string) ?? '', setWarehouse: (v: string) => setNcExtraField('warehouse', v),
    dbType: (ncExtra.dbType as string) ?? 'SERVICE_NAME', setDbType: (v: string) => setNcExtraField('dbType', v),
    actualKind: (ncExtra.actualKind as string) ?? undefined,
  };

  const commonObj: Omit<ObjStorageProps, 't'> = {
    endpointUrl: (ncExtra.endpointUrl as string) ?? newConnHost,
    setEndpointUrl: (v) => setNcExtraField('endpointUrl', v),
    bucket: (ncExtra.bucket as string) ?? '',
    setBucket: (v) => setNcExtraField('bucket', v),
    accessKey: (ncExtra.accessKey as string) ?? '',
    setAccessKey: (v) => setNcExtraField('accessKey', v),
    secretKey: (ncExtra.secretKey as string) ?? newConnPassword,
    setSecretKey: (v) => { setNcExtraField('secretKey', v); setNewConnPassword(v); },
    region: (ncExtra.region as string) ?? '',
    setRegion: (v) => setNcExtraField('region', v),
  };

  const commonFile: Omit<FileSourceProps, 't'> = {
    filePath: (ncExtra.filePath as string) ?? '',
    setFilePath: (v) => setNcExtraField('filePath', v),
    delimiter: (ncExtra.delimiter as string) ?? ',',
    setDelimiter: (v) => setNcExtraField('delimiter', v),
    encoding: (ncExtra.encoding as string) ?? 'UTF-8',
    setEncoding: (v) => setNcExtraField('encoding', v),
    hasHeader: ncExtra.hasHeader === true,
    setHasHeader: (v) => setNcExtraField('hasHeader', v),
    rowLimit: (ncExtra.rowLimit as number) ?? 0,
    setRowLimit: (v) => setNcExtraField('rowLimit', v),
  };

  const commonMinio: Omit<MinioProps, 't'> = {
    endpointUrl: (ncExtra.endpointUrl as string) ?? newConnHost,
    setEndpointUrl: (v) => setNcExtraField('endpointUrl', v),
    listenPort: (ncExtra.listenPort as number) ?? (newConnPort || 9000),
    setListenPort: (v) => setNcExtraField('listenPort', v),
    bucket: (ncExtra.bucket as string) ?? '',
    setBucket: (v) => setNcExtraField('bucket', v),
    roleArn: (ncExtra.roleArn as string) ?? '',
    setRoleArn: (v) => setNcExtraField('roleArn', v),
    accessKey: (ncExtra.accessKey as string) ?? '',
    setAccessKey: (v) => setNcExtraField('accessKey', v),
    secretKey: (ncExtra.secretKey as string) ?? newConnPassword,
    setSecretKey: (v) => { setNcExtraField('secretKey', v); setNewConnPassword(v); },
    pathPrefix: (ncExtra.pathPrefix as string) ?? '',
    setPathPrefix: (v) => setNcExtraField('pathPrefix', v),
  };

  const commonFs: Omit<FsProps, 't'> = {
    rootPath: (ncExtra.rootPath as string) ?? '',
    setRootPath: (v) => setNcExtraField('rootPath', v),
    pattern: (ncExtra.pattern as string) ?? '',
    setPattern: (v) => setNcExtraField('pattern', v),
    recursive: ncExtra.recursive === true,
    setRecursive: (v) => setNcExtraField('recursive', v),
  };

  const renderConfigForm = () => {
    switch (newConnType) {
      case 'postgresql':
        return <JdbcConfigForm t={t} kind="postgresql" {...commonJdbc} />;
      case 'mysql':
        return <JdbcConfigForm t={t} kind="mysql" {...commonJdbc} />;
      case 'doris':
        return <JdbcConfigForm t={t} kind="doris" {...commonJdbc} />;
      case 'clickhouse':
        return <JdbcConfigForm t={t} kind="clickhouse" {...commonJdbc} />;
      case 'oracle':
        return <JdbcConfigForm t={t} kind="oracle" {...commonJdbc} />;
      case 'mssql':
        return <JdbcConfigForm t={t} kind="mssql" {...commonJdbc} />;
      case 'dm':
        return <JdbcConfigForm t={t} kind="dm" {...commonJdbc} />;
      case 'kingbase':
        return <JdbcConfigForm t={t} kind="kingbase" {...commonJdbc} />;
      case 'gaussdb':
        return <JdbcConfigForm t={t} kind="gaussdb" {...commonJdbc} />;
      case 's3':
      case 'oss':
        return <ObjectStorageConfigForm t={t} kind={newConnType as 's3' | 'oss'} {...commonObj} />;
      case 'minio':
        return <MinioConfigForm t={t} {...commonMinio} />;
      case 'fs':
        return <FsConfigForm t={t} {...commonFs} />;
      case 'csv':
        return <FileSourceConfigForm t={t} {...commonFile} />;
      case 'sftp':
        return (
          <SftpConfigForm t={t}
            host={newConnHost} setHost={setNewConnHost}
            port={newConnPort} setPort={setNewConnPort}
            path={(ncExtra.path as string) ?? ''} setPath={(v: string) => setNcExtraField('path', v)}
            username={newConnUser} setUsername={setNewConnUser}
            password={newConnPassword} setPassword={setNewConnPassword}
            sshKey={(ncExtra.sshKey as string) ?? ''} setSshKey={(v: string) => setNcExtraField('sshKey', v)}
            sshKeyPass={(ncExtra.sshKeyPass as string) ?? ''} setSshKeyPass={(v: string) => setNcExtraField('sshKeyPass', v)}
            remotePath={(ncExtra.remotePath as string) ?? ''} setRemotePath={(v: string) => setNcExtraField('remotePath', v)}
          />
        );
      case 'sap':
        return (
          <SapConfigForm t={t}
            host={newConnHost} setHost={setNewConnHost}
            port={newConnPort} setPort={setNewConnPort}
            systemId={(ncExtra.systemId as string) ?? ''} setSystemId={(v: string) => setNcExtraField('systemId', v)}
            client={(ncExtra.client as string) ?? ''} setClient={(v: string) => setNcExtraField('client', v)}
            username={newConnUser} setUsername={setNewConnUser}
            password={newConnPassword} setPassword={setNewConnPassword}
            encoding={(ncExtra.encoding as string) ?? 'UTF-8'} setEncoding={(v: string) => setNcExtraField('encoding', v)}
          />
        );
      case 'rest_api':
        return (
          <RestApiConfigForm t={t}
            baseUrl={newConnHost} setBaseUrl={setNewConnHost}
            subPath={(ncExtra.apiSubPath as string) ?? ''} setSubPath={(v: string) => setNcExtraField('apiSubPath', v)}
            method={(ncExtra.apiMethod as string) ?? 'GET'} setMethod={(v: string) => setNcExtraField('apiMethod', v)}
            username={newConnUser} setUsername={setNewConnUser}
            password={newConnPassword} setPassword={setNewConnPassword}
            retryInterval={(ncExtra.retryInterval as number) ?? 30} setRetryInterval={(v: number) => setNcExtraField('retryInterval', v)}
            maxRetries={(ncExtra.maxRetries as number) ?? 3} setMaxRetries={(v: number) => setNcExtraField('maxRetries', v)}
            timeoutMs={(ncExtra.timeoutMs as number) ?? 5000} setTimeoutMs={(v: number) => setNcExtraField('timeoutMs', v)}
          />
        );
      case 'kafka':
        return (
          <KafkaConfigForm t={t}
            bootstrapServers={newConnHost} setBootstrapServers={setNewConnHost}
            topic={(ncExtra.topic as string) ?? ''} setTopic={(v: string) => setNcExtraField('topic', v)}
            groupId={(ncExtra.groupId as string) ?? ''} setGroupId={(v: string) => setNcExtraField('groupId', v)}
            protocol={(ncExtra.protocol as string) ?? 'PLAINTEXT'} setProtocol={(v: string) => setNcExtraField('protocol', v)}
            securityProtocol={(ncExtra.securityProtocol as string) ?? 'SASL_SSL'} setSecurityProtocol={(v: string) => setNcExtraField('securityProtocol', v)}
            saslMechanism={(ncExtra.saslMechanism as string) ?? 'PLAIN'} setSaslMechanism={(v: string) => setNcExtraField('saslMechanism', v)}
            username={newConnUser} setUsername={setNewConnUser}
            password={newConnPassword} setPassword={setNewConnPassword}
            autoCommit={ncExtra.autoCommit !== false} setAutoCommit={(v: boolean) => setNcExtraField('autoCommit', v)}
          />
        );
      case 'mongodb':
        return (
          <MongoConfigForm t={t}
            host={newConnHost} setHost={setNewConnHost}
            port={newConnPort} setPort={setNewConnPort}
            database={newConnDatabase} setDatabase={setNewConnDatabase}
            username={newConnUser} setUsername={setNewConnUser}
            password={newConnPassword} setPassword={setNewConnPassword}
            authSource={(ncExtra.authSource as string) ?? 'admin'} setAuthSource={(v: string) => setNcExtraField('authSource', v)}
          />
        );
      default:
        return <JdbcConfigForm t={t} kind="postgresql" {...commonJdbc} />;
    }
  };

  const handleTest = async () => {
    setTesting(true);
    setTestResult(null);
    try {
      const { testDataSourceRaw } = await import('./api');
      const result = await testDataSourceRaw({
        name: newConnName || 'test',
        type: newConnType,
        host: newConnHost || 'localhost',
        port: newConnPort || 0,
        username: newConnUser || '',
        password: newConnPassword || undefined,
        database: newConnDatabase || undefined,
        bucket: (ncExtra.bucket as string) || undefined,
        endpointUrl: (ncExtra.endpointUrl as string) || undefined,
        extra: Object.keys(ncExtra).length ? ncExtra : undefined,
      });
      if (result?.success) {
        setTestResult({ ok: true, msg: t('dw.conn.testConnSuccess') });
      } else {
        setTestResult({ ok: false, msg: result?.message || t('dw.conn.testConnFailed') });
      }
    } catch (e: any) {
      setTestResult({ ok: false, msg: e.message || t('dw.conn.testConnFailed') });
    } finally {
      setTesting(false);
    }
  };

  // Which fields to show in the common top section (name + type + basic fields)
  const showCommonFields = true;

  return (
    <div className={`absolute inset-0 ${styles.overlayBg} backdrop-blur-xs flex items-center justify-center z-50 p-4 select-none`}>
      <div className={`${styles.cardBg} rounded-xl shadow-lg border ${styles.cardBorder} max-w-lg w-full overflow-hidden flex flex-col`}>
        <div className={`px-5 py-4 border-b ${styles.cardBorder} flex justify-between items-center ${styles.cardBg}`}>
          <h3 className={`text-xs font-bold ${styles.cardText} flex items-center gap-1.5`}>
            <LucideIcon name="Database" size={14} className={styles.infoText} />
            <span>{t('dw.txt.332103')}</span>
          </h3>
          <button onClick={onClose} className={`${styles.cardTextMuted} p-1`}>
            <LucideIcon name="X" size={14} />
          </button>
        </div>

        <div className="p-5 space-y-3 text-xs max-h-[60vh] overflow-y-auto">
          {/* Name + Type */}
          <div className="space-y-1">
            <label className={`text-[10px] font-semibold ${styles.cardTextMuted} block`}>{t('dw.txt.58314a')}</label>
            <input type="text" placeholder="e.g. 生产派班主库_Read" value={newConnName}
              onChange={e => setNewConnName(e.target.value)}
              className={`w-full px-3 py-1.5 border ${styles.inputBorder} rounded focus:${styles.infoBorder} focus:outline-hidden`} />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1">
              <label className={`text-[10px] font-semibold ${styles.cardTextMuted} block`}>{t('dw.ct.type')}</label>
              <select value={newConnType} onChange={e => { setNewConnType(e.target.value as string); setPreviewVisible(false); setPreviewResult(null); }}
                className={`w-full px-2.5 py-1.5 border ${styles.inputBorder} rounded ${styles.cardBg} font-mono`}>
                {CONNECTION_TYPES.map(type => {
                  // PMO-48-T5: 标准版禁用 gaussdb/minio/fs
                  const disabled = isStandard && STANDARD_DISABLED_TYPES.includes(type.value);
                  return (
                    <option key={type.value} value={type.value} disabled={disabled}>
                      {t(type.i18nKey)}
                    </option>
                  );
                })}
              </select>
            </div>
            {(isJdbc || isMongo || isMinio || isFs) && (
              <div className="space-y-1">
                <label className={`text-[10px] font-semibold ${styles.cardTextMuted} block`}>{t('dw.ct.port')}</label>
                <input type="number" value={newConnPort}
                  onChange={e => setNewConnPort(parseInt(e.target.value) || 0)}
                  className={`w-full px-3 py-1.5 border ${styles.inputBorder} rounded focus:outline-hidden font-mono`} />
              </div>
            )}
          </div>

          {/* Type-specific config form */}
          <div className={`pt-3 border-t ${styles.cardBorder} space-y-3`}>
            {renderConfigForm()}
          </div>

            {/* Test result banner */}
          {testResult && (
            <div className={`flex items-center gap-2 px-3 py-2 rounded text-[11px] font-mono ${
              testResult.ok
                ? `${styles.successBg} ${styles.successText}`
                : `${styles.dangerBg} ${styles.dangerText}`
            }`}>
              <LucideIcon name={testResult.ok ? 'CheckCircle' : 'XCircle'} size={14} />
              <span>{testResult.msg}</span>
            </div>
          )}

          {/* PMO-48-T5: Schema Preview 按钮 + 结果 */}
          <div className="flex items-center gap-2">
            <button
              onClick={async () => {
                setPreviewLoading(true);
                setPreviewResult(null);
                try {
                  const { fetchSchemaPreview } = await import('./api');
                  const result = await fetchSchemaPreview({
                    type: newConnType,
                    host: newConnHost || 'localhost',
                    port: newConnPort || 0,
                    username: newConnUser || '',
                    password: newConnPassword || undefined,
                    database: newConnDatabase || undefined,
                    bucket: (ncExtra.bucket as string) || undefined,
                    endpointUrl: (ncExtra.endpointUrl as string) || undefined,
                    extra: ncExtra,
                  });
                  if ('fields' in result) {
                    setPreviewResult(result);
                    setPreviewVisible(true);
                  } else {
                    setTestResult({ ok: false, msg: result.error || t('dw.conn.previewFailed') });
                  }
                } catch {
                  setTestResult({ ok: false, msg: t('dw.conn.previewFailed') });
                } finally {
                  setPreviewLoading(false);
                }
              }}
              disabled={previewLoading}
              className={`px-2.5 py-1 border ${styles.cardBorder} ${styles.cardTextMuted} hover:${styles.appBg} rounded text-[10px] font-mono transition-colors cursor-pointer ${previewLoading ? 'opacity-50' : ''}`}
            >
              {previewLoading ? (locale === 'zh' ? '预览中…' : 'Previewing…') : t('dw.conn.previewSchema')}
            </button>
            {previewVisible && previewResult && (
              <span className={`text-[10px] ${styles.cardTextMuted} font-mono`}>
                {previewResult.fields.length} {t('dw.conn.previewFields')}
              </span>
            )}
          </div>
          {previewVisible && previewResult && (
            <div className={`max-h-32 overflow-y-auto border ${styles.cardBorder} rounded text-[10px] font-mono space-y-1 p-2 ${styles.appBg}`}>
              {previewResult.tableNames.length > 0 && (
                <div className={`text-[9px] ${styles.muted} uppercase`}>{t('dw.conn.previewTables')}: {previewResult.tableNames.join(', ')}</div>
              )}
              {previewResult.fields.map(f => (
                <div key={f.name} className={`font-mono ${styles.cardTextMuted}`}>
                  <span className={`${styles.cardText} font-semibold`}>{f.name}</span>
                  <span className={`${styles.cardTextMuted} ml-1`}>{f.type}</span>
                  {f.required && <span className={`${styles.dangerText} ml-1`}>*</span>}
                  {f.comment && <span className={`text-[9px] ${styles.muted} ml-1`}>{f.comment}</span>}
                </div>
              ))}
            </div>
          )}
        </div>

        <div className={`px-5 py-3 border-t ${styles.cardBorder} flex justify-end gap-2 ${styles.cardBg}`}>
          <button onClick={onClose}
            className={`px-3 py-1.5 ${styles.cardBg} border ${styles.cardBorder} ${styles.cardTextMuted} hover:${styles.appBg} rounded text-xs transition-colors cursor-pointer`}>
            {locale === 'zh' ? '取消' : 'Cancel'}
          </button>
          <button onClick={handleTest} disabled={testing}
            className={`px-3.5 py-1.5 border ${styles.cardBorder} ${styles.cardText} hover:${styles.appBg} rounded text-xs transition-colors cursor-pointer ${testing ? 'opacity-50' : ''}`}>
            {testing ? (locale === 'zh' ? '测试中…' : 'Testing…') : t('dw.conn.testConn')}
          </button>
          <button onClick={onCreate}
            className={`px-3.5 py-1.5 ${styles.accentBg} hover:${styles.accentBg} ${styles.cardText} font-semibold rounded text-xs transition-colors cursor-pointer`}>
            {locale === 'zh' ? '保存并连线' : 'Save & Connect'}
          </button>
        </div>
      </div>
    </div>
  );
}

interface AddSyncModalProps {
  t: (key: string) => string;
  locale: string;
  newSyncName: string; setNewSyncName: (v: string) => void;
  newSyncConn: string; setNewSyncConn: (v: string) => void;
  newSyncTable: string; setNewSyncTable: (v: string) => void;
  newSyncMode: string; setNewSyncMode: (v: string) => void;
  newSyncSched: string; setNewSyncSched: (v: string) => void;
  connections: DataConnection[];
  onClose: () => void;
  onCreate: () => void;
}

export function AddSyncModal({ t, locale, newSyncName, setNewSyncName, newSyncConn, setNewSyncConn, newSyncTable, setNewSyncTable, newSyncMode, setNewSyncMode, newSyncSched, setNewSyncSched, connections, onClose, onCreate }: AddSyncModalProps) {
  const { styles } = useTheme();
  return (
    <div className={`absolute inset-0 ${styles.overlayBg} backdrop-blur-xs flex items-center justify-center z-50 p-4 select-none`}>
      <div className={`${styles.cardBg} rounded-xl shadow-lg border ${styles.cardBorder} max-w-md w-full overflow-hidden flex flex-col`}>
        <div className={`px-5 py-4 border-b ${styles.cardBorder} flex justify-between items-center ${styles.cardBg}`}>
          <h3 className={`text-xs font-bold ${styles.cardText} flex items-center gap-1.5`}><LucideIcon name="Import" size={14} className={`${styles.successText}`} /><span>{t('dw.txt.ca4caf')}</span></h3>
          <button onClick={onClose} className={`${styles.cardTextMuted} hover:${styles.cardTextMuted} p-1`}><LucideIcon name="X" size={14} /></button>
        </div>
        <div className="p-5 space-y-3 text-xs">
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1"><label className={`text-[10px] font-semibold ${styles.cardTextMuted} block`}>{t('dw.txt.58314a')}</label><input type="text" placeholder="e.g. 每日航班同步" value={newSyncName} onChange={e => setNewSyncName(e.target.value)} className={`w-full px-2.5 py-1.5 border ${styles.inputBorder} rounded focus:outline-hidden`} /></div>
            <div className="space-y-1"><label className={`text-[10px] font-semibold ${styles.cardTextMuted} block`}>{t('dw.txt.d4baa9')}</label><select value={newSyncConn} onChange={e => setNewSyncConn(e.target.value)} className={`w-full px-2.5 py-1.5 border ${styles.inputBorder} rounded ${styles.cardBg} font-mono`}>{connections.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}</select></div>
          </div>
          <div className="grid grid-cols-3 gap-3">
            <div className="space-y-1"><label className={`text-[10px] font-semibold ${styles.cardTextMuted} block`}>{t('dw.txt.63a3c2')}</label><input type="text" placeholder="public.raw_flights" value={newSyncTable} onChange={e => setNewSyncTable(e.target.value)} className={`w-full px-2.5 py-1.5 border ${styles.inputBorder} rounded focus:outline-hidden font-mono`} /></div>
            <div className="space-y-1"><label className={`text-[10px] font-semibold ${styles.cardTextMuted} block`}>{t('dw.txt.b9ac77')}</label><select value={newSyncMode} onChange={e => setNewSyncMode(e.target.value)} className={`w-full px-2.5 py-1.5 border ${styles.inputBorder} rounded ${styles.cardBg}`}><option value="snapshot">Snapshot</option><option value="incremental">Incremental</option><option value="append">Append</option></select></div>
            <div className="space-y-1"><label className={`text-[10px] font-semibold ${styles.cardTextMuted} block`}>{t('dw.txt.d636a1')}</label><select value={newSyncSched} onChange={e => setNewSyncSched(e.target.value)} className={`w-full px-2.5 py-1.5 border ${styles.inputBorder} rounded ${styles.cardBg}`}><option value="manual">Manual</option><option value="hourly">Hourly</option><option value="daily">Daily</option><option value="cron">Cron</option></select></div>
          </div>
        </div>
        <div className={`px-5 py-3 border-t ${styles.cardBorder} flex justify-end gap-2 ${styles.cardBg}`}>
          <button onClick={onClose} className={`px-3 py-1.5 ${styles.cardBg} border ${styles.cardBorder} ${styles.cardTextMuted} hover:${styles.cardBg} rounded text-xs cursor-pointer`}>{locale === 'zh' ? '取消' : 'Cancel'}</button>
          <button onClick={onCreate} className={`px-3.5 py-1.5 ${styles.successBg} hover:${styles.successBg} ${styles.cardText} font-semibold rounded text-xs cursor-pointer`}>{locale === 'zh' ? '初始化同步任务' : 'Create Sync Task'}</button>
        </div>
      </div>
    </div>
  );
}

interface ExternalInterfacesDrawerProps {
  t: (key: string) => string;
  connections: DataConnection[];
  onClose: () => void;
}

export function ExternalInterfacesDrawer({ t, connections, onClose }: ExternalInterfacesDrawerProps) {
  const { styles } = useTheme();
  return (
    <div className={`absolute top-12 right-0 bottom-0 w-96 ${styles.overlayBg} ${styles.cardText} border-l ${styles.cardBorder} shadow-2xl z-40 flex flex-col overflow-hidden select-none`}>
      <div className={`px-5 py-4 border-b ${styles.cardBorder} flex justify-between items-center ${styles.overlayBg} shrink-0`}>
        <h3 className={`text-xs font-bold ${styles.cardText} flex items-center gap-2`}><LucideIcon name="Layers" size={14} className={`${styles.warningText} animate-pulse`} /><span>{t('dw.txt.c5dda0')}</span></h3>
        <button onClick={onClose} className={`${styles.cardTextMuted} hover:${styles.cardText} p-1`}><LucideIcon name="X" size={14} /></button>
      </div>
      <div className="flex-1 overflow-y-auto p-5 space-y-4">
        <p className={`text-[11px] ${styles.cardTextMuted} leading-relaxed font-sans`}>以下是当前 ECOS 集成平台与外界各大物理系统、调度系统、云对象存储以及 ERP 财务系统的注册接口。</p>
        {connections.map(conn => (
          <div key={conn.id} className={`p-3 ${styles.overlayBg} rounded-lg border ${styles.cardBorder} text-xs space-y-2.5`}>
            <div className={`flex justify-between items-center border-b ${styles.cardBorder} pb-1.5`}>
              <span className={`font-bold ${styles.cardText} font-mono`}>{conn.id}</span>
              <span className={`text-[9px] font-mono px-1.5 rounded-full ${conn.status === 'connected' ? `${styles.successBg} ${styles.successText}` : `${styles.dangerBg} ${styles.dangerText}`}`}>{conn.status.toUpperCase()}</span>
            </div>
            <div className={`space-y-1 text-[11px] ${styles.cardTextMuted}`}>
              <div><span className={`${styles.cardTextMuted} font-semibold uppercase text-[9px] block`}>{t('dw.txt.f274bd')}</span><span className={`${styles.cardText}`}>{conn.name}</span></div>
              <div><span className={`${styles.cardTextMuted} font-semibold uppercase text-[9px] block`}>{t('dw.txt.dec92b')}</span><span className={`${styles.cardTextMuted} font-mono`}>ECOS Connector v1.2 [{conn.type.toUpperCase()}]</span></div>
              {conn.config.host && <div><span className={`${styles.cardTextMuted} font-semibold uppercase text-[9px] block`}>{t('dw.txt.15e5bb')}</span><span className={`${styles.cardTextMuted} font-mono`}>{conn.config.host}:{conn.config.port || 5432}</span></div>}
            </div>
          </div>
        ))}
      </div>
      <div className={`p-4 ${styles.overlayBg} border-t ${styles.cardBorder} text-[10px] ${styles.muted} text-center select-none font-mono`}>Aviation Integration Gateway (Total: {connections.length} Endpoints)</div>
    </div>
  );
}
