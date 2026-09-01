param (
    [Parameter(Mandatory = $true)]
    [ValidateSet("atomic", "pessimistic", "optimistic", "redis")]
    [string]$Strategy
)

$BaseUrl = "http://localhost:8080"
$K6Script = ".\k6\entry-burst.js"

$WarmupRuns = 1
$MeasuredRuns = 5

function Create-Event {
    param (
        [string]$Name
    )

    $now = Get-Date

    $body = @{
        name = $Name
        capacity = 100
        strategy = $Strategy.ToUpper()
        openAt = $now.AddMinutes(-1).ToString("yyyy-MM-ddTHH:mm:ss")
        closeAt = $now.AddHours(2).ToString("yyyy-MM-ddTHH:mm:ss")
    } | ConvertTo-Json

    $response = Invoke-RestMethod `
        -Uri "$BaseUrl/api/events" `
        -Method Post `
        -ContentType "application/json" `
        -Body $body

    return $response.id
}

Write-Host ""
Write-Host "========================================"
Write-Host " 선착순 최종 벤치마크"
Write-Host " Strategy: $Strategy"
Write-Host " Warm-up : $WarmupRuns"
Write-Host " Measure : $MeasuredRuns"
Write-Host "========================================"
Write-Host ""

# Warm-up
for ($i = 1; $i -le $WarmupRuns; $i++) {

    $eventName = "$Strategy Warmup $i"
    $eventId = Create-Event -Name $eventName

    Write-Host ""
    Write-Host "----------------------------------------"
    Write-Host "[WARM-UP] Event ID: $eventId"
    Write-Host "----------------------------------------"

    k6 run `
        -e EVENT_ID=$eventId `
        -e STRATEGY=$Strategy `
        $K6Script

    Start-Sleep -Seconds 10
}

# Measured runs
for ($i = 1; $i -le $MeasuredRuns; $i++) {

    $eventName = "$Strategy Benchmark Run $i"
    $eventId = Create-Event -Name $eventName

    Write-Host ""
    Write-Host "========================================"
    Write-Host "[MEASURE $i/$MeasuredRuns]"
    Write-Host "Strategy : $Strategy"
    Write-Host "Event ID : $eventId"
    Write-Host "========================================"

    k6 run `
        -e EVENT_ID=$eventId `
        -e STRATEGY=$Strategy `
        $K6Script

    if ($i -lt $MeasuredRuns) {
        Write-Host ""
        Write-Host "10초 대기..."
        Start-Sleep -Seconds 10
    }
}

Write-Host ""
Write-Host "========================================"
Write-Host "$Strategy benchmark completed"
Write-Host "========================================"