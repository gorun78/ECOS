/**
 * DataSourceManager — 数据源管理页面入口
 * 组合 DataSourceList (列表+操作) + DataSourceWizard (向导式注册)
 *
 * @license Apache-2.0
 */

import React, { useState } from "react";
import ErrorBoundary from "../components/common/ErrorBoundary";
import DataSourceList from "./datasource/DataSourceList";
import DataSourceWizard from "./datasource/DataSourceWizard";

function DataSourceManagerInner() {
  const [showWizard, setShowWizard] = useState(false);

  const handleSuccess = () => {
    // Wizard will close itself; nothing extra needed
  };

  return (
    <>
      <DataSourceList onOpenWizard={() => setShowWizard(true)} />
      {showWizard && (
        <DataSourceWizard
          onClose={() => setShowWizard(false)}
          onSuccess={handleSuccess}
        />
      )}
    </>
  );
}

/**
 * DataSourceManager — wrapped with ErrorBoundary for resilience.
 */
export default function DataSourceManager() {
  return (
    <ErrorBoundary>
      <DataSourceManagerInner />
    </ErrorBoundary>
  );
}
