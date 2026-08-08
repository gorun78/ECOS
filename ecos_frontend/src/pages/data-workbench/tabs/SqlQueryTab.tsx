/* Extracted from DataWorkbenchLayout.tsx */
import React from 'react';
import SqlQueryConsole from '../../SqlQueryConsole';
import { useTheme } from '../../../components/ThemeContext';

interface SqlQueryTabProps {
  showToast?: (type: string, message: string) => void;
}

const SqlQueryTab: React.FC<SqlQueryTabProps> = ({ showToast }) => {
  const { styles } = useTheme();
  return (
    <div className={`flex-1 flex flex-col min-h-0 ${styles.appBg} overflow-hidden`}>
      <SqlQueryConsole />
    </div>
  );
};

export default SqlQueryTab;
