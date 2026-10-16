# Dependency-free integration tests. All Git mutations occur in disposable repositories.
[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'kome-dev-common.ps1')
$repo=Get-KomeRepository $PSScriptRoot
$testRoot=Join-Path $repo ('outputs/overnight-tool-tests/'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff')+'-'+[guid]::NewGuid().ToString('N').Substring(0,8))
[void][IO.Directory]::CreateDirectory($testRoot)
$checks=New-Object 'System.Collections.Generic.List[string]'

function Assert-Test([bool]$Condition,[string]$Name) {
    if (!$Condition) { throw "FAIL: $Name" }
    $checks.Add($Name)
    Write-Host "PASS: $Name"
}

function Invoke-TestScript([string]$Script,[string[]]$Parameters=@()) {
    $info=New-Object System.Diagnostics.ProcessStartInfo
    $info.FileName=Join-Path $env:SystemRoot 'System32/WindowsPowerShell/v1.0/powershell.exe'
    $info.Arguments=(@('-NoProfile','-ExecutionPolicy','Bypass','-File',$Script)+$Parameters | ForEach-Object { ConvertTo-KomeNativeArgument $_ }) -join ' '
    $info.WorkingDirectory=$testRoot # Deliberately not the fixture repository root.
    $info.UseShellExecute=$false
    $info.CreateNoWindow=$true
    $info.RedirectStandardOutput=$true
    $info.RedirectStandardError=$true
    $process=New-Object System.Diagnostics.Process
    $process.StartInfo=$info
    try {
        [void]$process.Start()
        $outTask=$process.StandardOutput.ReadToEndAsync()
        $errorTask=$process.StandardError.ReadToEndAsync()
        $process.WaitForExit()
        $result=[pscustomobject]@{ Code=$process.ExitCode; Text=$outTask.Result+$errorTask.Result }
        Write-KomeUtf8 (Join-Path $testRoot ('command-'+[guid]::NewGuid().ToString('N')+'.log')) $result.Text
        return $result
    } finally { $process.Dispose() }
}

try {
    foreach ($file in Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.ps1') {
        $tokens=$null; $parseErrors=$null
        [void][Management.Automation.Language.Parser]::ParseFile($file.FullName,[ref]$tokens,[ref]$parseErrors)
        Assert-Test (@($parseErrors).Count -eq 0) ('Parse '+$file.Name)
    }
    $fixture=Join-Path $testRoot 'source with spaces'
    [void][IO.Directory]::CreateDirectory((Join-Path $fixture 'scripts'))
    foreach ($file in Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.ps1') { Copy-KomeFile $file.FullName (Join-Path $fixture ('scripts/'+$file.Name)) }
    [void](Invoke-KomeGit $fixture @('init','-b','fixture'))
    [void](Invoke-KomeGit $fixture @('config','user.name','KOME disposable tooling test'))
    [void](Invoke-KomeGit $fixture @('config','user.email','kome-tool-test@example.invalid'))
    Write-KomeUtf8 (Join-Path $fixture '.gitignore') "outputs/`nbuild/`nrun/`nlibs/*.jar`n"
    foreach ($name in @('staged.txt','deleted.txt','renamed.txt','stash-only.txt')) { Write-KomeUtf8 (Join-Path $fixture $name) "base`n" }
    [IO.File]::WriteAllBytes((Join-Path $fixture 'binary.dat'),[byte[]]@(0,255,1,2,3))
    [void](Invoke-KomeGit $fixture @('add','.'))
    [void](Invoke-KomeGit $fixture @('commit','-m','Disposable baseline'))
    [void](Invoke-KomeGit $fixture @('branch','preserved-branch'))
    [void](Invoke-KomeGit $fixture @('tag','preserved-tag'))
    foreach ($text in @('older stash','newer stash')) {
        Write-KomeUtf8 (Join-Path $fixture 'stash-only.txt') $text
        [void](Invoke-KomeGit $fixture @('stash','push','-m',$text))
    }
    Write-KomeUtf8 (Join-Path $fixture 'staged.txt') 'staged version'
    [void](Invoke-KomeGit $fixture @('add','staged.txt'))
    Write-KomeUtf8 (Join-Path $fixture 'staged.txt') 'unstaged version'
    Remove-Item -LiteralPath (Join-Path $fixture 'deleted.txt')
    [void](Invoke-KomeGit $fixture @('mv','renamed.txt','renamed with spaces.txt'))
    [IO.File]::WriteAllBytes((Join-Path $fixture 'binary.dat'),[byte[]]@(0,254,253,128,9))
    $oddName='untracked [literal] '+[char]0x03bb+'.txt'
    Write-KomeUtf8 (Join-Path $fixture $oddName) ('Unicode bytes '+[char]0x03bb)
    [void][IO.Directory]::CreateDirectory((Join-Path $fixture 'libs'))
    foreach ($dependency in @('LOTRMod v36.15.jar','geckolib-unofficial-1.7.10-1.0.4.jar')) { Write-KomeUtf8 (Join-Path $fixture ('libs/'+$dependency)) 'fixture only; not a real jar' }
    [void][IO.Directory]::CreateDirectory((Join-Path $fixture 'run/world'))
    Write-KomeUtf8 (Join-Path $fixture 'run/world/sentinel.dat') 'must never be copied or modified'
    $before=Get-KomeState $fixture
    $backupScript=Join-Path $fixture 'scripts/kome-overnight-backup.ps1'
    $result=Invoke-TestScript $backupScript
    Assert-Test ($result.Code -eq 0) ('Backup succeeds: '+$result.Text.Trim())
    $backup=(Get-ChildItem -LiteralPath (Join-Path $fixture 'outputs/overnight-backups') -Directory | Select-Object -First 1).FullName
    $verified=Read-KomeBackup $backup
    Assert-Test (($before | ConvertTo-Json -Compress) -eq ((Get-KomeState $fixture) | ConvertTo-Json -Compress)) 'Backup leaves source Git state unchanged'
    Assert-Test (!(Test-Path -LiteralPath (Join-Path $backup 'working-files/run'))) 'Ignored world sentinel excluded'
    Assert-Test ((Get-Content -LiteralPath (Join-Path $fixture 'run/world/sentinel.dat') -Raw) -eq 'must never be copied or modified') 'World sentinel untouched'
    $restoreScript=Join-Path $fixture 'scripts/kome-overnight-restore.ps1'
    $recovered=Join-Path $testRoot 'recovered checkout'
    $result=Invoke-TestScript $restoreScript @('-Backup',$backup,'-Destination',$recovered)
    Assert-Test ($result.Code -eq 0) ('Recovery succeeds: '+$result.Text.Trim())
    $recoveredState=Get-KomeState $recovered
    foreach ($key in @('Head','Branch','Status','Refs','Stashes')) { Assert-Test ($before[$key] -eq $recoveredState[$key]) ('Recover exact '+$key) }
    Assert-Test ((Get-KomeGitText $recovered @('show','stash@{1}:stash-only.txt')).Trim() -eq 'older stash') 'Older stash content is recoverable'
    Assert-Test ((Get-KomeGitText $fixture @('diff','--cached','--binary')) -eq (Get-KomeGitText $recovered @('diff','--cached','--binary'))) 'Staged patch retained exactly'
    Assert-Test ((Get-KomeGitText $fixture @('diff','--binary')) -eq (Get-KomeGitText $recovered @('diff','--binary'))) 'Unstaged and binary patches retained exactly'
    Assert-Test ((Get-KomeHash (Join-Path $fixture $oddName)) -eq (Get-KomeHash (Join-Path $recovered $oddName))) 'Literal bracket/Unicode filename and bytes retained'
    Assert-Test (Test-Path -LiteralPath (Join-Path $recovered 'libs/LOTRMod v36.15.jar')) 'Ignored local build dependency retained'
    $result=Invoke-TestScript $restoreScript @('-Backup',$backup,'-Destination',$fixture)
    Assert-Test ($result.Code -ne 0) 'Recovery refuses an existing/original checkout'
    Write-KomeUtf8 (Join-Path $backup 'working-files/staged.txt') 'corrupted fixture backup'
    $badTarget=Join-Path $testRoot 'must-not-exist'
    $result=Invoke-TestScript $restoreScript @('-Backup',$backup,'-Destination',$badTarget)
    Assert-Test ($result.Code -ne 0 -and !(Test-Path -LiteralPath $badTarget)) 'Corrupt backup rejected before destination creation'
    Write-KomeUtf8 (Join-Path $fixture '.git/index.lock') 'fixture lock'
    $result=Invoke-TestScript $backupScript
    Assert-Test ($result.Code -ne 0) 'Backup refuses an active Git lock'
    Remove-Item -LiteralPath (Join-Path $fixture '.git/index.lock')

    # Fake wrapper checks argument routing/exit handling. Real Java validation runs separately.
    [void][IO.Directory]::CreateDirectory((Join-Path $fixture 'outputs'))
    [void][IO.Directory]::CreateDirectory((Join-Path $fixture 'build/test-results/test'))
    [void][IO.Directory]::CreateDirectory((Join-Path $fixture 'build/libs'))
    Write-KomeUtf8 (Join-Path $fixture 'outputs/fixture-exit.txt') '0'
    Write-KomeUtf8 (Join-Path $fixture 'build/test-results/test/TEST-fixture.xml') '<testsuite tests="1" failures="0" errors="0" skipped="0"><testcase name="fixture" classname="fixture"/></testsuite>'
    Write-KomeUtf8 (Join-Path $fixture 'build/libs/fixture.jar') 'fixture artifact'
    Write-KomeUtf8 (Join-Path $fixture 'gradlew.bat') "@echo off`r`necho %*>>outputs\wrapper-args.txt`r`necho ^> Task :test`r`nif exist outputs\mutate-source.txt echo changed during validation>>staged.txt`r`nset /p fixtureExit=<outputs\fixture-exit.txt`r`nexit /b %fixtureExit%`r`n"
    # This fixture-only wrapper must exist before validation captures its input Git state.
    $validate=Join-Path $fixture 'scripts/kome-validate.ps1'
    $result=Invoke-TestScript $validate @('-Mode','Fast')
    Assert-Test ($result.Code -eq 0) ('Fast default smoke succeeds from another CWD: '+$result.Text.Trim())
    $arguments=Get-Content -LiteralPath (Join-Path $fixture 'outputs/wrapper-args.txt') -Tail 1
    Assert-Test ($arguments -match 'compileJava test' -and ([regex]::Matches($arguments,'--tests')).Count -eq 5 -and $arguments.Contains('--offline')) 'Fast invokes compile and default focused tests offline'
    $result=Invoke-TestScript $validate @('-Mode','Fast','-Tests','fixture.Targeted')
    Assert-Test ($result.Code -eq 0) 'Explicit focused selector succeeds'
    $arguments=Get-Content -LiteralPath (Join-Path $fixture 'outputs/wrapper-args.txt') -Tail 1
    Assert-Test ($arguments.Contains('--tests fixture.Targeted')) 'Explicit focused selector forwarded'
    $result=Invoke-TestScript $validate @('-Mode','Full')
    Assert-Test ($result.Code -eq 0) 'Full fixture succeeds'
    $arguments=Get-Content -LiteralPath (Join-Path $fixture 'outputs/wrapper-args.txt') -Tail 1
    Assert-Test ($arguments.StartsWith('test build reobfJar ') -and !$arguments.Contains('--tests')) 'Full invokes complete test/build/reobfuscation'
    $latest=(Get-ChildItem -LiteralPath (Join-Path $fixture 'outputs/overnight-validation') -Directory | Sort-Object Name | Select-Object -Last 1).FullName
    $summary=Get-Content -LiteralPath (Join-Path $latest 'summary.json') -Raw | ConvertFrom-Json
    Assert-Test ($summary.Tests.Passed -eq 1 -and $summary.Artifacts.Count -eq 1) 'JUnit counts and artifact hashes recorded'
    $result=Invoke-TestScript $validate @('-Mode','Full','-Tests','fixture.Targeted')
    Assert-Test ($result.Code -eq 2) 'Full rejects test filtering with preflight exit 2'
    Write-KomeUtf8 (Join-Path $fixture 'outputs/fixture-exit.txt') '7'
    $result=Invoke-TestScript $validate @('-Mode','Fast')
    Assert-Test ($result.Code -eq 7) 'Native failure exit 7 propagates to caller'
    $latest=(Get-ChildItem -LiteralPath (Join-Path $fixture 'outputs/overnight-validation') -Directory | Sort-Object Name | Select-Object -Last 1).FullName
    $summary=Get-Content -LiteralPath (Join-Path $latest 'summary.json') -Raw | ConvertFrom-Json
    Assert-Test ($summary.Result -eq 'FAIL' -and $summary.ExitCode -eq 7 -and (Test-Path -LiteralPath (Join-Path $latest 'gradle.log'))) 'Failed command keeps diagnostic log and summary'
    Write-KomeUtf8 (Join-Path $fixture 'outputs/fixture-exit.txt') '0'
    Write-KomeUtf8 (Join-Path $fixture 'build/test-results/test/TEST-fixture.xml') '<broken XML'
    $result=Invoke-TestScript $validate @('-Mode','Fast')
    Assert-Test ($result.Code -ne 0) 'Evidence parsing failure cannot return success after wrapper exit 0'
    Write-KomeUtf8 (Join-Path $fixture 'build/test-results/test/TEST-fixture.xml') '<testsuite tests="1" failures="0" errors="0" skipped="0"><testcase name="fixture" classname="fixture"/></testsuite>'
    $result=Invoke-TestScript $validate @('-Mode','Fast','-Tests','bad;selector')
    Assert-Test ($result.Code -eq 2) 'Invalid selector rejected before invoking Gradle'
    $dirtyStatus=Get-KomeGitText $fixture @('status','--porcelain=v1','-z','--untracked-files=all')
    Write-KomeUtf8 (Join-Path $fixture 'outputs/mutate-source.txt') 'trigger fixture source edit'
    $result=Invoke-TestScript $validate @('-Mode','Fast')
    Assert-Test ($result.Code -ne 0 -and $result.Text.Contains('Repository changed during validation')) 'Editing an already-dirty file invalidates successful wrapper results'
    Assert-Test ($dirtyStatus -eq (Get-KomeGitText $fixture @('status','--porcelain=v1','-z','--untracked-files=all'))) 'Content guard detects edits even when Git status text stays identical'
    Remove-Item -LiteralPath (Join-Path $fixture 'outputs/mutate-source.txt')
    $lock=New-Object System.Threading.Mutex($false,'Local\KOME-Heavy-Validation')
    $ownsLock=$false
    try {
        try { $ownsLock=$lock.WaitOne(0) } catch [Threading.AbandonedMutexException] { $ownsLock=$true }
        if (!$ownsLock) { throw 'An unrelated real validation is active; rerun tool tests when it finishes.' }
        $result=Invoke-TestScript $validate @('-LockTimeoutSeconds','0')
        Assert-Test ($result.Code -eq 3) 'Busy heavy-validation mutex returns exit 3'
    } finally { if ($ownsLock) { $lock.ReleaseMutex() }; $lock.Dispose() }
    [void](Invoke-KomeGit $fixture @('worktree','add','--detach',(Join-Path $testRoot 'linked worktree'),'HEAD'))
    $result=Invoke-TestScript $backupScript
    Assert-Test ($result.Code -ne 0) 'Multiple/linked-worktree backup fails closed'
    Write-KomeJson (Join-Path $testRoot 'summary.json') @{ Result='PASS'; Checks=$checks.ToArray(); TestRoot=$testRoot }
    Write-Host "Tool integration checks passed: $($checks.Count). Evidence: $testRoot"
    exit 0
} catch {
    Write-KomeJson (Join-Path $testRoot 'summary.json') @{ Result='FAIL'; Checks=$checks.ToArray(); Error=$_.Exception.Message; TestRoot=$testRoot }
    Write-Error -ErrorAction Continue $_.Exception.Message
    Write-Host "Tool test evidence: $testRoot"
    exit 1
}
