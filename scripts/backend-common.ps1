function Save-BackendEnvironment {
    $snapshot=@{}
    foreach($entry in [Environment]::GetEnvironmentVariables('Process').GetEnumerator()) { $snapshot[$entry.Key]=$entry.Value }
    return $snapshot
}

function Restore-BackendEnvironment {
    param([hashtable]$Snapshot)
    foreach($name in @([Environment]::GetEnvironmentVariables('Process').Keys)) {
        if (-not $Snapshot.ContainsKey($name) -and -not $name.StartsWith('=')) { Remove-Item -LiteralPath ('Env:'+ $name) -ErrorAction SilentlyContinue }
    }
    foreach($name in $Snapshot.Keys) {
        if (-not $name.StartsWith('=')) { [Environment]::SetEnvironmentVariable($name,$Snapshot[$name],'Process') }
    }
}

function Import-BackendEnvironment {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw 'Configuration file is missing. Copy .env.example to .env and supply the database settings and original keys.' }
    $entries = @{}
    $lineNumber = 0
    foreach ($line in [IO.File]::ReadAllLines($Path)) {
        $lineNumber++
        $entry = $line.Trim()
        if (-not $entry -or $entry.StartsWith('#')) { continue }
        if ($entry -notmatch '^([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') { throw "Invalid environment entry on line $lineNumber." }
        $name = $matches[1]; $value = $matches[2].Trim()
        if ($entries.ContainsKey($name)) { throw "Duplicate configuration variable: $name." }
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) { $value = $value.Substring(1, $value.Length - 2) }
        $entries[$name] = $value
    }
    $inherited = @{}
    foreach ($name in $entries.Keys) {
        $base = $name -replace '_FILE$', ''
        $inherited[$base] = -not [string]::IsNullOrEmpty([Environment]::GetEnvironmentVariable($base, 'Process')) -or -not [string]::IsNullOrEmpty([Environment]::GetEnvironmentVariable("${base}_FILE", 'Process'))
    }
    foreach ($base in @('AES_VAULT_KEY','HMAC_IDENTITY_KEY','JWT_SECRET','DB_PASSWORD')) {
        if (-not $inherited[$base] -and $entries[$base] -and $entries["${base}_FILE"]) { throw "Configure only one of $base or ${base}_FILE in the environment file." }
    }
    foreach ($name in $entries.Keys) {
        if ($inherited[($name -replace '_FILE$', '')]) { continue }
        if ($entries[$name].Length -gt 0 -or $name -eq 'DB_PASSWORD') { [Environment]::SetEnvironmentVariable($name, $entries[$name], 'Process') }
    }
    Write-Host 'Loaded environment configuration; values are not logged. Process settings take precedence.'
}

function Get-BackendMaven {
    param([string]$Command, [string]$ProjectRoot)
    if ($Command) { return $Command }
    $shim = Join-Path $ProjectRoot 'mvn.cmd'
    $maven = Get-Command mvn.cmd -All -ErrorAction SilentlyContinue | Where-Object { $_.Source -ne $shim } | Select-Object -First 1
    if (-not $maven) { throw 'Install Maven or pass -MavenCommand with its full path.' }
    return $maven.Source
}

function Get-BackendJava {
    param([string]$Command)
    if ($Command -and $Command -ne 'java') { return $Command }
    if ($env:JAVA_HOME) {
        $java=Join-Path $env:JAVA_HOME 'bin/java.exe'
        if (Test-Path -LiteralPath $java -PathType Leaf) { return $java }
    }
    return (Get-Command java -ErrorAction Stop).Source
}

function Build-BackendClasspath {
    param([string]$MavenCommand, [string]$ProjectRoot)
    $build = Join-Path $ProjectRoot '.run/backend-build'
    New-Item -ItemType Directory -Force -Path $build | Out-Null
    $classpathFile = Join-Path $build 'classpath.txt'
    & $MavenCommand '-q' '-DskipTests' "-Dhotel.build.directory=$build" 'compile' 'dependency:build-classpath' "-Dmdep.outputFile=$classpathFile" | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'Backend build failed; inspect Maven output.' }
    return (Join-Path $build 'classes') + [IO.Path]::PathSeparator + [IO.File]::ReadAllText($classpathFile).Trim()
}
