#!/bin/bash
# Transform ceos_new source files to c2eos targets
# Handles: LucideIcon → lucide-react, import path fixes, type import corrections
set -e

SRC=/home/guorongxiao/ceos_new/src
DST=/home/guorongxiao/c2eos/src

echo "=== Step 1: Copy files ==="

# Copy all source files to temp location first, then adapt
cp "$SRC/components/OntologyGraph.tsx" /tmp/ceos_OntologyGraph.tsx
cp "$SRC/components/Sidebar.tsx" /tmp/ceos_Sidebar.tsx
cp "$SRC/components/OverviewView.tsx" /tmp/ceos_OverviewView.tsx
cp "$SRC/components/ObjectTypeView.tsx" /tmp/ceos_ObjectTypeView.tsx
cp "$SRC/components/LinkTypeView.tsx" /tmp/ceos_LinkTypeView.tsx
cp "$SRC/components/ActionTypeView.tsx" /tmp/ceos_ActionTypeView.tsx
cp "$SRC/components/FunctionTypeView.tsx" /tmp/ceos_FunctionTypeView.tsx
cp "$SRC/components/ObjectExplorerView.tsx" /tmp/ceos_ObjectExplorerView.tsx
cp "$SRC/components/OtherViews.tsx" /tmp/ceos_OtherViews.tsx
cp "$SRC/mockData.ts" /tmp/ceos_mockData.ts

echo "=== Step 2: Transform LucideIcon → lucide-react for each file ==="

# For each file, we need to:
# 1. Remove LucideIcon import line
# 2. Collect all icon names used
# 3. Add lucide-react imports
# We'll use a Python script for this since it's complex

python3 - <<'PYEOF'
import re, os, sys

def extract_icon_names(content):
    """Extract all LucideIcon name values"""
    # Match: <LucideIcon name="IconName" ... />
    names = set()
    for m in re.finditer(r'<LucideIcon\s+name="([^"]+)"', content):
        names.add(m.group(1))
    for m in re.finditer(r"<LucideIcon\s+name='([^']+)'", content):
        names.add(m.group(1))
    for m in re.finditer(r'<LucideIcon\s+name=\{([^}]+)\}', content):
        # Dynamic names like {ot.icon}, {expanded.object ? "ChevronDown" : "ChevronRight"}
        names.update(re.findall(r'"([^"]+)"', m.group(1)))
    return names

def transform_file(filepath, types_import_path, relative_paths_map=None):
    """Transform a ceos_new file to c2eos format"""
    with open(filepath, 'r') as f:
        content = f.read()
    
    # Step 1: Extract icon names
    icon_names = extract_icon_names(content)
    
    # Step 2: Remove LucideIcon import
    content = re.sub(r"import\s+LucideIcon\s+from\s+['\"][^'\"]+['\"]\s*;?\s*\n", '', content)
    # Also remove any variant like "import LucideIcon from './LucideIcon'"
    
    # Step 3: Replace LucideIcon usages with direct lucide-react components
    # <LucideIcon name="IconName" size={N} /> → <IconName size={N} />
    # <LucideIcon name="IconName" size={N} className="..." /> → <IconName size={N} className="..." />
    
    def replace_lucide_icon(match):
        full = match.group(0)
        # Extract attributes
        name_match = re.search(r'name="([^"]+)"', full)
        if not name_match:
            name_match = re.search(r"name='([^']+)'", full)
        if not name_match:
            name_match = re.search(r'name=\{([^}]+)\}', full)
        
        if not name_match:
            return full
        
        name_val = name_match.group(1)
        
        # For static icon names, replace directly
        if name_val.startswith('"') or name_val.startswith("'"):
            icon_component = name_val.strip('"\'')
        else:
            # Dynamic name - needs special handling
            # e.g., name={ot.icon}, name={expanded.object ? "ChevronDown" : "ChevronRight"}
            return full  # Keep as-is for dynamic names?
        
        # Extract other attributes
        size_match = re.search(r'size=\{(\d+)\}', full)
        class_match = re.search(r'className="([^"]*)"', full)
        class_match2 = re.search(r"className='([^']*)'", full)
        
        attrs = []
        if size_match:
            attrs.append(f'size={{{size_match.group(1)}}}')
        if class_match:
            attrs.append(f'className="{class_match.group(1)}"')
        elif class_match2:
            attrs.append(f"className='{class_match2.group(1)}'")
        
        attrs_str = ' '.join(attrs)
        if attrs_str:
            return f'<{icon_component} {attrs_str} />'
        else:
            return f'<{icon_component} />'
    
    # Replace static LucideIcon usages
    for name in icon_names:
        # Skip dynamic names (they contain ? or variables)
        if '?' in name or '{' in name:
            continue
        # Replace <LucideIcon name="X" ... /> with <X ... />
        pattern = re.compile(
            r'<LucideIcon\s+name="' + re.escape(name) + r'"((?:\s+\w+=(?:\{[^}]+\}|"[^"]*"|\'[^\']*\'))*)\s*/>',
            re.DOTALL
        )
        content = pattern.sub(lambda m: replace_lucide_icon(m), content)
    
    # Step 4: Add lucide-react import with all icon names used
    static_names = sorted([n for n in icon_names if '?' not in n and '{' not in n])
    if static_names:
        import_line = "import { " + ", ".join(static_names) + " } from 'lucide-react';\n"
        # Insert after the first import line or after license comment
        # Find the first line after license comment block
        lines = content.split('\n')
        insert_idx = 0
        in_license = False
        for i, line in enumerate(lines):
            if line.strip().startswith('/**') or line.strip().startswith('*') or line.strip().startswith('*/'):
                continue
            if line.strip().startswith('import'):
                insert_idx = i
                break
            if line.strip() == '' and i > 3:
                insert_idx = i
                break
        
        # Check if we already have a lucide-react import
        if "from 'lucide-react'" not in content and 'from "lucide-react"' not in content:
            # Insert before first existing import
            insert_point = 0
            for i, line in enumerate(lines):
                if line.strip().startswith('import React'):
                    insert_point = i + 1
                    break
            if insert_point == 0:
                for i, line in enumerate(lines):
                    if line.strip().startswith('import'):
                        insert_point = i
                        break
            lines.insert(insert_point, import_line)
            content = '\n'.join(lines)
    
    # Step 5: Fix types import path
    if types_import_path:
        content = re.sub(
            r"from\s+['\"]\.\./types['\"]",
            f"from '{types_import_path}'",
            content
        )
        content = re.sub(
            r"from\s+['\"]\./types['\"]",
            f"from '{types_import_path}'",
            content
        )
    
    # Step 6: Fix internal component imports
    if relative_paths_map:
        for old_path, new_path in relative_paths_map.items():
            content = content.replace(old_path, new_path)
    
    return content

