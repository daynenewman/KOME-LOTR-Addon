param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $GradleArgs
)

$ErrorActionPreference = "Stop"

$env:JAVA_HOME = ""
$env:GRADLE_USER_HOME = Join-Path $PSScriptRoot ".gradle-user-home"

& "$PSScriptRoot\gradlew.bat" @GradleArgs
exit $LASTEXITCODE
