# Recover to a brand-new directory; never modify the original checkout.
[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$Backup, [Parameter(Mandatory=$true)][string]$Destination)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'kome-dev-common.ps1')
try {
    $Backup = [IO.Path]::GetFullPath($Backup)
    $Destination = [IO.Path]::GetFullPath($Destination)
    if (Test-Path -LiteralPath $Destination) { throw 'Recovery destination must not exist. Choose a NEW directory.' }
    Assert-KomeNoReparse $Destination
    $verified = Read-KomeBackup $Backup
    if ((Test-KomeUnderPath $Destination $verified.Metadata.Repository) -or (Test-KomeUnderPath $Destination $Backup)) { throw 'Recovery must be outside both the original checkout and the backup.' }
    [void][IO.Directory]::CreateDirectory($Destination)
    foreach ($entry in $verified.Entries) {
        if ($entry.Path -eq 'snapshot.json') { continue }
        $relative = if ($entry.Path.StartsWith('git/')) { '.git/' + $entry.Path.Substring(4) } else { $entry.Path.Substring(14) }
        Copy-KomeFile (Resolve-KomeChild $Backup $entry.Path) (Resolve-KomeChild $Destination $relative)
    }
    Assert-KomePrimaryCheckout $Destination
    $state = Get-KomeState $Destination
    foreach ($key in @('Head','Branch','Status','Refs','Stashes')) {
        if ($state[$key] -ne $verified.Metadata.State.$key) { throw "Recovered $key differs from snapshot. Leave the original untouched; inspect $Destination" }
    }
    $check = Invoke-KomeGit $Destination @('fsck','--full')
    Write-KomeUtf8 (Join-Path $Destination '.git/overnight-recovery-fsck.txt') ($check.Text + $check.ErrorText)
    Write-Host "Recovered and verified checkout: $Destination"
    Write-Host 'HEAD, branch refs, staged/unstaged/untracked state and the stash stack match. No worlds were restored.'
    exit 0
} catch { Write-Error -ErrorAction Continue $_.Exception.Message; exit 1 }
