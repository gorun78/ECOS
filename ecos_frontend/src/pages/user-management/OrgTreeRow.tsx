/**
 * OrgTreeRow — 组织机构树递归行（从 UserManagement.tsx 抽取，逐字搬迁）
 * @license Apache-2.0
 */

import { useState } from "react";
import { Edit3, Trash2 } from "lucide-react";
import type { IamOrg } from "../../api";
import { useTheme } from "../../components/ThemeContext";

export default function OrgTreeRow({ org, depth, onEdit, onDelete }: {
  org: IamOrg; depth: number; onEdit: (o: IamOrg) => void; onDelete: (o: IamOrg) => void;
}) {
  const { styles } = useTheme();
  const [expanded, setExpanded] = useState(true);
  const hasChildren = org.children && org.children.length > 0;
  return (
    <>
      <div className={`flex items-center gap-2 px-3 py-2 border-b ${styles.cardBorder} text-[13px] ${styles.sidebarHoverBg}`}
        style={{ paddingLeft: `${depth * 24 + 12}px` }}>
        <span className={`w-4 text-center shrink-0 ${styles.muted}`}>
          {hasChildren ? <button type="button" onClick={e => { e.stopPropagation(); setExpanded(!expanded); }} className="text-xs hover:opacity-70">{expanded ? '▼' : '▶'}</button>
            : <span className={styles.muted}>•</span>}
        </span>
        <span className="flex-1 truncate font-medium">{org.orgName}</span>
        <code className={`text-[11px] ${styles.sidebarBg} px-1.5 py-0.5 rounded`}>{org.orgCode}</code>
        <span className={`text-xs shrink-0 ${org.status === 'ACTIVE' ? 'text-green-500' : 'text-red-500'}`}>● {org.status}</span>
        <span className="text-xs opacity-50 shrink-0">{org.orgType}</span>
        <span className="text-xs opacity-40 shrink-0 max-w-[120px] truncate">{org.description || '-'}</span>
        <div className="flex gap-1 shrink-0 ml-2">
          <button type="button" onClick={e => { e.stopPropagation(); onEdit(org); }} className="text-indigo-500 hover:text-indigo-700 p-0.5"><Edit3 size={13} /></button>
          <button type="button" onClick={e => { e.stopPropagation(); onDelete(org); }} className="text-red-500 hover:text-red-700 p-0.5"><Trash2 size={13} /></button>
        </div>
      </div>
      {hasChildren && expanded && org.children!.map(child => <OrgTreeRow key={child.orgId} org={child} depth={depth + 1} onEdit={onEdit} onDelete={onDelete} />)}
    </>
  );
}
