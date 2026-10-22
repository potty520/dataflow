# -*- coding: utf-8 -*-
"""把分卷手册合并成 docs/操作手册.md，并生成可打印的 HTML 与 PDF。

用法：
  python deploy/cmds/build_manual.py            # 合并 md + 生成 html
  （PDF 由 Playwright 打印 html 得到，见命令输出提示）
"""
import io
import os
import re

ROOT = r"C:\Users\Administrator\Documents\1\datagov"
DOCS = os.path.join(ROOT, "docs")
PARTS = ["_manual_part1.md", "_manual_part2.md", "_manual_part3.md"]
MD_OUT = os.path.join(DOCS, "操作手册.md")
HTML_OUT = os.path.join(DOCS, "_manual_print.html")


def merge():
    chunks = []
    for name in PARTS:
        p = os.path.join(DOCS, name)
        if os.path.exists(p):
            chunks.append(io.open(p, encoding="utf-8").read())
    text = "\n".join(chunks)
    io.open(MD_OUT, "w", encoding="utf-8", newline="\n").write(text)
    print("merged ->", MD_OUT, len(text), "chars")
    return text


# ---------------------------------------------------------------- 轻量 Markdown -> HTML
def inline(s):
    s = re.sub(r"!\[([^\]]*)\]\(([^)]+)\)", r'<img alt="\1" src="\2" />', s)
    s = re.sub(r"\[([^\]]+)\]\(([^)]+)\)", r'<a href="\2">\1</a>', s)
    s = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", s)
    s = re.sub(r"`([^`]+)`", r"<code>\1</code>", s)
    return s


def convert(md):
    lines = md.split("\n")
    out = []
    i = 0
    in_code = False
    code_lang = ""
    code_buf = []
    while i < len(lines):
        line = lines[i]
        # code fence
        if line.strip().startswith("```"):
            if not in_code:
                in_code = True
                code_lang = line.strip()[3:].strip()
                code_buf = []
            else:
                in_code = False
                body = "\n".join(code_buf)
                if body.strip():
                    out.append('<pre class="code"><code>%s</code></pre>' % body.replace("&", "&amp;").replace("<", "&lt;"))
                else:
                    out.append('<pre class="code"><code></code></pre>')
            i += 1
            continue
        if in_code:
            code_buf.append(line)
            i += 1
            continue

        stripped = line.strip()
        # headings
        m = re.match(r"^(#{1,6})\s+(.*)$", line)
        if m:
            level = len(m.group(1))
            text = inline(m.group(2))
            if level == 1:
                out.append('<h1 class="doc-title">%s</h1>' % text)
            else:
                out.append("<h%d>%s</h%d>" % (level, text, level))
            i += 1
            continue
        # hr
        if stripped in ("---", "***"):
            out.append('<hr />')
            i += 1
            continue
        # blockquote
        if stripped.startswith(">"):
            buf = []
            while i < len(lines) and lines[i].strip().startswith(">"):
                buf.append(inline(lines[i].strip()[1:].strip()))
                i += 1
            out.append('<blockquote>%s</blockquote>' % "<br/>".join(buf))
            continue
        # table
        if stripped.startswith("|") and i + 1 < len(lines) and re.match(r"^\s*\|[\s:|-]+\|\s*$", lines[i + 1]):
            header = [c.strip() for c in stripped.strip("|").split("|")]
            i += 2
            rows = []
            while i < len(lines) and lines[i].strip().startswith("|"):
                rows.append([c.strip() for c in lines[i].strip().strip("|").split("|")])
                i += 1
            html = ['<table><thead><tr>']
            for h in header:
                html.append("<th>%s</th>" % inline(h))
            html.append("</tr></thead><tbody>")
            for r in rows:
                html.append("<tr>" + "".join("<td>%s</td>" % inline(c) for c in r) + "</tr>")
            html.append("</tbody></table>")
            out.append("".join(html))
            continue
        # lists
        if re.match(r"^\s*[-*]\s+", line):
            items = []
            while i < len(lines) and re.match(r"^\s*[-*]\s+", lines[i]):
                items.append(inline(re.sub(r"^\s*[-*]\s+", "", lines[i])))
                i += 1
            out.append("<ul>%s</ul>" % "".join("<li>%s</li>" % x for x in items))
            continue
        if re.match(r"^\s*\d+\.\s+", line):
            items = []
            while i < len(lines) and re.match(r"^\s*\d+\.\s+", lines[i]):
                items.append(inline(re.sub(r"^\s*\d+\.\s+", "", lines[i])))
                i += 1
            out.append("<ol>%s</ol>" % "".join("<li>%s</li>" % x for x in items))
            continue
        # blank
        if stripped == "":
            i += 1
            continue
        # paragraph: collect until blank/special
        buf = []
        while i < len(lines) and lines[i].strip() != "" and not re.match(
                r"^(#{1,6}\s|```|\||>\s|\s*[-*]\s+|\s*\d+\.\s+|-)", lines[i]):
            buf.append(lines[i].strip())
            i += 1
        out.append("<p>%s</p>" % inline(" ".join(buf)))
    return "\n".join(out)


