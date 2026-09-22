/**
 * DatalakeUploadPanel — 非结构化近源层 · 文件夹数据源模式
 *
 * 模式：前端不上传二进制。用户在「连接」页保存 FILESYSTEM 类型数据源后，
 * 本面板选择该数据源 → "刷新文件列表"读取服务端目录内全部白名单文件 →
 * 勾选目标文件 → "采集到近源层"由服务端把文件读入 MinIO 近源层
 * raw/unstructured/{datasourceId}/{docId}/{fileName} 并登记
 * td_data_resource (layer=RAW, zone=UNSTRUCTURED, resource_type=LAKE_OBJECT)
 * （分层规范 §三/§五/§六，经 runtime-access MinioStorageService）。
 *
 * 能力：
 *  - 数据湖（MinIO 近源库）健康状态实时校验
 *  - FILESYSTEM 数据源选择 + 刷新文件列表 + 勾选采集
 *  - 已登记对象列表（listDatalakeObjects 拉取 raw/unstructured/ 前缀）
 *
 * @license Apache-2.0
 */
import React, { useCallback, useEffect, useState } from 'react';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';
import LucideIcon from '../LucideIcon';
import {
  fetchDatalakeStatus,
  listDatalakeObjects,
  listFolderFiles,
  collectFolderFiles,
} from '../api';
import type { FolderFileVo } from '../api';

/** 面板 Props：showToast 由上层 ConnectionsTab 提供；dsId 为当前选中的数据源 ID */
interface Props {
  showToast: (type: 'success' | 'info' | 'error', message: string) => void;
  dsId: string;
}

interface LakeObjectItem {
  name: string;
  size?: number;
  lastModified?: string;
  isDir?: boolean;
}

interface DatalakeStatus {
  endpoint?: string;
  bucket?: string;
  status?: string;
  initialized?: boolean;
  accessKey?: string;
}

