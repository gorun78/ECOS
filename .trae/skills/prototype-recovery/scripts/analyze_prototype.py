#!/usr/bin/env python3
"""
Prototype Analysis Script (Python Version)

Automatically analyzes HTML prototype files and generates:
1. Element mapping table
2. CSS class mapping
3. Interaction flow documentation

Usage:
python analyze_prototype.py <prototype-file> <output-dir>
"""

import os
import sys
import json
import re
from datetime import datetime
from html.parser import HTMLParser
from pathlib import Path


class PrototypeParser(HTMLParser):
    """Parse HTML prototype and extract elements, classes, and interactions."""
    
    def __init__(self):
        super().__init__()
        self.elements = []
        self.css_classes = set()
        self.interactions = []
        self.current_element = None
        self.elements_stack = []
    
    def handle_starttag(self, tag, attrs):
        """Handle start tag."""
        attrs_dict = dict(attrs)
        element_data = {
            'tag': tag.lower(),
            'id': attrs_dict.get('id', None),
            'classes': [],
            'textContent': '',
            'attributes': [],
            'children': [],
            'events': []
        }
        
        # Parse classes
        if 'class' in attrs_dict:
            classes = attrs_dict['class'].split()
            element_data['classes'] = [c for c in classes if c]
            self.css_classes.update(element_data['classes'])
        
        # Parse events and other attributes
        for attr_name, attr_value in attrs_dict.items():
            if attr_name.startswith('on'):
                element_data['events'].append({
                    'type': attr_name[2:],
                    'handler': attr_value
                })
            elif attr_name not in ('class', 'id'):
                element_data['attributes'].append({
                    'name': attr_name,
                    'value': attr_value
                })
        
        # Add to parent's children
        if self.elements_stack:
            parent = self.elements_stack[-1]
            parent['children'].append({
                'tag': tag.lower(),
                'id': element_data['id'],
                'classes': element_data['classes']
            })
        
        self.elements_stack.append(element_data)
        self.current_element = element_data
    
    def handle_endtag(self, tag):
        """Handle end tag."""
        if self.elements_stack:
            element = self.elements_stack.pop()
            self.elements.append(element)
            # Store unique interactions
            for event in element['events']:
                interaction_key = f"{element['id'] or ''}_{event['type']}"
                if not any(i['elementId'] == element['id'] and i['eventType'] == event['type'] for i in self.interactions):
                    self.interactions.append({
                        'elementId': element['id'],
                        'elementClasses': element['classes'],
                        'eventType': event['type'],
                        'handler': event['handler'],
                        'elementText': element['textContent'][:100]
                    })
    
    def handle_data(self, data):
        """Handle text content."""
        if self.current_element:
            self.current_element['textContent'] += data.strip()


def extract_css(html_content):
    """Extract CSS styles from HTML content."""
    styles = {}
    
    # Find all style blocks
    style_blocks = re.findall(r'<style[^>]*>(.*?)</style>', html_content, re.DOTALL)
    
    for block in style_blocks:
        # Extract class selectors and their rules
        class_rules = re.findall(r'\.([a-zA-Z][a-zA-Z0-9_-]*)\s*\{([^}]*)\}', block)
        
        for class_name, rules_text in class_rules:
            rules = [r.strip() for r in rules_text.split(';') if r.strip()]
            
            if class_name not in styles:
                styles[class_name] = []
            
            for rule in rules:
                if ':' in rule:
                    prop, value = rule.split(':', 1)
                    styles[class_name].append({
                        'property': prop.strip(),
                        'value': value.strip()
                    })
    
    return styles


def extract_javascript(html_content):
    """Extract JavaScript functions from HTML content."""
    functions = []
    
    # Find all script blocks
    script_blocks = re.findall(r'<script[^>]*>(.*?)</script>', html_content, re.DOTALL)
    
    for block in script_blocks:
        # Extract function definitions
        func_defs = re.findall(r'function\s+([a-zA-Z][a-zA-Z0-9_-]*)\s*\(([^)]*)\)\s*\{([^}]*)\}', block)
        
        for func_name, params, body in func_defs:
            functions.append({
                'name': func_name,
                'parameters': [p.strip() for p in params.split(',') if p.strip()],
                'body': body.strip()[:200],
                'type': 'function'
            })
        
        # Extract arrow functions
        arrow_funcs = re.findall(r'const\s+([a-zA-Z][a-zA-Z0-9_-]*)\s*=\s*([^(]+)\s*=>\s*\{([^}]*)\}', block)
        
        for func_name, params, body in arrow_funcs:
            functions.append({
                'name': func_name,
                'parameters': params.strip(),
                'body': body.strip()[:200],
                'type': 'arrow'
            })
    
    return functions


