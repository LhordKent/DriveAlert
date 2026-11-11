from pathlib import Path
import json,re
import pypdfium2 as pdfium
b=Path(r'C:\DriveAlert\artifacts\numbering-audit');texts=json.loads((b/'pdf-text.json').read_text(encoding='utf-8'));ps=json.loads((b/'paragraphs.json').read_text(encoding='utf-8'))
locations={}
for n,t in enumerate(texts[8:],9):
 for m in re.finditer(r'(?m)^\s*(Table\s+\d+(?:\.\d+)?|Figure\s+\d+(?:\.\d+)?)\s*(?::|\n)',t):
  label=' '.join(m[1].split());locations.setdefault(label,[]).append(n)
lines=[]
for start,end,kind in [(205,301,'Table'),(305,500,'Figure')]:
 vals=[p['text'].strip() for p in ps[start:end] if p['text'].strip()]
 for i in range(0,len(vals),3):
  num,title,old=vals[i:i+3];label=kind+' '+num
  loc=locations.get(label) or locations.get(label+'.1')
  if not loc:print('MISSING',label);continue
  current=loc[0]-8
  lines.append((kind,num,title,old,current,loc[0]))
  if str(current)!=old:print('PAGE MISMATCH',label,'listed',old,'actual',current,'physical',loc[0])
(b/'locations.json').write_text(json.dumps(locations,indent=2),encoding='utf-8')
(b/'index-comparison.json').write_text(json.dumps(lines,indent=2),encoding='utf-8')
print('MISSING ENTRIES')
for label in ['Table '+str(i) for i in range(33,39)]+['Figure 63','Figure 64']:print(label,locations.get(label),'printed',[p-8 for p in locations.get(label,[])])
d=pdfium.PdfDocument(b/'original.pdf')
for n in [6,7,65,163,164,167,186]:d[n-1].render(scale=1.5).to_pil().save(b/f'page-{n}.png')
