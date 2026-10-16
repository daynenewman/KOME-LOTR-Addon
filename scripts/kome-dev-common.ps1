# Shared PowerShell 5.1+ helpers. Dot-source from the development scripts.
Set-StrictMode -Version Latest

function ConvertTo-KomeNativeArgument([string]$Value) {
    if ($Value.Length -gt 0 -and $Value -notmatch '[\s"]') { return $Value }
    '"' + [regex]::Replace([regex]::Replace($Value, '(\\*)"', '$1$1\"'), '(\\+)$', '$1$1') + '"'
}

function Invoke-KomeGit([string]$Repository, [string[]]$GitArgs, [switch]$AllowFailure) {
    $info = New-Object System.Diagnostics.ProcessStartInfo
    $info.FileName = (Get-Command git.exe -ErrorAction Stop).Source
    # Trust only the explicitly selected checkout for this invocation; no global Git config edits.
    $nativeArgs = @('--no-optional-locks', '-c', ('safe.directory='+$Repository.Replace('\','/')), '-c', 'core.fsmonitor=false', '-C', $Repository) + $GitArgs
    $info.Arguments = ($nativeArgs | ForEach-Object { ConvertTo-KomeNativeArgument $_ }) -join ' '
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.StandardOutputEncoding = New-Object System.Text.UTF8Encoding($false)
    $info.StandardErrorEncoding = $info.StandardOutputEncoding
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $info
    try {
        [void]$process.Start()
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        $process.WaitForExit()
        $result = [pscustomobject]@{ Code=$process.ExitCode; Text=$stdout.Result; ErrorText=$stderr.Result }
        if (!$AllowFailure -and $result.Code -ne 0) { throw "git $($GitArgs -join ' ') exited $($result.Code): $($result.ErrorText)" }
        return $result
    } finally { $process.Dispose() }
}

function Get-KomeGitText([string]$Repository, [string[]]$GitArgs) { (Invoke-KomeGit $Repository $GitArgs).Text }

function Get-KomeRepository([string]$ScriptsDirectory) {
    $root = [IO.Path]::GetFullPath((Join-Path $ScriptsDirectory '..'))
    $actual = (Get-KomeGitText $root @('rev-parse', '--show-toplevel')).Trim()
    if ([IO.Path]::GetFullPath($actual) -ne $root) { throw 'Scripts must reside in the repository scripts directory.' }
    return $root
}

function ConvertTo-KomeLongPath([string]$Path) {
    $full=[IO.Path]::GetFullPath($Path)
    if ($full.StartsWith('\\?\')) { return $full }
    if ($full.StartsWith('\\')) { return '\\?\UNC\'+$full.Substring(2) }
    return '\\?\'+$full
}
function Test-KomePath([string]$Path) { $full=ConvertTo-KomeLongPath $Path; [IO.File]::Exists($full) -or [IO.Directory]::Exists($full) }
function Read-KomeUtf8([string]$Path) { [IO.File]::ReadAllText((ConvertTo-KomeLongPath $Path),[Text.Encoding]::UTF8) }
function Write-KomeUtf8([string]$Path, [string]$Text) { [IO.File]::WriteAllText((ConvertTo-KomeLongPath $Path), $Text, (New-Object System.Text.UTF8Encoding($false))) }
function Write-KomeJson([string]$Path, $Value) { Write-KomeUtf8 $Path ($Value | ConvertTo-Json -Depth 12) }

function Test-KomeUnderPath([string]$Path, [string]$Root) {
    $full = [IO.Path]::GetFullPath($Path).TrimEnd('\', '/')
    $base = [IO.Path]::GetFullPath($Root).TrimEnd('\', '/')
    return $full.Equals($base, [StringComparison]::OrdinalIgnoreCase) -or
        $full.StartsWith($base + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)
}

function Resolve-KomeChild([string]$Root, [string]$RelativePath) {
    if ([IO.Path]::IsPathRooted($RelativePath) -or $RelativePath -match '(^|[/\\])\.\.([/\\]|$)|:') { throw "Unsafe relative path: $RelativePath" }
    $full = [IO.Path]::GetFullPath((Join-Path $Root $RelativePath))
    if (!(Test-KomeUnderPath $full $Root) -or $full -eq [IO.Path]::GetFullPath($Root)) { throw "Path escapes root: $RelativePath" }
    return $full
}

function Assert-KomeNoReparse([string]$Path) {
    $cursor = [IO.Path]::GetFullPath($Path)
    while ($cursor) {
        if (Test-KomePath $cursor) {
            if (([IO.File]::GetAttributes((ConvertTo-KomeLongPath $cursor)) -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw "Reparse points/symbolic links are unsupported: $cursor" }
        }
        $cursor = [IO.Path]::GetDirectoryName($cursor)
    }
}

function Get-KomeTreeFiles([string]$Root) {
    Assert-KomeNoReparse $Root
    $pending = New-Object 'System.Collections.Generic.Stack[string]'
    $pending.Push($Root)
    while ($pending.Count -gt 0) {
        $directory = $pending.Pop()
        foreach ($entry in [IO.Directory]::EnumerateFileSystemEntries((ConvertTo-KomeLongPath $directory))) {
            $attributes=[IO.File]::GetAttributes($entry)
            $normal=if ($entry.StartsWith('\\?\UNC\')) { '\\'+$entry.Substring(8) } else { $entry.Substring(4) }
            if (($attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw "Unsupported link: $normal" }
            if (($attributes -band [IO.FileAttributes]::Directory) -ne 0) { $pending.Push($normal) } else { $normal }
        }
    }
}

function Get-KomeHash([string]$Path) {
    $stream=[IO.File]::OpenRead((ConvertTo-KomeLongPath $Path))
    $algorithm=[Security.Cryptography.SHA256]::Create()
    try { ([BitConverter]::ToString($algorithm.ComputeHash($stream))).Replace('-','').ToLowerInvariant() }
    finally { $algorithm.Dispose(); $stream.Dispose() }
}

function Copy-KomeFile([string]$Source, [string]$Target) {
    Assert-KomeNoReparse $Source
    Assert-KomeNoReparse $Target
    [void][IO.Directory]::CreateDirectory((ConvertTo-KomeLongPath ([IO.Path]::GetDirectoryName($Target))))
    [IO.File]::Copy((ConvertTo-KomeLongPath $Source), (ConvertTo-KomeLongPath $Target), $false)
    if ((Get-KomeHash $Source) -ne (Get-KomeHash $Target)) { throw "Copy verification failed: $Source" }
}

function Get-KomeSourcePaths([string]$Repository) {
    $text = Get-KomeGitText $Repository @('ls-files', '-z', '--cached', '--others', '--exclude-standard')
    @($text.Split([char]0) | Where-Object { $_.Length -gt 0 } | Sort-Object -Unique)
}

function Get-KomeInputFingerprint([string]$Repository) {
    $paths=@(Get-KomeSourcePaths $Repository)+@('libs/LOTRMod v36.15.jar','libs/geckolib-unofficial-1.7.10-1.0.4.jar')
    $rows=New-Object Text.StringBuilder
    foreach ($relative in $paths | Sort-Object -Unique) {
        $full=Resolve-KomeChild $Repository $relative
        $hash=if ([IO.File]::Exists((ConvertTo-KomeLongPath $full))) { Get-KomeHash $full } else { 'MISSING' }
        [void]$rows.Append($relative).Append([char]0).Append($hash).Append([char]0)
    }
    $indexPath=(Get-KomeGitText $Repository @('rev-parse','--git-path','index')).Trim()
    if (![IO.Path]::IsPathRooted($indexPath)) { $indexPath=Join-Path $Repository $indexPath }
    $indexHash=if ([IO.File]::Exists((ConvertTo-KomeLongPath $indexPath))) { Get-KomeHash $indexPath } else { 'MISSING' }
    [void]$rows.Append('INDEX').Append([char]0).Append($indexHash)
    $algorithm=[Security.Cryptography.SHA256]::Create()
    try { ([BitConverter]::ToString($algorithm.ComputeHash([Text.Encoding]::UTF8.GetBytes($rows.ToString())))).Replace('-','').ToLowerInvariant() }
    finally { $algorithm.Dispose() }
}

function Get-KomeState([string]$Repository) {
    [ordered]@{
        Head=(Get-KomeGitText $Repository @('rev-parse', 'HEAD')).Trim()
        Branch=(Get-KomeGitText $Repository @('branch', '--show-current')).Trim()
        Status=Get-KomeGitText $Repository @('status', '--porcelain=v1', '-z', '--untracked-files=all')
        Refs=Get-KomeGitText $Repository @('for-each-ref', '--format=%(refname) %(objectname)')
        Stashes=(Invoke-KomeGit $Repository @('reflog', 'show', '--format=%H %gd %gs', 'refs/stash') -AllowFailure).Text
        Worktrees=Get-KomeGitText $Repository @('worktree', 'list', '--porcelain')
    }
}

function Assert-KomePrimaryCheckout([string]$Repository) {
    if (!(Test-Path -LiteralPath (Join-Path $Repository '.git') -PathType Container)) { throw 'Raw Git recovery requires a primary checkout with a .git directory; linked worktrees are unsupported.' }
    if (([regex]::Matches((Get-KomeGitText $Repository @('worktree', 'list', '--porcelain')), '(?m)^worktree ')).Count -ne 1) { throw 'Use a dedicated primary checkout: registered additional worktrees are unsupported by raw Git recovery.' }
    if ((Invoke-KomeGit $Repository @('config', '--get', 'core.worktree') -AllowFailure).Text.Trim()) { throw 'External core.worktree is unsupported.' }
    if (Test-Path -LiteralPath (Join-Path $Repository '.git/objects/info/alternates')) { throw 'External Git object alternates are unsupported.' }
    if ((Get-KomeGitText $Repository @('ls-files', '--stage')) -match '(?m)^160000 ') { throw 'Submodules require a separate backup procedure.' }
    $locks = @(Get-KomeTreeFiles (Join-Path $Repository '.git') | Where-Object { $_ -like '*.lock' })
    if ($locks.Count -gt 0) { throw "Active Git locks found. Wait for the writer: $($locks -join ', ')" }
}

function Read-KomeBackup([string]$Backup) {
    Assert-KomeNoReparse $Backup
    $complete = Read-KomeUtf8 (Join-Path $Backup 'COMPLETE.json') | ConvertFrom-Json
    $manifestPath = Join-Path $Backup 'manifest.json'
    if ($complete.Version -ne 1 -or (Get-KomeHash $manifestPath) -ne $complete.ManifestSha256) { throw 'Backup manifest integrity check failed.' }
    $entries = ConvertFrom-Json -InputObject (Read-KomeUtf8 $manifestPath)
    $seen = @{}
    foreach ($entry in $entries) {
        if ($entry.Path -notmatch '^(git/|working-files/|snapshot\.json$)' -or $seen.ContainsKey($entry.Path)) { throw "Invalid/duplicate backup entry: $($entry.Path)" }
        $seen[$entry.Path] = $true
        $file = Resolve-KomeChild $Backup $entry.Path
        Assert-KomeNoReparse $file
        if ((Get-KomeHash $file) -ne $entry.Sha256) { throw "Backup SHA256 mismatch: $($entry.Path)" }
    }
    if (!$seen.ContainsKey('snapshot.json') -or !$seen.ContainsKey('git/HEAD') -or !$seen.ContainsKey('git/config')) { throw 'Incomplete source/Git backup.' }
    $metadata = Read-KomeUtf8 (Join-Path $Backup 'snapshot.json') | ConvertFrom-Json
    if ($metadata.Version -ne 1) { throw 'Unsupported backup version.' }
    [pscustomobject]@{ Entries=$entries; Metadata=$metadata }
}
