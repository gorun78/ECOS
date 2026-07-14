#!/usr/bin/env python3
"""
ECOS E2E Test Runner — Python + Playwright (Async)
Usage: python3 run_e2e.py [--headed] [--suite navigation|data|crud|all]
"""
import asyncio, sys, json, os
from datetime import datetime
from pathlib import Path

# Use Hermes venv's playwright
REPO = Path("/mnt/d/workspace/ECOS/04-测试设计/e2e")
REPORT_DIR = REPO / "reports"
SCREENSHOT_DIR = REPORT_DIR / "screenshots"
os.makedirs(SCREENSHOT_DIR, exist_ok=True)

FRONTEND = "http://localhost:5173"
BACKEND = "http://localhost:8081/sys-man/api/v1/gsxk"

# ── Test Results Collector ──────────────────────────────
results = {"suites": {}, "total": 0, "passed": 0, "failed": 0, "errors": []}

def record(suite, name, passed, detail=""):
    results["total"] += 1
    if passed:
        results["passed"] += 1
    else:
        results["failed"] += 1
    results["suites"].setdefault(suite, []).append({
        "name": name, "status": "PASS" if passed else "FAIL", "detail": detail
    })

# ── Suite 1: Auth ───────────────────────────────────────
async def suite_auth(browser):
    print("\n" + "="*60)
    print("  SUITE 1: Authentication")
    print("="*60)
    context = await browser.new_context()
    page = await context.new_page()
    
    try:
        await page.goto(f"{FRONTEND}/", timeout=15000, wait_until="networkidle")
        await page.wait_for_timeout(2000)
        
        # Find login form
        username = page.locator('input[type="text"]').first()
        password = page.locator('input[type="password"]').first()
        
        if await username.is_visible():
            await username.fill("admin")
            await password.fill("admin123")
            await page.click('button[type="submit"], button:has-text("登录")')
            await page.wait_for_timeout(3000)
        
        url = page.url
        logged_in = "mission_control" in url or "biz_dashboard" in url
        record("auth", "Login flow", logged_in, f"URL: {url}")
        print(f"  {'✅' if logged_in else '❌'} Login {'success' if logged_in else 'failed'} → {url}")
        
        # Save auth state
        await context.storage_state(path=str(REPO / ".auth" / "user.json"))
        
        if logged_in:
            await page.screenshot(path=str(SCREENSHOT_DIR / "01_login_success.png"))
    except Exception as e:
        record("auth", "Login flow", False, str(e))
        print(f"  ❌ Login error: {e}")
    finally:
        await context.close()

# ── Suite 2: Navigation Smoke ──────────────────────────
PAGES = [
    ("Mission Control", "/#/mission_control"),
    ("Biz Dashboard", "/#/biz_dashboard"),
    ("Data Catalog", "/#/data_catalog"),
    ("Data Explorer", "/#/data_explorer"),
    ("Data Lineage", "/#/data_lineage"),
    ("Object Runtime", "/#/object_runtime"),
    ("Data Quality", "/#/data_quality"),
    ("Ontology Explorer", "/#/ontology_explorer"),
    ("Ontology Designer", "/#/ontology_designer"),
    ("Glossary", "/#/glossary"),
    ("Workflow Studio", "/#/workflow_studio"),
    ("World Model", "/#/world_model"),
    ("Pipeline Builder", "/#/pipeline_builder"),
    ("Code Workbook", "/#/code_workbook"),
    ("Agent Studio", "/#/agent_studio"),
    ("Agent Mesh", "/#/agent_mesh"),
    ("Cognitive OS", "/#/cognitive_os"),
    ("Security Audit", "/#/security_audit"),
    ("IAM Users", "/#/iam_users"),
    ("Project Tracker", "/#/project_tracker"),
    ("Contract Manager", "/#/contract_manager"),
    ("Ops Dashboard", "/#/ops_dashboard"),
    ("Dataset Explorer", "/#/dataset_explorer/test"),
    ("Market", "/#/market"),
]

async def suite_navigation(browser):
    print("\n" + "="*60)
    print("  SUITE 2: Navigation Smoke (25 pages)")
    print("="*60)
    
    context = await browser.new_context()
    # Try to load saved auth state
    auth_file = REPO / ".auth" / "user.json"
    if auth_file.exists():
        context = await browser.new_context(storage_state=str(auth_file))
    else:
        context = await browser.new_context()
    
    passed = 0
    for name, hash_route in PAGES:
        page = await context.new_page()
        try:
            await page.goto(f"{FRONTEND}/{hash_route}", timeout=15000, wait_until="domcontentloaded")
            await page.wait_for_timeout(2000)
            
            # Check content
            body_text = await page.text_content("body")
            has_content = body_text and len(body_text.strip()) > 50
            
            # Check for error banners
            error_visible = False
            try:
                error_el = page.locator('[class*="error"], [class*="Error"], .bg-red-500')
                error_visible = await error_el.is_visible()
            except:
                pass
            
            ok = has_content and not error_visible
            record("navigation", name, ok, 
                   f"content={len(body_text or '')} chars, error={error_visible}")
            
            icon = "✅" if ok else ("⚠️" if has_content else "❌")
            print(f"  {icon} {name:25s} | {len(body_text or ''):4d} chars | error={error_visible}")
            
            if ok:
                passed += 1
                await page.screenshot(path=str(SCREENSHOT_DIR / f"nav_{name.replace(' ','_')}.png"))
        except Exception as e:
            record("navigation", name, False, str(e)[:100])
            print(f"  ❌ {name:25s} | {str(e)[:60]}")
        finally:
            await page.close()
    
    await context.close()
    print(f"\n  Navigation: {passed}/{len(PAGES)} passed")
    return passed