const fmtBytes = (n?: number): string => {
  if (!n) return '-';
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / (1024 * 1024)).toFixed(2)} MB`;
};

const DatalakeUploadPanel: React.FC<Props> = ({ showToast, dsId }) => {
  const { styles } = useTheme();
  const { t } = useLanguage();

  // ── 数据湖状态 ──
  const [lakeStatus, setLakeStatus] = useState<DatalakeStatus | null>(null);
  // ── 文件夹数据源（面板使用当前选中的数据源） ──
  const [docIdSrc, setDocIdSrc] = useState<string>('');
  // ── 文件列表（服务端目录） ──
  const [files, setFiles] = useState<FolderFileVo[]>([]);
  const [listLoading, setListLoading] = useState(false);
  const [fileError, setFileError] = useState<string | null>(null);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  // ── 采集态 ──
  const [collecting, setCollecting] = useState(false);
  // ── 已登记近源层对象（raw/unstructured/） ──
  const [objects, setObjects] = useState<LakeObjectItem[]>([]);
  const [registeredLoading, setRegisteredLoading] = useState(false);

  // 默认 docId：doc_${dsId}_${时间戳}
  const defaultDocId = useCallback(
    (ds: string) => `doc_${(ds || 'default').replace(/[^a-zA-Z0-9_-]/g, '_')}_${Date.now()}`,
    []
  );

  // 数据源 ID 变化 → 重置文件列表上下文（选中/错误）
  useEffect(() => {
    setSelected(new Set());
    setFileError(null);
    setFiles([]);
  }, [dsId]);

  // 拉取数据湖状态（非结构与结构化共享 bucket）
  useEffect(() => {
    let cancelled = false;
    (async () => {
      const s = await fetchDatalakeStatus();
      if (!cancelled) setLakeStatus(s);
    })();
    return () => { cancelled = true; };
  }, []);

  // 拉取已登记的非结构化对象（raw/unstructured/ 前缀）
  const refreshObjects = useCallback(async () => {
    setRegisteredLoading(true);
    try {
      const r = await listDatalakeObjects('raw/unstructured/');
      setObjects(r?.items || []);
    } finally {
      setRegisteredLoading(false);
    }
  }, []);

  useEffect(() => { refreshObjects(); }, [refreshObjects]);

  // 刷新文件列表（服务端按 FILESYSTEM 数据源 connectionConfig 目录读取）
  const refreshFiles = useCallback(async () => {
    if (!dsId) return;
    setListLoading(true);
    setSelected(new Set());
    setFileError(null);
    try {
      const list = await listFolderFiles(dsId);
      setFiles(list);
      if (list.length === 0) setFileError(t('datalake.folder.file.list.empty'));
    } catch (e) {
      const msg = e instanceof Error ? e.message : 'HTTP error';
      setFileError(msg);
      setFiles([]);
    } finally {
      setListLoading(false);
    }
  }, [dsId, t]);

  const toggleSelect = (name: string) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(name)) next.delete(name);
      else next.add(name);
      return next;
    });
  };

  // 采集到近源层（后端逐文件：MinIO 写入 + 登记）
  const onCollect = async () => {
    if (!dsId) {
      showToast('error', t('datalake.folder.selectFirst') || '请先选择 FILESYSTEM 文件数据源');
      return;
    }
    if (selected.size === 0) {
      showToast('info', t('datalake.folder.collect.noSelection') || '请先勾选要采集的文件');
      return;
    }
    const doc = docIdSrc.trim() || defaultDocId(dsId);
    if (!doc) {
      showToast('error', t('datalake.folder.collect.noDocId') || 'docId 不能为空');
      return;
    }
    setCollecting(true);
    try {
      const res = await collectFolderFiles({
        datasourceId: dsId,
        docId: doc,
        fileNames: Array.from(selected),
      });
      if (res.failed > 0) {
        const firstErr = res.items?.find((i) => i.status !== 'SUCCESS')?.error;
        showToast('error',
          (t('datalake.folder.collect.fail.partial') || '采集完成：N 成功 / M 失败').replace('N', String(res.collected)).replace('M', String(res.failed))
          + (firstErr ? `（${firstErr}）` : ''));
      } else {
        showToast('success',
          (t('datalake.folder.collect.success') || '采集成功 N 个文件').replace('N', String(res.collected)));
      }
      setSelected(new Set());
      await refreshObjects();
    } catch (e) {
      const msg = e instanceof Error ? e.message : 'collect failed';
      showToast('error', msg);
    } finally {
      setCollecting(false);
    }
  };

  const lakeReady = lakeStatus?.status === 'UP' || lakeStatus?.status === 'DEGRADED';
  const selectedCount = selected.size;

  return (
    <div className={`border ${styles.cardBorder} rounded-xl overflow-hidden ${styles.appBg}/40 space-y-3`}>
      {/* 标题栏 */}
      <div className={`px-3 py-2 border-b ${styles.cardBorder} flex items-center justify-between gap-2 ${styles.sidebarBg}/70`}>
        <div className="flex items-center gap-1.5 text-xs font-bold" style={{ color: styles.cardText }}>
          <LucideIcon name="FolderOpen" size={13} className={styles.accentText} />
          <span>{t('datalake.folder.title') || '文档近源层 · 文件夹数据源'}</span>
        </div>
        <div className="flex items-center gap-2">
          {lakeStatus && (
            <span
              className="text-[9px] font-mono px-1.5 py-0.5 rounded-full border"
              style={{
                color: lakeStatus.status === 'UP' ? styles.successText : styles.cardTextMuted,
                borderColor: styles.cardBorder,
                background: lakeStatus.status === 'UP' ? styles.successBg : styles.sidebarBg,
              }}
            >
              {lakeStatus.status || '—'}
            </span>
          )}
          <button
            type="button"
            onClick={refreshObjects}
            disabled={registeredLoading}
            className="p-1 rounded border text-[10px] disabled:opacity-40"
            style={{ borderColor: styles.cardBorder, color: styles.cardTextMuted, background: styles.appBg }}
            title={t('datalake.upload.refresh') || '刷新'}
          >
            <LucideIcon name="RefreshCw" size={11} className={registeredLoading ? 'animate-spin' : ''} />
          </button>
        </div>
      </div>

      <div className="p-3 space-y-3">
        {/* 文件数据源（FILESYSTEM）+ docId 过滤 */}
        <div className="grid grid-cols-2 gap-2">
          <div>
            <label className="text-[10px] uppercase tracking-wider block mb-1" style={{ color: styles.cardTextMuted }}>
              {t('datalake.folder.dataSource') || '文件数据源（FILESYSTEM）'}
            </label>
            <div
              className="w-full px-2 py-1 border rounded text-xs font-mono flex items-center gap-1.5"
              style={{ borderColor: styles.cardBorder, background: styles.inputBg, color: styles.inputText }}
              title={dsId}
            >
              <LucideIcon name="HardDrive" size={12} />
              <span className="truncate">{dsId || '—'}</span>
            </div>
          </div>
          <div>
            <label className="text-[10px] uppercase tracking-wider block mb-1" style={{ color: styles.cardTextMuted }}>
              {t('datalake.folder.docId.label') || 'docId（本次采集文件共享）'}
            </label>
            <input
              value={docIdSrc}
              onChange={(e) => setDocIdSrc(e.target.value)}
              disabled={collecting}
              className="w-full px-2 py-1 border rounded text-xs font-mono disabled:opacity-50"
              style={{ borderColor: styles.cardBorder, background: styles.inputBg, color: styles.inputText }}
              placeholder={t('datalake.folder.docId.placeholder') || '留空自动生成 doc_{数据源}_{时间戳}'}
            />
          </div>
        </div>

        {/* 文件列表操作条 */}
        <div className="flex items-center justify-between gap-2">
          <button
            type="button"
            onClick={refreshFiles}
            disabled={listLoading}
            className="px-2 py-1 rounded border text-[11px] flex items-center gap-1 cursor-pointer disabled:opacity-40"
            style={{ borderColor: styles.cardBorder, color: styles.cardText, background: styles.appBg }}
          >
            <LucideIcon name="RefreshCw" size={11} className={listLoading ? 'animate-spin' : ''} />
            {t('datalake.folder.refresh') || '刷新文件列表'}
          </button>
          <span className="text-[11px] font-mono" style={{ color: styles.cardTextMuted }}>
            {t('datalake.folder.selected') || '已选'}{' '}
            <span className="font-bold" style={{ color: styles.cardText }}>{selectedCount}</span>
            {' ' + (t('datalake.folder.selected.unit') || '个文件')}
          </span>
        </div>

        {/* 文件列表（checkbox + 名称左 + 大小右 + 修改时间） */}
        {fileError && files.length === 0 && (
          <div className="text-[11px] py-2 px-2 rounded border flex items-center gap-1.5"
            style={{ borderColor: styles.dangerBorder, background: styles.dangerBg, color: styles.cardText }}
          >
            <LucideIcon name="AlertTriangle" size={12} />
            <span>{fileError}</span>
          </div>
        )}
        {!dsId ? (
          <div className="text-[11px] py-3 text-center" style={{ color: styles.cardTextMuted }}>
            {t('datalake.folder.no_source') || '请先在连接页创建 FILESYSTEM 数据源指向文件目录'}
          </div>
        ) : (
          <ul className={`max-h-44 overflow-y-auto space-y-1 pr-1 border rounded ${styles.appBg}/60`}>
            {files.length === 0 && !listLoading ? (
              <li className="text-[11px] py-3 text-center" style={{ color: styles.cardTextMuted }}>
                {t('datalake.folder.file.list.empty') || '目录内无白名单文件（.pdf/.docx/.doc/.txt/.md）'}
              </li>
            ) : files.map((f) => (
              <li
                key={f.name}
                className="px-2 py-1.5 rounded border flex items-center gap-2 cursor-pointer"
                style={{ borderColor: styles.cardBorder }}
                onClick={() => toggleSelect(f.name)}
              >
                <input
                  type="checkbox"
                  checked={selected.has(f.name)}
                  onChange={() => toggleSelect(f.name)}
                  className="shrink-0"
                />
                <span className="font-mono text-[11px] truncate flex-1 text-left" style={{ color: styles.cardText }}>
                  {f.name}
                </span>
                <span className="font-mono text-[10px] shrink-0" style={{ color: styles.cardTextMuted }}>
                  {fmtBytes(f.size)}
                </span>
                {f.lastModified && (
                  <span className="text-[9px] font-mono shrink-0" style={{ color: styles.cardTextMuted }}>
                    {new Date(f.lastModified).toLocaleDateString()}
                  </span>
                )}
              </li>
            ))}
          </ul>
        )}

        {/* 采集按钮 */}
        <button
          type="button"
          onClick={onCollect}
          disabled={selectedCount === 0 || collecting || !lakeReady || !dsId}
          className="w-full px-3 py-2 rounded-md text-xs font-semibold flex items-center justify-center gap-1.5 disabled:opacity-40 cursor-pointer transition-all"
          style={{ background: styles.accentBg, color: styles.cardText }}
        >
          <LucideIcon name="CheckCircle" size={13} />
          {collecting
            ? (t('datalake.folder.collect.collecting') || '采集中…')
            : (t('datalake.folder.collect.button') || '采集到近源层')}
        </button>

        {/* 已登记近源层对象列表 */}
        <div className="space-y-1">
          <div className="flex items-center justify-between text-[10px] uppercase tracking-wider" style={{ color: styles.cardTextMuted }}>
            <span>
              {t('datalake.upload.listTitle') || '已登记对象'}
              {' · '}
              <span className="font-mono">{objects.length}</span>
            </span>
            {lakeStatus?.bucket && (
              <span className="font-mono">{lakeStatus.bucket}</span>
            )}
          </div>
          {objects.length === 0 && !registeredLoading ? (
            <div className="text-[11px] py-3 text-center" style={{ color: styles.cardTextMuted }}>
              {t('datalake.upload.empty') || '暂无已上传对象'}
            </div>
          ) : (
            <ul className="max-h-40 overflow-y-auto space-y-1 pr-1">
              {objects.map((o) => (
                <li
                  key={o.name}
                  className={`px-2 py-1.5 rounded border text-xs ${styles.appBg}/60`}
                  style={{ borderColor: styles.cardBorder }}
                >
                  <div className="flex items-center gap-1.5">
                    <LucideIcon name="FileText" size={11} className={styles.accentText} />
                    <span className="font-mono text-[11px] truncate flex-1" style={{ color: styles.cardText }} title={o.name}>
                      {o.name}
                    </span>
                    <span className="font-mono text-[10px] shrink-0" style={{ color: styles.cardTextMuted }}>
                      {fmtBytes(o.size)}
                    </span>
                  </div>
                  {o.lastModified && (
                    <div className="text-[9px] font-mono mt-0.5" style={{ color: styles.cardTextMuted }}>
                      {new Date(o.lastModified).toLocaleString()}
                    </div>
                  )}
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </div>
  );
};

export default DatalakeUploadPanel;