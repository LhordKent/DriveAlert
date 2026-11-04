param([string]$InputPath, [string]$OutputPath)
$ErrorActionPreference = 'Stop'
$wordApp = $null
$wordDoc = $null
try {
    $wordApp = New-Object -ComObject Word.Application
    $wordApp.Visible = $false
    $wordApp.DisplayAlerts = 0
    $wordDoc = $wordApp.Documents.Open($InputPath, $false, $true, $false)
    $wordDoc.Repaginate()
    $pageCount = $wordDoc.ComputeStatistics(2)
    $wordDoc.ExportAsFixedFormat($OutputPath, 17)
    Write-Output "PDF exported; pages=$pageCount; path=$OutputPath"
} finally {
    if ($null -ne $wordDoc) { $wordDoc.Close(0) }
    if ($null -ne $wordApp) { $wordApp.Quit(0) }
    if ($null -ne $wordDoc) { [void][System.Runtime.InteropServices.Marshal]::FinalReleaseComObject($wordDoc) }
    if ($null -ne $wordApp) { [void][System.Runtime.InteropServices.Marshal]::FinalReleaseComObject($wordApp) }
}
