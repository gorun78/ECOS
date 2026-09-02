#!/usr/bin/env python3
"""
Test cases for Prototype Recovery skill (Python Version)

Usage: python test-analysis.py
"""

import os
import sys
sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

from scripts.analyze_prototype import (
    PrototypeParser,
    extract_css,
    extract_javascript,
    generate_element_mapping,
    generate_class_mapping,
    generate_interaction_flow
)


def test_analyze_prototype():
    """Test: Analyze prototype extracts elements."""
    print('Test 1: Analyze prototype extracts elements')
    
    html = '''
    <div class="step-flow-bar">
      <div class="step-flow-right">
        <span id="flowItemName">Test Item</span>
        <span class="flow-switch-btn" onclick="showModal()">🔄</span>
      </div>
    </div>
    '''
    
    parser = PrototypeParser()
    parser.feed(html)
    
    # Check elements
    if len(parser.elements) >= 2:
        print('  ✅ Elements extracted')
    else:
        print(f'  ❌ Elements not extracted (got {len(parser.elements)})')
        return False
    
    # Check CSS classes
    if 'step-flow-bar' in parser.css_classes:
        print('  ✅ CSS classes extracted')
    else:
        print('  ❌ CSS classes not extracted')
        return False
    
    # Check interactions
    if len(parser.interactions) >= 1:
        print('  ✅ Interactions extracted')
    else:
        print('  ❌ Interactions not extracted')
        return False
    
    return True


def test_extract_css():
    """Test: Extract CSS styles."""
    print('Test 2: Extract CSS styles')
    
    html = '''
    <style>
      .step-flow-bar { padding: 10px; }
      .flow-switch-btn { cursor: pointer; }
    </style>
    <div class="step-flow-bar"></div>
    '''
    
    result = extract_css(html)
    
    if 'step-flow-bar' in result:
        print('  ✅ step-flow-bar styles extracted')
    else:
        print('  ❌ step-flow-bar styles not extracted')
        return False
    
    if 'flow-switch-btn' in result:
        print('  ✅ flow-switch-btn styles extracted')
    else:
        print('  ❌ flow-switch-btn styles not extracted')
        return False
    
    return True


def test_extract_javascript():
    """Test: Extract JavaScript functions."""
    print('Test 3: Extract JavaScript functions')
    
    html = '''
    <script>
      function showSwitchItemModal() {
        document.getElementById('switchModal').style.display = 'block';
      }
    </script>
    '''
    
    result = extract_javascript(html)
    
    func = next((f for f in result if f['name'] == 'showSwitchItemModal'), None)
    if func:
        print('  ✅ Function extracted')
        print(f'  ✅ Function name: {func["name"]}')
    else:
        print('  ❌ Function not extracted')
        return False
    
    return True


def test_real_prototype():
    """Test: Analyze real prototype."""
    print('Test 4: Analyze real prototype')
    
    prototype_path = os.path.join(
        os.path.dirname(os.path.dirname(__file__)),
        '../../../../../docs/01需求分析/01-政务窗口助手/02-原型系统/综合窗口智能收件助手/index.html'
    )
    
    if not os.path.exists(prototype_path):
        print('  ⚠️ Prototype file not found, skipping')
        return True
    
    with open(prototype_path, 'r', encoding='utf-8') as f:
        html = f.read()
    
    parser = PrototypeParser()
    parser.feed(html)
    
    print(f'  ✅ Found {len(parser.elements)} elements')
    print(f'  ✅ Found {len(parser.css_classes)} CSS classes')
    print(f'  ✅ Found {len(parser.interactions)} interactions')
    
    has_step_flow = 'step-flow-bar' in parser.css_classes
    has_flow_switch = 'flow-switch-btn' in parser.css_classes
    
    if has_step_flow:
        print('  ✅ step-flow-bar class found')
    else:
        print('  ❌ step-flow-bar class not found')
        return False
    
    if has_flow_switch:
        print('  ✅ flow-switch-btn class found')
    else:
        print('  ❌ flow-switch-btn class not found')
        return False
    
    return True


def test_generate_outputs():
    """Test: Generate output files."""
    print('Test 5: Generate output files')
    
    html = '''
    <div class="test-class" id="testId" onclick="handleClick()">Test</div>
    '''
    
    parser = PrototypeParser()
    parser.feed(html)
    
    # Test element mapping
    element_mapping = generate_element_mapping(parser)
    if 'elements' in element_mapping:
        print('  ✅ Element mapping generated')
    else:
        print('  ❌ Element mapping not generated')
        return False
    
    # Test class mapping
    css_styles = extract_css(html)
    class_mapping = generate_class_mapping(parser, css_styles)
    if 'test-class' in class_mapping:
        print('  ✅ Class mapping generated')
    else:
        print('  ❌ Class mapping not generated')
        return False
    
    # Test interaction flow
    functions = extract_javascript(html)
    interaction_flow = generate_interaction_flow(parser, functions)
    if 'Event Handlers' in interaction_flow:
        print('  ✅ Interaction flow generated')
    else:
        print('  ❌ Interaction flow not generated')
        return False
    
    return True


def run_tests():
    """Run all tests."""
    print('\n=== Prototype Recovery Tests (Python) ===\n')
    
    tests = [
        test_analyze_prototype,
        test_extract_css,
        test_extract_javascript,
        test_real_prototype,
        test_generate_outputs
    ]
    
    passed = 0
    failed = 0
    
    for test in tests:
        try:
            result = test()
            if result:
                passed += 1
            else:
                failed += 1
        except Exception as e:
            print(f'  ❌ Test failed with error: {e}')
            failed += 1
        print('')
    
    print(f'=== Results: {passed} passed, {failed} failed ===')
    
    if failed > 0:
        sys.exit(1)


if __name__ == '__main__':
    run_tests()
