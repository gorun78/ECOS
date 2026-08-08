/* DataLineage tab wrapper — delegates to DataLineage component */
import React from 'react';
import DataLineage from '../../DataLineage';

const DataLineageTab: React.FC = () => (
  <div className="flex-1 flex flex-col min-h-0 overflow-hidden">
    <DataLineage />
  </div>
);

export default DataLineageTab;
