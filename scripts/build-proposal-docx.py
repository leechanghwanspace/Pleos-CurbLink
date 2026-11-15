"""Build the contest proposal DOCX from docs/기획서_CurbLink_Drive.md (standard library only).

Supports the markdown used in that file: #/##/### headings, paragraphs, - bullets, 1. numbered items,
> quotes, | tables |, ---, **bold**, `code` and [text](link).

Usage: python scripts/build-proposal-docx.py [output.docx]
"""
import re
import sys
import zipfile
from datetime import datetime, timezone
from pathlib import Path
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'docs/기획서_CurbLink_Drive.md'
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / 'submission/기획서_CurbLink_Drive.docx'
FONT = '맑은 고딕'
TEAL, INK, MUTED, MINT, LINE = '007F73', '152E35', '536970', 'E2F4ED', 'C9D6D5'
PAGE_W, PAGE_H, MARGIN = 11906, 16838, 1134          # A4, 2 cm margins (DXA)
TEXT_W = PAGE_W - 2 * MARGIN

W = 'xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" ' \
    'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"'


def runs(text, bold=False, color=None, size=None):
    """Inline markdown -> w:r elements."""
    text = re.sub(r'\[([^\]]+)\]\([^)]+\)', r'\1', text)          # links -> label
    out = []
    for part in re.split(r'(\*\*[^*]+\*\*|`[^`]+`)', text):
        if not part:
            continue
        b, code, t = bold, False, part
        if part.startswith('**') and part.endswith('**'):
            b, t = True, part[2:-2]
        elif part.startswith('`') and part.endswith('`'):
            code, t = True, part[1:-1]
        props = ''
        if b:
            props += '<w:b/><w:bCs/>'
        if code:
            props += '<w:rFonts w:ascii="Consolas" w:hAnsi="Consolas"/><w:shd w:val="clear" w:color="auto" w:fill="EEF2F2"/>'
        if color:
            props += f'<w:color w:val="{color}"/>'
        if size:
            props += f'<w:sz w:val="{size}"/><w:szCs w:val="{size}"/>'
        out.append(f'<w:r>{"<w:rPr>" + props + "</w:rPr>" if props else ""}'
                   f'<w:t xml:space="preserve">{escape(t)}</w:t></w:r>')
    return ''.join(out)


def para(text, style=None, extra_ppr='', **kw):
    ppr = (f'<w:pStyle w:val="{style}"/>' if style else '') + extra_ppr
    return f'<w:p>{"<w:pPr>" + ppr + "</w:pPr>" if ppr else ""}{runs(text, **kw)}</w:p>'


