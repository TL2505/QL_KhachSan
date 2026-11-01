$colleagueRoot = "C:\Users\Admin\Downloads\QL_KhachSan\web-ui"
$colleaguePub = "C:\Users\Admin\Downloads\QL_KhachSan\web-ui\public"
$myWebUi = "C:\NetBeansProjects\QL_KhachSan\web-ui"

Write-Host "=== Colleague Root vs Colleague Public Diff ==="
Get-ChildItem -Recurse $colleaguePub -File | Where-Object { $_.FullName -notmatch '\\node_modules\\' -and $_.FullName -notmatch '\\\.git\\' } | ForEach-Object {
    $rel = $_.FullName.Substring($colleaguePub.Length).TrimStart('\', '/')
    $rootFile = Join-Path $colleagueRoot $rel
    if (Test-Path $rootFile) {
        $pubHash = (Get-FileHash $_.FullName -Algorithm SHA256).Hash
        $rootHash = (Get-FileHash $rootFile -Algorithm SHA256).Hash
        if ($pubHash -ne $rootHash) {
            Write-Host "DIFF: $rel | Public: $($_.Length) bytes vs Root: $((Get-Item $rootFile).Length) bytes"
        }
    } else {
        Write-Host "ONLY IN PUBLIC: $rel ($($_.Length) bytes)"
    }
}

Write-Host "`n=== Colleague (Best/Public) vs My Web-UI Diff ==="
Get-ChildItem -Recurse $colleaguePub -File | Where-Object { $_.FullName -notmatch '\\node_modules\\' -and $_.FullName -notmatch '\\\.git\\' } | ForEach-Object {
    $rel = $_.FullName.Substring($colleaguePub.Length).TrimStart('\', '/')
    $myFile = Join-Path $myWebUi $rel
    if (Test-Path $myFile) {
        $colHash = (Get-FileHash $_.FullName -Algorithm SHA256).Hash
        $myHash = (Get-FileHash $myFile -Algorithm SHA256).Hash
        if ($colHash -ne $myHash) {
            Write-Host "DIFF WITH MY WEB-UI: $rel | Colleague: $($_.Length) vs Mine: $((Get-Item $myFile).Length)"
        }
    } else {
        Write-Host "MISSING IN MY WEB-UI: $rel ($($_.Length) bytes)"
    }
}

Write-Host "`n=== Files in My Web-UI but not in Colleague ==="
Get-ChildItem -Recurse $myWebUi -File | Where-Object { 
    $_.FullName -notmatch '\\public\\' -and 
    $_.FullName -notmatch '\\node_modules\\' -and 
    $_.FullName -notmatch '\\\.git\\' -and
    $_.FullName -notmatch '\\tests\\'
} | ForEach-Object {
    $rel = $_.FullName.Substring($myWebUi.Length).TrimStart('\', '/')
    $colFile = Join-Path $colleaguePub $rel
    $colRootFile = Join-Path $colleagueRoot $rel
    if (-not (Test-Path $colFile) -and -not (Test-Path $colRootFile)) {
        Write-Host "ONLY IN MY WEB-UI: $rel ($($_.Length) bytes)"
    }
}
