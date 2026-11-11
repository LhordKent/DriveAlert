from pathlib import Path
from pypdf import PdfReader
import re,json
b=Path(r'C:\DriveAlert\artifacts\numbering-audit'); d=PdfReader(b/'original.pdf')
texts=[p.extract_text() for p in d.pages];(b/'pdf-text.json').write_text(json.dumps(texts,ensure_ascii=False),encoding='utf-8')
for i,t in enumerate(texts):
 caps=re.findall(r'(?:Table\s+\d+(?:\.\d+)?|Figure\s+\d+(?:\.\d+)?\s*:[^\n]*)',t)
 if caps:print('PDF PAGE',i+1,'FOOTER',repr(t[-90:]),'CAPTIONS',caps)
print('FRONT MATTER')
for i,t in enumerate(texts[:12]):print('PDF PAGE',i+1,t[:100],t[-60:])

