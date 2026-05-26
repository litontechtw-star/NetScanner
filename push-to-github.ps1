# NetScanner Pro - GitHub Push Script
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
Write-Host "--- STEP 1: Personal Access Token ---" -ForegroundColor Yellow
Write-Host "Get token from: https://github.com/settings/tokens/new" -ForegroundColor Green
Write-Host "  - Note: NetScanner"
Write-Host "  - Expiration: 30 days"
Write-Host "  - Check 'repo' scope"
Write-Host "  - Click Generate token, copy ghp_..."
Write-Host ""
$token = Read-Host "Paste your token (ghp_...)"

if ([string]::IsNullOrWhiteSpace($token)) {
    Write-Host "Token cannot be empty. Please re-run the script." -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

# STEP 2: Confirm repo exists
Write-Host ""
Write-Host "--- STEP 2: Confirm GitHub Repository ---" -ForegroundColor Yellow
Write-Host "Go to: https://github.com/new" -ForegroundColor Green
Write-Host "  - Repository name: NetScanner"
Write-Host "  - Do NOT check any init options (README, .gitignore)"
Write-Host ""
Read-Host "Press Enter after repo is created..."

# STEP 3: Setup git
Write-Host ""
Write-Host "[1/5] Initializing Git..." -ForegroundColor Cyan
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

if (Test-Path ".git") {
    Remove-Item -Recurse -Force ".git" -ErrorAction SilentlyContinue
}

& git init
& git config user.email $gitEmail
& git config user.name $gitName

$branch = & git branch --show-current 2>$null
if ($branch -ne "main") {
    & git checkout -b main 2>$null
    if ($LASTEXITCODE -ne 0) {
        & git branch -M main 2>$null
    }
}
Write-Host "    Done" -ForegroundColor Green

# STEP 4: Add files
Write-Host "[2/5] Adding files..." -ForegroundColor Cyan
& git add .
Write-Host "    Done" -ForegroundColor Green

# STEP 5: Commit
Write-Host "[3/5] Creating commit..." -ForegroundColor Cyan
$commitMsg = "Initial commit: NetScanner Pro"
& git commit -m $commitMsg
if ($LASTEXITCODE -ne 0) {
    Write-Host "Commit failed. Please check git installation." -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}
Write-Host "    Done" -ForegroundColor Green

# STEP 6: Set remote
Write-Host "[4/5] Setting remote..." -ForegroundColor Cyan
$remoteUrl = "https://github.com/" + $githubUser + "/" + $repoName + ".git"
Write-Host "    Remote URL: $remoteUrl" -ForegroundColor Gray
& git remote add origin $remoteUrl
Write-Host "    Verifying remote:" -ForegroundColor Gray
& git remote -v
Write-Host "    Done" -ForegroundColor Green

# STEP 7: Push - override any credential manager with explicit Basic Auth header
Write-Host "[5/5] Pushing to GitHub..." -ForegroundColor Cyan

# Build Base64 auth: litontechtw-star:TOKEN
$authString = $githubUser + ":" + $token
$base64Auth = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($authString))
$authHeader = "Authorization: Basic " + $base64Auth

Write-Host "    Auth user: $githubUser" -ForegroundColor Gray
Write-Host "    Header set: YES" -ForegroundColor Gray

# Single-line push with explicit credential override
& git -c "credential.helper=" -c "http.extraHeader=$authHeader" push -u origin main

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "================================================" -ForegroundColor Green
    Write-Host "   SUCCESS! APK build has started!" -ForegroundColor Green
    Write-Host "================================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "Check build progress (ready in ~5 min):" -ForegroundColor Cyan
    $actionsUrl = "https://github.com/" + $githubUser + "/" + $repoName + "/actions"
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
    Write-Host "  1. Token invalid or expired -> regenerate and retry" -ForegroundColor White
    Write-Host "  2. Repository does not exist -> create at github.com/new" -ForegroundColor White
    Write-Host "  3. Repository is not empty -> delete and recreate (no README)" -ForegroundColor White
}

Write-Host ""
Read-Host "Press Enter to exit"
