/* Shared helpers for data-workbench tabs */

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
