$r = Invoke-WebRequest -Uri 'http://localhost:4000/portal.html' -UseBasicParsing
$bytes = [System.Text.Encoding]::UTF8.GetBytes($r.Content)
$h = [System.BitConverter]::ToString([System.Security.Cryptography.SHA256]::Create().ComputeHash($bytes)).Replace('-','').ToLower()
Write-Host "Port 4000 portal.html SHA-256: $h"

