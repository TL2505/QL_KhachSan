$idx = Get-Content 'web-ui\index.html' -Raw
Write-Host "--- index.html checks ---"
Write-Host "Shield logo: $([bool]($idx -match 'shield|logo'))"
Write-Host "Google Sign-in: $([bool]($idx -match 'google|btn-google'))"
Write-Host "Facebook Sign-in: $([bool]($idx -match 'facebook|btn-facebook'))"
Write-Host "Concierge Chat: $([bool]($idx -match 'concierge-chat'))"
Write-Host "PayOS / QR: $([bool]($idx -match 'payos|vietqr|payment'))"

$portal = Get-Content 'web-ui\portal.html' -Raw
Write-Host "`n--- portal.html checks ---"
Write-Host "Shield logo: $([bool]($portal -match 'shield|logo'))"
Write-Host "Account Settings: $([bool]($portal -match 'account-settings'))"
Write-Host "Concierge Chat: $([bool]($portal -match 'concierge'))"
Write-Host "PayOS / QR: $([bool]($portal -match 'payos|vietqr|payment'))"

