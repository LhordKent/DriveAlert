from pathlib import Path
from datetime import date, timedelta
from copy import deepcopy
from io import BytesIO
from zipfile import ZipFile
from lxml import etree
from docx import Document
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
import hashlib, json, re, uuid

ROOT = Path(r'C:\DriveAlert\output\gantt-edit-20261103')
TARGET = Path(r'C:\Users\Admin\Downloads\GantChartTest.docx')
manifest = json.loads((ROOT / 'baseline-manifest.json').read_text(encoding='utf-8'))
raw = TARGET.read_bytes()
assert hashlib.sha256(raw).hexdigest() == manifest['source_sha256'], 'Source changed after inspection'
doc = Document(BytesIO(raw))

# Nine task rows per revised sprint keep the original total table-row count.
# Tuple fields: task wording, lead, inclusive start MM-DD, inclusive end MM-DD.
stages = [
 (5, 3, 'PROJECT FOUNDATION, IOT SOURCING, AND MANUSCRIPT COMPLIANCE', [
  ('Create the Git repository and configure Android, web, and Firebase projects', 'Basubas, Isagunde, and Borces', '07-23', '07-28'),
  ('Refine the local and cloud database schema and account structures', 'Borces', '07-23', '07-29'),
  ('Identify suppliers and source the required IoT components', 'Basubas and All', '07-23', '08-03'),
  ('Review component specifications, compatibility, and proposed wiring', 'Basubas and Borces', '07-25', '08-04'),
  ('Establish local storage and initial database operations', 'Borces', '07-27', '08-04'),
  ('Prepare project structure and initial account-screen layouts', 'Isagunde and Catarig', '07-27', '08-04'),
  ('Revise the manuscript and diagrams based on review feedback', 'All', '07-23', '08-04'),
  ('Prepare compliance documents and route the approval sheet', 'All', '07-27', '08-05'),
  ('Check builds and documents, commit and push changes, and review readiness', 'All', '08-03', '08-05'),
 ]),
 (6, 4, 'IOT ASSEMBLY, COMMUNICATION, AND CORE APPLICATION FUNCTIONS', [
  ('Assemble the camera, speaker, power connections, and mounting', 'Basubas and All', '08-06', '08-09'),
  ('Configure camera firmware, capture settings, and device services', 'Basubas', '08-08', '08-11'),
  ('Implement Bluetooth provisioning and local Wi-Fi connection', 'Basubas', '08-09', '08-14'),
  ('Implement the IoT HTTP stream and Android video receiving', 'Basubas and Borces', '08-10', '08-16'),
  ('Verify onboard speaker playback and initial warning commands', 'Basubas and Catarig', '08-11', '08-16'),
  ('Develop Android account functions, setup screens, and navigation', 'Basubas and Isagunde', '08-06', '08-16'),
  ('Develop web authentication, routing, and initial account views', 'Isagunde and Catarig', '08-06', '08-16'),
  ('Test power, streaming, reconnection, and account functions', 'All', '08-14', '08-19'),
  ('Upload tested firmware, push changes to GitHub, and review connection readiness', 'Basubas and Borces', '08-18', '08-19'),
 ]),
 (7, 5, 'MEDIAPIPE INTEGRATION AND LIVE FACIAL MEASUREMENTS', [
  ('Integrate MediaPipe Face Landmarker into Android processing', 'Basubas', '08-20', '08-25'),
  ('Build live frame processing from the IoT camera stream', 'Basubas and Borces', '08-22', '08-28'),
  ('Identify eye and mouth landmarks and calculate EAR and MAR', 'Basubas and Borces', '08-23', '08-30'),
  ('Calculate head-pitch measurements from facial transformations', 'Basubas', '08-24', '08-30'),
  ('Implement Driver-area selection and tracking continuity', 'Basubas', '08-25', '09-01'),
  ('Implement facial-lighting and visibility checks', 'Basubas and Borces', '08-26', '09-01'),
  ('Develop camera-alignment and facial-visibility feedback', 'Isagunde', '08-27', '09-02'),
  ('Test landmark stability, lighting, delay, and Android compatibility', 'All', '08-29', '09-02'),
  ('Commit and push processing changes and review calibration readiness', 'Basubas and Borces', '09-01', '09-02'),
 ]),
 (8, 6, 'CALIBRATION, DROWSINESS DETECTION, AND WARNING CONTROL', [
  ('Implement guided Driver calibration and stored calibration values', 'Basubas', '09-03', '09-08'),
  ('Implement prolonged-eye-closure detection using calibrated EAR', 'Basubas', '09-04', '09-10'),
  ('Implement yawning detection using calibrated MAR', 'Catarig and Basubas', '09-04', '09-10'),
  ('Implement relative head-pitch and temporal head-down detection', 'Basubas', '09-05', '09-11'),
  ('Implement confirmation, episode grouping, and warning-stage rules', 'Basubas', '09-08', '09-13'),
  ('Connect staged warnings to the onboard speaker command', 'Basubas and Isagunde', '09-09', '09-14'),
  ('Implement local monitoring-session and alert-history records', 'Borces', '09-09', '09-15'),
  ('Test calibration, false alerts, stage progression, and offline records', 'All', '09-12', '09-16'),
  ('Commit and push tested changes and review detection reliability', 'All', '09-15', '09-16'),
 ]),
 (9, 7, 'TRUSTED CONTACT SHARING AND SYSTEM INTEGRATION', [
  ('Implement connection requests, approval, and Driver relationships', 'Borces and Catarig', '09-17', '09-22'),
  ('Implement approved access, revocation, and security rules', 'Borces', '09-18', '09-24'),
  ('Create eligible Stage 3 transition and persistence records', 'Basubas and Borces', '09-19', '09-25'),
  ('Implement synchronization queues, retries, and status updates', 'Basubas and Borces', '09-21', '09-28'),
  ('Develop mobile shared-record views and Driver alert summaries', 'Isagunde and Catarig', '09-20', '09-27'),
  ('Connect the web portal to approved Driver records and requests', 'Isagunde and Catarig', '09-20', '09-27'),
  ('Integrate mobile, web, IoT warnings, records, and synchronization', 'All', '09-23', '09-29'),
  ('Test sharing eligibility, revocation, offline queues, and full workflow', 'All', '09-26', '09-30'),
  ('Push tested changes, deploy the web test build, and review integration', 'Isagunde and Borces', '09-29', '09-30'),
 ]),
 (10, 8, 'INTEGRATED TESTING AND SYSTEM REFINEMENT', [
  ('Test mobile and web accounts, setup, validation, and navigation', 'Borces and Catarig', '10-01', '10-04'),
  ('Test camera tracking, lighting pauses, and connection recovery', 'Basubas and Isagunde', '10-02', '10-07'),
  ('Test calibration, sign confirmation, warning stages, and speaker response', 'Basubas and Catarig', '10-03', '10-08'),
  ('Test local records, alert summaries, settings, and retention', 'Isagunde and Borces', '10-03', '10-09'),
  ('Test approved sharing, revocation, synchronization, and retries', 'Borces and Catarig', '10-05', '10-10'),
  ('Test web responsiveness, usability, and device compatibility', 'Isagunde and Catarig', '10-05', '10-11'),
  ('Correct integrated defects and perform focused regression tests', 'All', '10-08', '10-14'),
  ('Update manuscript descriptions and diagrams to match the system', 'All', '10-08', '10-14'),
  ('Push fixes, generate a test APK, and update the web test deployment', 'Basubas and Isagunde', '10-12', '10-14'),
 ]),
 (11, 9, 'CONTROLLED EVALUATION AND RELEASE PREPARATION', [
  ('Finalize controlled-evaluation procedures, forms, and device setup', 'All', '10-15', '10-17'),
  ('Conduct controlled eye-closure, yawning, and head-down trials', 'All', '10-17', '10-22'),
  ('Evaluate audible warnings, offline monitoring, and approved sharing', 'All', '10-18', '10-23'),
  ('Record false alerts, missed detections, delays, and test results', 'Basubas and Borces', '10-18', '10-24'),
  ('Resolve remaining mobile, web, IoT, and synchronization defects', 'All', '10-21', '10-26'),
  ('Analyze evaluation findings against the study objectives', 'Basubas and Catarig', '10-23', '10-27'),
  ('Complete manuscript findings, diagrams, and supporting evidence', 'Isagunde and Borces', '10-24', '10-28'),
  ('Run complete regression tests and verify release readiness', 'All', '10-25', '10-28'),
  ('Push verified changes, generate the release-candidate APK, and update the web', 'Basubas and Isagunde', '10-27', '10-28'),
 ]),
 (12, 10, 'FINAL SUBMISSION, DEPLOYMENT, AND PRESENTATION', [
  ('Finalize manuscript formatting, references, and required corrections', 'All', '10-29', '11-01'),
  ('Complete approval requirements, compliance documents, and appendices', 'All', '10-29', '11-03'),
  ('Verify manuscript completeness and prepare the submission package', 'All', '11-01', '11-03'),
  ('Submit the final manuscript', 'All', '11-04', '11-04'),
  ('Resolve final system issues and verify corrected functions', 'All', '10-29', '11-07'),
  ('Generate and install the final APK and upload verified IoT firmware', 'Basubas and Borces', '11-05', '11-09'),
  ('Deploy and verify the Trusted Contact web portal and access rules', 'Isagunde and Borces', '11-05', '11-09'),
  ('Prepare presentation materials and rehearse the demonstration', 'All', '10-29', '11-10'),
  ('Push final changes and verify deployment, presentation, and project completion', 'All', '11-09', '11-11'),
 ]),
]

