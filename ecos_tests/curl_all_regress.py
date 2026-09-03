#!/usr/bin/env python3
"""ECOS 591 端点回归 (Wave-5.2 T22 - 守裴 G4).
跑全后端 @RequestMapping/@*Mapping 扫描出的 ~591 端点, 0 5xx + 0 neterr 即 G4 GO.
"""
import json, os, re, ssl, sys, time
import urllib.request
import urllib.error

BASE = os.environ.get('BASE', 'http://localhost:8080')
LOGIN_USER = os.environ.get('LOGIN_USER', 'super_admin')
LOGIN_PASS = os.environ.get('LOGIN_PASS', 'SuperAdmin@2026')
BACKEND_ROOT = os.environ.get('BACKEND_ROOT', os.path.expanduser('~/ECOS/ecos_backend'))
ENDPOINTS_OUT = os.environ.get('ENDPOINTS_OUT', '/tmp/ecos_endpoints.tsv')
DETAIL_OUT = os.environ.get('DETAIL_OUT', '/tmp/curl_all_detail.tsv')
TIME_LIMIT = int(os.environ.get('CURL_TIMEOUT', '30'))

# Parse --sample
SAMPLE = 0
for i, a in enumerate(sys.argv[1:]):
    if a == '--sample' and i + 2 <= len(sys.argv):
        SAMPLE = int(sys.argv[i + 2])

# ── Step 0: sanity GW up ─────────────────────────────────────
try:
    r = urllib.request.urlopen(BASE + '/actuator/health', timeout=5)
    print(f'actuator/health: {r.status}')
except Exception as e:
    # 用 /api/v1/auth/me 兜底
    try:
        req = urllib.request.Request(BASE + '/api/v1/auth/me')
        r = urllib.request.urlopen(req, timeout=5)
        print(f'actuator/health: skipped, /me={r.status}')
    except Exception as e2:
        # 401 also OK means GW alive
        if '401' in str(e2) or '403' in str(e2):
            print('GW alive (some 4xx hit)')
        else:
            print(f'ERROR: GW down ({e2})')
            sys.exit(1)

# ── Step 1: login ─────────────────────────────────────────────
def login(u, p):
    body = json.dumps({'username': u, 'password': p}).encode()
    req = urllib.request.Request(BASE + '/api/v1/auth/login', data=body,
                                 headers={'Content-Type': 'application/json'})
    try:
        resp = urllib.request.urlopen(req, timeout=TIME_LIMIT)
        d = json.loads(resp.read())
        data = d.get('data') or {}
        return data.get('accessToken') or d.get('accessToken') or d.get('token') or None
    except urllib.error.HTTPError as e:
        try:
            d = json.loads(e.read())
            data = d.get('data') or {}
            return data.get('accessToken') or d.get('accessToken') or d.get('token') or None
        except Exception:
            return None
    except Exception:
        return None

token = login(LOGIN_USER, LOGIN_PASS)
USER_USED = LOGIN_USER
if not token:
    USER_USED = 'admin'
    token = login('admin', 'admin123')
    if not token:
        print('LOGIN-FAIL both attempts')
        sys.exit(1)
print(f'token acquired: {len(token)} chars as {USER_USED}')

# ── Step 2: scan endpoints ────────────────────────────────────
MAPPING_TAGS = [('GetMapping','GET'), ('PostMapping','POST'), ('PutMapping','PUT'),
                ('DeleteMapping','DELETE'), ('PatchMapping','PATCH')]
class_re = re.compile(r'@RequestMapping\(\s*(?:value\s*=\s*)?"([^"]+)"\s*\)')
class_arr_re = re.compile(r'@RequestMapping\(\s*(?:value\s*=\s*)?\{([^}]*)\}\s*\)')
lit_re = {t: re.compile(r'@%s\((?:[^()]|\([^()]*\))*?"([^"]+)"' % t) for t,_ in MAPPING_TAGS}

def norm(p):
    p = p.strip()
    if not p.startswith('/'): p = '/' + p
    return p.rstrip('/')

def sub_pv(p):
    return re.sub(r'\{[A-Za-z0-9_\-]+\}', 'x', p)

events = set()
skipped = 0
for dirpath, dirs, files in os.walk(BACKEND_ROOT):
    p_abs = dirpath.replace('\\', '/')
    if '/target/' in p_abs or '/node_modules/' in p_abs or '/src/test' in p_abs:
        continue
    for fn in files:
        if not fn.endswith('.java'):
            continue
        p = os.path.join(dirpath, fn)
        try:
            txt = open(p, encoding='utf-8', errors='replace').read()
        except Exception:
            continue
        class_paths = []
        m = class_re.search(txt)
        if m:
            class_paths = [m.group(1)]
        else:
            m = class_arr_re.search(txt)
            if m:
                class_paths = [s.strip().strip('"') for s in m.group(1).split(',')]
        if not class_paths:
            skipped += 1
            continue
        for tag, meth in MAPPING_TAGS:
            for mm in lit_re[tag].finditer(txt):
                lit = mm.group(1)
                sub = sub_pv(norm(lit))
                for cp in class_paths:
                    base = norm(cp)
                    full = (base + sub) if (sub and not base.endswith(sub) and sub != '/') else base
                    full = full if full.startswith('/') else '/' + full
                    events.add((meth, full))

print(f'scanned: {len(events)} unique endpoints (skipped {skipped} files w/o class-base)')
with open(ENDPOINTS_OUT, 'w') as f:
    for meth, path in sorted(events):
        f.write(f'{meth}\t{path}\n')

