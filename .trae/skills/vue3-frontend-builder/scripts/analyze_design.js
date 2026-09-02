/**
 * analyze_design.js - Design Analysis Script
 *
 * Analyze design mockups to extract UI elements, styles, and components.
 * This script generates the design_scan_registry.json for component abstraction.
 *
 * Usage: node analyze_design.js <mockup_dir> [output_dir]
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

/**
 * Analyze a single design file
 * @param {string} filePath - Path to the design image
 * @returns {Object} Detected elements
 */
function analyzeDesignFile(filePath) {
  const fileName = path.basename(filePath, path.extname(filePath));

  // This is a template - integrate with actual vision analysis tool
  // In production, call: vision_analyze(filePath)
  return {
    file: filePath,
    fileName: fileName,
    sha256: computeSHA256(filePath),
    page_name: inferPageName(fileName),
    analyzed_at: new Date().toISOString(),
    elements: [],
    element_count: 0,
    note: 'Replace with actual vision_analyze call in production'
  };
}

/**
 * Infer page name from filename
 * @param {string} fileName
 * @returns {string}
 */
function inferPageName(fileName) {
  const nameMap = {
    'dashboard': 'Dashboard',
    'home': 'Home',
    'login': 'Login',
    'profile': 'Profile',
    'settings': 'Settings',
    'list': 'List',
    'detail': 'Detail',
    'form': 'Form'
  };

  const lower = fileName.toLowerCase();
  for (const [key, value] of Object.entries(nameMap)) {
    if (lower.includes(key)) return value;
  }
  return fileName.split(/[-_]/).map(capitalize).join(' ');
}

/**
 * Compute SHA-256 hash of file
 * @param {string} filePath
 * @returns {string}
 */
function computeSHA256(filePath) {
  try {
    const buffer = fs.readFileSync(filePath);
    // Note: In production, use crypto.createHash('sha256')
    return buffer.slice(0, 8).toString('hex') + '...';
  } catch {
    return 'unknown';
  }
}

/**
 * Capitalize first letter
 */
function capitalize(str) {
  return str.charAt(0).toUpperCase() + str.slice(1);
}

/**
 * Main analysis function
 */
function main() {
  const args = process.argv.slice(2);

  if (args.length < 1) {
    console.error('Usage: node analyze_design.js <mockup_dir> [output_dir]');
    console.error('Example: node analyze_design.js ./mockups ./outputs');
    process.exit(1);
  }

  const mockupDir = args[0];
  const outputDir = args[1] || path.join(__dirname, '../outputs/abstraction');

  // Validate input directory
  if (!fs.existsSync(mockupDir)) {
    console.error(`Error: Directory not found: ${mockupDir}`);
    process.exit(1);
  }

  // Create output directory
  if (!fs.existsSync(outputDir)) {
    fs.mkdirSync(outputDir, { recursive: true });
  }

  // Find all image files
  const imageExtensions = ['.png', '.jpg', '.jpeg', '.webp', '.svg'];
  const files = fs.readdirSync(mockupDir)
    .filter(f => imageExtensions.includes(path.extname(f).toLowerCase()))
    .map(f => path.join(mockupDir, f));

  if (files.length === 0) {
    console.log('No design files found in directory.');
    return;
  }

  console.log(`Analyzing ${files.length} design file(s)...`);

  // Analyze each file
  const designs = files.map(filePath => {
    console.log(`  Analyzing: ${path.basename(filePath)}`);
    return analyzeDesignFile(filePath);
  });

  // Generate registry
  const registry = {
    version: '1.0.0',
    generated_at: new Date().toISOString(),
    total_designs: designs.length,
    designs: designs,
    element_index: []
  };

  // Write output
  const outputFile = path.join(outputDir, 'design_scan_registry.json');
  fs.writeFileSync(outputFile, JSON.stringify(registry, null, 2));

  console.log(`\nAnalysis complete!`);
  console.log(`  Output: ${outputFile}`);
  console.log(`  Total designs: ${registry.total_designs}`);
  console.log(`  Total elements: ${registry.designs.reduce((sum, d) => sum + d.element_count, 0)}`);
  console.log(`\nNext steps:`);
  console.log(`  1. Review ${outputFile}`);
  console.log(`  2. Run vision_analyze on each design to populate elements`);
  console.log(`  3. Execute phase 2 component abstraction`);
}

main();