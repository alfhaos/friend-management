try {
    . (Join-Path $PSScriptRoot 'quality-common.ps1')
    Enter-RepositoryRoot
    $existingHooksPath = & git config --get core.hooksPath
    if ($LASTEXITCODE -gt 1) {
        throw 'Unable to read core.hooksPath.'
    }
    if ($existingHooksPath -and $existingHooksPath.Trim() -ne '.githooks') {
        throw "core.hooksPath already points to '$existingHooksPath'. Integrate that hook before installing this gate."
    }
    Invoke-Git -Arguments @('config', '--local', 'core.hooksPath', '.githooks')
    Write-Host 'Installed repository pre-commit lint hook (.githooks). Do not use --no-verify.'
    exit 0
}
catch {
    Write-Error -ErrorAction Continue $_
    exit 1
}
