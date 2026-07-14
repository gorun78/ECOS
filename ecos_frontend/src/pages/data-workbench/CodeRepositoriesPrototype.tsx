/**
 * CodeRepositoriesPrototype — Git code repository browser (stub)
 * @license Apache-2.0
 */
import React, { useState } from 'react';
import { GitBranch, FileCode, FolderOpen, Clock } from 'lucide-react';
import { useTheme } from '../../components/ThemeContext';

interface Props {
  onCommitToGit?: (message: string, filesChanged: string[]) => void;
  globalGitHistory?: { hash: string; message: string; author: string; time: string; tool: string }[];
}

export default function CodeRepositoriesPrototype({ onCommitToGit, globalGitHistory = [] }: Props) {
  const { styles } = useTheme();
  const [activeFile, setActiveFile] = useState<string | null>(null);

  const repoFiles = [
    { name: 'pipelines/', type: 'dir', children: ['aviation_etl.py', 'flight_cleanse.sql', 'pilot_join.py'] },
    { name: 'schemas/', type: 'dir', children: ['bronze_schema.json', 'silver_schema.json'] },
    { name: 'Dockerfile', type: 'file' },
    { name: 'requirements.txt', type: 'file' },
    { name: 'README.md', type: 'file' },
  ];

  return (
    <div className={`h-full flex ${styles.cardBg}`}>
      {/* File tree */}
      <div className={`w-56 border-r overflow-y-auto p-3 ${styles.sidebarBorder}`}>
        <div className={`flex items-center gap-1.5 mb-3 text-xs font-bold uppercase ${styles.sidebarText}`}>
          <FolderOpen size={13} /> Repository
        </div>
        {repoFiles.map(f => (
          <div key={f.name}
            onClick={() => f.type === 'file' && setActiveFile(f.name)}
            className={`flex items-center gap-1.5 px-2 py-1 rounded text-xs cursor-pointer transition
              ${activeFile === f.name ? `${styles.accentBg} ${styles.accentText} font-medium` : `${styles.cardTextMuted} ${styles.sidebarHoverBg}`}`}
          >
            {f.type === 'dir' ? <FolderOpen size={12} className="text-amber-500" /> : <FileCode size={12} className="text-slate-400" />}
            {f.name}
          </div>
        ))}
      </div>

      {/* Content area */}
      <div className="flex-1 flex flex-col">
        <div className={`flex items-center justify-between px-4 py-2 border-b ${styles.appBorder} ${styles.sidebarBg}`}>
          <div className="flex items-center gap-2 text-xs">
            <GitBranch size={13} className="text-emerald-600" />
            <span className={`font-mono font-semibold ${styles.cardText}`}>main</span>
          </div>
          <button
            onClick={() => onCommitToGit?.('chore: repo update', ['multiple files'])}
            className={`px-3 py-1 ${styles.accentBg} ${styles.accentHover} text-white rounded text-xs font-medium transition`}
          >
            Commit
          </button>
        </div>

        {activeFile ? (
          <div className={`flex-1 p-4 font-mono text-xs overflow-auto ${styles.cardTextMuted}`}>
            <div className={`mb-2 ${styles.cardTextMuted}`}>// {activeFile}</div>
            <pre className="whitespace-pre-wrap">
{activeFile.endsWith('.py') ? `# ECOS Pipeline\nfrom ecos import Pipeline, Transform\n\npipeline = Pipeline("aviation_etl")\npipeline.add_source("bronze_flights")\npipeline.add_transform(Transform.cleanse())\npipeline.add_sink("silver_flights")\n\nif __name__ == "__main__":\n    pipeline.run()` :
 activeFile.endsWith('.sql') ? `-- Flight Cleanse SQL\nSELECT\n  flight_id,\n  COALESCE(dep_airport, 'UNKNOWN') as dep_airport,\n  arr_time - dep_time AS duration_min\nFROM bronze.flights\nWHERE dep_time IS NOT NULL;` :
 activeFile.endsWith('.json') ? `{\n  "schema": "silver",\n  "tables": ["flights", "pilots", "aircraft"],\n  "version": "1.2.0"\n}` :
 activeFile.endsWith('.md') ? `# Aviation Data Pipeline\n\n## Overview\nECOS data pipeline for aviation domain.\n\n## Structure\n- Bronze: raw ingest\n- Silver: cleansed & joined\n- Gold: ontology-mapped` :
 `# ${activeFile}\ndata-pipeline>=2.0\npyspark>=3.5\n`}
            </pre>
          </div>
        ) : (
          <div className={`flex-1 flex items-center justify-center text-sm ${styles.cardTextMuted}`}>
            选择文件以查看内容
          </div>
        )}

        {/* Git history */}
        {globalGitHistory.length > 0 && (
          <div className={`border-t p-3 max-h-32 overflow-y-auto ${styles.appBorder}`}>
            <div className={`flex items-center gap-1.5 mb-2 text-[10px] font-bold uppercase ${styles.cardTextMuted}`}>
              <Clock size={11} /> Recent Commits
            </div>
            {globalGitHistory.slice(0, 5).map((entry, i) => (
              <div key={i} className="flex items-center gap-2 text-[10px] py-0.5">
                <span className="font-mono text-amber-600">{entry.hash}</span>
                <span className={`${styles.cardTextMuted} truncate`}>{entry.message}</span>
                <span className={`ml-auto shrink-0 ${styles.cardTextMuted}`}>{entry.time}</span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
