param(
    [Parameter(Mandatory = $true)]
    [string]$ResultFile,
    [Parameter(Mandatory = $true)]
    [string]$StatisticsFile,
    [Parameter(Mandatory = $true)]
    [string]$OutputFile
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $ResultFile)) {
    throw "JTL result file not found: $ResultFile"
}
if (-not (Test-Path -LiteralPath $StatisticsFile)) {
    throw "JMeter statistics file not found: $StatisticsFile"
}

$rows = @(Import-Csv -LiteralPath $ResultFile)
$statistics = Get-Content -LiteralPath $StatisticsFile -Raw -Encoding UTF8 | ConvertFrom-Json
$bookingRows = @($rows | Where-Object { $_.label -eq 'LOAD - Concurrent booking' })
$bookingStatistics = $statistics.'LOAD - Concurrent booking'
$totalStatistics = $statistics.Total

if (-not $bookingStatistics -or $bookingRows.Count -eq 0) {
    throw 'Concurrent booking samples are missing; Chinese summary cannot be generated.'
}

$created = @($bookingRows | Where-Object { $_.responseCode -eq '201' }).Count
$full = @($bookingRows | Where-Object { $_.responseCode -eq '409' }).Count
$unexpected = @($bookingRows | Where-Object { $_.responseCode -notin @('201', '409') }).Count
$failedSamples = @($rows | Where-Object { $_.success -ne 'true' }).Count
$passed = $created -eq 3 -and $full -eq 9 -and $unexpected -eq 0 -and $failedSamples -eq 0

$verdict = if ($passed) { '通过' } else { '需要检查' }
$verdictClass = if ($passed) { 'is-success' } else { 'is-danger' }
$conclusion = if ($passed) {
    '容量为 3 的时段在 12 人同时预约时准确得到 3 个成功和 9 个名额已满，没有超卖，也没有测试错误。'
} else {
    '本次结果没有满足 3 个成功、9 个名额已满且零错误的通过条件，请结合原始报告和后端日志定位原因。'
}

$mean = [math]::Round([double]$bookingStatistics.meanResTime, 1)
$median = [math]::Round([double]$bookingStatistics.medianResTime, 1)
$p90 = [math]::Round([double]$bookingStatistics.pct1ResTime, 1)
$p95 = [math]::Round([double]$bookingStatistics.pct2ResTime, 1)
$p99 = [math]::Round([double]$bookingStatistics.pct3ResTime, 1)
$maximum = [math]::Round([double]$bookingStatistics.maxResTime, 1)
$throughput = [math]::Round([double]$bookingStatistics.throughput, 1)
$errorRate = [math]::Round([double]$totalStatistics.errorPct, 2)
$createdPct = [math]::Round(($created / $bookingRows.Count) * 100, 2)
$fullPct = [math]::Round(($full / $bookingRows.Count) * 100, 2)
$generatedAt = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
$resultName = [System.Net.WebUtility]::HtmlEncode((Split-Path -Leaf $ResultFile))

$merchantLogin = $statistics.'SETUP - Merchant login'
$warmupNote = if ($merchantLogin -and [double]$merchantLogin.maxResTime -ge 1000) {
    $merchantTime = [math]::Round([double]$merchantLogin.maxResTime, 1)
    "首次商家登录为 ${merchantTime}ms，但只有 1 个样本，可能包含应用或数据库预热开销，不能单独认定为性能瓶颈。"
} else {
    '本次准备阶段未出现超过 1 秒的接口样本。'
}

