import json,re
from pathlib import Path
ps=json.loads(Path(r'C:\DriveAlert\artifacts\numbering-audit\paragraphs.json').read_text(encoding='utf-8'))
for p in ps:
 if p['i']>=7680 and re.search(r'\b(?:table|figure|fig\.)\s*\d',p['text'],re.I):print(p['i'],p['text'])
print('FIELDS',[(p['i'],p['fields']) for p in ps if p['fields']]);print('CACHED BREAK COUNT',sum(p['breaks'] for p in ps))
for a,b in [(700,740),(770,785),(820,840),(7660,7750)]:
 print('RANGE',a,b)
 for p in ps[a:b]:
  if p['text']:print(p['i'],p['text'])