CSS = """
@page { size: A4; margin: 18mm 16mm 16mm 16mm; }
body { font-family: "Microsoft YaHei","PingFang SC","Noto Sans CJK SC",sans-serif; font-size: 10.5pt; line-height: 1.65; color: #1f2328; }
h1.doc-title { font-size: 26pt; text-align: center; margin: 60pt 0 8pt; color: #14548c; }
h2 { font-size: 16pt; margin: 22pt 0 8pt; padding-bottom: 4pt; border-bottom: 2px solid #2f6ea5; color: #14548c; page-break-before: always; }
h2:first-of-type { page-break-before: avoid; }
h3 { font-size: 13pt; margin: 16pt 0 6pt; color: #1d5f92; }
h4 { font-size: 11.5pt; margin: 12pt 0 4pt; color: #23506e; }
p { margin: 5pt 0; text-align: justify; }
ul, ol { margin: 4pt 0 8pt 18pt; padding: 0; }
li { margin: 2pt 0; }
table { border-collapse: collapse; width: 100%; margin: 8pt 0; font-size: 9pt; }
th, td { border: 1px solid #b8c4ce; padding: 4pt 6pt; text-align: left; vertical-align: top; }
th { background: #eaf1f8; color: #1b4b6b; }
tr { page-break-inside: avoid; }
img { max-width: 100%; height: auto; display: block; margin: 8pt auto; border: 1px solid #d4dbe2; border-radius: 3px; page-break-inside: avoid; }
pre.code { background: #f6f8fa; border: 1px solid #d8dee4; border-radius: 4px; padding: 8pt 10pt; font-size: 8.5pt; line-height: 1.45; white-space: pre-wrap; word-break: break-all; page-break-inside: avoid; }
code { background: #eef1f4; padding: 1px 3px; border-radius: 3px; font-size: 9pt; }
blockquote { margin: 8pt 0; padding: 6pt 10pt; background: #f2f7fb; border-left: 3px solid #5b8db8; color: #33414d; }
hr { border: none; border-top: 1px solid #d8dee4; margin: 14pt 0; }
a { color: #1d5f92; text-decoration: none; }
"""

HTML = """<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="utf-8" />
<title>DGP 数据治理平台 操作手册</title>
<style>%s</style></head><body>
%s
</body></html>
""" % (CSS, "")


def build_html(md):
    body = convert(md)
    html = HTML.replace("<body>\n\n</body>", "<body>\n" + body + "\n</body>")
    io.open(HTML_OUT, "w", encoding="utf-8", newline="\n").write(html)
    print("html ->", HTML_OUT, len(html), "chars")


if __name__ == "__main__":
    md = merge()
    build_html(md)
