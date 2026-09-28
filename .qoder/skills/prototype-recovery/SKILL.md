---
name: prototype-recovery
version: 1.0.0
description: Recovers frontend implementations from prototypes with high fidelity. Invoke when implementing UI from prototypes, fixing UI bugs, or verifying prototype compliance.
category:
  - frontend-development
  - prototype-analysis
  - ui-restoration
---

# Prototype Recovery Skill

## Purpose

Restore frontend implementations that match product prototypes with high fidelity. This skill is based on lessons learned from actual prototype restoration projects, addressing common failure points that cause low first-pass success rates.

## Core Principles

Based on real project experience, these principles address the root causes of low first-pass success:

### Principle 1: Complete Prototype Analysis (35% of failures)
- Read ALL prototype HTML, CSS, and JS before implementation
- Never implement based on partial understanding
- Map every prototype element to implementation code

### Principle 2: Data Flow First (30% of failures)
- Analyze data flow before writing UI code
- Define component communication contracts upfront
- Ensure data consistency across all components

### Principle 3: Clear Component Boundaries (20% of failures)
- Separate presentation from data
- Use props for data input, emit for events
- Avoid hardcoded data in components

### Principle 4: Comprehensive Verification (15% of failures)
- Verify: Display → Interaction → Data flow
- Test complete user journeys
- Validate dynamic state changes

## Analysis Tools

### Python Scripts

**analyze_prototype.py** - Automated prototype analysis tool:

```bash
python scripts/analyze_prototype.py <prototype-file> <output-dir>
```

**Features**:
- Extracts all HTML elements with IDs and classes
- Parses inline CSS styles from `<style>` blocks
- Identifies JavaScript functions and event handlers
- Generates element mapping, CSS class mapping, and interaction flow documentation

**Output Files**:
- `element-mapping.json` - Elements with their selectors and properties
- `class-mapping.json` - CSS classes with their style rules
- `interaction-flow.md` - Event handlers and function documentation
- `analysis-summary.json` - Analysis statistics

**test-analysis.py** - Test suite for the analysis script:

```bash
python scripts/test-analysis.py
```

### Manual Analysis

If the automated tool doesn't capture everything, use these steps:

1. Read prototype HTML structure
2. Extract CSS styles and class names
3. Identify JS interactions and state transitions
4. Build element mapping table

**Output**:
- Element mapping table: `prototype-elements.json`
- CSS class mapping: `class-mapping.json`
- Interaction flow diagram: `interaction-flow.md`

**Must-verify**:
- ✅ All HTML elements identified
- ✅ All CSS classes documented
- ✅ All JS functions mapped

## Workflow

### Phase 0: Prototype Analysis (Mandatory)

**Goal**: Complete understanding of prototype structure

**Steps**:
1. Run automated analysis: `python scripts/analyze_prototype.py <prototype-file> <output-dir>`
2. Review generated mapping files
3. Perform manual verification for any gaps

### Phase 1: Data Flow Design

**Goal**: Define how data flows between components

**Steps**:
1. Identify data sources
2. Define component contracts (props/emit)
3. Map data transformation paths
4. Design state management strategy

**Output**:
- Data flow diagram: `data-flow.md`
- Component interface definitions: `component-contracts.md`

**Must-verify**:
- ✅ Data sources identified
- ✅ Props/emit contracts defined
- ✅ State updates propagate correctly

### Phase 2: Implementation

**Goal**: Implement with prototype-accurate code

**Steps**:
1. Create component structure matching prototype
2. Use exact class names from prototype
3. Apply precise CSS styles
4. Implement interactions

**Output**:
- Component files
- Integration code

**Must-verify**:
- ✅ Class names match prototype exactly
- ✅ Styles match prototype CSS
- ✅ Interactions match prototype behavior

### Phase 3: Verification

**Goal**: Validate implementation against prototype

