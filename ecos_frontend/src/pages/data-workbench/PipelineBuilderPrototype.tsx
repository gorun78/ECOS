/**
 * PipelineBuilderPrototype — visual pipeline editor (stub)
 * @license Apache-2.0
 */
import React, { useState } from 'react';
import { Play, Download } from 'lucide-react';
import { useTheme } from '../../components/ThemeContext';

interface Props {
  onCommitToGit?: (message: string, filesChanged: string[]) => void;
  onCompileComplete?: (output: {
    datasetPath: string; columns: string[]; rowCount: number;
    lastCompiled: string; expressionsCount: number;
  }) => void;
  showToast?: (type: 'success' | 'info' | 'error', message: string) => void;
}

export default function PipelineBuilderPrototype({ onCommitToGit, onCompileComplete, showToast }: Props) {
  const { styles } = useTheme();
  const [nodes] = useState([
    { id: 'src', label: 'Bronze Source', type: 'source', x: 60, y: 80 },
    { id: 'clean', label: 'Data Cleanse', type: 'transform', x: 240, y: 80 },
    { id: 'join', label: 'Entity Join', type: 'transform', x: 420, y: 80 },
    { id: 'sink', label: 'Silver Sink', type: 'sink', x: 600, y: 80 },
  ]);

  const handleCompile = () => {
    const output = {
      datasetPath: '/data/silver/aviation_flights_v2',
      columns: ['flight_id', 'dep_time', 'arr_time', 'aircraft_type', 'pilot_id', 'status'],
      rowCount: 124583,
      lastCompiled: new Date().toISOString(),
      expressionsCount: 8,
    };
    onCompileComplete?.(output);
    onCommitToGit?.('feat: pipeline compilation output', ['pipelines/aviation_flights_v2.json']);
    showToast?.('success', '管道编译成功！');
  };

  return (
    <div className={`h-full flex flex-col ${styles.cardBg}`}>
      <div className={`flex items-center justify-between px-4 py-2 border-b ${styles.appBorder} ${styles.sidebarBg}`}>
        <span className={`text-xs font-bold uppercase tracking-wider ${styles.cardTextMuted}`}>可视化管道编辑器</span>
        <div className="flex gap-2">
          <button onClick={handleCompile} className="flex items-center gap-1.5 px-3 py-1 bg-blue-600 hover:bg-blue-700 text-white rounded text-xs font-medium transition">
            <Play size={12} /> 编译运行
          </button>
          <button className={`flex items-center gap-1.5 px-3 py-1 border rounded text-xs transition ${styles.appBorder} ${styles.cardText} hover:bg-slate-100`}>
            <Download size={12} /> 导出
          </button>
        </div>
      </div>
      <div className={`flex-1 relative ${styles.sidebarBg}`} style={{ backgroundImage: 'radial-gradient(circle, #cbd5e1 1px, transparent 1px)', backgroundSize: '24px 24px' }}>
        {nodes.map(node => (
          <div key={node.id}
            className={`absolute border-2 border-blue-400 rounded-lg px-4 py-2 shadow-sm hover:shadow-md transition cursor-pointer ${styles.cardBg}`}
          >
            <span className={`text-xs font-semibold ${styles.cardText}`}>{node.label}</span>
            <div className={`text-[9px] mt-0.5 ${styles.cardTextMuted}`}>{node.type}</div>
          </div>
        ))}
        {/* SVG connectors */}
        <svg className="absolute inset-0 pointer-events-none" width="100%" height="100%">
          {nodes.slice(0, -1).map((node, i) => {
            const next = nodes[i + 1];
            return (
              <line key={node.id}
                x1={node.x + 72} y1={node.y + 24}
                x2={next.x} y2={next.y + 24}
                stroke="#3b82f6" strokeWidth="2" strokeDasharray="6,3"
              />
            );
          })}
        </svg>
      </div>
    </div>
  );
}
