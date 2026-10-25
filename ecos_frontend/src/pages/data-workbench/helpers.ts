/* Shared helpers for data-workbench tabs */

import { CONNECTION_TYPES } from './types';
import type { DataConnection } from './types';

/** 从 catch 的 unknown 错误中取消息（fetch 抛出的均为 Error 实例） */
export const getErrorMessage = (e: unknown): string =>
  e instanceof Error ? e.message : '';

export const getSourceIcon = (type: string) => {
  switch (type) {
    case 'postgresql': return 'Database';
    case 's3': return 'Cloud';
    case 'rest_api': return 'Globe';
    case 'sftp': return 'FolderGit';
    case 'sap': return 'Cpu';
    default: return 'HardDrive';
  }
};

export const getSourceTypeLabel = (type: string, t: (key: string) => string) => {
  switch (type) {
    case 'postgresql': return t('dw.src.postgresql');
    case 's3': return t('dw.src.s3');
    case 'rest_api': return t('dw.src.rest_api');
    case 'sftp': return t('dw.src.sftp');
    case 'sap': return t('dw.src.sap');
    default: return t('dw.src.unknown');
  }
};

/**
 * 连接类型徽章文案（#142/B4-3e-64，差距 <b>D-B4-93</b> 的用户面读数）。
 *
 * <p>单源 = `CONNECTION_TYPES` 的 `i18nKey` 面（本函数不再自带第二份类型→文案表）。
 * `type === 'unknown'` 是 `mapDsType` 「解析不到」的读数，此处把它连同后端原文
 * `typeRaw` 一起呈现成「未知类型 (原值 X)」——改前这一路会被 `mapDsType` 的
 * 无条件 `return 'postgresql'` 折成「PostgreSQL」，用户看到的是<b>另一枚类型</b>，
 * 而不是「我们不知道」。未解析一律显式，禁猜。</p>
 */
export const connTypeBadgeText = (
  conn: Pick<DataConnection, 'type' | 'typeRaw'>,
  t: (key: string) => string,
): string => {
  const unresolved = t('dw.connType.unknown');
  if (conn.type === 'unknown') {
    const raw = (conn.typeRaw ?? '').trim();
    return raw ? `${unresolved} (${raw})` : unresolved;
  }
  const option = CONNECTION_TYPES.find((o) => o.value === conn.type);
  // 显示名在 CONNECTION_TYPES 里无归属＝显示面与类型面分裂，同样按未解析出，禁静默显示原值
  return option ? t(option.i18nKey) : `${unresolved} (${conn.type})`;
};
