$myPub = "C:\NetBeansProjects\QL_KhachSan\web-ui\public"
$myWeb = "C:\NetBeansProjects\QL_KhachSan\web-ui"

Write-Host "Files in my public directory:"
Get-ChildItem -Recurse $myPub -File | ForEach-Object {
    $rel = $_.FullName.Substring($myPub.Length).TrimStart('\', '/')
    Write-Host "$rel ($($_.Length) bytes)"
}