# Define transformations for each file
transforms = [
    {
        'src': '/tmp/ceos_OntologyGraph.tsx',
        'dst': '/home/guorongxiao/c2eos/src/components/OntologyGraph.tsx',
        'types_import': '../types/ontology',
        'path_map': {},
    },
    {
        'src': '/tmp/ceos_Sidebar.tsx',
        'dst': '/home/guorongxiao/c2eos/src/components/ontology/Sidebar.tsx',
        'types_import': '../../types/ontology',
        'path_map': {},
    },
    {
        'src': '/tmp/ceos_OverviewView.tsx',
        'dst': '/home/guorongxiao/c2eos/src/pages/ontology/OverviewView.tsx',
        'types_import': '../../types/ontology',
        'path_map': {
            "from './OntologyGraph'": "from '../../components/OntologyGraph'",
            "from './LucideIcon'": "from 'lucide-react'",
        },
    },
    {
        'src': '/tmp/ceos_ObjectTypeView.tsx',
        'dst': '/home/guorongxiao/c2eos/src/pages/ontology/ObjectTypeDetail.tsx',
        'types_import': '../../types/ontology',
        'path_map': {},
    },
    {
        'src': '/tmp/ceos_LinkTypeView.tsx',
        'dst': '/home/guorongxiao/c2eos/src/pages/ontology/LinkTypeDetail.tsx',
        'types_import': '../../types/ontology',
        'path_map': {},
    },
    {
        'src': '/tmp/ceos_ActionTypeView.tsx',
        'dst': '/home/guorongxiao/c2eos/src/pages/ontology/ActionTypeDetail.tsx',
        'types_import': '../../types/ontology',
        'path_map': {},
    },
    {
        'src': '/tmp/ceos_FunctionTypeView.tsx',
        'dst': '/home/guorongxiao/c2eos/src/pages/ontology/FunctionTypeDetail.tsx',
        'types_import': '../../types/ontology',
        'path_map': {},
    },
    {
        'src': '/tmp/ceos_ObjectExplorerView.tsx',
        'dst': '/home/guorongxiao/c2eos/src/pages/ObjectExplorerView.tsx',
        'types_import': '../types/ontology',
        'path_map': {},
    },
    {
        'src': '/tmp/ceos_OtherViews.tsx',
        'dst': '/home/guorongxiao/c2eos/src/pages/ontology/OtherViews.tsx',
        'types_import': '../../types/ontology',
        'path_map': {},
    },
    {
        'src': '/tmp/ceos_mockData.ts',
        'dst': '/home/guorongxiao/c2eos/src/data/ontologyMockData.ts',
        'types_import': '../types/ontology',
        'path_map': {},
    },
]

for t in transforms:
    print(f"Processing: {t['src']} → {t['dst']}")
    content = transform_file(t['src'], t['types_import'], t['path_map'])
    os.makedirs(os.path.dirname(t['dst']), exist_ok=True)
    with open(t['dst'], 'w') as f:
        f.write(content)
    print(f"  Done: {len(content)} bytes")

print("\n=== All files transformed ===")
PYEOF

echo "=== Done ==="
