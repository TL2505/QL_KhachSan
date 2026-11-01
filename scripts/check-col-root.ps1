$colRoot = "C:\Users\Admin\Downloads\QL_KhachSan\web-ui"
$colPub = "C:\Users\Admin\Downloads\QL_KhachSan\web-ui\public"

Get-ChildItem -Recurse $colRoot -File | Where-Object { 
    $_.FullName -notmatch '\\public\\' -and 
    $_.FullName -notmatch '\\node_modules\\' -and 
    $_.FullName -notmatch '\\\.git\\' -and 
    $_.FullName -notmatch '\\tests\\' 
} | ForEach-Object {
    $rel = $_.FullName.Substring($colRoot.Length).TrimStart('\', '/')
    $pubFile = Join-Path $colPub $rel
    if (-not (Test-Path $pubFile)) {
        Write-Host "In Colleague Root but NOT in Colleague Public: $rel"
    }
}

