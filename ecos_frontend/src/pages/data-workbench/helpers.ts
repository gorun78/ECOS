/* Shared helpers for data-workbench tabs */
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

export const getSourceTypeLabel = (type: string) => {
  switch (type) {
    case 'postgresql': return 'PostgreSQL 关系型数据库';
    case 's3': return 'Amazon S3 云端对象存储';
    case 'rest_api': return 'REST OpenAPI 服务端点';
    case 'sftp': return 'SFTP 机组报文共享服务器';
    case 'sap': return 'SAP ERP 业务流连接器';
    default: return '未知数据源';
  }
};
