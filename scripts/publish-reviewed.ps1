[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('setup-checkpoint', 'full-refactor')]
    [string]$Scope,
    [Parameter(Mandatory = $true)]
    [string]$AttestationPath,
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$Message
)

try {
    . (Join-Path $PSScriptRoot 'quality-common.ps1')
    $attestationFile = (Resolve-Path -LiteralPath $AttestationPath).Path
    Enter-RepositoryRoot
    $branch = (Invoke-Git -Arguments @('branch', '--show-current')).Trim()
    if ($branch -ne 'refactor/backend-quality') {
        throw 'Automatic publication is restricted to refactor/backend-quality.'
    }
    $hooksPath = (Invoke-Git -Arguments @('config', '--get', 'core.hooksPath')).Trim()
    if ($hooksPath -ne '.githooks') {
        throw 'Install the mandatory lint hook with scripts/install-git-hooks.ps1 first.'
    }
    $stagedTree = Assert-StagedSnapshot
    & git diff --cached --quiet
    if ($LASTEXITCODE -eq 0) { throw 'There is no staged change to publish.' }
    if ($LASTEXITCODE -ne 1) { throw 'Unable to inspect staged changes.' }

    $review = Get-Content -LiteralPath $attestationFile -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($review.verdict -ne 'approved' -or [string]::IsNullOrWhiteSpace($review.reviewer)) {
        throw 'An independent reviewer must return verdict=approved and identify the reviewer.'
    }
    if ($review.scope -ne $Scope -or $review.stagedTree -ne $stagedTree) {
        throw 'Review scope/tree does not match the exact staged tree. Review again after any edit.'
    }
    if (@($review.findings).Count -ne 0) { throw 'Open review findings block publication.' }
    $reviewTime = [DateTimeOffset]::Parse($review.reviewedAt)
    if ($reviewTime -gt [DateTimeOffset]::UtcNow.AddMinutes(5)) { throw 'Review timestamp is in the future.' }
    $completed = @($review.completedSteps | Sort-Object -Unique)
    $expectedSteps = if ($Scope -eq 'setup-checkpoint') { @(1) } else { @(1, 2, 3, 4, 5, 6) }
    if (@(Compare-Object $completed $expectedSteps).Count -ne 0) {
        throw "Completed steps do not match publication scope '$Scope'."
    }

    $workflowState = Get-Content -LiteralPath 'agent-context/STATE.json' -Raw -Encoding UTF8 | ConvertFrom-Json
    $blockingDecisions = @($workflowState.pendingDecisions | Where-Object {
        $_.status -ne 'resolved' -and @($_.blocksScopes) -contains $Scope
    })
    if ($blockingDecisions.Count -gt 0) {
        throw "User decisions are still pending for '$Scope': $($blockingDecisions.id -join ', ')."
    }

    Invoke-ProjectVerification -IncludeTests
    if ((Assert-StagedSnapshot) -ne $stagedTree) { throw 'Staged tree changed during verification. Review again.' }
    # Do not set skip flags or use --no-verify: the installed hook independently runs lint.
    Invoke-Git -Arguments @('commit', '-m', $Message)
    $committedTree = (Invoke-Git -Arguments @('rev-parse', 'HEAD^{tree}')).Trim()
    if ($committedTree -ne $stagedTree) { throw 'Commit tree differs from reviewed tree. Push is blocked.' }
    if ((Invoke-Git -Arguments @('branch', '--show-current')).Trim() -ne $branch) {
        throw 'Branch changed while committing. Push is blocked.'
    }
    # A failed/non-fast-forward push is a stop condition; never force or merge main.
    Invoke-Git -Arguments @('push', '--set-upstream', 'origin', 'refactor/backend-quality')
    Write-Host "Published reviewed scope '$Scope'."
    exit 0
}
catch {
    Write-Error -ErrorAction Continue $_
    exit 1
}