**Steps**:
1. Display verification: Elements exist and styled correctly
2. Interaction verification: Events trigger correctly
3. Data verification: Data flows correctly
4. Edge case verification: Error handling works

**Output**:
- Verification report: `verification-report.md`

**Must-verify**:
- ✅ All elements visible
- ✅ All interactions functional
- ✅ Data updates correctly
- ✅ Edge cases handled

## Key Techniques

### Technique 1: Prototype Element Mapping

Create a mapping table for every element:

```json
{
  "step-flow-bar": {
    "prototypeSelector": ".step-flow-bar",
    "implementationFile": "ProcessEngine.vue",
    "cssClasses": ["step-flow-bar", "show"],
    "children": {
      "step-flow-right": {
        "prototypeSelector": ".step-flow-right",
        "content": "事项名称",
        "children": {
          "flowItemName": {
            "prototypeSelector": "#flowItemName",
            "binding": "currentProcess?.name"
          },
          "flowSwitchBtn": {
            "prototypeSelector": ".flow-switch-btn",
            "event": "click → handleSwitchMatter"
          }
        }
      }
    }
  }
}
```

### Technique 2: Data Flow Mapping

Document the complete data flow:

```
User Action → Component → State Change → Emit → Parent → Props Update → Render

Example:
1. HomePage: click hot item
2. emit('hotItemClick', name)
3. App: handleHotItemClick → confirmItem(name)
4. App: update currentProcessId
5. ProcessEngine: props.processId changes
6. ProcessEngine: computed currentProcess updates
7. ProcessEngine: re-renders with new name
```

### Technique 3: Component Contract Definition

Define interfaces before implementation:

```typescript
// Parent contract
interface ParentProps {
  processId: string
}

interface ParentEmits {
  'update:processId': [id: string]
  back: []
  complete: [data: ProcessData]
}

// Child contract (SwitchModal)
interface ChildProps {
  visible: boolean
  matters?: Matter[]
}

interface ChildEmits {
  close: []
  select: [matter: Matter]
}
```

## Common Failure Patterns and Fixes

### Failure Pattern 1: Class Name Mismatch

**Problem**: Implementation uses different class names than prototype

**Fix**: Copy class names exactly from prototype CSS

### Failure Pattern 2: Missing Data Flow

**Problem**: Component modifies local state but doesn't notify parent

**Fix**: Use emit('update:propName') pattern for state changes

### Failure Pattern 3: Hardcoded Data

**Problem**: Component has hardcoded data that doesn't match actual data sources

**Fix**: Pass data via props, use computed for derived data

### Failure Pattern 4: Incorrect Positioning

**Problem**: Fixed/sticky positioning not implemented correctly

**Fix**: Copy position CSS exactly from prototype

### Failure Pattern 5: Missing Event Handlers

**Problem**: Click handlers not wired to correct functions

**Fix**: Map prototype onclick to implementation methods

## Quality Gates

### Gate 1: Prototype Analysis Complete
- All elements mapped
- All styles documented
- All interactions identified

### Gate 2: Data Flow Validated
- Data sources identified
- Component contracts defined
- Flow verified

### Gate 3: Implementation Matches Prototype
- Class names exact match
- Styles exact match
- Interactions exact match

### Gate 4: End-to-End Verification
- Display: ✅
- Interaction: ✅
- Data: ✅
- Edge cases: ✅

## Usage Scenarios

1. **New Feature Implementation**: Use when implementing features from prototypes
2. **Bug Fix**: Use when fixing UI bugs that deviate from prototype
3. **Refactoring**: Use when refactoring code to match prototype
4. **Quality Assurance**: Use when verifying implementation fidelity

## References

Based on lessons learned from:
- Review analysis: `.trae/project/review.md`
- Prototype system: `docs/01需求分析/01-政务窗口助手/02-原型系统/`

## Notes

- Always read prototype files completely before implementation
- Always verify data flow before writing UI code
- Always test complete user journeys, not just individual elements
- Always use exact class names from prototype