def generate_element_mapping(parser):
    """Generate element mapping JSON."""
    mapping = {
        'prototypePath': '',
        'implementationPath': '',
        'elements': []
    }
    
    for el in parser.elements:
        mapping['elements'].append({
            'name': el['id'] or el['tag'],
            'prototypeSelector': f"#{el['id']}" if el['id'] else f".{el['classes'][0]}" if el['classes'] else el['tag'],
            'cssClasses': el['classes'],
            'textContent': el['textContent'][:100],
            'events': el['events'],
            'children': el['children']
        })
    
    return mapping


def generate_class_mapping(parser, css_styles):
    """Generate CSS class mapping JSON."""
    mapping = {}
    
    for cls in parser.css_classes:
        mapping[cls] = {
            'prototypeRules': css_styles.get(cls, []),
            'implementationClass': cls,
            'priority': 'high'
        }
    
    return mapping


def generate_interaction_flow(parser, functions):
    """Generate interaction flow markdown."""
    md = '# Interaction Flow\n\n'
    
    md += '## Event Handlers\n\n'
    md += '| Element | Event | Handler |\n'
    md += '|---------|-------|----------|\n'
    
    for interaction in parser.interactions:
        element_identifier = interaction['elementId'] or ', '.join(interaction['elementClasses'])
        md += f'| {element_identifier} | {interaction["eventType"]} | {interaction["handler"]} |\n'
    
    md += '\n## Functions\n\n'
    md += '| Name | Parameters | Body Preview |\n'
    md += '|------|------------|--------------|\n'
    
    for func in functions:
        params = ', '.join(func['parameters']) if isinstance(func['parameters'], list) else func['parameters']
        md += f'| {func["name"]} | {params} | {func["body"]} |\n'
    
    return md


def main():
    """Main function."""
    if len(sys.argv) < 3:
        print('Usage: python analyze_prototype.py <prototype-file> <output-dir>')
        sys.exit(1)
    
    prototype_file = sys.argv[1]
    output_dir = sys.argv[2]
    
    # Read prototype file
    try:
        with open(prototype_file, 'r', encoding='utf-8') as f:
            html_content = f.read()
    except FileNotFoundError:
        print(f'Error: Prototype file not found - {prototype_file}')
        sys.exit(1)
    except Exception as e:
        print(f'Error reading prototype file: {e}')
        sys.exit(1)
    
    # Create output directory
    try:
        os.makedirs(output_dir, exist_ok=True)
    except Exception as e:
        print(f'Error creating output directory: {e}')
        sys.exit(1)
    
    # Analyze prototype
    print('Analyzing prototype...')
    parser = PrototypeParser()
    parser.feed(html_content)
    
    print(f'Found {len(parser.elements)} elements, {len(parser.css_classes)} CSS classes, {len(parser.interactions)} interactions')
    
    # Extract CSS
    print('Extracting CSS styles...')
    css_styles = extract_css(html_content)
    print(f'Found {len(css_styles)} CSS class definitions')
    
    # Extract JavaScript
    print('Extracting JavaScript functions...')
    functions = extract_javascript(html_content)
    print(f'Found {len(functions)} functions')
    
    # Generate output files
    print('Generating output files...')
    
    # Element mapping
    element_mapping = generate_element_mapping(parser)
    with open(os.path.join(output_dir, 'element-mapping.json'), 'w', encoding='utf-8') as f:
        json.dump(element_mapping, f, indent=2, ensure_ascii=False)
    
    # CSS class mapping
    class_mapping = generate_class_mapping(parser, css_styles)
    with open(os.path.join(output_dir, 'class-mapping.json'), 'w', encoding='utf-8') as f:
        json.dump(class_mapping, f, indent=2, ensure_ascii=False)
    
    # Interaction flow
    interaction_flow = generate_interaction_flow(parser, functions)
    with open(os.path.join(output_dir, 'interaction-flow.md'), 'w', encoding='utf-8') as f:
        f.write(interaction_flow)
    
    # Summary
    summary = {
        'prototypeFile': prototype_file,
        'analysisTime': datetime.now().isoformat(),
        'elementCount': len(parser.elements),
        'cssClassCount': len(parser.css_classes),
        'interactionCount': len(parser.interactions),
        'functionCount': len(functions),
        'outputFiles': [
            'element-mapping.json',
            'class-mapping.json',
            'interaction-flow.md'
        ]
    }
    
    with open(os.path.join(output_dir, 'analysis-summary.json'), 'w', encoding='utf-8') as f:
        json.dump(summary, f, indent=2, ensure_ascii=False)
    
    print('\nAnalysis complete!')
    print(f'Output directory: {output_dir}')
    print(f'Files generated: {", ".join(summary["outputFiles"])}')


if __name__ == '__main__':
    main()
