param(
    [Parameter(Mandatory = $true)][ValidateRange(1, 2147483647)][int]$BookingId,
    [string]$BackendOrigin = 'http://localhost:8081'
)
$ErrorActionPreference = 'Stop'
$origin = $BackendOrigin.TrimEnd('/')
if (([uri]$origin).Scheme -notin @('http', 'https')) { throw 'An HTTP(S) backend origin is required.' }

function Assert-BookingOrder([object[]]$Rows, [string]$Label) {
    if ($Rows.Count -eq 0) { throw "$Label returned no bookings." }
    if ([long]$Rows[0].id -ne $BookingId) {
        throw "$Label first ID is $($Rows[0].id), expected $BookingId. Check for newer concurrent bookings."
    }
    $culture = [Globalization.CultureInfo]::GetCultureInfo('en-US')
    $previousDate = [datetime]::MaxValue
    $previousId = [long]::MaxValue
    foreach ($row in $Rows) {
        $id = [long]$row.id
        # Legacy NULL timestamps sort last in SQL; never replace them with today's date.
        $created = [datetime]::MinValue
        if ($row.createdAt) { $created = [datetime]::Parse([string]$row.createdAt, $culture) }
        if ($created -gt $previousDate -or ($created -eq $previousDate -and $id -ge $previousId)) {
            throw "$Label has unstable order at booking $id."
        }
        $previousDate = $created; $previousId = $id
    }
    if (-not $Rows[0].createdAt) { throw "$Label new booking has no creation timestamp." }
    Write-Host "$Label OK: first booking=$BookingId; rows=$($Rows.Count); createdAt=$($Rows[0].createdAt)"
}

foreach ($role in @('Staff', 'Admin')) {
    $credential = Get-Credential -Message "Sign in with an existing $role account. Credentials/tokens are not written to a file."
    if (-not $credential) { throw "$role credentials are required." }
    $plainPassword = $credential.GetNetworkCredential().Password
    try {
        $payload = @{ username = $credential.UserName; password = $plainPassword } | ConvertTo-Json -Compress
        $login = Invoke-RestMethod -Uri "$origin/api/v1/auth/login" -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($payload))
    } catch { throw "$role login failed. Check credentials and backend logs; do not share tokens or passwords." }
    finally { $plainPassword = $null; $payload = $null; $credential = $null }
    $allowed = if ($role -eq 'Admin') { @('admin') } else { @('staff', 'front_desk') }
    if (-not $login.data.token -or ([string]$login.data.role).ToLowerInvariant() -notin $allowed) {
        throw "The supplied account is not an authorized $role account."
    }
    $headers = @{ Authorization = "Bearer $($login.data.token)"; 'Cache-Control' = 'no-cache' }
    try {
        # Both route aliases and the customer-specific list share the DAO's SQL ordering.
        $customerId = $null
        foreach ($path in @('/api/v1/bookings', '/api/bookings', '/api/v1/bookings')) {
            $response = Invoke-RestMethod -Uri "$origin$path" -Headers $headers -Method Get
            if ($response.data -isnot [array]) { throw "$role $path did not return a booking array." }
            Assert-BookingOrder -Rows @($response.data) -Label "$role $path"
            $customerId = $response.data[0].customerId
        }
        $response = Invoke-RestMethod -Uri "$origin/api/v1/bookings/customer/$customerId" -Headers $headers -Method Get
        if ($response.data -isnot [array]) { throw "$role customer list did not return a booking array." }
        Assert-BookingOrder -Rows @($response.data) -Label "$role customer list"
    } finally { $headers = $null; $login = $null; $response = $null }
}
Write-Host 'API verification completed. Also confirm the database row and Staff/Admin browser tables as documented in docs/verification/booking-order.md.'
