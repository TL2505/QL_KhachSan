function Get-UrlHash($url) {
    try {
        $resp = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 5
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($resp.Content)
        return [System.BitConverter]::ToString([System.Security.Cryptography.SHA256]::Create().ComputeHash($bytes)).Replace('-','').ToLower()
    } catch {
        return "ERROR: $_"
    }
}

Write-Host "=== ENDPOINT COMPARISON: COLLEAGUE (4000) VS VM (3000 & 8081) ==="
$pages = @("index.html", "portal.html", "housekeeping-preview.html", "style.css")

foreach ($page in $pages) {
    $h4000 = Get-UrlHash "http://localhost:4000/$page"
    $h3000 = Get-UrlHash "http://192.168.56.102:3000/$page"
    $h8081 = Get-UrlHash "http://192.168.56.102:8081/$page"
    
    Write-Host "`nPage: $page"
    Write-Host "  Colleague (Port 4000): $h4000"
    Write-Host "  VM Nginx  (Port 3000): $h3000"
    Write-Host "  VM Java   (Port 8081): $h8081"
    
    if ($h4000 -eq $h3000) {
        Write-Host "  => Port 4000 == Port 3000: MATCH" -ForegroundColor Green
    } else {
        Write-Host "  => Port 4000 != Port 3000 (Needs git pull on VM)" -ForegroundColor Yellow
    }
}

