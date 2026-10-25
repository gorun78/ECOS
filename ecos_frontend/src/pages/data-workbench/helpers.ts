/* Shared helpers for data-workbench tabs */

import { CONNECTION_TYPES } from './types';
import type { DataConnection } from './types';

/** 从 catch 的 unknown 错误中取消息（fetch 抛出的均为 Error 实例） */
export const getErrorMessage = (e: unknown): string =>
  e instanceof Error ? e.message : '';

/**
 * 未解析图标的唯一读数（#142/B4-3e-66，差距 <b>D-B4-95</b>）。
 *
 * <p>与 {@code 'unknown'} 不入 {@code CONNECTION_TYPES} 选项面同理：它不是某一枚类型，
 * 而是「没解析出来」的读数，所以放在表外、由 {@link getSourceIcon} 单点持有。
 * 改前 `default` 落 {@code 'HardDrive'}，而 HardDrive 正是本地文件系统（{@code fs}）的图标
 * ——14 枚已知类型（MySQL/Oracle/DM/KingBase/GaussDB/OSS/MinIO/CSV/Kafka/MongoDB/Doris/
 * ClickHouse/MSSQL/MongoDB 等）全部显示成「本地磁盘」＝同族第四处「已知折成同一个默认读数」。</p>
 */
export const UNRESOLVED_ICON = 'CircleQuestionMark';

/**
 * 连接类型图标名（单源 = {@code CONNECTION_TYPES.iconName}，19 枚逐枚在位）。
 *
 * <p>改前本函数自带 5 枚私有 switch＋{@code default 'HardDrive'}；现与名称面
 * {@code i18nKey}、说明面 {@code descKey} 读同一份表。解析不到（含 {@code 'unknown'}
 * 读数与运行时表外值）出 {@link UNRESOLVED_ICON} 显式未解析图标，<b>禁借用任何一枚
 * 已知类型的图标</b>。</p>
 *
 * <p>图标名须经 {@code LucideIcon.tsx} 的 {@code iconMap} 桥接才会画出来，桥接缺位时
 * 该包装器 {@code console.warn} 后落 {@code AlertTriangle}——那又是一次静默折叠，
 * 故逐枚在位由前端门 {@code b4ConnTypeIconChannel.spec.ts} 对账。</p>
 */
export const getSourceIcon = (type: string): string => {
  const option = CONNECTION_TYPES.find((o) => o.value === type);
  return option ? option.iconName : UNRESOLVED_ICON;
};

/**
 * 连接类型说明文案（#142/B4-3e-65，差距 <b>D-B4-94</b> 的用户面读数）。
 *
 * <p>改前本文件自带一份 {@code getSourceTypeLabel} 的 5 枚私有 switch 表
 * （postgresql/s3/rest_api/sftp/sap），其余 14 枚<b>已知</b>类型一律落 {@code dw.src.unknown}
 * ——MySQL 连接在详情页副标题上显示「未知数据源」，与同族 D-B4-93「把不知道折成一个具体值」
 * 互为反向（这里是把已知折成未知）。现单源＝{@code CONNECTION_TYPES.descKey}，
 * 与名称通道 {@code i18nKey}、徽章通道 {@link connTypeBadgeText} 同一份表。</p>
 *
 * <p>未解析（{@code 'unknown'} 或运行时值在显示面无归属）一律显式出「未知数据源（原值 X）」，
 * <b>禁借用任何一枚已知类型的说明</b>——那正是被本函数收口的折叠形态。</p>
 */
export const connTypeDescText = (
  conn: Pick<DataConnection, 'type' | 'typeRaw'>,
  t: (key: string) => string,
): string => {
  const unresolved = t('dw.src.unknown');
  if (conn.type === 'unknown') {
    const raw = (conn.typeRaw ?? '').trim();
    return raw ? `${unresolved} (${raw})` : unresolved;
  }
  const option = CONNECTION_TYPES.find((o) => o.value === conn.type);
  // 运行时值在显示面无归属＝显示面与类型面分裂，同样按未解析出并点名原值
  return option ? t(option.descKey) : `${unresolved} (${conn.type})`;
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
