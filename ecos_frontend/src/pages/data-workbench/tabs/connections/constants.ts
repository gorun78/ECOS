/* Extracted from ConnectionsTab.tsx — 常量与纯格式化辅助 */

/** 字节数格式化（文件夹数据源文件列表用） */
export const fmtBytes = (n?: number): string => {
  if (!n) return '-';
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / (1024 * 1024)).toFixed(2)} MB`;
};

export const STRATEGY_OPTIONS: { value: string; key: string }[] = [
  { value: 'MANUAL', key: 'dw.strategy.manual' },
  { value: 'ON_SAVE', key: 'dw.strategy.onSave' },
  { value: 'ON_SCHEDULE', key: 'dw.strategy.onSchedule' },
];
export const COUNT_METHOD_OPTIONS: { value: string; key: string }[] = [
  { value: 'OFF', key: 'dw.strategy.countOff' },
  { value: 'ESTIMATE', key: 'dw.strategy.countEstimate' },
  { value: 'EXACT', key: 'dw.strategy.countExact' },
];
