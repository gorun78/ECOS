# PlantUML SVG Huffman 编码 ~1 前缀修复

## 问题现象

PlantUML SVG 文件在浏览器中显示错误文本：
"The plugin you are using seems to generated a bad URL. This URL does not look like DEFLATE data."

## 根因

PlantUML 1.2026.7beta11 使用 **Huffman 编码**（非 DEFLATE）。URL 中的编码数据前需要 `~1` 前缀作为编码标识。架构师 `arch-1785224485752` 的 plantuml 技能在转换 .puml → .svg 时可能漏掉了这个前缀，导致 plantuml.com 返回错误页面而非正确的 SVG。

## 修复脚本（Python）

```python
import zlib, base64

PLANTUML_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_"

def plantuml_encode(data):
    """DEFLATE compress and encode using PlantUML custom base64."""
    compressed = zlib.compress(data.encode('utf-8'))[2:-4]  # strip zlib header/footer
    b64 = base64.b64encode(compressed).decode('ascii')
    std = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    return b64.translate(str.maketrans(std, PLANTUML_ALPHABET))

with open("diagram.puml") as f:
    puml_text = f.read()

encoded = plantuml_encode(puml_text)
url = f"https://www.plantuml.com/plantuml/svg/{encoded}"  # Note: NO ~1 prefix for DEFLATE

import requests
r = requests.get(url, timeout=30)
with open("diagram.svg", "w") as f:
    f.write(r.text)
```

## 两种编码方式对比

| 编码方式 | URL格式 | 前缀 |
|---------|---------|------|
| DEFLATE (zlib) | `/svg/{deflate_encoded}` | 无 |
| Huffman | `/svg/~1{huffman_encoded}` | `~1` |

混用会出错：
- DEFLATE 数据加 `~1` → 渲染可能失败
- Huffman 数据不加 `~1` → "bad URL" 错误

## SVG 修复（已损坏文件的补丁）

```python
import re

with open("damaged.svg") as f:
    svg = f.read()

# 给所有无 ~1 的 plantuml URL 加上前缀
svg = re.sub(
    r'(www\.plantuml\.com/plantuml/(?:svg|png)/)((?!~1)[A-Za-z0-9+/=_-]+)',
    r'\1~1\2',
    svg
)

with open("damaged.svg", "w") as f:
    f.write(svg)
```

## 真实案例（2026-07-28）

架构师生成 7 张 PlantUML SVG 图表，全部为错误页面（"bad URL"）。根因：Huffman 编码数据未加 `~1` 前缀。修复：DEFLATE 重新编码所有 .puml 文件 → 7 张 SVG 全部重新生成。其中 frontend-router.puml 因含 `leaf` 等特殊元素导致 PlantUML 服务器返回广告页/CloudFlare 拦截，改为纯 `rectangle` 语法后通过。
