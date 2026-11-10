param(
    [string]$Sdk = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }),
    [string]$Jdk = $(if ($env:JAVA_HOME) { $env:JAVA_HOME } else { Join-Path $env:ProgramFiles 'Android\Android Studio\jbr' }),
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9._-]*\.apk$')]
    [string]$OutputName = 'LW-TV-local.apk',
    [string]$SigningKey,
    [string]$SigningPasswordFile,
    [string]$SigningAlias = 'lwtv'
)

$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = $Jdk

function Invoke-Checked {
    param([string]$Executable, [string[]]$Arguments)
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Executable failed with exit code $LASTEXITCODE" }
}

$platform = Get-ChildItem -LiteralPath (Join-Path $Sdk 'platforms') -Directory |
    Where-Object { $_.Name -match '^android-(\d+)(\.\d+)?$' -and [int]$Matches[1] -ge 34 } |
    Sort-Object { [version]($_.Name.Substring(8) + $(if ($_.Name -notmatch '\.') { '.0' })) } -Descending |
    Select-Object -First 1
$tools = Get-ChildItem -LiteralPath (Join-Path $Sdk 'build-tools') -Directory |
    Where-Object Name -Match '^\d+\.\d+\.\d+$' |
    Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
if (!$platform -or !$tools) { throw 'Install Android SDK Platform 34 or newer and Android SDK Build-Tools.' }

$androidJar = Join-Path $platform.FullName 'android.jar'
$app = Join-Path $PSScriptRoot 'app\src\main'
$dist = New-Item -ItemType Directory -Path (Join-Path $PSScriptRoot 'dist') -Force
if ([bool]$SigningKey -ne [bool]$SigningPasswordFile) {
    throw 'Provide both -SigningKey and -SigningPasswordFile to use an existing key.'
}
if ($SigningKey) {
    $key = (Resolve-Path -LiteralPath $SigningKey).Path
    $passwordFile = (Resolve-Path -LiteralPath $SigningPasswordFile).Path
} else {
    $signing = New-Item -ItemType Directory -Path (Join-Path $PSScriptRoot 'signing') -Force
    $key = Join-Path $signing.FullName 'local-build.keystore'
    $passwordFile = Join-Path $signing.FullName 'local-build-password.txt'
    if (!(Test-Path -LiteralPath $passwordFile)) {
        if (Test-Path -LiteralPath $key) { throw 'The local signing password file is missing.' }
        $bytes = [Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
        [IO.File]::WriteAllText($passwordFile, [Convert]::ToBase64String($bytes))
    }
}
$work = New-Item -ItemType Directory -Path (Join-Path $PSScriptRoot ('.build-' + [guid]::NewGuid().ToString('N')))

try {
    $classes = New-Item -ItemType Directory -Path (Join-Path $work.FullName 'classes')
    $resources = Join-Path $work.FullName 'res.zip'
    $unpacked = Join-Path $work.FullName 'unpacked.apk'
    $aligned = Join-Path $work.FullName 'aligned.apk'
    $signed = Join-Path $work.FullName 'signed.apk'
    $jar = Join-Path $work.FullName 'classes.jar'
    $sources = @(Get-ChildItem -LiteralPath (Join-Path $app 'java') -Filter '*.java' -Recurse | ForEach-Object FullName)
    $aapt = Join-Path $tools.FullName 'aapt2.exe'
    $signer = Join-Path $tools.FullName 'apksigner.bat'

    Invoke-Checked $aapt @('compile', '--dir', (Join-Path $app 'res'), '-o', $resources)
    Invoke-Checked $aapt @('link', '-I', $androidJar, '--manifest', (Join-Path $app 'AndroidManifest.xml'),
        '--java', $classes.FullName, '-o', $unpacked, $resources)
    $resourcesJava = Join-Path $classes.FullName 'com\zerotv\next\R.java'
    Invoke-Checked (Join-Path $Jdk 'bin\javac.exe') (@('--release', '8', '-encoding', 'UTF-8',
        '-classpath', $androidJar, '-d', $classes.FullName, $resourcesJava) + $sources)
    Invoke-Checked (Join-Path $Jdk 'bin\jar.exe') @('cf', $jar, '-C', $classes.FullName, 'com')
    Invoke-Checked (Join-Path $tools.FullName 'd8.bat') @('--release', '--min-api', '23', '--lib', $androidJar,
        '--output', $work.FullName, $jar)

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [IO.Compression.ZipFile]::Open($unpacked, [IO.Compression.ZipArchiveMode]::Update)
    try {
        $null = [IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
            $zip, (Join-Path $work.FullName 'classes.dex'), 'classes.dex')
    } finally {
        $zip.Dispose()
    }
    Invoke-Checked (Join-Path $tools.FullName 'zipalign.exe') @('-f', '4', $unpacked, $aligned)

    if (!(Test-Path -LiteralPath $key)) {
        Invoke-Checked (Join-Path $Jdk 'bin\keytool.exe') @('-genkeypair', '-keystore', $key, '-storepass:file',
            $passwordFile, '-keypass:file', $passwordFile, '-alias', $SigningAlias, '-keyalg', 'RSA', '-keysize', '2048',
            '-validity', '10000', '-dname', 'CN=LW TV Local')
    }
    Invoke-Checked $signer @('sign', '--ks', $key, '--ks-pass', "file:$passwordFile", '--ks-key-alias', $SigningAlias,
        '--out', $signed, $aligned)
    Invoke-Checked $signer @('verify', $signed)
    $output = Join-Path $dist.FullName $OutputName
    Copy-Item -LiteralPath $signed -Destination $output -Force
    Write-Output $output
} finally {
    if ((Get-Item -LiteralPath $work.FullName).Parent.FullName -eq $PSScriptRoot) {
        Remove-Item -LiteralPath $work.FullName -Recurse -Force
    }
}
