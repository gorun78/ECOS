/* DataLineage tab wrapper — 支持 initialTable 透传(从目录树"查看血缘"跳转) */
import React from 'react';
import DataLineage from '../../DataLineage';

interface Props {
  /** 跳转进来的表名(可选);若有值则血缘视图默认切到"单表查询"并填入该表名 */
  initialTable?: string;
}

const DataLineageTab: React.FC<Props> = ({ initialTable }) => (
  <div className="flex-1 flex flex-col min-h-0 overflow-hidden">
    <DataLineage initialTable={initialTable} />
  </div>
);

export default DataLineageTab;
