$ErrorActionPreference='Stop'
$wordAuditApp = New-Object -ComObject Word.Application
$wordAuditApp.Visible = $false
$wordAuditApp.DisplayAlerts = 0
try {
 $auditDoc = $wordAuditApp.Documents.Open('C:\Users\Admin\Downloads\DriveAlertlatest-codex-fixes-tables-page142.docx', $false, $true)
 $auditDoc.Repaginate()
 $auditDoc.ExportAsFixedFormat('C:\DriveAlert\artifacts\numbering-audit\original.pdf',17)
 Write-Output ('Pages: ' + $auditDoc.ComputeStatistics(2))
} finally {
 if ($auditDoc) {$auditDoc.Close(0)}
 $wordAuditApp.Quit()
}
