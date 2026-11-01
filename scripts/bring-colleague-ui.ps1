$colPub = "C:\Users\Admin\Downloads\QL_KhachSan\web-ui\public"
$myWeb = "C:\NetBeansProjects\QL_KhachSan\web-ui"

Write-Host "Updating web-ui from colleague's latest public assets..." -ForegroundColor Cyan

# 1. Images
$images = @("deluxe.jpg", "family.jpg", "standard.jpg")
foreach ($img in $images) {
    $src = Join-Path $colPub $img
    $dst = Join-Path $myWeb $img
    if (Test-Path $src) {
        Copy-Item -Path $src -Destination $dst -Force
        Write-Host "Copied $img to web-ui/" -ForegroundColor Green
    }
}

# 2. CSS files
$cssFiles = @(
    "style.css",
    "css\account-settings-ui.css",
    "css\admin-users-light.css",
    "css\admin-users.css",
    "css\ai-settings-ui.css",
    "css\housekeeping-portal.css",
    "css\housekeeping-preview.css",
    "css\concierge-chat.css",
    "css\theme-overrides.css"
)
foreach ($css in $cssFiles) {
    $src = Join-Path $colPub $css
    $dst = Join-Path $myWeb $css
    $dstDir = Split-Path -Parent $dst
    if (-not (Test-Path $dstDir)) { New-Item -ItemType Directory -Path $dstDir -Force | Out-Null }
    if (Test-Path $src) {
        Copy-Item -Path $src -Destination $dst -Force
        Write-Host "Copied $css to web-ui/" -ForegroundColor Green
    }
}

# 3. JS files from Colleague
$jsFiles = @(
    "app.js",
    "js\concierge-room-tools.js",
    "js\concierge-chat.js",
    "js\housekeeping-preview.js",
    "js\admin\bookings.js",
    "js\admin\dashboard.js",
    "js\admin\rooms.js",
    "js\admin\users.js",
    "js\customer\loyalty.js",
    "js\staff\checkout.js"
)
foreach ($js in $jsFiles) {
    $src = Join-Path $colPub $js
    $dst = Join-Path $myWeb $js
    $dstDir = Split-Path -Parent $dst
    if (-not (Test-Path $dstDir)) { New-Item -ItemType Directory -Path $dstDir -Force | Out-Null }
    if (Test-Path $src) {
        Copy-Item -Path $src -Destination $dst -Force
        Write-Host "Copied $js to web-ui/" -ForegroundColor Green
    }
}

Write-Host "Completed bringing colleague UI assets to web-ui/." -ForegroundColor Green

