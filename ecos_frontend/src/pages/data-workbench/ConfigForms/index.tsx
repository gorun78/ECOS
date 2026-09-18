/**
 * ConfigForms — type-specific connection configuration panels
 * Each component renders its type's fields. All labels use t() (i18n).
 * No internal state — fully controlled via props from AddConnectionModal.
 * @license Apache-2.0
 */
import React from 'react';
import { useTheme } from '../../../components/ThemeContext';

type TFn = (key: string, params?: Record<string, unknown>) => string;
type SetFn = (v: string) => void;
type NumSetFn = (v: number) => void;
type BoolSetFn = (v: boolean) => void;

function Label({ children }: { children: React.ReactNode }) {
  const { styles } = useTheme();
  return <label className={`text-[10px] font-semibold ${styles.cardTextMuted} block`}>{children}</label>;
}

function TxtInput({ value, onChange, type = 'text', placeholder = '', mono = true }: {
  value: string; onChange: SetFn; type?: string; placeholder?: string; mono?: boolean;
}) {
  const { styles } = useTheme();
  return (
    <input type={type} value={value} placeholder={placeholder}
      onChange={e => onChange(e.target.value)}
      className={`w-full px-3 py-1.5 border ${styles.inputBorder} rounded focus:outline-hidden ${mono ? 'font-mono' : ''}`} />
  );
}

function NumInput({ value, onChange, placeholder = '' }: { value: number; onChange: NumSetFn; placeholder?: string }) {
  const { styles } = useTheme();
  return (
    <input type="number" value={value || ''} placeholder={placeholder}
      onChange={e => onChange(parseInt(e.target.value) || 0)}
      className={`w-full px-3 py-1.5 border ${styles.inputBorder} rounded focus:outline-hidden font-mono`} />
  );
}

