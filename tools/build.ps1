param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleTasks = @(':app:testDebugUnitTest', ':app:lintDebug', ':app:assembleDebug')
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$aliasRoot = Join-Path $env:USERPROFILE '.codex\build-paths'
$aliasPath = Join-Path $aliasRoot 'reply-assistant'
New-Item -ItemType Directory -Force -Path $aliasRoot | Out-Null

if (Test-Path -LiteralPath $aliasPath) {
    $existingTarget = (Get-Item -LiteralPath $aliasPath -Force).Target
    if ($existingTarget -ne $projectRoot) {
        throw "构建路径已被其他项目占用：$aliasPath"
    }
} else {
    New-Item -ItemType Junction -Path $aliasPath -Target $projectRoot | Out-Null
}

$androidDir = Join-Path $aliasPath 'android'
$sdkProperties = Join-Path $androidDir 'local.properties'
if (-not (Test-Path -LiteralPath $sdkProperties)) {
    $sdkPath = if ($env:ANDROID_HOME) { $env:ANDROID_HOME }
        elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT }
        else { Join-Path $env:USERPROFILE 'scoop\apps\android-clt\current' }
    if (-not (Test-Path -LiteralPath $sdkPath)) { throw "找不到 Android SDK：$sdkPath" }
    $sdkPropertyPath = $sdkPath.Replace('\', '/').Replace(':', '\:')
    Set-Content -LiteralPath $sdkProperties -Value "sdk.dir=$sdkPropertyPath" -Encoding ascii
}

Push-Location $androidDir
try {
    & '.\gradlew.bat' @GradleTasks
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
    Pop-Location
}
