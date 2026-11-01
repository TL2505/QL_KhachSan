$colPub = "C:\Users\Admin\Downloads\QL_KhachSan\web-ui\public"
$myPub = "C:\NetBeansProjects\QL_KhachSan\web-ui\public"

Write-Host "=== Colleague Public vs My Public Diff ==="
Get-ChildItem -Recurse $colPub -File | Where-Object { $_.FullName -notmatch '\\node_modules\\' -and $_.FullName -notmatch '\\\.git\\' } | ForEach-Object {
    $rel = $_.FullName.Substring($colPub.Length).TrimStart('\', '/')
    $myFile = Join-Path $myPub $rel
    if (Test-Path $myFile) {
        $cHash = (Get-FileHash $_.FullName -Algorithm SHA256).Hash
        $mHash = (Get-FileHash $myFile -Algorithm SHA256).Hash
        if ($cHash -ne $mHash) {
            Write-Host "DIFF: $rel | Colleague: $($_.Length) vs Mine: $((Get-Item $myFile).Length)"
        }
    } else {
        Write-Host "MISSING IN MY PUB: $rel ($($_.Length) bytes)"
    }
}

Write-Host "`n=== Files in My Public but not in Colleague Public ==="
Get-ChildItem -Recurse $myPub -File | ForEach-Object {
    $rel = $_.FullName.Substring($myPub.Length).TrimStart('\', '/')
    $cFile = Join-Path $colPub $rel
    if (-not (Test-Path $cFile)) {
        Write-Host "ONLY IN MY PUB: $rel ($($_.Length) bytes)"
    }
}

