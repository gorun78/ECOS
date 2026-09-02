#!/usr/bin/env bash
set +e
echo "=== 02 transform — with data[] form (UT-5 00) ==="
curl -s -m 30 -X POST http://localhost:8080/api/v1/engine/data/transform/execute -H "Authorization: Bearer $(curl -s -m 5 -X POST http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}' | python3 -c 'import sys,json; print(json.load(sys.stdin)["data"]["accessToken"])')" -H "Content-Type: application/json" -d '{"input":{"data":[{"name":"  Zhang San  ","age":30}]},"chain":[{"type":"cleansing","params":{"op":"trim","column":"name"}}]}' | head -c 500
echo
echo "=== 02 full 6-step ==="
curl -s -m 60 -X POST http://localhost:8080/api/v1/engine/data/transform/execute -H "Authorization: Bearer $(curl -s -m 5 -X POST http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}' | python3 -c 'import sys,json; print(json.load(sys.stdin)["data"]["accessToken"])')" -H "Content-Type: application/json" -d '{"input":{"data":[{"emp":"  A  ","dept":"R&D","age":"30","salary":"10000"},{"emp":"  B  ","dept":"R&D","age":"40","salary":"11000"},{"emp":"  C  ","dept":"QA","age":"35","salary":"12000"}]},"chain":[{"type":"cleansing","params":{"op":"trim","column":"emp"}},{"type":"mapping","params":{"mapping":{"emp":"name"}}},{"type":"typeConversion","params":{"column":"age","target":"integer"}},{"type":"validation","params":{"column":"age","op":"gt","threshold":0}},{"type":"aggregation","params":{"groupBy":"dept","op":"avg","column":"salary"}}]}' | head -c 1000
echo
echo "=== cognitive /diagnose — no null check skill ==="
curl -s -m 60 -X POST http://localhost:8080/api/v1/cognitive/diagnose -H "Authorization: Bearer $(curl -s -m 5 -X POST http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}' | python3 -c 'import sys,json; print(json.load(sys.stdin)["data"]["accessToken"])')" -H "Content-Type: application/json" -d '{"metric":"revenue","deviation":-15,"domain":"finance","maxDepth":5}' | head -c 2000
echo
echo "DONE2"