def parse_day(s): return date.fromisoformat('2026-' + s)
def label(dt): return f'{dt.strftime("%B")} {dt.day}'

slots = []
active = False
for table in doc.tables[:5]:
    for row in table.rows:
        key = row.cells[0].text.strip()
        if key == '5': active = True
        if active and re.fullmatch(r'\d+(?:\.\d+)?', key): slots.append(row._tr)
assert len(slots) == 80
task_template = deepcopy(slots[1])
header_template = deepcopy(slots[0])
early_snapshot = [etree.tostring(row._tr) for table in doc.tables[:5] for row in table.rows
                  if row._tr not in slots]

def new_ids(node):
    for element in node.iter():
        for key in list(element.attrib):
            if etree.QName(key).localname in ('paraId', 'textId'):
                element.set(key, uuid.uuid4().hex[:8].upper())

def set_text(cell, value):
    paragraphs = cell.findall(qn('w:p'))
    p = paragraphs[0]
    for old in paragraphs[1:]: cell.remove(old)
    rpr = p.find('.//' + qn('w:rPr'))
    rpr = deepcopy(rpr) if rpr is not None else None
    for child in list(p):
        if child.tag != qn('w:pPr'): p.remove(child)
    r = OxmlElement('w:r')
    if rpr is not None: r.append(rpr)
    text = OxmlElement('w:t'); text.text = value
    if value.startswith(' ') or value.endswith(' '): text.set(qn('xml:space'), 'preserve')
    r.append(text); p.append(r)

