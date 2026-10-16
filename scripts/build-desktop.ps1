param([string]$MavenCommand='mvn',[string]$JdkHome=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
if(-not $JdkHome -or -not(Test-Path -LiteralPath (Join-Path $JdkHome 'bin/jpackage.exe'))){throw 'Supply a Java 17 JDK with -JdkHome.'}
$javaVersion=(& (Join-Path $JdkHome 'bin/java.exe') -version 2>&1 | Out-String)
if($javaVersion -notmatch 'version "17\.'){throw 'Java 17 is required for the desktop runtime.'}
$apiUrl=$env:HOTEL_DESKTOP_API_URL
$uri=$null
if(-not [Uri]::TryCreate($apiUrl,[UriKind]::Absolute,[ref]$uri) -or $uri.Scheme -notin @('http','https') -or $uri.UserInfo -or $uri.Query -or $uri.Fragment){throw 'Set HOTEL_DESKTOP_API_URL to an approved absolute HTTP(S) API base URL.'}
Push-Location $projectRoot
try {
    & $MavenCommand '-Pdesktop-dist' '-DskipTests' package
    if($LASTEXITCODE -ne 0){throw 'Desktop JAR build failed.'}
    # Unique output directories make repeat builds non-destructive.
    $output=Join-Path $projectRoot ('target/desktop-'+[Guid]::NewGuid().ToString('N'))
    $input=Join-Path $output 'input';New-Item -ItemType Directory -Path $input -Force | Out-Null
    $jar=Get-ChildItem target -Filter '*-desktop.jar' | Select-Object -First 1
    if(-not $jar){throw 'Shaded desktop JAR is missing.'}
    Copy-Item -LiteralPath $jar.FullName -Destination $input
    [IO.File]::WriteAllText((Join-Path $input 'config.properties'),"server.url=$apiUrl`n",[Text.UTF8Encoding]::new($false))
    $modules=& (Join-Path $JdkHome 'bin/jdeps.exe') --multi-release 17 --ignore-missing-deps --print-module-deps $jar.FullName
    if($LASTEXITCODE -ne 0){throw 'JDK module analysis failed.'}
    $modules=($modules | Select-Object -Last 1).Trim()
    if($modules -notmatch '^[A-Za-z0-9_.,]+$'){throw 'Unexpected JDK module analysis output.'}
    & (Join-Path $JdkHome 'bin/jlink.exe') --add-modules "$modules,jdk.crypto.ec,jdk.unsupported,jdk.httpserver" --strip-debug --no-header-files --no-man-pages --compress=2 --output (Join-Path $output 'runtime')
    if($LASTEXITCODE -ne 0){throw 'Self-contained runtime build failed.'}
    & (Join-Path $JdkHome 'bin/jpackage.exe') --type app-image --name 'QLKhachSan-Phase3Preview' --app-version 3.0.0 --input $input --main-jar $jar.Name --main-class quanlykhachsan.Quanlykhachsan --runtime-image (Join-Path $output 'runtime') --java-options '-Dhotel.config.file=$APPDIR/config.properties' --dest (Join-Path $output 'package')
    if($LASTEXITCODE -ne 0){throw 'Desktop application packaging failed.'}
    Write-Output "Preview application image: $(Join-Path $output 'package/QLKhachSan-Phase3Preview')"
} finally {Pop-Location}