function SelInput({ value, onChange, options }: { value: string; onChange: SetFn; options: { value: string; label: string }[] }) {
  const { styles } = useTheme();
  return (
    <select value={value} onChange={e => onChange(e.target.value)}
      className={`w-full px-2.5 py-1.5 border ${styles.inputBorder} rounded ${styles.cardBg} font-mono`}>
      {options.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
    </select>
  );
}

function Chk({ checked, onChange, label }: { checked: boolean; onChange: BoolSetFn; label: string }) {
  return (
    <label className="flex items-center gap-1.5 cursor-pointer select-none">
      <input type="checkbox" checked={checked} onChange={e => onChange(e.target.checked)} className="accent-blue-500" />
      <span className="text-[10px]">{label}</span>
    </label>
  );
}

function Row({ children }: { children: React.ReactNode }) {
  return <div className="grid grid-cols-2 gap-3">{children}</div>;
}

function FG({ label, children }: { label: string; children: React.ReactNode }) {
  return <div className="space-y-1"><Label>{label}</Label>{children}</div>;
}

// ──────────────────────────────────────────────
// JdbcConfigForm — PostgreSQL / MySQL / Doris / Oracle / MSSQL / DM / Kingbase / GaussDB
// PMO-48-T5: facade 类型 (type=facade 但 dbType 指定真实库) 由本组件渲染对应子类
// ──────────────────────────────────────────────
export interface JdbcProps {
  t: TFn;
  host: string; setHost: SetFn;
  port: number; setPort: NumSetFn;
  database: string; setDatabase: SetFn;
  username: string; setUsername: SetFn;
  password: string; setPassword: SetFn;
  ssl: boolean; setSsl: BoolSetFn;
  schema: string; setSchema: SetFn;
  warehouse: string; setWarehouse: SetFn;
  /** PMO-48-T5: 子类型标识 (oracle 的 SID/SERVICE_NAME) */
  dbType?: string; setDbType?: SetFn;
  /** PMO-48-T5: 实际渲染的 kind (facade 分解后) */
  actualKind?: string;
}

/** PMO-48-T5: 各数据库驱动默认端口 */
const DB_DEFAULT_PORTS: Record<string, string> = {
  postgresql: '5432', mysql: '3306', doris: '9030', clickhouse: '8123',
  oracle: '1521', mssql: '1433', dm: '5236', kingbase: '54321', gaussdb: '5432',
};

export function JdbcConfigForm({
  t, kind, host, setHost, port, setPort,
  database, setDatabase, username, setUsername,
  password, setPassword, ssl, setSsl,
  schema, setSchema, warehouse, setWarehouse,
  dbType, setDbType, actualKind,
}: JdbcProps & { kind: 'postgresql' | 'mysql' | 'doris' | 'clickhouse' | 'oracle' | 'mssql' | 'dm' | 'kingbase' | 'gaussdb' }) {
  const effectiveKind = actualKind || kind;
  const dbDefault = DB_DEFAULT_PORTS[effectiveKind] || '5432';
  // 标准版 facade 类型 (gaussdb/minio/fs) 不应在此渲染，由上层门控
  return (
    <div className="space-y-3">
      <Row>
        <FG label={t('dw.ct.host')}><TxtInput value={host} onChange={setHost} placeholder="localhost" /></FG>
        <FG label={t('dw.ct.port')}><NumInput value={port} onChange={setPort} placeholder={dbDefault} /></FG>
      </Row>
      <Row>
        <FG label={t('dw.ct.database')}><TxtInput value={database} onChange={setDatabase} placeholder={effectiveKind === 'postgresql' ? 'sys_man' : effectiveKind === 'oracle' ? 'ORCL' : ''} /></FG>
        {effectiveKind !== 'doris' && (
          <FG label={t('dw.ct.schema')}><TxtInput value={schema} onChange={setSchema} placeholder="public" /></FG>
        )}
      </Row>
      {effectiveKind === 'doris' && (
        <FG label={t('dw.ct.warehouse')}><TxtInput value={warehouse} onChange={setWarehouse} /></FG>
      )}
      {effectiveKind === 'oracle' && setDbType && (
        <FG label={t('dw.oracle.connectionType')}>
          <SelInput value={dbType || 'SERVICE_NAME'} onChange={(v: string) => setDbType(v)} options={[
            { value: 'SERVICE_NAME', label: 'SERVICE_NAME' },
            { value: 'SID', label: 'SID' },
            { value: 'EZCONNECT', label: 'EZCONNECT (host:port/service)' },
          ]} />
        </FG>
      )}
      <Row>
        <FG label={t('dw.ct.username')}><TxtInput value={username} onChange={setUsername} placeholder="readonly_user" /></FG>
        <FG label={t('dw.ct.password')}><TxtInput value={password} onChange={setPassword} type="password" placeholder="••••••" /></FG>
      </Row>
      <Chk checked={ssl} onChange={setSsl} label={t('dw.ct.ssl')} />
    </div>
  );
}

// ──────────────────────────────────────────────
// ObjectStorageConfigForm — S3 / OSS
// ──────────────────────────────────────────────
export interface ObjStorageProps {
  t: TFn;
  endpointUrl: string; setEndpointUrl: SetFn;
  bucket: string; setBucket: SetFn;
  accessKey: string; setAccessKey: SetFn;
  secretKey: string; setSecretKey: SetFn;
  region: string; setRegion: SetFn;
}

export function ObjectStorageConfigForm({
  t, kind, endpointUrl, setEndpointUrl, bucket, setBucket,
  accessKey, setAccessKey, secretKey, setSecretKey,
  region, setRegion,
}: ObjStorageProps & { kind: 's3' | 'oss' }) {
  return (
    <div className="space-y-3">
      <FG label={t('dw.ct.endpointUrl')}>
        <TxtInput value={endpointUrl} onChange={setEndpointUrl}
          placeholder={kind === 's3' ? 's3.amazonaws.com' : 'oss-cn-hangzhou.aliyuncs.com'} />
      </FG>
      <Row>
        <FG label={t('dw.ct.bucket')}><TxtInput value={bucket} onChange={setBucket} placeholder="ecos-data" /></FG>
        <FG label={t('dw.ct.region')}><TxtInput value={region} onChange={setRegion} placeholder={kind === 's3' ? 'us-east-1' : 'cn-hangzhou'} /></FG>
      </Row>
      <Row>
        <FG label={t('dw.ct.accessKey')}><TxtInput value={accessKey} onChange={setAccessKey} placeholder="AK..." /></FG>
        <FG label={t('dw.ct.secretKey')}><TxtInput value={secretKey} onChange={setSecretKey} type="password" placeholder="••••••" /></FG>
      </Row>
    </div>
  );
}

// ──────────────────────────────────────────────
// FileSourceConfigForm — CSV
// ──────────────────────────────────────────────
export interface FileSourceProps {
  t: TFn;
  filePath: string; setFilePath: SetFn;
  delimiter: string; setDelimiter: SetFn;
  encoding: string; setEncoding: SetFn;
  hasHeader: boolean; setHasHeader: BoolSetFn;
  rowLimit: number; setRowLimit: NumSetFn;
}

export function FileSourceConfigForm({
  t, filePath, setFilePath, delimiter, setDelimiter,
  encoding, setEncoding, hasHeader, setHasHeader,
  rowLimit, setRowLimit,
}: FileSourceProps) {
  return (
    <div className="space-y-3">
      <FG label={t('dw.ct.filePath')}><TxtInput value={filePath} onChange={setFilePath} placeholder="/data/exports/sales.csv" /></FG>
      <Row>
        <FG label={t('dw.ct.delimiter')}>
          <SelInput value={delimiter} onChange={setDelimiter} options={[
            { value: ',', label: 'Comma (,)' },
            { value: '\t', label: 'Tab' },
            { value: ';', label: 'Semicolon (;)' },
            { value: '|', label: 'Pipe (|)' },
          ]} />
        </FG>
        <FG label={t('dw.ct.encoding')}>
          <SelInput value={encoding} onChange={setEncoding} options={[
            { value: 'UTF-8', label: 'UTF-8' },
            { value: 'GBK', label: 'GBK' },
            { value: 'ISO-8859-1', label: 'ISO-8859-1' },
          ]} />
        </FG>
      </Row>
      <Row>
        <div className="flex items-end pb-1.5">
          <Chk checked={hasHeader} onChange={setHasHeader} label={t('dw.ct.hasHeader')} />
        </div>
        <FG label={t('dw.ct.rowLimit')}><NumInput value={rowLimit} onChange={setRowLimit} placeholder="0 = all" /></FG>
      </Row>
    </div>
  );
}

// ──────────────────────────────────────────────
// SftpConfigForm — SFTP
// ──────────────────────────────────────────────
export interface SftpProps {
  t: TFn;
  host: string; setHost: SetFn;
  port: number; setPort: NumSetFn;
  path: string; setPath: SetFn;
  username: string; setUsername: SetFn;
  password: string; setPassword: SetFn;
  sshKey: string; setSshKey: SetFn;
  sshKeyPass: string; setSshKeyPass: SetFn;
  remotePath: string; setRemotePath: SetFn;
}

export function SftpConfigForm({
  t, host, setHost, port, setPort, path, setPath,
  username, setUsername, password, setPassword,
  sshKey, setSshKey, sshKeyPass, setSshKeyPass,
  remotePath, setRemotePath,
}: SftpProps) {
  return (
    <div className="space-y-3">
      <Row>
        <FG label={t('dw.ct.host')}><TxtInput value={host} onChange={setHost} placeholder="sftp.example.com" /></FG>
        <FG label={t('dw.ct.port')}><NumInput value={port} onChange={setPort} placeholder="22" /></FG>
      </Row>
      <Row>
        <FG label={t('dw.ct.username')}><TxtInput value={username} onChange={setUsername} placeholder="sftp_user" /></FG>
        <FG label={t('dw.ct.password')}><TxtInput value={password} onChange={setPassword} type="password" placeholder="••••••" /></FG>
      </Row>
      <FG label={t('dw.ct.sshKey')}><TxtInput value={sshKey} onChange={setSshKey} placeholder="/home/user/.ssh/id_rsa" /></FG>
      <Row>
        <FG label={t('dw.ct.sshKeyPass')}><TxtInput value={sshKeyPass} onChange={setSshKeyPass} type="password" placeholder="••••••" /></FG>
        <FG label={t('dw.ct.remotePath')}><TxtInput value={remotePath} onChange={setRemotePath} placeholder="/data/" /></FG>
      </Row>
      <FG label={t('dw.ct.path')}><TxtInput value={path} onChange={setPath} placeholder="/uploads/" /></FG>
    </div>
  );
}

// ──────────────────────────────────────────────
// SapConfigForm — SAP RFC
// ──────────────────────────────────────────────
export interface SapProps {
  t: TFn;
  host: string; setHost: SetFn;
  port: number; setPort: NumSetFn;
  systemId: string; setSystemId: SetFn;
  client: string; setClient: SetFn;
  username: string; setUsername: SetFn;
  password: string; setPassword: SetFn;
  encoding: string; setEncoding: SetFn;
}

export function SapConfigForm({
  t, host, setHost, port, setPort,
  systemId, setSystemId, client, setClient,
  username, setUsername, password, setPassword,
  encoding, setEncoding,
}: SapProps) {
  return (
    <div className="space-y-3">
      <Row>
        <FG label={t('dw.ct.host')}><TxtInput value={host} onChange={setHost} placeholder="sap-erp.example.com" /></FG>
        <FG label={t('dw.ct.systemId')}><TxtInput value={systemId} onChange={setSystemId} placeholder="SAP" /></FG>
      </Row>
      <Row>
        <FG label={t('dw.ct.port')}><NumInput value={port} onChange={setPort} placeholder="3300" /></FG>
        <FG label={t('dw.ct.client')}><TxtInput value={client} onChange={setClient} placeholder="100" /></FG>
      </Row>
      <Row>
        <FG label={t('dw.ct.username')}><TxtInput value={username} onChange={setUsername} placeholder="ABAP_USER" /></FG>
        <FG label={t('dw.ct.password')}><TxtInput value={password} onChange={setPassword} type="password" placeholder="••••••" /></FG>
      </Row>
      <FG label={t('dw.ct.encoding')}>
        <SelInput value={encoding} onChange={setEncoding} options={[
          { value: 'UTF-8', label: 'UTF-8' },
          { value: 'ISO-8859-1', label: 'ISO-8859-1' },
          { value: 'SAPW', label: 'SAPW (Western)' },
        ]} />
      </FG>
    </div>
  );
}

// ──────────────────────────────────────────────
// RestApiConfigForm — REST API
// ──────────────────────────────────────────────
export interface RestApiProps {
  t: TFn;
  baseUrl: string; setBaseUrl: SetFn;
  subPath: string; setSubPath: SetFn;
  method: string; setMethod: SetFn;
  username: string; setUsername: SetFn;
  password: string; setPassword: SetFn;
  retryInterval: number; setRetryInterval: NumSetFn;
  maxRetries: number; setMaxRetries: NumSetFn;
  timeoutMs: number; setTimeoutMs: NumSetFn;
}

export function RestApiConfigForm({
  t, baseUrl, setBaseUrl, subPath, setSubPath, method, setMethod,
  username, setUsername, password, setPassword,
  retryInterval, setRetryInterval, maxRetries, setMaxRetries,
  timeoutMs, setTimeoutMs,
}: RestApiProps) {
  return (
    <div className="space-y-3">
      <FG label={t('dw.ct.baseUrl')}><TxtInput value={baseUrl} onChange={setBaseUrl} placeholder="https://api.example.com" mono={false} /></FG>
      <Row>
        <FG label={t('dw.ct.subPath')}><TxtInput value={subPath} onChange={setSubPath} placeholder="/v1/data" mono={false} /></FG>
        <FG label={t('dw.ct.method')}>
          <SelInput value={method} onChange={setMethod} options={[
            { value: 'GET', label: 'GET' },
            { value: 'POST', label: 'POST' },
            { value: 'PUT', label: 'PUT' },
            { value: 'DELETE', label: 'DELETE' },
          ]} />
        </FG>
      </Row>
      <Row>
        <FG label={t('dw.ct.username')}><TxtInput value={username} onChange={setUsername} placeholder="api_user" /></FG>
        <FG label={t('dw.ct.password')}><TxtInput value={password} onChange={setPassword} type="password" placeholder="••••••" /></FG>
      </Row>
      <Row>
        <FG label={t('dw.ct.retryInterval')}><NumInput value={retryInterval} onChange={setRetryInterval} placeholder="30" /></FG>
        <FG label={t('dw.ct.maxRetries')}><NumInput value={maxRetries} onChange={setMaxRetries} placeholder="3" /></FG>
      </Row>
      <FG label={t('dw.ct.timeoutMs')}><NumInput value={timeoutMs} onChange={setTimeoutMs} placeholder="5000" /></FG>
    </div>
  );
}

// ──────────────────────────────────────────────
// KafkaConfigForm — Kafka
// ──────────────────────────────────────────────
export interface KafkaProps {
  t: TFn;
  bootstrapServers: string; setBootstrapServers: SetFn;
  topic: string; setTopic: SetFn;
  groupId: string; setGroupId: SetFn;
  protocol: string; setProtocol: SetFn;
  securityProtocol: string; setSecurityProtocol: SetFn;
  saslMechanism: string; setSaslMechanism: SetFn;
  username: string; setUsername: SetFn;
  password: string; setPassword: SetFn;
  autoCommit: boolean; setAutoCommit: BoolSetFn;
}

export function KafkaConfigForm({
  t, bootstrapServers, setBootstrapServers,
  topic, setTopic, groupId, setGroupId,
  protocol, setProtocol, securityProtocol, setSecurityProtocol,
  saslMechanism, setSaslMechanism,
  username, setUsername, password, setPassword,
  autoCommit, setAutoCommit,
}: KafkaProps) {
  return (
    <div className="space-y-3">
      <FG label={t('dw.ct.bootstrapServers')}>
        <TxtInput value={bootstrapServers} onChange={setBootstrapServers} placeholder="kafka1:9092,kafka2:9092" />
      </FG>
      <Row>
        <FG label={t('dw.ct.topic')}><TxtInput value={topic} onChange={setTopic} placeholder="ecos.events" /></FG>
        <FG label={t('dw.ct.groupId')}><TxtInput value={groupId} onChange={setGroupId} placeholder="ecos-group" /></FG>
      </Row>
      <Row>
        <FG label={t('dw.ct.protocol')}>
          <SelInput value={protocol} onChange={setProtocol} options={[
            { value: 'PLAINTEXT', label: 'PLAINTEXT' },
            { value: 'SSL', label: 'SSL' },
            { value: 'SASL_PLAINTEXT', label: 'SASL_PLAINTEXT' },
            { value: 'SASL_SSL', label: 'SASL_SSL' },
          ]} />
        </FG>
        <FG label={t('dw.ct.securityProtocol')}>
          <SelInput value={securityProtocol} onChange={setSecurityProtocol} options={[
            { value: 'NOAUTH', label: 'NOAUTH' },
            { value: 'BASIC', label: 'BASIC' },
            { value: 'SCRAM-SHA-256', label: 'SCRAM-SHA-256' },
            { value: 'SCRAM-SHA-512', label: 'SCRAM-SHA-512' },
          ]} />
        </FG>
      </Row>
      <Row>
        <FG label={t('dw.ct.saslMechanism')}>
          <SelInput value={saslMechanism} onChange={setSaslMechanism} options={[
            { value: 'PLAIN', label: 'PLAIN' },
            { value: 'SCRAM-SHA-256', label: 'SCRAM-SHA-256' },
            { value: 'SCRAM-SHA-512', label: 'SCRAM-SHA-512' },
          ]} />
        </FG>
        <div className="flex items-end pb-1.5">
          <Chk checked={autoCommit} onChange={setAutoCommit} label={t('dw.ct.autoCommit')} />
        </div>
      </Row>
      <Row>
        <FG label={t('dw.ct.username')}><TxtInput value={username} onChange={setUsername} placeholder="kafka_user" /></FG>
        <FG label={t('dw.ct.password')}><TxtInput value={password} onChange={setPassword} type="password" placeholder="••••••" /></FG>
      </Row>
    </div>
  );
}

// ──────────────────────────────────────────────
// MinioConfigForm — MinIO (PMO-48-T5: 监听端口三选一阶梯)
// ──────────────────────────────────────────────
export interface MinioProps {
  t: TFn;
  endpointUrl: string; setEndpointUrl: SetFn;
  listenPort: number; setListenPort: NumSetFn;
  bucket: string; setBucket: SetFn;
  roleArn: string; setRoleArn: SetFn;
  accessKey: string; setAccessKey: SetFn;
  secretKey: string; setSecretKey: SetFn;
  pathPrefix: string; setPathPrefix: SetFn;
}

export function MinioConfigForm({
  t, endpointUrl, setEndpointUrl, listenPort, setListenPort,
  bucket, setBucket, roleArn, setRoleArn,
  accessKey, setAccessKey, secretKey, setSecretKey,
  pathPrefix, setPathPrefix,
}: MinioProps) {
  const useSts = roleArn.startsWith('arn:aws:iam');
  return (
    <div className="space-y-3">
      <FG label={t('dw.ct.endpointUrl')}>
        <TxtInput value={endpointUrl} onChange={setEndpointUrl} placeholder="http://localhost:9000" mono={false} />
      </FG>
      <Row>
        <FG label={t('dw.minio.listenPort')}><NumInput value={listenPort} onChange={setListenPort} placeholder="9000" /></FG>
        <FG label={t('dw.ct.bucket')}><TxtInput value={bucket} onChange={setBucket} placeholder="ecos-data" /></FG>
      </Row>
      <FG label={t('dw.minio.accessLadder')}>
        {/* P-9 阶梯: 0=AccessKey (默认) / 1=HTTPS (listenPort=443) / 2=STS (临时凭据) */}
        <div className="space-y-1">
          <label className="flex items-center gap-2 cursor-pointer">
            <input type="radio" checked={!useSts && listenPort !== 443} onChange={() => { setRoleArn(''); setListenPort(9000); }} className="accent-blue-500" />
            <span className="text-[11px]">{t('dw.minio.ladderLevel0')}</span>
          </label>
          <label className="flex items-center gap-2 cursor-pointer">
            <input type="radio" checked={!useSts && listenPort === 443} onChange={() => { setRoleArn(''); setListenPort(443); }} className="accent-blue-500" />
            <span className="text-[11px]">{t('dw.minio.ladderLevel1')}</span>
          </label>
          <label className="flex items-center gap-2 cursor-pointer">
            <input type="radio" checked={useSts} onChange={() => { if (!roleArn.startsWith('arn:aws:iam')) setRoleArn('arn:aws:iam:::assumed-role/ecos-dw-role/s3'); setListenPort(9000); }} className="accent-blue-500" />
            <span className="text-[11px]">{t('dw.minio.ladderLevel2')}</span>
          </label>
        </div>
      </FG>
      {useSts ? (
        <FG label={t('dw.minio.roleArn')}>
          <TxtInput value={roleArn} onChange={setRoleArn} placeholder="arn:aws:iam:::assumed-role/ecos-dw-role/s3" />
        </FG>
      ) : (
        <Row>
          <FG label={t('dw.ct.accessKey')}><TxtInput value={accessKey} onChange={setAccessKey} placeholder="minioadmin" /></FG>
          <FG label={t('dw.minio.secretKey')}><TxtInput value={secretKey} onChange={setSecretKey} type="password" placeholder="••••••" /></FG>
        </Row>
      )}
      <FG label={t('dw.ct.pathPrefix')}><TxtInput value={pathPrefix} onChange={setPathPrefix} placeholder="/" /></FG>
    </div>
  );
}

// ──────────────────────────────────────────────
// FsConfigForm — 本地文件系统 (PMO-48-T5)
// ──────────────────────────────────────────────
export interface FsProps {
  t: TFn;
  rootPath: string; setRootPath: SetFn;
  pattern: string; setPattern: SetFn;
  recursive: boolean; setRecursive: BoolSetFn;
}

export function FsConfigForm({
  t, rootPath, setRootPath, pattern, setPattern, recursive, setRecursive,
}: FsProps) {
  return (
    <div className="space-y-3">
      <FG label={t('dw.fs.rootPath')}>
        <TxtInput value={rootPath} onChange={setRootPath} placeholder="/data/ecos/warehouse" mono={false} />
      </FG>
      <FG label={t('dw.fs.pattern')}>
        <TxtInput value={pattern} onChange={setPattern} placeholder="*.parquet,*.orc,*.csv" mono={false} />
      </FG>
      <Chk checked={recursive} onChange={setRecursive} label={t('dw.fs.recursive')} />
    </div>
  );
}

// ──────────────────────────────────────────────
// MongoConfigForm — MongoDB
// ──────────────────────────────────────────────
export interface MongoProps {
  t: TFn;
  host: string; setHost: SetFn;
  port: number; setPort: NumSetFn;
  database: string; setDatabase: SetFn;
  username: string; setUsername: SetFn;
  password: string; setPassword: SetFn;
  authSource: string; setAuthSource: SetFn;
}

export function MongoConfigForm({
  t, host, setHost, port, setPort,
  database, setDatabase,
  username, setUsername, password, setPassword,
  authSource, setAuthSource,
}: MongoProps) {
  return (
    <div className="space-y-3">
      <Row>
        <FG label={t('dw.ct.host')}><TxtInput value={host} onChange={setHost} placeholder="mongo.example.com" /></FG>
        <FG label={t('dw.ct.port')}><NumInput value={port} onChange={setPort} placeholder="27017" /></FG>
      </Row>
      <Row>
        <FG label={t('dw.ct.database')}><TxtInput value={database} onChange={setDatabase} placeholder="ecos_db" /></FG>
        <FG label={t('dw.ct.authSource')}><TxtInput value={authSource} onChange={setAuthSource} placeholder="admin" /></FG>
      </Row>
      <Row>
        <FG label={t('dw.ct.username')}><TxtInput value={username} onChange={setUsername} placeholder="ecos_user" /></FG>
        <FG label={t('dw.ct.password')}><TxtInput value={password} onChange={setPassword} type="password" placeholder="••••••" /></FG>
      </Row>
    </div>
  );
}
