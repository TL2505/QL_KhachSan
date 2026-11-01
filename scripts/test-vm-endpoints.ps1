try {
    $r3000 = Invoke-WebRequest -Uri 'http://192.168.56.102:3000/portal.html' -UseBasicParsing -TimeoutSec 5
    $bytes3000 = [System.Text.Encoding]::UTF8.GetBytes($r3000.Content)
    $h3000 = [System.BitConverter]::ToString([System.Security.Cryptography.SHA256]::Create().ComputeHash($bytes3000)).Replace('-','').ToLower()
    Write-Host "VM Port 3000 portal.html SHA-256: $h3000"
} catch {
    Write-Host "VM Port 3000 error: $_"
}

try {
    $r8081 = Invoke-WebRequest -Uri 'http://192.168.56.102:8081/portal.html' -UseBasicParsing -TimeoutSec 5
    $bytes8081 = [System.Text.Encoding]::UTF8.GetBytes($r8081.Content)
    $h8081 = [System.BitConverter]::ToString([System.Security.Cryptography.SHA256]::Create().ComputeHash($bytes8081)).Replace('-','').ToLower()
    Write-Host "VM Port 8081 portal.html SHA-256: $h8081"
} catch {
    Write-Host "VM Port 8081 error: $_"
}

