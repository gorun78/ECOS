/* Extracted from DataWorkbenchLayout.tsx */
import React from 'react';
import DataEngineConfigPanel from '../DataEngineConfigPanel';
import { useTheme } from '../../../components/ThemeContext';

interface EngineConfigTabProps {
  showToast?: (type: string, message: string) => void;
}

const EngineConfigTab: React.FC<EngineConfigTabProps> = ({ showToast }) => {
  const { styles } = useTheme();
  return (
    <div className={`flex-1 flex flex-col min-h-0 ${styles.appBg} overflow-hidden`}>
      <DataEngineConfigPanel showToast={showToast} />
    </div>
  );
};

export default EngineConfigTab;
