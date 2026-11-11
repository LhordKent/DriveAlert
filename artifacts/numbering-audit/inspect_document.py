import json,re
from pathlib import Path
b=Path(r'C:\DriveAlert\artifacts\numbering-audit'); ps=json.loads((b/'paragraphs.json').read_text(encoding='utf-8'))
for p in ps:
 if (p['i']<596 and p['text']) or (p['i']>=7680 and re.search(r'\b(table|figure|fig\.)\s*\d',p['text'],re.I)):print(p['i'],p['text'])
print('FIELDS',[(p['i'],p['fields']) for p in ps if p['fields']])
print('CAPTION CONTEXT')
for j,p in enumerate(ps):
 if re.match(r'^Table\s+\d',p['text']) and len(p['text'])<35:
  print('\n',[(q['i'],q['text']) for q in ps[max(0,j-2):j+5]])
