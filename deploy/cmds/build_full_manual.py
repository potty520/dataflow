# -*- coding: utf-8 -*-
"""构建《操作手册-全功能截图版》的打印 HTML 与 DOCX。

PDF 由 docshot/print_manual.js 使用 Playwright 打印 docs/_manual_full_print.html。
"""
import io
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
DOCS = os.path.join(ROOT, "docs")
MD_SRC = os.path.join(DOCS, "操作手册-全功能截图版.md")
HTML_OUT = os.path.join(DOCS, "_manual_full_print.html")
DOCX_OUT = os.path.join(DOCS, "操作手册-全功能截图版.docx")

sys.path.insert(0, HERE)
import build_manual  # noqa: E402
import build_manual_docx  # noqa: E402


def main():
    md = io.open(MD_SRC, encoding="utf-8").read()
    build_manual.HTML_OUT = HTML_OUT
    build_manual.build_html(md)
    build_manual_docx.MD = MD_SRC
    build_manual_docx.OUT = DOCX_OUT
    build_manual_docx.build()


if __name__ == "__main__":
    main()
