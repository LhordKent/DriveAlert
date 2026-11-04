from pathlib import Path
from io import BytesIO
from zipfile import ZipFile
from lxml import etree
from docx import Document
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
import re
p=Path(r'C:\Users\Admin\Downloads\GantChartTest.docx')
raw=p.read_bytes();d=Document(BytesIO(raw))
def keep(par):
    pr=par.find(qn('w:pPr'))
    if pr is None:
        pr=OxmlElement('w:pPr');par.insert(0,pr)
    if pr.find(qn('w:keepNext')) is None:pr.append(OxmlElement('w:keepNext'))
for par in d.paragraphs:
    if par.text.strip().startswith('Table 6.') or par.text.strip()=='GANTT CHART':keep(par._p)
for ti,t in enumerate(d.tables[:5]):
    if ti>0:
        for row in t.rows[:2]:
            for par in row._tr.iter(qn('w:p')):keep(par)
    for row in t.rows:
        key=row.cells[0].text.strip()
        if key.isdigit() and int(key)>=5:
            for par in row._tr.iter(qn('w:p')):keep(par)
xml=etree.tostring(d._element,encoding='UTF-8',xml_declaration=True,standalone=True)
buf=BytesIO()
with ZipFile(BytesIO(raw)) as zin,ZipFile(buf,'w') as zout:
    for info in zin.infolist():zout.writestr(info,xml if info.filename=='word/document.xml' else zin.read(info.filename))
p.write_bytes(buf.getvalue())
print('Caption and section-heading pagination updated; font, grid, and margins preserved.')