# ── Step 3: curl each ─────────────────────────────────────────
with open(ENDPOINTS_OUT) as f:
    lines = [l.rstrip('\n').split('\t') for l in f if l.strip()]

detail_path = DETAIL_OUT
with open(detail_path, 'w') as f:
    f.write('method\tpath\tcode\ttime_s\tbody_head\n')

tot = 0
counts = {'2xx': 0, '4xx': 0, '5xx': 0, 'auth': 0, '404': 0, 'other4': 0, 'neterr': 0}
fails5xx = []
fails_other4 = []
fails_net = []
fails_auth403 = []
fails_auth401 = []
slow_records = []
body_log = open('/tmp/curl_all_bodies.log', 'w')

for meth, path in lines:
    tot += 1
    if SAMPLE > 0 and tot > SAMPLE:
        break
    body = b'{}' if meth in ('POST', 'PUT', 'PATCH') else b''
    req = urllib.request.Request(BASE + path, data=(body if body else None),
                                 method=meth,
                                 headers={'Authorization': f'Bearer {token}',
                                          **({'Content-Type': 'application/json'} if body else {})})
    t0 = time.time()
    try:
        r = urllib.request.urlopen(req, timeout=TIME_LIMIT)
        code = r.status
        rbody = r.read(512)
    except urllib.error.HTTPError as e:
        code = e.code
        try:
            rbody = e.read(2048)
        except Exception:
            rbody = b''
    except Exception as e:
        code = 0
        rbody = f'EXC: {e.__class__.__name__}: {str(e)[:60] or ""}'.encode('utf-8', errors='replace')
    dt = time.time() - t0
    try:
        head_b = rbody.decode('utf-8', errors='replace')[:80].replace('\n', ' ').replace('\t', ' ')
    except Exception:
        head_b = head_b  # keep
    if not head_b and isinstance(rbody, bytes):
        head_b = rbody.decode('latin-1', errors='replace')[:80].replace('\n',' ')
    with open(detail_path, 'a') as f:
        f.write(f'{meth}\t{path}\t{code}\t{dt:.3f}\t{head_b[:80]}\n')
    body_log.write(f'\n--- {meth} {path} code={code} t={dt:.3f} ---\n{head_b[:500]}\n')
    if 200 <= code < 300:
        counts['2xx'] += 1; st = 'PASS'
    elif code == 401:
        counts['auth'] += 1; st = 'AUTH'
        fails_auth401.append((meth, path, code, dt, head_b))
    elif code == 403:
        counts['auth'] += 1; st = 'AUTH'
        fails_auth403.append((meth, path, code, dt, head_b))
    elif code == 404:
        counts['404'] += 1; st = 'MISS'
    elif 400 <= code < 500:
        counts['other4'] += 1; st = 'FAIL'
        fails_other4.append((meth, path, code, dt, head_b))
    elif 500 <= code < 600:
        counts['5xx'] += 1; st = '5XX'
        fails5xx.append((meth, path, code, dt, head_b))
    else:
        counts['neterr'] += 1; st = 'ERR'
        fails_net.append((meth, path, code, dt, head_b))
    slow_records.append((dt, meth, path, code, head_b[:60]))
    print(f'{st} {tot} {code} {meth} {path} {dt:.3f}s')
body_log.close()

# ── Step 4: stats ─────────────────────────────────────────────
total = tot
s2xx = counts['2xx']
s4xx = counts['auth'] + counts['404'] + counts['other4']
s5xx = counts['5xx']
s000 = counts['neterr']
print('===STATS===')
print(f'TOTAL={total} 2XX={s2xx} 4XX={s4xx} 5XX={s5xx} NETERR={s000}')
print(f'  4XX拆解: AUTH(401+403)={counts["auth"]} 404={counts["404"]} other4xx={counts["other4"]}')

print('===TOP20-SLOWEST===')
slow_records_sorted = sorted(slow_records, key=lambda r: -r[0])[:20]
for dt, meth, path, code, hb in slow_records_sorted:
    print(f'{dt:.3f}s  {code} {meth} {path} | {hb[:60]}')

print('===FAILS-5XX===')
for m, p, c, dt, hb in fails5xx:
    print(f'{c} {m} {p} {dt:.3f}s | {hb[:80]}')

print('===FAILS-4XX-OTHER===')
for m, p, c, dt, hb in fails_other4:
    print(f'{c} {m} {p} {dt:.3f}s | {hb[:80]}')

print('===FAILS-NET===')
for m, p, c, dt, hb in fails_net:
    print(f'{c} {m} {p} {dt:.3f}s | {hb[:80]}')

print('===AUTH-403-COUNT===')
print(f'403 total: {len(fails_auth403)}')
# 实体链接 P0 留多清单
elastic = [x for x in fails_auth403 if '/entity-link' in x[1].lower()]
print(f'403 with /entity-link: {len(elastic)}  (Wave-4.2 遗留 P0, PLAN 留多不 fix)')
for m, p, c, dt, hb in elastic:
    print(f'  {m} {p}')
print('===VERDICT===')
if s5xx == 0 and s000 == 0:
    print(f'G4=GO (0 5xx, 0 neterr; 2XX={s2xx}, {s4xx} 4xx 不阻断)')
else:
    print(f'G4=NO-GO ({s5xx} 5xx, {s000} neterr; 守铁律 5xx/NETERR 必须 0)')