def table(rows):
    header, body = rows[0], rows[1:]
    ncol = len(header)
    weight = [max(6, max(len(re.sub(r'\*\*|`', '', r[i])) if i < len(r) else 0 for r in rows)) for i in range(ncol)]
    weight = [min(w, 60) for w in weight]
    # each column gets a floor (wider for short label columns), then the rest is shared by content length
    floor = min(1500, TEXT_W // ncol - 200)
    spare = TEXT_W - floor * ncol
    widths = [floor + int(spare * w / sum(weight)) for w in weight]
    widths[-1] += TEXT_W - sum(widths)
    grid = ''.join(f'<w:gridCol w:w="{w}"/>' for w in widths)

    def cell(text, w, head):
        shade = f'<w:shd w:val="clear" w:color="auto" w:fill="{MINT}"/>' if head else ''
        return (f'<w:tc><w:tcPr><w:tcW w:w="{w}" w:type="dxa"/>{shade}</w:tcPr>'
                f'{para(text, "TableText", bold=head, color=INK if head else None)}</w:tc>')

    trs = []
    for i, r in enumerate([header] + body):
        r = (r + [''] * ncol)[:ncol]
        head = i == 0
        trpr = '<w:trPr><w:tblHeader/><w:cantSplit/></w:trPr>' if head else '<w:trPr><w:cantSplit/></w:trPr>'
        trs.append('<w:tr>' + trpr + ''.join(cell(t, widths[j], head) for j, t in enumerate(r)) + '</w:tr>')
    borders = ''.join(f'<w:{s} w:val="single" w:sz="4" w:space="0" w:color="{LINE}"/>'
                      for s in ('top', 'left', 'bottom', 'right', 'insideH', 'insideV'))
    return (f'<w:tbl><w:tblPr><w:tblW w:w="{TEXT_W}" w:type="dxa"/><w:tblBorders>{borders}</w:tblBorders>'
            f'<w:tblLayout w:type="fixed"/><w:tblCellMar><w:top w:w="60" w:type="dxa"/><w:left w:w="100" w:type="dxa"/>'
            f'<w:bottom w:w="60" w:type="dxa"/><w:right w:w="100" w:type="dxa"/></w:tblCellMar></w:tblPr>'
            f'<w:tblGrid>{grid}</w:tblGrid>{"".join(trs)}</w:tbl>' + para('', 'Spacer'))


def body_xml(md):
    lines = md.splitlines()
    out, i = [], 0
    while i < len(lines):
        ln = lines[i].rstrip()
        if not ln.strip():
            i += 1
            continue
        if ln.startswith('# '):
            out.append(para(ln[2:], 'Title'))
            # cover: skip the note about the markdown file itself, keep the meta bullets as a subtitle block
            i += 1
            continue
        if ln.startswith('차량용 앱 개발 공모전 제출용 기획서 원문'):
            i += 1
            continue
        if ln.startswith('## '):
            out.append(para(ln[3:], 'Heading1'))
        elif ln.startswith('### '):
            out.append(para(ln[4:], 'Heading2'))
        elif ln.strip() == '---':
            out.append(para('', 'Rule'))
        elif ln.startswith('|'):
            rows = []
            while i < len(lines) and lines[i].startswith('|'):
                cells = [c.strip() for c in lines[i].strip().strip('|').split('|')]
                if not all(re.fullmatch(r':?-{3,}:?', c) for c in cells):
                    rows.append(cells)
                i += 1
            out.append(table(rows))
            continue
        elif ln.startswith('> '):
            out.append(para(ln[2:], 'Quote'))
        elif re.match(r'- ', ln):
            out.append(f'<w:p><w:pPr><w:pStyle w:val="ListText"/><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr>{runs(ln[2:])}</w:p>')
        elif re.match(r'\d+\. ', ln):
            num = re.match(r'(\d+)\. (.*)', ln)
            out.append(para(f'{num[1]}. {num[2]}', 'ListText', extra_ppr='<w:ind w:left="400" w:hanging="300"/>'))
        else:
            out.append(para(ln))
        i += 1
    return ''.join(out)


STYLES = f'''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles {W}>
<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="{FONT}" w:hAnsi="{FONT}" w:eastAsia="{FONT}" w:cs="{FONT}"/>
<w:color w:val="{INK}"/><w:sz w:val="20"/><w:szCs w:val="20"/><w:lang w:val="en-US" w:eastAsia="ko-KR"/></w:rPr></w:rPrDefault>
<w:pPrDefault><w:pPr><w:spacing w:after="100" w:line="300" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults>
<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style>
<w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:before="0" w:after="200"/>
<w:pBdr><w:bottom w:val="single" w:sz="12" w:space="8" w:color="{TEAL}"/></w:pBdr></w:pPr><w:rPr><w:b/><w:color w:val="{INK}"/><w:sz w:val="40"/><w:szCs w:val="40"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/>
<w:pPr><w:keepNext/><w:keepLines/><w:spacing w:before="360" w:after="140"/><w:outlineLvl w:val="0"/>
<w:pBdr><w:left w:val="single" w:sz="24" w:space="6" w:color="{TEAL}"/></w:pBdr></w:pPr><w:rPr><w:b/><w:color w:val="{INK}"/><w:sz w:val="30"/><w:szCs w:val="30"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/>
<w:pPr><w:keepNext/><w:keepLines/><w:spacing w:before="220" w:after="100"/><w:outlineLvl w:val="1"/></w:pPr><w:rPr><w:b/><w:color w:val="{TEAL}"/><w:sz w:val="23"/><w:szCs w:val="23"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="TableText"><w:name w:val="Table Text"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:after="0" w:line="264" w:lineRule="auto"/></w:pPr><w:rPr><w:sz w:val="18"/><w:szCs w:val="18"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="ListText"><w:name w:val="List Text"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:after="60"/></w:pPr></w:style>
<w:style w:type="paragraph" w:styleId="Quote"><w:name w:val="Quote"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:before="120" w:after="160"/><w:ind w:left="200" w:right="200"/>
<w:pBdr><w:left w:val="single" w:sz="18" w:space="8" w:color="{TEAL}"/></w:pBdr><w:shd w:val="clear" w:color="auto" w:fill="F1F6F5"/></w:pPr><w:rPr><w:b/><w:color w:val="{INK}"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Rule"><w:name w:val="Rule"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:before="0" w:after="120"/>
<w:pBdr><w:bottom w:val="single" w:sz="4" w:space="1" w:color="{LINE}"/></w:pBdr></w:pPr></w:style>
<w:style w:type="paragraph" w:styleId="Spacer"><w:name w:val="Spacer"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:after="40" w:line="120" w:lineRule="auto"/></w:pPr></w:style>
<w:style w:type="paragraph" w:styleId="Footer"><w:name w:val="footer"/><w:basedOn w:val="Normal"/><w:pPr><w:jc w:val="center"/><w:spacing w:after="0"/></w:pPr><w:rPr><w:color w:val="{MUTED}"/><w:sz w:val="16"/></w:rPr></w:style>
</w:styles>'''

NUMBERING = f'''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:numbering {W}><w:abstractNum w:abstractNumId="0"><w:multiLevelType w:val="singleLevel"/>
<w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="bullet"/><w:lvlText w:val="•"/><w:lvlJc w:val="left"/>
<w:pPr><w:ind w:left="400" w:hanging="260"/></w:pPr><w:rPr><w:color w:val="{TEAL}"/></w:rPr></w:lvl></w:abstractNum>
<w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num></w:numbering>'''

FOOTER = f'''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:ftr {W}><w:p><w:pPr><w:pStyle w:val="Footer"/></w:pPr><w:r><w:t xml:space="preserve">CurbLink Drive 기획서 · </w:t></w:r>
<w:r><w:fldChar w:fldCharType="begin"/></w:r><w:r><w:instrText xml:space="preserve"> PAGE </w:instrText></w:r><w:r><w:fldChar w:fldCharType="separate"/></w:r>
<w:r><w:t>1</w:t></w:r><w:r><w:fldChar w:fldCharType="end"/></w:r></w:p></w:ftr>'''


def build():
    md = SRC.read_text(encoding='utf-8')
    document = (f'<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document {W}><w:body>{body_xml(md)}'
                f'<w:sectPr><w:footerReference w:type="default" r:id="rIdFooter"/>'
                f'<w:pgSz w:w="{PAGE_W}" w:h="{PAGE_H}"/><w:pgMar w:top="{MARGIN}" w:right="{MARGIN}" w:bottom="{MARGIN}" '
                f'w:left="{MARGIN}" w:header="567" w:footer="567" w:gutter="0"/></w:sectPr></w:body></w:document>')
    now = datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')
    parts = {
        '[Content_Types].xml': '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
            '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/>'
            '<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>'
            '<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>'
            '<Override PartName="/word/numbering.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"/>'
            '<Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/>'
            '<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>'
            '<Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/></Types>',
        '_rels/.rels': '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>'
            '<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>'
            '<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/></Relationships>',
        'word/_rels/document.xml.rels': '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            '<Relationship Id="rIdStyles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>'
            '<Relationship Id="rIdNumbering" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering" Target="numbering.xml"/>'
            '<Relationship Id="rIdFooter" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer" Target="footer1.xml"/></Relationships>',
        'word/document.xml': document, 'word/styles.xml': STYLES, 'word/numbering.xml': NUMBERING, 'word/footer1.xml': FOOTER,
        'docProps/core.xml': '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" '
            'xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">'
            f'<dc:title>CurbLink Drive 기획서</dc:title><dc:creator>CurbLink</dc:creator><cp:lastModifiedBy>CurbLink</cp:lastModifiedBy>'
            f'<dcterms:created xsi:type="dcterms:W3CDTF">{now}</dcterms:created><dcterms:modified xsi:type="dcterms:W3CDTF">{now}</dcterms:modified></cp:coreProperties>',
        'docProps/app.xml': '<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"><Application>CurbLink proposal builder</Application></Properties>',
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(OUT, 'w', zipfile.ZIP_DEFLATED) as z:
        for name, data in parts.items():
            z.writestr(name, data.encode('utf-8'))
    print('WROTE', OUT)


build()
