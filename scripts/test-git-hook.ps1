[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$sourceRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$fixtureRoot = Join-Path $sourceRoot ('build/hook-smoke-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $fixtureRoot | Out-Null
Copy-Item -LiteralPath (Join-Path $sourceRoot 'scripts') -Destination $fixtureRoot -Recurse
Copy-Item -LiteralPath (Join-Path $sourceRoot '.githooks') -Destination $fixtureRoot -Recurse
$smokeSource = Join-Path $fixtureRoot 'src/main/java'
New-Item -ItemType Directory -Force -Path $smokeSource | Out-Null
[System.IO.File]::WriteAllText((Join-Path $smokeSource 'Smoke.java'), "class Smoke {}`n")

Push-Location -LiteralPath $fixtureRoot
try {
    & git init -q
    if ($LASTEXITCODE -ne 0) { throw 'Unable to initialize hook fixture.' }
    & powershell -NoProfile -File scripts/install-git-hooks.ps1
    if ($LASTEXITCODE -ne 0) { throw 'Hook installation failed in fixture.' }

    [System.IO.File]::WriteAllText((Join-Path $fixtureRoot 'gradlew.bat'), "@echo off`r`nexit /b 1`r`n")
    & git add -- .githooks scripts gradlew.bat src
    if ($LASTEXITCODE -ne 0) { throw 'Unable to stage hook fixture.' }
    & git -c user.name=HookSmoke -c user.email=hook-smoke@example.invalid commit -m 'hook must reject lint failure'
    if ($LASTEXITCODE -eq 0) { throw 'Pre-commit hook allowed a failing lint command.' }
    $unexpectedCommits = @(& git rev-list --max-count=1 --all)
    if ($LASTEXITCODE -ne 0 -or $unexpectedCommits.Count -gt 0) {
        throw 'A commit was created after failing lint, or the fixture could not be inspected.'
    }

    [System.IO.File]::WriteAllText((Join-Path $fixtureRoot 'gradlew.bat'), "@echo off`r`nexit /b 0`r`n")
    & git add -- gradlew.bat
    if ($LASTEXITCODE -ne 0) { throw 'Unable to stage passing lint fixture.' }
    & git -c user.name=HookSmoke -c user.email=hook-smoke@example.invalid commit -m 'hook accepts passing lint'
    if ($LASTEXITCODE -ne 0) { throw 'Pre-commit hook rejected a passing lint command.' }

    & git branch -m refactor/backend-quality
    if ($LASTEXITCODE -ne 0) { throw 'Unable to prepare publication branch fixture.' }
    $contextRoot = Join-Path $fixtureRoot 'agent-context'
    New-Item -ItemType Directory -Force -Path $contextRoot | Out-Null
    $state = @{ pendingDecisions = @(@{
        id = 'PUB-001'; status = 'awaiting-user-answer'; blocksScopes = @('setup-checkpoint')
    }) } | ConvertTo-Json -Depth 5
    [System.IO.File]::WriteAllText((Join-Path $contextRoot 'STATE.json'), $state)
    & git add -- agent-context/STATE.json
    if ($LASTEXITCODE -ne 0) { throw 'Unable to stage pending-decision fixture.' }
    $stagedTree = (& git write-tree).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Unable to identify staged fixture tree.' }
    $attestation = @{
        scope = 'setup-checkpoint'
        stagedTree = $stagedTree
        verdict = 'approved'
        reviewer = 'fixture-reviewer'
        reviewedAt = [DateTimeOffset]::UtcNow.ToString('o')
        completedSteps = @(1)
        findings = @()
    }
    [System.IO.File]::WriteAllText((Join-Path $fixtureRoot '.git/review.json'),
            ($attestation | ConvertTo-Json -Depth 5))
    $savedErrorAction = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $publishOutput = & powershell -NoProfile -File scripts/publish-reviewed.ps1 `
            -Scope setup-checkpoint -AttestationPath .git/review.json -Message 'must be blocked' 2>&1
        $publishExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $savedErrorAction
    }
    $publishText = [string]::Join(' ', @($publishOutput | ForEach-Object { $_.ToString() }))
    if ($publishExitCode -eq 0 -or $publishText -notmatch 'PUB-001') {
        throw "Publication did not stop specifically for the unresolved user decision (exit $publishExitCode): $($publishOutput | Out-String)"
    }

    $state = @{ pendingDecisions = @() } | ConvertTo-Json -Depth 5
    [System.IO.File]::WriteAllText((Join-Path $contextRoot 'STATE.json'), $state)
    & git add -- agent-context/STATE.json
    if ($LASTEXITCODE -ne 0) { throw 'Unable to stage resolved-decision fixture.' }
    $stagedTree = (& git write-tree).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Unable to identify step-2 fixture tree.' }
    $attestation.scope = 'step-2-checkpoint'
    $attestation.stagedTree = $stagedTree
    $attestation.completedSteps = @(1)
    [System.IO.File]::WriteAllText((Join-Path $fixtureRoot '.git/review.json'),
            ($attestation | ConvertTo-Json -Depth 5))
    $ErrorActionPreference = 'Continue'
    try {
        $publishOutput = & powershell -NoProfile -File scripts/publish-reviewed.ps1 `
            -Scope step-2-checkpoint -AttestationPath .git/review.json -Message 'must be blocked' 2>&1
        $publishExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $savedErrorAction
    }
    $publishText = [string]::Join(' ', @($publishOutput | ForEach-Object { $_.ToString() }))
    $publishCompact = $publishText -replace '\s', ''
    if ($publishExitCode -eq 0 -or $publishCompact -notmatch "Completedstepsdonotmatchpublicationscope'step-2-checkpoint'") {
        throw "Step-2 publication accepted an incomplete attestation (exit $publishExitCode): $($publishOutput | Out-String)"
    }
    $attestation.scope = 'step-3-checkpoint'
    $attestation.completedSteps = @(1, 2)
    [System.IO.File]::WriteAllText((Join-Path $fixtureRoot '.git/review.json'),
            ($attestation | ConvertTo-Json -Depth 5))
    $ErrorActionPreference = 'Continue'
    try {
        $publishOutput = & powershell -NoProfile -File scripts/publish-reviewed.ps1 `
            -Scope step-3-checkpoint -AttestationPath .git/review.json -Message 'must be blocked' 2>&1
        $publishExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $savedErrorAction
    }
    $publishText = [string]::Join(' ', @($publishOutput | ForEach-Object { $_.ToString() }))
    $publishCompact = $publishText -replace '\s', ''
    if ($publishExitCode -eq 0 -or $publishCompact -notmatch "Completedstepsdonotmatchpublicationscope'step-3-checkpoint'") {
        throw "Step-3 publication accepted an incomplete attestation (exit $publishExitCode): $($publishOutput | Out-String)"
    }
    $attestation.scope = 'step-4-checkpoint'
    $attestation.completedSteps = @(1, 2, 3)
    [System.IO.File]::WriteAllText((Join-Path $fixtureRoot '.git/review.json'),
            ($attestation | ConvertTo-Json -Depth 5))
    $ErrorActionPreference = 'Continue'
    try {
        $publishOutput = & powershell -NoProfile -File scripts/publish-reviewed.ps1 `
            -Scope step-4-checkpoint -AttestationPath .git/review.json -Message 'must be blocked' 2>&1
        $publishExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $savedErrorAction
    }
    $publishText = [string]::Join(' ', @($publishOutput | ForEach-Object { $_.ToString() }))
    $publishCompact = $publishText -replace '\s', ''
    if ($publishExitCode -eq 0 -or $publishCompact -notmatch "Completedstepsdonotmatchpublicationscope'step-4-checkpoint'") {
        throw "Step-4 publication accepted an incomplete attestation (exit $publishExitCode): $($publishOutput | Out-String)"
    }
    $attestation.scope = 'step-5-checkpoint'
    $attestation.completedSteps = @(1, 2, 3, 4)
    [System.IO.File]::WriteAllText((Join-Path $fixtureRoot '.git/review.json'),
            ($attestation | ConvertTo-Json -Depth 5))
    $ErrorActionPreference = 'Continue'
    try {
        $publishOutput = & powershell -NoProfile -File scripts/publish-reviewed.ps1 `
            -Scope step-5-checkpoint -AttestationPath .git/review.json -Message 'must be blocked' 2>&1
        $publishExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $savedErrorAction
    }
    $publishText = [string]::Join(' ', @($publishOutput | ForEach-Object { $_.ToString() }))
    $publishCompact = $publishText -replace '\s', ''
    if ($publishExitCode -eq 0 -or $publishCompact -notmatch "Completedstepsdonotmatchpublicationscope'step-5-checkpoint'") {
        throw "Step-5 publication accepted an incomplete attestation (exit $publishExitCode): $($publishOutput | Out-String)"
    }
    Write-Host 'Git gate smoke test passed: lint failure blocks commit; pending decisions and incomplete step-2/step-3/step-4/step-5 attestations block publication.'
    exit 0
}
finally {
    Pop-Location
}
