/**
 * Modals — Add Connection / Add Sync panels / External Interfaces drawer
 * Extracted from DataWorkbenchLayout.tsx
 * PMO-47 Wave3: type-specific ConfigForms + connection test
 * @license Apache-2.0
 */
import React, { useState, useRef, useEffect } from 'react';
import {
  AlertTriangle,
  CheckCircle,
  Database,
  Import,
  Loader2,
  X,
  XCircle
} from 'lucide-react';
import { useTheme } from '../../components/ThemeContext';
import type { DataConnection } from './types';
// #142/B4-3e-47（D-B4-76 前端腿）：预览出参按属主契约的判别联合消费，页面不得再自设第二份键面
import type { SchemaPreviewSuccess, SchemaPreviewFailureKind } from './api';
// #142/B4-3e-64（D-B4-78 前端侧腿）：「可新建哪些数据源」的唯一事实源 = 承流出口
// `GET /api/v1/datanet/datasource/supported-types`（数据单源 DataSourceTypeVocabulary）。
// 本弹窗因此<b>不再 import CONNECTION_TYPES</b>——那是显示元数据面，19 枚里有 9 枚后端不承流，
// 直接渲染就是 D-B4-78 的用户面病灶（选了只能撞 400）。下拉渲染的是
// `deriveSupportedOptions(属主目录)` 裁剪后的子集。
import type { SupportedTypesFailure, SupportedTypesSuccess } from './api';
import { fetchSupportedDataSourceTypes, deriveSupportedOptions } from './api';
import { getErrorMessage } from './helpers';
import { STANDARD_DISABLED_TYPES } from './types';
import { detectEdition, type EcosEdition } from './DataEngineConfigPanelTypes';
import {
  JdbcConfigForm, ObjectStorageConfigForm, FileSourceConfigForm,
  SftpConfigForm, SapConfigForm, RestApiConfigForm, KafkaConfigForm, MongoConfigForm,
  MinioConfigForm, FsConfigForm,
  type JdbcProps, type ObjStorageProps, type FileSourceProps,
  type SftpProps, type SapProps, type RestApiProps,
  type KafkaProps, type MongoProps, type MinioProps, type FsProps,
} from './ConfigForms';

/**
 * §4.7.2 分态文案键（#142/B4-3e-47）：预览失败的每一种语义各出一句，禁互相折叠。
 *
 * <p>改前本弹窗把 `ok:false` 一律渲染成 `dw.conn.previewFailed`，且成功面被恒空的幻影
 * 通道喂成「0 个字段」——「通道不可用」「无权限」「对象面确实为空」三种对用户完全不同的
 * 结论在界面上是同一句话。`kind` 由 API 层按后端状态行推出，页面只负责出文案。</p>
 */
const PREVIEW_FAILURE_KEYS: Record<SchemaPreviewFailureKind, string> = {
  invalid_input: 'dw.conn.previewKindInvalid',
  denied: 'dw.conn.previewKindDenied',
  not_found: 'dw.conn.previewKindNotFound',
  unavailable: 'dw.conn.previewKindUnavailable',
  server_error: 'dw.conn.previewKindServerError',
  failed_envelope: 'dw.conn.previewKindEnvelope',
  network: 'dw.conn.previewKindNetwork',
  invalid_response: 'dw.conn.previewKindInvalidResponse',
  unknown: 'dw.conn.previewKindUnknown',
};

/**
 * §4.7.2 分态文案键（#142/B4-3e-64）：类型目录读取失败的每一种语义各出一句。
 *
 * <p>词汇与 {@link PREVIEW_FAILURE_KEYS} 同一份（`kind` 由 API 层按后端状态行经单源判据
 * 推出），但句子说的是本通道自己的事——把「预览失败」的文案复用到「目录失败」上，
 * 用户读到的是另一件事的结论。目录失败时本弹窗按 <b>fail-closed</b> 处置：禁用类型选择，
 * 并阻塞测试/预览/创建三个动作，<b>禁止回落本地 19 枚显示面</b>（回落即本批收口的
 * 「第二事实源」形态）。</p>
 */
const CATALOG_FAILURE_KEYS: Record<SchemaPreviewFailureKind, string> = {
  invalid_input: 'dw.conn.typeCatalogKindInvalidInput',
  denied: 'dw.conn.typeCatalogKindDenied',
  not_found: 'dw.conn.typeCatalogKindNotFound',
  unavailable: 'dw.conn.typeCatalogKindUnavailable',
  server_error: 'dw.conn.typeCatalogKindServerError',
  failed_envelope: 'dw.conn.typeCatalogKindEnvelope',
  network: 'dw.conn.typeCatalogKindNetwork',
  invalid_response: 'dw.conn.typeCatalogKindInvalidResponse',
  unknown: 'dw.conn.typeCatalogKindUnknown',
};

