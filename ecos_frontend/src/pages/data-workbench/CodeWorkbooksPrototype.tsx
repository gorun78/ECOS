/**
 * CodeWorkbooksPrototype — interactive code notebook (stub)
 * @license Apache-2.0
 */
import React, { useState } from 'react';
import { Play, BookOpen, Plus } from 'lucide-react';
import { useTheme } from '../../components/ThemeContext';

interface Props {
  onCommitToGit?: (message: string, filesChanged: string[]) => void;
}

export default function CodeWorkbooksPrototype({ onCommitToGit }: Props) {
  const { styles } = useTheme();
  const [cells] = useState([
    { id: 'c1', type: 'markdown', content: '# Aviation Data Analysis\nExplore flight patterns and pilot assignments.' },
    { id: 'c2', type: 'code', lang: 'python', content: 'import pandas as pd\nfrom ecos.data import SilverDataset\n\n# Load silver-layer flight data\nflights = SilverDataset("aviation_flights_v2").to_pandas()\nprint(f"Loaded {len(flights)} flights")\nflights.head()', output: 'Loaded 124,583 flights\n   flight_id  dep_time  arr_time  aircraft_type\n0  CA1234    08:30     10:45     B737\n1  CA1235    12:15     14:30     A320' },
    { id: 'c3', type: 'code', lang: 'sql', content: '-- Pilot workload analysis\nSELECT pilot_id, COUNT(*) as flight_count, AVG(duration_min) as avg_duration\nFROM silver.flights\nGROUP BY pilot_id\nORDER BY flight_count DESC\nLIMIT 10;', output: 'pilot_id | flight_count | avg_duration\nPL001    | 342         | 125.3\nPL002    | 298         | 118.7' },
  ]);

  return (
    <div className={`h-full flex flex-col ${styles.cardBg}`}>
      <div className={`flex items-center justify-between px-4 py-2 border-b ${styles.appBorder} ${styles.sidebarBg}`}>
        <div className={`flex items-center gap-2 text-xs font-bold ${styles.cardTextMuted}`}>
          <BookOpen size={13} className="text-violet-600" />
          <span>aviation_analysis.ecosnb</span>
        </div>
        <div className="flex gap-2">
          <button className={`flex items-center gap-1 px-2 py-1 rounded text-xs transition ${styles.sidebarHoverBg} ${styles.sidebarBg} ${styles.cardText}`}>
            <Plus size={11} /> 添加Cell
          </button>
          <button
            onClick={() => onCommitToGit?.('feat: workbook update', ['workbooks/aviation_analysis.ecosnb'])}
            className={`px-3 py-1 ${styles.accentBg} ${styles.accentHover} text-white rounded text-xs font-medium transition`}
          >
            Commit
          </button>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto p-4 space-y-4">
        {cells.map(cell => (
          <div key={cell.id} className={`border rounded-lg overflow-hidden ${styles.cardBorder}`}>
            {cell.type === 'markdown' ? (
              <div className={`p-4 text-sm ${styles.cardText} ${styles.sidebarBg} prose prose-sm max-w-none`}>
                {cell.content.split('\n').map((line, i) => {
                  if (line.startsWith('# ')) return <h1 key={i} className="text-lg font-bold mb-1">{line.slice(2)}</h1>;
                  if (line.startsWith('## ')) return <h2 key={i} className="text-base font-semibold mb-1">{line.slice(3)}</h2>;
                  return <p key={i} className="text-xs">{line}</p>;
                })}
              </div>
            ) : (
              <>
                <div className={`flex items-center justify-between px-3 py-1.5 ${styles.appBg} ${styles.cardTextMuted} text-[10px]`}>
                  <span className="font-mono uppercase">{cell.lang}</span>
                  <button className="flex items-center gap-1 text-emerald-400 hover:text-emerald-300 transition">
                    <Play size={10} /> Run
                  </button>
                </div>
                <pre className={`p-4 font-mono text-xs ${styles.cardText} ${styles.cardBg} overflow-x-auto`}>{cell.content}</pre>
                {'output' in cell && (
                  <div className={`border-t p-3 font-mono text-[11px] whitespace-pre-wrap ${styles.appBorder} ${styles.sidebarBg} ${styles.cardTextMuted}`}>
                    {cell.output}
                  </div>
                )}
              </>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
