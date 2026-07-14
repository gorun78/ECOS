#!/usr/bin/env python3
"""ECOS API 契约测试 — 防线2 (v2: rate-limit aware)"""
import subprocess, json, sys, time

BASE = "http://localhost:8080"
CONTRACTS = "/home/guorongxiao/ECOS/05-质量保障/api-contracts.txt"

# Login
r = subprocess.run([
    "curl", "-s", "-X", "POST", f"{BASE}/api/v1/auth/login",
    "-H", "Content-Type: application/json",
    "-d", '{"username":"admin","password":"admin123"}'
], capture_output=True, text=True, timeout=10)
data = json.loads(r.stdout)
token = data["data"]["access_token"] if "access_token" in data["data"] else data["data"].get("accessToken", data.get("token", ""))
auth_hdr = "Authorization: Bearer" + token

# Read contracts
with open(CONTRACTS) as f:
    lines = f.readlines()

# Known concrete IDs for parameterized paths (from existing data)
test_ids = {
    "/api/v1/system/tenants/{id}": "default",
    "/api/v1/ecos/objects/project/{id}": "",
}

passed = 0
failed = 0
skipped = 0

for line in lines:
    line = line.strip()
    if not line or line.startswith("#"):
        continue
    parts = line.split("|")
    if len(parts) < 3:
        continue
    method, path, expected = parts[0], parts[1], parts[2]
    
    # Skip auth login (handled separately)
    if path == "/api/v1/auth/login":
        skipped += 1
        continue
    
    # Resolve parameterized paths
    if "{id}" in path:
        if path not in test_ids or not test_ids[path]:
            skipped += 1
            continue
        path = path.replace("{id}", test_ids[path])
    
    # Add delay between requests to avoid rate limiting
    time.sleep(0.3)
    
    try:
        r = subprocess.run([
            "curl", "-s", "-o", "/dev/null", "-w", "%{http_code}",
            "-X", method, f"{BASE}{path}",
            "-H", auth_hdr,
            "-H", "Content-Type: application/json"
        ], capture_output=True, text=True, timeout=10)
        http_code = r.stdout.strip()
        
        if http_code == "429":
            # Rate limited — wait and retry once
            time.sleep(2)
            r = subprocess.run([
                "curl", "-s", "-o", "/dev/null", "-w", "%{http_code}",
                "-X", method, f"{BASE}{path}",
                "-H", auth_hdr,
                "-H", "Content-Type: application/json"
            ], capture_output=True, text=True, timeout=10)
            http_code = r.stdout.strip()
    except Exception as e:
        http_code = str(e)
    
    if http_code == expected:
        print(f"✅ {method} {path} → {http_code}")
        passed += 1
    else:
        print(f"❌ {method} {path} → {http_code} (expected {expected})")
        failed += 1

print("================================")
print(f"结果: {passed} PASS, {failed} FAIL, {skipped} SKIPPED")
sys.exit(1 if failed > 0 else 0)
