Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Invoke-Git {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)
    $result = & git @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Arguments -join ' ') failed with exit code $LASTEXITCODE."
    }
    return $result
}

function Enter-RepositoryRoot {
    $repositoryRoot = (Invoke-Git -Arguments @('rev-parse', '--show-toplevel')).Trim()
    Set-Location -LiteralPath $repositoryRoot
}

function Assert-StagedSnapshot {
    # Tests and Gradle read disk, while Git commits the index. They must agree.
    & git diff --quiet --ignore-submodules --
    if ($LASTEXITCODE -ne 0) {
        throw 'Unstaged tracked changes exist. Stage the reviewed result before running the gate.'
    }
    $untracked = @(Invoke-Git -Arguments @('ls-files', '--others', '--exclude-standard'))
    $untrackedInputs = @($untracked | Where-Object {
        $_ -match '^(src/|config/|gradle/|scripts/|\.githooks/|build\.gradle$|settings\.gradle$|gradle\.properties$|gradlew(?:\.bat)?$)'
    })
    if ($untrackedInputs.Count -gt 0) {
        throw "Untracked build/gate inputs must be staged: $($untrackedInputs -join ', ')"
    }
    & git diff --cached --check
    if ($LASTEXITCODE -ne 0) {
        throw 'Staged whitespace or merge-marker check failed.'
    }
    return (Invoke-Git -Arguments @('write-tree')).Trim()
}

function Invoke-ProjectVerification {
    param([switch]$IncludeTests)
    $gradle = if ($env:OS -eq 'Windows_NT') { '.\gradlew.bat' } else { './gradlew' }
    $tasks = @('lint', '--rerun-tasks', '--no-build-cache')
    if ($IncludeTests) {
        $tasks = @('lint', 'test', 'sampleDataClasses', '--rerun-tasks', '--no-build-cache')
    }
    & $gradle @tasks
    if ($LASTEXITCODE -ne 0) {
        throw 'Gradle quality checks failed. Commit and push are blocked.'
    }
}
