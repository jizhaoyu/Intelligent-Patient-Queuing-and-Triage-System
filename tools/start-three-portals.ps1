param(
  [string]$ApiTarget = 'http://localhost:8080',
  [switch]$SkipInstall,
  [switch]$DryRun
)

$ErrorActionPreference = 'Stop'

function Assert-Command {
  param([Parameter(Mandatory = $true)][string]$Name)

  if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
    throw "Missing required command: $Name"
  }
}

function Escape-SingleQuotedString {
  param([Parameter(Mandatory = $true)][string]$Value)

  return $Value -replace "'", "''"
}

function Test-PortInUse {
  param([Parameter(Mandatory = $true)][int]$Port)

  $listener = $null

  try {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $Port)
    $listener.Start()
    return $false
  } catch {
    return $true
  } finally {
    if ($null -ne $listener) {
      $listener.Stop()
    }
  }
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir
$webDir = Join-Path $repoRoot 'web'
$nodeModulesDir = Join-Path $webDir 'node_modules'

Assert-Command -Name 'npm'

if (-not (Test-Path -LiteralPath $webDir)) {
  throw "Web directory not found: $webDir"
}

if (-not $SkipInstall -and -not (Test-Path -LiteralPath $nodeModulesDir)) {
  Write-Host 'web/node_modules not found, running npm install first...'
  & npm install --prefix $webDir

  if ($LASTEXITCODE -ne 0) {
    throw "npm install failed with exit code $LASTEXITCODE"
  }
}

$targets = @(
  @{ Name = 'Admin'; Script = 'dev:admin'; Port = 5173; Url = 'http://localhost:5173/login' },
  @{ Name = 'Workstation'; Script = 'dev:workstation'; Port = 5174; Url = 'http://localhost:5174/login' },
  @{ Name = 'Screen'; Script = 'dev:screen'; Port = 5175; Url = 'http://localhost:5175/login' }
)

foreach ($target in $targets) {
  if (Test-PortInUse -Port $target.Port) {
    Write-Warning "Port $($target.Port) is already in use. $($target.Name) may fail because Vite uses strictPort=true."
  }
}

$escapedWebDir = Escape-SingleQuotedString -Value $webDir
$escapedApiTarget = Escape-SingleQuotedString -Value $ApiTarget

foreach ($target in $targets) {
  $title = "Triage $($target.Name) :$($target.Port)"
  $escapedTitle = Escape-SingleQuotedString -Value $title
  $command = @"
`$Host.UI.RawUI.WindowTitle = '$escapedTitle'
Set-Location -LiteralPath '$escapedWebDir'
`$env:VITE_API_PROXY_TARGET = '$escapedApiTarget'
npm run $($target.Script)
"@

  if ($DryRun) {
    Write-Host "===== $($target.Name) ====="
    Write-Host $command
    continue
  }

  Start-Process -FilePath 'powershell.exe' -WorkingDirectory $webDir -ArgumentList @(
    '-NoExit',
    '-ExecutionPolicy',
    'Bypass',
    '-Command',
    $command
  ) | Out-Null
}

if ($DryRun) {
  return
}

Write-Host ''
Write-Host 'Started three frontend portals in separate windows:'
foreach ($target in $targets) {
  Write-Host "  $($target.Name): $($target.Url)"
}
Write-Host "API proxy target: $ApiTarget"
Write-Host 'Backend is not started by this script. Make sure Spring Boot is already running on port 8080.'
