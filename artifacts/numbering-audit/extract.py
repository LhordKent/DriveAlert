from zipfile import ZipFile
from lxml import etree as E
from pathlib import Path
import json,re
src=Path(r'C:\Users\Admin\Downloads\DriveAlertlatest-codex-fixes-tables-page142.docx')
out=Path(r'C:\DriveAlert\artifacts\numbering-audit')
ns={'w':'http://schemas.openxmlformats.org/wordprocessingml/2006/main'}
with ZipFile(src) as z:
 root=E.fromstring(z.read('word/document.xml'))
 pars=[]
 for i,p in enumerate(root.xpath('//w:body//w:p',namespaces=ns)):
  txt=''.join(p.xpath('.//w:t/text()',namespaces=ns))
  style=p.xpath('./w:pPr/w:pStyle/@w:val',namespaces=ns)
  pars.append({'i':i,'text':txt,'style':style,'images':len(p.xpath('.//w:drawing|.//w:pict',namespaces=ns)),'fields':p.xpath('.//w:instrText/text()',namespaces=ns),'breaks':len(p.xpath('.//w:lastRenderedPageBreak',namespaces=ns))})
 tables=[]
 for i,t in enumerate(root.xpath('//w:body//w:tbl',namespaces=ns)):
  rows=[[' | '.join(''.join(p.xpath('.//w:t/text()',namespaces=ns)) for p in c.xpath('./w:p',namespaces=ns)) for c in r.xpath('./w:tc',namespaces=ns)] for r in t.xpath('./w:tr',namespaces=ns)]
  tables.append(rows)
 (out/'paragraphs.json').write_text(json.dumps(pars,ensure_ascii=False,indent=2),encoding='utf-8')
 (out/'tables.json').write_text(json.dumps(tables,ensure_ascii=False,indent=2),encoding='utf-8')
 (out/'text.txt').write_text('\n'.join(f"[{p['i']}] {p['text']}" for p in pars),encoding='utf-8')
 print('Paragraphs',len(pars),'Tables',len(tables),'Images',sum(p['images'] for p in pars))
 for p in pars:
  if re.search(r'\b(?:Table|Figure|Fig\.)\s*\d',p['text'],re.I): print(f"[{p['i']}] {p['text']}")
 print('FIELDS',[(p['i'],p['fields']) for p in pars if p['fields']])
