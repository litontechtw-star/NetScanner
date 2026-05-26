# NetScanner Pro - GitHub Push Script (Incremental Update)
# Run: powershell -ExecutionPolicy Bypass -File push-to-github.ps1

$githubUser = "litontechtw-star"
$repoName   = "NetScanner"
$gitEmail   = "litontech.tw@gmail.com"
$gitName    = "LitonTech"

Write-Host ""
Write-Host "================================================" -ForegroundColor Cyan
Write-Host "   NetScanner Pro - GitHub Push Tool" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "GitHub  : $githubUser/$repoName" -ForegroundColor Green
Write-Host ""

# STEP 1: Get Token
Write-Host "--- Personal Access Token ---" -ForegroundColor Yellow
Write-Host "Get token from: https://github.com/settings/tokens/new" -ForegroundColor Green
Write-Host "  - Check 'repo' AND 'workflow' scopes"
Write-Host ""
$token = Read-Host "Paste your token (ghp_...)"

if ([string]::IsNullOrWhiteSpace($token)) {
    Write-Host "Token cannot be empty." -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

# Setup auth header
$authString = $githubUser + ":" + $token
$base64Auth = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($authString))
$authHeader = "Authorization: Basic " + $base64Auth

# Navigate to script directory
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

# Configure git user
& git config user.email $gitEmail
& git config user.name $gitName

# Check if already a git repo
if (-not (Test-Path ".git")) {
    Write-Host "[1/4] Initializing Git..." -ForegroundColor Cyan
    & git init
    & git checkout -b main 2>$null
    if ($LASTEXITCODE -ne 0) { & git branch -M main 2>$null }

    $remoteUrl = "https://github.com/" + $githubUser + "/" + $repoName + ".git"
    & git remote add origin $remoteUrl
    Write-Host "    Done" -ForegroundColor Green
} else {
    Write-Host "[1/4] Git repo exists, using existing..." -ForegroundColor Cyan
    # Update remote URL in case it changed
    $remoteUrl = "https://github.com/" + $githubUser + "/" + $repoName + ".git"
    & git remote set-url origin $remoteUrl 2>$null
    Write-Host "    Done" -ForegroundColor Green
}

# Stage all changes
Write-Host "[2/4] Staging changes..." -ForegroundColor Cyan
& git add .
$status = & git status --short
if ([string]::IsNullOrWhiteSpace($status)) {
    Write-Host "    No changes to commit." -ForegroundColor Yellow
} else {
    Write-Host "    Changed files:" -ForegroundColor Gray
    $status | ForEach-Object { Write-Host "      $_" -ForegroundColor Gray }
}
Write-Host "    Done" -ForegroundColor Green

# Commit
Write-Host "[3/4] Committing..." -ForegroundColor Cyan
$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm"
& git commit -m "Fix: add mipmap icons ($timestamp)" 2>&1 | Out-String | Write-Host -ForegroundColor Gray
Write-Host "    Done" -ForegroundColor Green

# Push
Write-Host "[4/4] Pushing to GitHub..." -ForegroundColor Cyan
& git -c "credential.helper=" -c "http.extraHeader=$authHeader" push -u origin main

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "================================================" -ForegroundColor Green
    Write-Host "   SUCCESS! APK build has started!" -ForegroundColor Green
    Write-Host "================================================" -ForegroundColor Green
    Write-Host ""
    $actionsUrl = "https://github.com/" + $githubUser + "/" + $repoName + "/actions"
    Write-Host "Check build progress (~3-5 min):" -ForegroundColor Cyan
    Write-Host $actionsUrl -ForegroundColor Green
    Write-Host ""
    Write-Host "Download APK: Actions page -> latest workflow -> Artifacts" -ForegroundColor White
    Write-Host ""
    Start-Process $actionsUrl
} else {
    Write-Host ""
    Write-Host "Push failed (code: $LASTEXITCODE)" -ForegroundColor Red
    Write-Host ""
    Write-Host "Possible causes:" -ForegroundColor Yellow
    Write-Host "  1. Token invalid/expired -> regenerate at github.com/settings/tokens" -ForegroundColor White
    Write-Host "  2. Need 'repo' + 'workflow' scopes on token" -ForegroundColor White
    Write-Host "  3. Repository does not exist -> create at github.com/new" -ForegroundColor White
}

Write-Host ""
Read-Host "Press Enter to exit"