def set_fill(cell, fill):
    pr = cell.find(qn('w:tcPr'))
    for shd in list(pr.findall(qn('w:shd'))): pr.remove(shd)
    if fill:
        shd = OxmlElement('w:shd'); shd.set(qn('w:val'), 'clear')
        shd.set(qn('w:color'), 'auto'); shd.set(qn('w:fill'), fill); pr.append(shd)

def week_index(day): return 5 + (day.month - 6) * 4 + min((day.day - 1) // 7, 3)

replacement_rows = []
records = []
for section, sprint, title, tasks in stages:
    sprint_start = date(2026,6,25) + timedelta(days=14*(sprint-1))
    sprint_end = sprint_start + timedelta(days=13)
    header = deepcopy(header_template); new_ids(header)
    cells = header.findall(qn('w:tc'))
    set_text(cells[0], str(section)); set_text(cells[1], title)
    replacement_rows.append(header)
    for n,(text, lead, start, end) in enumerate(tasks,1):
        a,b = parse_day(start),parse_day(end)
        assert sprint_start <= a <= b <= sprint_end, (section,n,a,b)
        row = deepcopy(task_template); new_ids(row)
        cells = row.findall(qn('w:tc')); assert len(cells)==29
        for c,value in zip(cells[:5],[f'{section}.{n}',text,lead,label(a),label(b)]): set_text(c,value)
        occupied = {week_index(a + timedelta(days=i)) for i in range((b-a).days+1)}
        for j in range(5,29):
            set_text(cells[j],''); set_fill(cells[j], 'FF0000' if j in occupied else None)
        replacement_rows.append(row)
        records.append({'id':f'{section}.{n}','sprint':sprint,'task':text,'lead':lead,
                        'start':a.isoformat(),'end':b.isoformat(),'status':'INCOMPLETE',
                        'week_columns':sorted(occupied)})

assert len(replacement_rows)==len(slots)
for old,new in zip(slots,replacement_rows): old.getparent().replace(old,new)
assert [len(t.rows) for t in doc.tables] == manifest['row_counts']
assert [etree.tostring(s._sectPr).decode() for s in doc.sections] == manifest['sections']
assert [etree.tostring(t._tbl.tblGrid).decode() for t in doc.tables] == manifest['grids']
assert early_snapshot == [etree.tostring(row._tr) for table in doc.tables[:5] for row in table.rows
                          if row._tr not in replacement_rows]
assert max(r['end'] for r in records)=='2026-11-11'
assert any(r['task']=='Submit the final manuscript' and r['start']==r['end']=='2026-11-04' for r in records)
assert not any('infrared' in r['task'].lower() or 'ir led' in r['task'].lower() for r in records)

new_xml = etree.tostring(doc._element,encoding='UTF-8',xml_declaration=True,standalone=True)
buf=BytesIO()
with ZipFile(BytesIO(raw)) as zin, ZipFile(buf,'w') as zout:
    for info in zin.infolist():
        zout.writestr(info,new_xml if info.filename=='word/document.xml' else zin.read(info.filename))
out=buf.getvalue()
with ZipFile(BytesIO(out)) as z:
    changed=[name for name,digest in manifest['parts'].items() if hashlib.sha256(z.read(name)).hexdigest()!=digest]
assert changed==['word/document.xml'],changed
assert hashlib.sha256(TARGET.read_bytes()).hexdigest()==manifest['source_sha256'], 'Source changed while preparing edit'
TARGET.write_bytes(out)
(ROOT/'schedule.json').write_text(json.dumps(records,indent=2),encoding='utf-8')
(ROOT/'validation.json').write_text(json.dumps({'updated':str(TARGET),'task_count':len(records),
 'changed_parts':changed,'rows':manifest['row_counts'],'sprints':[x[1] for x in stages],
 'first_two_sprints_preserved':True,'section_geometry_preserved':True,'column_grids_preserved':True,
 'submission':'2026-11-04','end':'2026-11-11','sha256':hashlib.sha256(out).hexdigest()},indent=2),encoding='utf-8')
print('Updated in place:',TARGET)
print('Revised tasks:',len(records),'Preserved chart table rows:',manifest['row_counts'])
print('Only modified package part:',changed)
