# Source/dependency snapshot, including the complete local Git object database and reflogs.
[CmdletBinding()]
param([string]$DestinationRoot = '')
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'kome-dev-common.ps1')
$backupFolder = $null
try {
    $repo = Get-KomeRepository $PSScriptRoot
    Assert-KomePrimaryCheckout $repo
    $before = Get-KomeState $repo
    if (!$DestinationRoot) { $DestinationRoot = Join-Path $repo 'outputs/overnight-backups' }
    $DestinationRoot = [IO.Path]::GetFullPath($DestinationRoot)
    if ((Test-KomeUnderPath $DestinationRoot $repo) -and !(Test-KomeUnderPath $DestinationRoot (Join-Path $repo 'outputs/overnight-backups'))) { throw 'Inside the checkout, backups must be under ignored outputs/overnight-backups.' }
    Assert-KomeNoReparse $DestinationRoot
    $stamp = (Get-Date -Format 'yyyyMMdd-HHmmss-fff') + '-' + [guid]::NewGuid().ToString('N').Substring(0,8)
    $backupFolder = Join-Path $DestinationRoot $stamp
    if (Test-Path -LiteralPath $backupFolder) { throw 'Backup destination already exists.' }
    [void][IO.Directory]::CreateDirectory($backupFolder)
    $entries = New-Object 'System.Collections.Generic.List[object]'
    $originals = New-Object 'System.Collections.Generic.List[object]'
    $paths = @(Get-KomeSourcePaths $repo)
    $dependencies = @('libs/LOTRMod v36.15.jar','libs/geckolib-unofficial-1.7.10-1.0.4.jar')
    foreach ($dependency in $dependencies) { if (Test-Path -LiteralPath (Join-Path $repo $dependency)) { $paths += $dependency } }
    $paths = @($paths | Sort-Object -Unique)
    $missing = @()
    foreach ($relative in $paths) {
        if ($relative -match '^(run|runs|saves|world|worlds)(/|$)') { throw "Refusing runtime/save data in source snapshot: $relative" }
        $source = Resolve-KomeChild $repo $relative
        Assert-KomeNoReparse $source
        if (![IO.File]::Exists((ConvertTo-KomeLongPath $source))) { $missing += $relative; continue }
        $stored = 'working-files/' + $relative.Replace('\','/')
        Copy-KomeFile $source (Resolve-KomeChild $backupFolder $stored)
        $hash = Get-KomeHash $source
        $entries.Add([pscustomobject]@{ Path=$stored; Sha256=$hash })
        $originals.Add([pscustomobject]@{ Path=$source; Sha256=$hash })
    }
    $gitRoot = Join-Path $repo '.git'
    $gitFiles = @(Get-KomeTreeFiles $gitRoot | Sort-Object)
    foreach ($source in $gitFiles) {
        $relative = $source.Substring($gitRoot.Length + 1).Replace('\','/')
        $stored = 'git/' + $relative
        Copy-KomeFile $source (Resolve-KomeChild $backupFolder $stored)
        $hash = Get-KomeHash $source
        $entries.Add([pscustomobject]@{ Path=$stored; Sha256=$hash })
        $originals.Add([pscustomobject]@{ Path=$source; Sha256=$hash })
    }
    # Refuse a torn snapshot if another source editor or Git writer ran.
    foreach ($file in $originals) { if ((Get-KomeHash $file.Path) -ne $file.Sha256) { throw "Source changed during backup: $($file.Path)" } }
    foreach ($relative in $missing) { if (Test-KomePath (Join-Path $repo $relative)) { throw "Deleted file reappeared: $relative" } }
    $afterPaths = @(Get-KomeSourcePaths $repo)
    $beforePaths = @($paths | Where-Object { $_ -notin $dependencies })
    if (@(Compare-Object $beforePaths $afterPaths).Count -gt 0 -or
        ((Get-KomeTreeFiles $gitRoot | Sort-Object) -join "`n") -ne ($gitFiles -join "`n") -or
        ($before | ConvertTo-Json -Compress) -ne ((Get-KomeState $repo) | ConvertTo-Json -Compress)) { throw 'Repository changed during backup. Stop concurrent writers and retry.' }
    Write-KomeJson (Join-Path $backupFolder 'snapshot.json') ([ordered]@{
        Version=1; Created=(Get-Date -Format o); Repository=$repo; State=$before; MissingFiles=$missing
        Scope='All tracked/nonignored untracked files, complete primary .git, and the two local dependency jars. Ignored runtimes, worlds, caches and outputs excluded.'
    })
    $entries.Add([pscustomobject]@{ Path='snapshot.json'; Sha256=(Get-KomeHash (Join-Path $backupFolder 'snapshot.json')) })
    Write-KomeJson (Join-Path $backupFolder 'manifest.json') @($entries.ToArray())
    Write-KomeJson (Join-Path $backupFolder 'COMPLETE.json') @{ Version=1; ManifestSha256=(Get-KomeHash (Join-Path $backupFolder 'manifest.json')) }
    [void](Read-KomeBackup $backupFolder)
    Write-Host "Verified source/Git backup: $backupFolder ($($entries.Count) hashed files)"
    Write-Host 'Recover only into a NEW directory with scripts/kome-overnight-restore.ps1. This is not a world backup.'
    Write-Output $backupFolder
    exit 0
} catch {
    if ($backupFolder -and (Test-Path -LiteralPath $backupFolder)) {
        $marker = Join-Path $backupFolder 'COMPLETE.json'
        if (Test-KomePath $marker) { [IO.File]::Delete((ConvertTo-KomeLongPath $marker)) }
        Write-KomeUtf8 (Join-Path $backupFolder 'FAILED.txt') $_.Exception.Message
    }
    Write-Error -ErrorAction Continue $_.Exception.Message
    exit 1
}