# ── Suite 3: API Data Loading ──────────────────────────
async def suite_api(request_context):
    print("\n" + "="*60)
    print("  SUITE 3: Backend API Verification")
    print("="*60)
    
    endpoints = [
        ("GET", f"{BACKEND}/datasets"),
        ("GET", f"{BACKEND}/ontology/entities"),
        ("GET", f"{BACKEND}/worldmodel/goals"),
        ("GET", f"{BACKEND}/pipelines"),
        ("GET", f"{BACKEND}/worldmodel/scenarios"),
    ]
    
    for method, url in endpoints:
        try:
            resp = await request_context.fetch(url, method=method)
            status = resp.status
            ok = status == 200
            name = url.split("/")[-1]
            record("api", name, ok, f"HTTP {status}")
            icon = "✅" if ok else "⚠️" if status != 404 else "❌"
            print(f"  {icon} {method} {url.split('/gsxk/')[-1]:40s} → HTTP {status}")
        except Exception as e:
            record("api", url.split("/")[-1], False, str(e)[:100])
            print(f"  ❌ {method} {url.split('/gsxk/')[-1]:40s} → {str(e)[:50]}")

# ── Suite 4: CRUD ──────────────────────────────────────
async def suite_crud(request_context):
    print("\n" + "="*60)
    print("  SUITE 4: Ontology Entity CRUD")
    print("="*60)
    
    entity_name = f"E2E_Test_{datetime.now().strftime('%H%M%S')}"
    
    # CREATE
    try:
        resp = await request_context.fetch(
            f"{BACKEND}/ontology/entities",
            method="POST",
            headers={"Content-Type": "application/json"},
            data=json.dumps({"name": entity_name, "description": "E2E test", "entityType": "CONCEPT"})
        )
        body = await resp.json()
        ok = resp.status == 200 and body.get("code") == 0
        record("crud", f"CREATE {entity_name}", ok, f"HTTP {resp.status}")
        print(f"  {'✅' if ok else '❌'} CREATE → HTTP {resp.status} | {str(body)[:80]}")
        
        # Extract ID
        entity_id = body.get("data", {}).get("id") or body.get("data", {}).get("entityId")
        if not entity_id:
            record("crud", "EXTRACT ID", False, "No ID in response")
            return
        
        # UPDATE
        resp2 = await request_context.fetch(
            f"{BACKEND}/ontology/entities/{entity_id}",
            method="PUT",
            headers={"Content-Type": "application/json"},
            data=json.dumps({"name": f"{entity_name}_UPDATED", "description": "Updated"})
        )
        ok2 = resp2.status in (200, 201)
        record("crud", f"UPDATE {entity_name}", ok2, f"HTTP {resp2.status}")
        print(f"  {'✅' if ok2 else '❌'} UPDATE → HTTP {resp2.status}")
        
        # DELETE
        resp3 = await request_context.fetch(
            f"{BACKEND}/ontology/entities/{entity_id}",
            method="DELETE"
        )
        ok3 = resp3.status in (200, 204)
        record("crud", f"DELETE {entity_name}", ok3, f"HTTP {resp3.status}")
        print(f"  {'✅' if ok3 else '❌'} DELETE → HTTP {resp3.status}")
    except Exception as e:
        record("crud", "CRUD Flow", False, str(e)[:100])
        print(f"  ❌ CRUD error: {e}")

# ── Main ────────────────────────────────────────────────
async def main():
    from playwright.async_api import async_playwright
    
    headed = "--headed" in sys.argv
    suite_filter = next((a.split("--suite=")[1] for a in sys.argv if a.startswith("--suite=")), "all")
    
    print("╔══════════════════════════════════════════════════╗")
    print("║        ECOS E2E Test Runner (Playwright)         ║")
    print(f"║        {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}                              ║")
    print("╚══════════════════════════════════════════════════╝")
    
    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=not headed)
        
        # Suite 1: Auth
        if suite_filter in ("all", "auth"):
            await suite_auth(browser)
        
        # Suite 2: Navigation (needs browser)
        if suite_filter in ("all", "navigation"):
            await suite_navigation(browser)
        
        await browser.close()
    
    # Suite 3+4: API (use request context via new browser)
    async with async_playwright() as p:
        browser = await p.chromium.launch(headless=True)
        context = await browser.new_context()
        request_ctx = context.request
        
        if suite_filter in ("all", "api"):
            await suite_api(request_ctx)
        
        if suite_filter in ("all", "crud"):
            await suite_crud(request_ctx)
        
        await browser.close()
    
    # ── Report ──────────────────────────────────────────
    print("\n" + "="*60)
    print("  TEST RESULTS")
    print("="*60)
    for suite, cases in results["suites"].items():
        passed = sum(1 for c in cases if c["status"] == "PASS")
        print(f"  {suite:15s}: {passed}/{len(cases)} passed")
    
    print(f"\n  TOTAL: {results['passed']}/{results['total']} passed")
    
    # Save JSON report
    report_path = REPORT_DIR / f"results_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
    with open(report_path, "w") as f:
        json.dump({
            "timestamp": datetime.now().isoformat(),
            "summary": {"total": results["total"], "passed": results["passed"], "failed": results["failed"]},
            "suites": results["suites"]
        }, f, indent=2, ensure_ascii=False)
    print(f"\n  📄 Report saved: {report_path}")
    
    # Return exit code
    return 0 if results["failed"] == 0 else 1

if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
