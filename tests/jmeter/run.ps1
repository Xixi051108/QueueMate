param(
    [string]$HostName = '127.0.0.1',
    [int]$Port = 8080,
    [string]$RunId = '',
    [string]$JMeterBat = 'D:\JMeter\apache-jmeter-5.6.3\apache-jmeter-5.6.3\bin\jmeter.bat'
)

$ErrorActionPreference = 'Stop'
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$testPlan = Join-Path $scriptRoot 'concurrent-booking.jmx'
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$resultsDirectory = Join-Path $scriptRoot 'results'
$resultFile = Join-Path $resultsDirectory "concurrent-booking-$timestamp.jtl"
$logFile = Join-Path $resultsDirectory "jmeter-$timestamp.log"
$reportRoot = Join-Path $scriptRoot 'report'
$reportDirectory = Join-Path $reportRoot $timestamp
$summaryFile = Join-Path $reportDirectory 'index-zh.html'

if (-not (Test-Path -LiteralPath $JMeterBat)) {
    throw "JMeter executable not found: $JMeterBat"
}

New-Item -ItemType Directory -Force -Path $resultsDirectory | Out-Null
$arguments = @(
    '-n',
    '-t', $testPlan,
    '-l', $resultFile,
    '-j', $logFile,
    '-e',
    '-o', $reportDirectory,
    "-Jhost=$HostName",
    "-Jport=$Port"
)
if ($RunId) {
    $arguments += "-JrunId=$RunId"
}

& $JMeterBat @arguments
$jmeterExitCode = $LASTEXITCODE
if ($jmeterExitCode -ne 0) {
    throw "JMeter process failed with exit code $jmeterExitCode. Log: $logFile"
}
if (-not (Test-Path -LiteralPath $resultFile)) {
    throw "JMeter did not create the expected JTL result: $resultFile. Log: $logFile"
}
if (-not (Test-Path -LiteralPath (Join-Path $reportDirectory 'statistics.json'))) {
    throw "JMeter did not create the expected HTML report: $reportDirectory. Log: $logFile"
}

$rows = Import-Csv -LiteralPath $resultFile
$failedRows = @($rows | Where-Object { $_.success -ne 'true' })
$bookingRows = @($rows | Where-Object { $_.label -eq 'LOAD - Concurrent booking' })
$created = @($bookingRows | Where-Object { $_.responseCode -eq '201' }).Count
$full = @($bookingRows | Where-Object { $_.responseCode -eq '409' }).Count

$summaryScript = Join-Path $scriptRoot 'write-summary-zh.ps1'
& $summaryScript `
    -ResultFile $resultFile `
    -StatisticsFile (Join-Path $reportDirectory 'statistics.json') `
    -OutputFile $summaryFile

Write-Host "Concurrent booking: created=$created full=$full samples=$($rows.Count) errors=$($failedRows.Count)"
Write-Host "HTML report: $reportDirectory\index.html"

if ($failedRows.Count -gt 0) {
    $summary = $failedRows | Group-Object label, responseCode | ForEach-Object { "$($_.Count)x $($_.Name)" }
    throw "JMeter assertions failed: $($summary -join '; ')"
}
if ($created -ne 3 -or $full -ne 9) {
    throw "Unexpected booking distribution: created=$created full=$full"
}
