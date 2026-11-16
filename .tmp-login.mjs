const r = await fetch('http://localhost:8080/api/v1/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
  body: JSON.stringify({ username: 'admin', password: 'admin123' }),
});
const j = await r.json();
const t = j && j.data && (j.data.accessToken || j.data.token);
if (!t) { console.log('LOGIN_FAIL', r.status, JSON.stringify(j).slice(0,300)); process.exit(1); }
const fs = await import('node:fs');
fs.writeFileSync('/tmp/tok.txt', t);
console.log('LOGIN_OK', r.status, 'len=' + t.length);
