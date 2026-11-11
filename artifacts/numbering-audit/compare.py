import json,re
from pathlib import Path
ps=json.loads(Path(r'C:\DriveAlert\artifacts\numbering-audit\paragraphs.json').read_text(encoding='utf-8'))
for a,b in [(201,301),(301,500)]:
 vals=[p['text'].strip() for p in ps[a+4:b] if p['text'].strip()]
 print('LIST',a,len(vals))
 for i in range(0,len(vals),3):
  if i+2>=len(vals): print('incomplete',vals[i:]);break
  num,title,page=vals[i:i+3]
  if a==201:
   matches=[p for p in ps[500:] if re.fullmatch('Table '+re.escape(num)+r'(\.\d+)?',p['text'].strip())]
  else: matches=[p for p in ps[500:] if re.match('Figure '+re.escape(num)+':',p['text'])]
  if not matches:print('NO EXACT BASE',num,title)
  elif a==301:
   actual=matches[0]['text'].split(':',1)[1].strip()
   if title!=actual:print('TITLE MISMATCH',num,title,actual)
print('CAPTION COUNTS')
for kind in ['Table','Figure']:
 cs=[p['text'] for p in ps[500:] if re.match(r'^'+kind+r'\s+\d+(?:\.\d+)?(?:\s*$|:)',p['text'])]
 print(kind,len(cs),'distinct',len(set(cs)))
 print('\n'.join(cs[-20:]))
