try {
    . (Join-Path $PSScriptRoot 'quality-common.ps1')
    Enter-RepositoryRoot
    $stagedTree = Assert-StagedSnapshot
    Invoke-ProjectVerification
    if ((Assert-StagedSnapshot) -ne $stagedTree) {
        throw 'The staged tree changed during lint. Run review and the gate again.'
    }
    Write-Host 'Pre-commit lint passed for the staged tree.'
    exit 0
}
catch {
    Write-Error -ErrorAction Continue $_
    exit 1
}