$html = @"
<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>QueueMate JMeter 中文测试概览</title>
  <style>
    :root {
      --primary-700: #124e78;
      --primary-600: #176b9c;
      --primary-100: #dceef7;
      --ink-900: #142b3a;
      --ink-700: #38505f;
      --ink-500: #667a86;
      --line-300: #cad5db;
      --line-200: #dce4e8;
      --surface: #ffffff;
      --canvas: #f5f7f8;
      --success-700: #18734a;
      --success-100: #dcf3e7;
      --danger-700: #b42318;
      --danger-100: #fde3e1;
      --warning-700: #a64b08;
      --warning-100: #fce8d2;
      --font-sans: "Noto Sans SC", "Microsoft YaHei UI", "PingFang SC", system-ui, sans-serif;
      --font-data: "DIN Alternate", "Roboto Mono", Consolas, monospace;
    }
    * { box-sizing: border-box; }
    body {
      margin: 0;
      color: var(--ink-900);
      background: var(--canvas);
      font-family: var(--font-sans);
      line-height: 1.6;
    }
    a { color: var(--primary-700); }
    a:focus-visible { outline: 3px solid var(--primary-600); outline-offset: 3px; }
    .page { width: min(1120px, calc(100% - 48px)); margin: 40px auto 64px; }
    .ticket {
      position: relative;
      overflow: hidden;
      padding: 28px 32px 30px;
      border: 1px solid var(--line-300);
      border-top: 6px solid var(--primary-700);
      border-radius: 8px;
      background: var(--surface);
    }
    .ticket::after {
      position: absolute;
      right: 0;
      bottom: 0;
      left: 0;
      height: 5px;
      content: "";
      background: repeating-linear-gradient(90deg, var(--primary-100) 0 18px, transparent 18px 28px);
    }
    .ticket-top { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; }
    .eyebrow { margin: 0 0 6px; color: var(--primary-700); font-size: 13px; font-weight: 700; }
    h1 { margin: 0; font-size: clamp(26px, 4vw, 38px); line-height: 1.25; }
    .stamp {
      flex: 0 0 auto;
      min-width: 96px;
      padding: 9px 16px;
      border: 2px solid currentColor;
      border-radius: 999px;
      text-align: center;
      font-weight: 700;
    }
    .stamp.is-success { color: var(--success-700); background: var(--success-100); }
    .stamp.is-danger { color: var(--danger-700); background: var(--danger-100); }
    .ticket-rule { margin: 24px 0; border: 0; border-top: 1px dashed var(--line-300); }
    .conclusion { max-width: 840px; margin: 0; color: var(--ink-700); font-size: 17px; }
    .meta { margin: 14px 0 0; color: var(--ink-500); font-size: 13px; }
    .section { margin-top: 32px; }
    .section-heading { display: flex; align-items: baseline; justify-content: space-between; gap: 16px; margin-bottom: 14px; }
    h2 { margin: 0; font-size: 21px; }
    .section-hint { color: var(--ink-500); font-size: 13px; }
    .metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); border: 1px solid var(--line-300); background: var(--surface); }
    .metric { min-width: 0; padding: 20px; border-right: 1px solid var(--line-200); border-bottom: 1px solid var(--line-200); }
    .metric:nth-child(4n) { border-right: 0; }
    .metric:nth-last-child(-n+4) { border-bottom: 0; }
    .metric-label { display: block; color: var(--ink-500); font-size: 13px; }
    .metric-value { display: block; margin-top: 7px; font-family: var(--font-data); font-size: 28px; font-weight: 700; line-height: 1.2; font-variant-numeric: tabular-nums; }
    .metric-unit { margin-left: 3px; color: var(--ink-500); font-family: var(--font-sans); font-size: 13px; font-weight: 500; }
    .panel { padding: 24px; border: 1px solid var(--line-300); border-radius: 8px; background: var(--surface); }
    .distribution { display: flex; height: 34px; overflow: hidden; border: 1px solid var(--line-300); border-radius: 4px; }
    .distribution > span { display: grid; min-width: 0; place-items: center; font-family: var(--font-data); font-size: 13px; font-weight: 700; }
    .created { width: $createdPct%; color: var(--success-700); background: var(--success-100); }
    .full { width: $fullPct%; color: var(--warning-700); background: var(--warning-100); }
    .legend { display: flex; flex-wrap: wrap; gap: 16px 28px; margin-top: 14px; color: var(--ink-700); font-size: 14px; }
    .legend strong { font-family: var(--font-data); color: var(--ink-900); font-variant-numeric: tabular-nums; }
    .table-wrap { overflow-x: auto; border: 1px solid var(--line-300); border-radius: 8px; background: var(--surface); }
    table { width: 100%; min-width: 680px; border-collapse: collapse; }
    th, td { padding: 14px 16px; border-bottom: 1px solid var(--line-200); text-align: left; }
    th { color: var(--ink-700); background: #f0f7fb; font-size: 13px; }
    td { font-family: var(--font-data); font-variant-numeric: tabular-nums; }
    tbody tr:last-child td { border-bottom: 0; }
    .notes { display: grid; gap: 12px; margin: 0; padding-left: 22px; color: var(--ink-700); }
    .footer { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 12px; margin-top: 28px; color: var(--ink-500); font-size: 13px; }
    @media (max-width: 760px) {
      .page { width: min(100% - 32px, 1120px); margin-top: 16px; }
      .ticket { padding: 22px 18px 26px; }
      .ticket-top { display: grid; }
      .stamp { width: fit-content; }
      .metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }
      .metric:nth-child(4n) { border-right: 1px solid var(--line-200); }
      .metric:nth-child(2n) { border-right: 0; }
      .metric:nth-last-child(-n+4) { border-bottom: 1px solid var(--line-200); }
      .metric:nth-last-child(-n+2) { border-bottom: 0; }
      .section-heading { display: block; }
      .section-hint { display: block; margin-top: 4px; }
      .table-wrap { overflow: visible; }
      table { min-width: 0; }
      thead { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
      tbody tr { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }
      td { display: grid; gap: 3px; padding: 13px 14px; border-right: 1px solid var(--line-200); }
      td:nth-child(2n) { border-right: 0; }
      td::before { color: var(--ink-500); content: attr(data-label); font-family: var(--font-sans); font-size: 12px; }
    }
    @media (max-width: 340px) {
      .metrics { grid-template-columns: 1fr; }
      .metric, .metric:nth-child(2n), .metric:nth-child(4n) { border-right: 0; border-bottom: 1px solid var(--line-200); }
      .metric:last-child { border-bottom: 0; }
    }
    @media print {
      body { background: #fff; }
      .page { width: 100%; margin: 0; }
      .ticket, .panel, .table-wrap { break-inside: avoid; }
    }
  </style>
</head>
<body>
  <main class="page">
    <header class="ticket">
      <div class="ticket-top">
        <div>
          <p class="eyebrow">QueueMate · 并发预约测试回执</p>
          <h1>JMeter 中文测试概览</h1>
        </div>
        <div class="stamp $verdictClass" aria-label="测试结论：$verdict">$verdict</div>
      </div>
      <hr class="ticket-rule">
      <p class="conclusion">$conclusion</p>
      <p class="meta">生成时间：$generatedAt　·　原始结果：$resultName</p>
    </header>

    <section class="section" aria-labelledby="metrics-title">
      <div class="section-heading">
        <h2 id="metrics-title">关键指标</h2>
        <span class="section-hint">响应时间仅统计 12 个并发预约请求</span>
      </div>
      <div class="metrics">
        <div class="metric"><span class="metric-label">并发预约请求</span><span class="metric-value">$($bookingRows.Count)<span class="metric-unit">个</span></span></div>
        <div class="metric"><span class="metric-label">测试错误率</span><span class="metric-value">$errorRate<span class="metric-unit">%</span></span></div>
        <div class="metric"><span class="metric-label">平均响应时间</span><span class="metric-value">$mean<span class="metric-unit">ms</span></span></div>
        <div class="metric"><span class="metric-label">中位响应时间</span><span class="metric-value">$median<span class="metric-unit">ms</span></span></div>
        <div class="metric"><span class="metric-label">P90 响应时间</span><span class="metric-value">$p90<span class="metric-unit">ms</span></span></div>
        <div class="metric"><span class="metric-label">P95 响应时间</span><span class="metric-value">$p95<span class="metric-unit">ms</span></span></div>
        <div class="metric"><span class="metric-label">最大响应时间</span><span class="metric-value">$maximum<span class="metric-unit">ms</span></span></div>
        <div class="metric"><span class="metric-label">瞬时吞吐量</span><span class="metric-value">$throughput<span class="metric-unit">请求/秒</span></span></div>
      </div>
    </section>

    <section class="section" aria-labelledby="distribution-title">
      <div class="section-heading">
        <h2 id="distribution-title">预约结果分布</h2>
        <span class="section-hint">409 是符合预期的“名额已满”，不属于测试错误</span>
      </div>
      <div class="panel">
        <div class="distribution" role="img" aria-label="预约成功 $created 个，名额已满 $full 个">
          <span class="created">201</span><span class="full">409</span>
        </div>
        <div class="legend">
          <span>预约成功 <strong>$created</strong> 个</span>
          <span>名额已满 <strong>$full</strong> 个</span>
          <span>非预期响应 <strong>$unexpected</strong> 个</span>
        </div>
      </div>
    </section>

    <section class="section" aria-labelledby="response-title">
      <div class="section-heading">
        <h2 id="response-title">响应时间明细</h2>
        <span class="section-hint">P99 为 99% 请求不超过的响应时间</span>
      </div>
      <div class="table-wrap">
        <table>
          <thead><tr><th>平均值</th><th>中位数</th><th>P90</th><th>P95</th><th>P99</th><th>最大值</th></tr></thead>
          <tbody><tr><td data-label="平均值">${mean}ms</td><td data-label="中位数">${median}ms</td><td data-label="P90">${p90}ms</td><td data-label="P95">${p95}ms</td><td data-label="P99">${p99}ms</td><td data-label="最大值">${maximum}ms</td></tr></tbody>
        </table>
      </div>
    </section>

    <section class="section" aria-labelledby="notes-title">
      <div class="section-heading"><h2 id="notes-title">怎么看这份结果</h2></div>
      <div class="panel">
        <ul class="notes">
          <li>本场景主要验证数据库并发控制和防超卖，不代表系统在几百人持续访问时也没有瓶颈。</li>
          <li>$warmupNote</li>
          <li>若要判断系统容量，还需要增加 50、100、300 人阶梯并发，并同时观察 CPU、内存、数据库连接池和慢 SQL。</li>
        </ul>
      </div>
    </section>

    <footer class="footer">
      <span>QueueMate 自动化测试资产</span>
      <a href="index.html">查看 JMeter 原始英文报告</a>
    </footer>
  </main>
</body>
</html>
"@

$outputDirectory = Split-Path -Parent $OutputFile
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
Set-Content -LiteralPath $OutputFile -Value $html -Encoding UTF8

Write-Host "Chinese summary: $OutputFile"
