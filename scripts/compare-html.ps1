$colleaguePub = "C:\Users\Admin\Downloads\QL_KhachSan\web-ui\public"
$myWebUi = "C:\NetBeansProjects\QL_KhachSan\web-ui"

Get-ChildItem -Path $colleaguePub -Filter "*.html" | ForEach-Object {
    $myFile = Join-Path $myWebUi $_.Name
    if (Test-Path $myFile) {
        $pubHash = (Get-FileHash $_.FullName -Algorithm SHA256).Hash
        $myHash = (Get-FileHash $myFile -Algorithm SHA256).Hash
        if ($pubHash -ne $myHash) {
            Write-Host "HTML DIFF: $($_.Name)"
            & fc.exe $_.FullName $myFile
        } else {
            Write-Host "HTML MATCH: $($_.Name)"
        }
    } else {
        Write-Host "HTML MISSING IN MINE: $($_.Name)"
    }
}