/** 类型目录通道的面板态（#142/B4-3e-64）：加载／成功／失败三态各自可判读，禁折叠 */
type TypeCatalogPanel =
  | { phase: 'loading' }
  | { phase: 'ready'; value: SupportedTypesSuccess }
  | { phase: 'failed'; value: SupportedTypesFailure };

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
  t,
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
  const [previewResult, setPreviewResult] = useState<SchemaPreviewSuccess | null>(null);

  // #142/B4-3e-64（D-B4-78 前端侧腿）：类型目录从属主出口取，取不到就什么都不给选。
  // 三态（loading／ready／failed）分开持，禁把「还没读到」和「读失败」折成同一个画面——
  // 后者会让用户以为目录里就只有默认那一枚。
  const [catalog, setCatalog] = useState<TypeCatalogPanel>({ phase: 'loading' });
  const [catalogReloadTick, setCatalogReloadTick] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setCatalog({ phase: 'loading' });
    fetchSupportedDataSourceTypes().then((outcome) => {
      if (cancelled) return;
      // 判别用 `in`（成功面独有 `entries`）：本仓 tsconfig 无 strict，`outcome.ok` 不参与类型缩窄
      setCatalog('entries' in outcome ? { phase: 'ready', value: outcome } : { phase: 'failed', value: outcome });
    });
    return () => { cancelled = true; };
  }, [catalogReloadTick]);

  const catalogEntries = catalog.phase === 'ready' ? catalog.value.entries : null;
  /** 属主目录裁剪显示面后的下拉项；目录未就绪 = 空面（禁回落本地 19 枚） */
  const supportedOptions = catalogEntries ? deriveSupportedOptions(catalogEntries) : null;
  const typeOptions = supportedOptions ? supportedOptions.options : [];
  /** 属主已登记而前端显示面无归属的类型：必须显式可读，禁静默丢枚（丢的后果＝后端能登而前端看不到） */
  const unresolvedCanonical = supportedOptions ? supportedOptions.unresolvedCanonical : [];
  const catalogFailure = catalog.phase === 'failed' ? catalog.value : null;
  /** 当前选中类型是否在属主目录内：目录就绪但选种不被承流时，动作一律阻塞，禁把 400 留给用户去撞 */
  const selectionSupported = typeOptions.some((o) => o.value === newConnType);
  const actionsBlocked = supportedOptions === null || !selectionSupported;

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
    } catch (e) {
      setTestResult({ ok: false, msg: getErrorMessage(e) || t('dw.conn.testConnFailed') });
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
            <Database size={14} className={styles.infoText} />
            <span>{t('dw.txt.332103')}</span>
          </h3>
          <button type="button" onClick={onClose} className={`${styles.cardTextMuted} p-1`}>
            <X size={14} />
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
                disabled={supportedOptions === null}
                className={`w-full px-2.5 py-1.5 border ${styles.inputBorder} rounded ${styles.cardBg} font-mono ${supportedOptions === null ? 'opacity-50' : ''}`}>
                {/* #142/B4-3e-64：数据源 = 属主目录裁剪后的子集，不再是 CONNECTION_TYPES 全 19 枚 */}
                {typeOptions.map(type => {
                  // PMO-48-T5: 标准版禁用 gaussdb/minio（fs 全版本可用）
                  const disabled = isStandard && STANDARD_DISABLED_TYPES.includes(type.value);
                  return (
                    <option key={type.value} value={type.value} disabled={disabled}>
                      {t(type.i18nKey)}
                    </option>
                  );
                })}
              </select>
            </div>
            {(isJdbc || isMongo || isMinio) && (
              <div className="space-y-1">
                <label className={`text-[10px] font-semibold ${styles.cardTextMuted} block`}>{t('dw.ct.port')}</label>
                <input type="number" value={newConnPort}
                  onChange={e => setNewConnPort(parseInt(e.target.value) || 0)}
                  className={`w-full px-3 py-1.5 border ${styles.inputBorder} rounded focus:outline-hidden font-mono`} />
              </div>
            )}
          </div>

          {/* #142/B4-3e-64（D-B4-78 前端侧腿）：类型目录通道三态读数——加载/失败/部分未解析各出一句，禁折叠 */}
          {catalog.phase === 'loading' && (
            <div className={`flex items-center gap-2 px-3 py-2 rounded text-[11px] font-mono ${styles.cardBorder} ${styles.cardTextMuted} border`}>
              <Loader2 size={14} />
              <span>{t('dw.conn.typeCatalogLoading')}</span>
            </div>
          )}
          {catalogFailure && (
            <div className={`flex items-start gap-2 px-3 py-2 rounded text-[11px] font-mono ${styles.dangerBg} ${styles.dangerText}`}>
              <XCircle size={14} />
              <div className="space-y-1">
                {/* 类别句 + 后端原文各占一段（ASCII 分隔，禁把原文并进句子以免混淆口径）*/}
                <div>{t(CATALOG_FAILURE_KEYS[catalogFailure.kind])}</div>
                {catalogFailure.error && <div className="text-[9px]">{catalogFailure.error}</div>}
                <div className={`text-[9px] ${styles.cardTextMuted}`}>{t('dw.conn.typeCatalogFailClosed')}</div>
                <button type="button"
                  onClick={() => setCatalogReloadTick((n) => n + 1)}
                  className={`px-2 py-0.5 border ${styles.cardBorder} ${styles.cardText} rounded text-[10px] cursor-pointer`}>
                  {t('dw.conn.typeCatalogRetry')}
                </button>
              </div>
            </div>
          )}
          {unresolvedCanonical.length > 0 && (
            <div className={`flex items-start gap-2 px-3 py-2 rounded text-[11px] font-mono ${styles.warningBg} ${styles.warningText}`}>
              <AlertTriangle size={14} />
              <span>{t('dw.conn.typeCatalogUnresolved')}: {unresolvedCanonical.join(', ')}</span>
            </div>
          )}
          {supportedOptions !== null && !selectionSupported && (
            <div className={`flex items-start gap-2 px-3 py-2 rounded text-[11px] font-mono ${styles.warningBg} ${styles.warningText}`}>
              <AlertTriangle size={14} />
              {/* 承流面已撤回当前选种（或目录内无默认项）：显式提示并阻塞动作，禁静默按这一枚提交 */}
              <span>{t('dw.conn.typeCatalogSelectionUnsupported')}{newConnType ? ` | ${newConnType}` : ''}</span>
            </div>
          )}

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
              {testResult.ok ? <CheckCircle size={14} /> : <XCircle size={14} />}
              <span>{testResult.msg}</span>
            </div>
          )}

          {/* PMO-48-T5: Schema Preview 按钮 + 结果 */}
          <div className="flex items-center gap-2">
            <button type="button"
              onClick={async () => {
                setPreviewLoading(true);
                setPreviewResult(null);
                setPreviewVisible(false);
                try {
                  const { fetchSchemaPreview } = await import('./api');
                  const result = await fetchSchemaPreview({
                    name: newConnName || undefined,
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
                  // 判别用 `in`（失败面独有 `kind`）：本仓 tsconfig 无 strict，`result.ok` 不参与类型缩窄
                  if ('kind' in result) {
                    // 类别句 + 后端原文各占一段（ASCII 分隔，禁把原文并进句子以免混淆口径）
                    const kindText = t(PREVIEW_FAILURE_KEYS[result.kind]);
                    setTestResult({ ok: false, msg: result.error ? `${kindText} | ${result.error}` : kindText });
                  } else {
                    setPreviewResult(result);
                    setPreviewVisible(true);
                  }
                } finally {
                  setPreviewLoading(false);
                }
              }}
              disabled={previewLoading || actionsBlocked}
              className={`px-2.5 py-1 border ${styles.cardBorder} ${styles.cardTextMuted} hover:${styles.appBg} rounded text-[10px] font-mono transition-colors ${previewLoading || actionsBlocked ? 'opacity-50 cursor-default' : 'cursor-pointer'}`}
            >
              {previewLoading ? t('dw.modal.previewing') : t('dw.conn.previewSchema')}
            </button>
            {previewVisible && previewResult && (
              <span className={`text-[10px] ${styles.cardTextMuted} font-mono`}>
                {previewResult.fields.length} {t('dw.conn.previewFields')}
              </span>
            )}
          </div>
          {previewVisible && previewResult && (
            <div className={`max-h-32 overflow-y-auto border ${styles.cardBorder} rounded text-[10px] font-mono space-y-1 p-2 ${styles.appBg}`}>
              {previewResult.note && (
                <div className={`text-[9px] ${styles.warningText}`}>{previewResult.note}</div>
              )}
              {previewResult.tableNames.length > 0 && (
                <div className={`text-[9px] ${styles.muted} uppercase`}>{t('dw.conn.previewTables')}: {previewResult.tableNames.join(', ')}</div>
              )}
              {previewResult.fieldsObject && (
                <div className={`text-[9px] ${styles.muted}`}>{t('dw.conn.previewFieldsObject')}: {previewResult.fieldsObject}</div>
              )}
              {previewResult.fieldsDegraded && (
                <div className={`text-[9px] ${styles.warningText}`}>
                  {t('dw.conn.previewDegraded')}
                  {previewResult.fieldsReason ? ` | ${previewResult.fieldsReason}` : ''}
                </div>
              )}
              {previewResult.fields.length === 0 && !previewResult.fieldsDegraded && (
                <div className={`text-[9px] ${styles.muted}`}>{t('dw.conn.previewNoFields')}</div>
              )}
              {previewResult.fields.map((f, i) => (
                <div key={`${f.name ?? 'col'}-${i}`} className={`font-mono ${styles.cardTextMuted}`}>
                  <span className={`${styles.cardText} font-semibold`}>{f.name ?? t('dw.conn.previewUnknownAttr')}</span>
                  <span className={`${styles.cardTextMuted} ml-1`}>{f.type ?? t('dw.conn.previewUnknownAttr')}</span>
                  {f.required === true && <span className={`${styles.dangerText} ml-1`}>*</span>}
                  {f.comment && <span className={`text-[9px] ${styles.muted} ml-1`}>{f.comment}</span>}
                </div>
              ))}
            </div>
          )}
        </div>

        <div className={`px-5 py-3 border-t ${styles.cardBorder} flex justify-end gap-2 ${styles.cardBg}`}>
          <button type="button" onClick={onClose}
            className={`px-3 py-1.5 ${styles.cardBg} border ${styles.cardBorder} ${styles.cardTextMuted} hover:${styles.appBg} rounded text-xs transition-colors cursor-pointer`}>
            {t('dw.modal.cancel')}
          </button>
          <button type="button" onClick={handleTest} disabled={testing || actionsBlocked}
            className={`px-3.5 py-1.5 border ${styles.cardBorder} ${styles.cardText} hover:${styles.appBg} rounded text-xs transition-colors ${testing || actionsBlocked ? 'opacity-50 cursor-default' : 'cursor-pointer'}`}>
            {testing ? t('dw.modal.testing') : t('dw.conn.testConn')}
          </button>
          {/* #142/B4-3e-64：目录未就绪或选种不被承流时禁创建（fail-closed）——宁可不给点，不给一个必然 400 的请求 */}
          <button type="button" onClick={onCreate} disabled={actionsBlocked}
            className={`px-3.5 py-1.5 ${styles.accentBg} hover:${styles.accentBg} ${styles.cardText} font-semibold rounded text-xs transition-colors ${actionsBlocked ? 'opacity-50 cursor-default' : 'cursor-pointer'}`}>
            {t('dw.modal.saveAndConnect')}
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
          <h3 className={`text-xs font-bold ${styles.cardText} flex items-center gap-1.5`}><Import size={14} className={`${styles.successText}`} /><span>{t('dw.txt.ca4caf')}</span></h3>
          <button type="button" onClick={onClose} className={`${styles.cardTextMuted} hover:${styles.cardTextMuted} p-1`}><X size={14} /></button>
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
          <button type="button" onClick={onClose} className={`px-3 py-1.5 ${styles.cardBg} border ${styles.cardBorder} ${styles.cardTextMuted} hover:${styles.cardBg} rounded text-xs cursor-pointer`}>{t('dw.modal.cancel')}</button>
          <button type="button" onClick={onCreate} className={`px-3.5 py-1.5 ${styles.successBg} hover:${styles.successBg} ${styles.cardText} font-semibold rounded text-xs cursor-pointer`}>{t('dw.modal.createSyncTask')}</button>
        </div>
      </div>
    </div>
  );
}


// [2026-10-13 前端检视修复] ExternalInterfacesDrawer 已摘除：零打开入口的死组件（DataWorkbenchLayout 调用点同步删除）。
// 如需恢复真实外部接口清单能力，须接后端集成端点并以六态（空/加载/错误/边界/权限/离线）呈现，禁止再落内存假数据。
