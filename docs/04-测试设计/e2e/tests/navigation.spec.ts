import { test, expect } from '@playwright/test';
import path from 'path';

const AUTH_FILE = path.join(__dirname, '..', '.auth', 'user.json');

test.use({ storageState: AUTH_FILE });

// All ECOS pages with their hash routes
const PAGES = [
  { name: 'Mission Control', hash: '#/mission_control' },
  { name: 'Biz Dashboard', hash: '#/biz_dashboard' },
  { name: 'Data Catalog', hash: '#/data_catalog' },
  { name: 'Data Explorer', hash: '#/data_explorer' },
  { name: 'Data Lineage', hash: '#/data_lineage' },
  { name: 'Object Runtime', hash: '#/object_runtime' },
  { name: 'Data Quality', hash: '#/data_quality' },
  { name: 'Ontology Explorer', hash: '#/ontology_explorer' },
  { name: 'Ontology Designer', hash: '#/ontology_designer' },
  { name: 'Glossary', hash: '#/glossary' },
  { name: 'Workflow Studio', hash: '#/workflow_studio' },
  { name: 'World Model', hash: '#/world_model' },
  { name: 'Pipeline Builder', hash: '#/pipeline_builder' },
  { name: 'Code Workbook', hash: '#/code_workbook' },
  { name: 'Agent Studio', hash: '#/agent_studio' },
  { name: 'Agent Mesh', hash: '#/agent_mesh' },
  { name: 'Cognitive OS', hash: '#/cognitive_os' },
  { name: 'Security Audit', hash: '#/security_audit' },
  { name: 'IAM Users', hash: '#/iam_users' },
  { name: 'Project Tracker', hash: '#/project_tracker' },
  { name: 'Contract Manager', hash: '#/contract_manager' },
  { name: 'Ops Dashboard', hash: '#/ops_dashboard' },
  { name: 'Operations Dashboard', hash: '#/operations_dashboard' },
  { name: 'Market', hash: '#/market' },
  { name: 'Dataset Explorer', hash: '#/dataset_explorer' },
];

for (const { name, hash } of PAGES) {
  test(`Page renders: ${name}`, async ({ page }) => {
    const route = hash.replace('#', '');
    await page.goto(`/#${route}`);
    await page.waitForTimeout(2000);
    
    // Check no error banner
    const errorBanner = page.locator('[class*="error"], [class*="Error"], .bg-red-500, .text-red-600');
    const hasError = await errorBanner.isVisible().catch(() => false);
    
    // Check page has content (not blank)
    const bodyText = await page.textContent('body');
    const hasContent = bodyText && bodyText.trim().length > 50;
    
    // Take debug screenshot
    await page.screenshot({ path: `reports/screenshots/${name.replace(/\s+/g, '_')}.png` });
    
    if (hasError) {
      console.warn(`⚠️  ${name}: Error banner visible`);
    }
    
    expect(hasContent, `${name}: Page should have visible content`).toBe(true);
  });
}
