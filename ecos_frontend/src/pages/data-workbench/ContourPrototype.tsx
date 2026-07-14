/**
 * ContourPrototype — data contour / drill-down analysis (stub)
 * @license Apache-2.0
 */
import React, { useState } from 'react';
import { Layers, BarChart3, Download } from 'lucide-react';
import { useTheme } from '../../components/ThemeContext';

interface Props {
  onCommitToGit?: (message: string, filesChanged: string[]) => void;
}

export default function ContourPrototype({ onCommitToGit }: Props) {
  const { styles } = useTheme();
  const [selectedDimension, setSelectedDimension] = useState('aircraft_type');
  const [selectedMetric, setSelectedMetric] = useState('count');

  const dimensions = ['aircraft_type', 'dep_airport', 'pilot_id', 'status', 'month'];
  const metrics = ['count', 'avg_duration', 'max_delay', 'total_distance'];

  // Mock chart data
  const chartData = [
    { label: 'B737', value: 45230, color: 'bg-blue-500' },
    { label: 'A320', value: 38120, color: 'bg-emerald-500' },
    { label: 'B787', value: 22180, color: 'bg-violet-500' },
    { label: 'A330', value: 12840, color: 'bg-amber-500' },
    { label: 'E190', value: 6213, color: 'bg-rose-500' },
  ];
  const maxVal = Math.max(...chartData.map(d => d.value));

  return (
    <div className={`h-full flex flex-col ${styles.cardBg}`}>
      <div className={`flex items-center justify-between px-4 py-2 border-b ${styles.appBorder} ${styles.sidebarBg}`}>
        <div className={`flex items-center gap-2 text-xs font-bold ${styles.cardTextMuted}`}>
          <Layers size={13} className="text-rose-600" />
          <span>Contour 数据轮廓分析</span>
        </div>
        <div className="flex gap-2">
          <button className={`flex items-center gap-1 px-2 py-1 border rounded text-xs hover:bg-slate-100 transition ${styles.appBorder} ${styles.cardText}`}>
            <Download size={11} /> 导出
          </button>
          <button
            onClick={() => onCommitToGit?.('feat: contour analysis saved', ['contours/aviation_analysis.json'])}
            className={`px-3 py-1 ${styles.accentBg} ${styles.accentHover} text-white rounded text-xs font-medium transition`}
          >
            Commit
          </button>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto p-6">
        {/* Controls */}
        <div className="flex gap-4 mb-6 flex-wrap">
          <div>
            <label className={`text-[10px] font-bold ${styles.cardTextMuted} uppercase block mb-1`}>维度</label>
            <select value={selectedDimension} onChange={e => setSelectedDimension(e.target.value)}
              className={`text-xs border rounded px-2 py-1.5 min-w-[140px] ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}>
              {dimensions.map(d => <option key={d} value={d}>{d}</option>)}
            </select>
          </div>
          <div>
            <label className={`text-[10px] font-bold ${styles.cardTextMuted} uppercase block mb-1`}>指标</label>
            <select value={selectedMetric} onChange={e => setSelectedMetric(e.target.value)}
              className={`text-xs border rounded px-2 py-1.5 min-w-[140px] ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}>
              {metrics.map(m => <option key={m} value={m}>{m}</option>)}
            </select>
          </div>
        </div>

        {/* Bar chart */}
        <div className="space-y-2 mb-6">
          {chartData.map(d => (
            <div key={d.label} className="flex items-center gap-3">
              <span className={`text-xs font-mono w-16 text-right ${styles.cardTextMuted}`}>{d.label}</span>
              <div className={`flex-1 h-6 rounded relative overflow-hidden ${styles.sidebarBg}`}>
                <div
                  className={`h-full ${d.color} rounded transition-all duration-500 flex items-center`}
                  style={{ width: `${(d.value / maxVal) * 100}%` }}
                >
                  <span className="text-[10px] text-white font-semibold ml-2">{d.value.toLocaleString()}</span>
                </div>
              </div>
            </div>
          ))}
        </div>

        {/* Stats summary */}
        <div className="grid grid-cols-3 gap-4">
          {[
            { label: '总记录', val: '124,583', icon: <BarChart3 size={14} /> },
            { label: '唯一值', val: '47', icon: <Layers size={14} /> },
            { label: '空值率', val: '2.1%', icon: <BarChart3 size={14} /> },
          ].map(s => (
            <div key={s.label} className={`border rounded-lg p-3 text-center ${styles.cardBorder}`}>
              <div className={`flex justify-center ${styles.cardTextMuted} mb-1`}>{s.icon}</div>
              <div className={`text-lg font-bold ${styles.cardText}`}>{s.val}</div>
              <div className={`text-[10px] ${styles.cardTextMuted}`}>{s.label}</div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
