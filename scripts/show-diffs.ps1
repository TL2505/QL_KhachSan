$diffFiles = @(
    "api.js",
    "index.html",
    "css\concierge-chat.css",
    "css\theme-overrides.css",
    "js\api.js",
    "js\app.js",
    "js\concierge-chat.js",
    "js\admin\dashboard.js",
    "js\admin\rooms.js",
    "js\admin\users.js",
    "js\customer\bookings.js",
    "js\customer\loyalty.js",
    "js\staff\checkout.js",
    "js\staff\room-map.js"
)

$colPub = "C:\Users\Admin\Downloads\QL_KhachSan\web-ui\public"
$myPub = "C:\NetBeansProjects\QL_KhachSan\web-ui\public"

foreach ($file in $diffFiles) {
    Write-Host "=========================================================="
    Write-Host "DIFF FOR: $file"
    Write-Host "=========================================================="
    $f1 = Join-Path $colPub $file
    $f2 = Join-Path $myPub $file
    git diff --no-index $f2 $f1 | Select-Object -First 50 | ForEach-Object { Write-Host $_ }
}

