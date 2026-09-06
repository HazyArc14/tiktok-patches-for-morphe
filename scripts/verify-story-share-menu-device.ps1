param(
    [Parameter(Mandatory = $true)]
    [string]$DeviceSerial,
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $Adb -PathType Leaf)) {
    throw "ADB not found: $Adb"
}

$state = (& $Adb -s $DeviceSerial get-state 2>&1 | Out-String).Trim()
if ($state -ne 'device') {
    throw "ADB device is not ready: $DeviceSerial state=$state"
}

$remoteDump = '/sdcard/morphe-story-share-menu.xml'
& $Adb -s $DeviceSerial shell uiautomator dump $remoteDump *> $null
if ($LASTEXITCODE -ne 0) {
    throw 'Could not capture the current Android UI tree.'
}

$xml = (& $Adb -s $DeviceSerial shell cat $remoteDump | Out-String)
$hasShareSheet = $xml -match 'content-desc="Bottom sheet"' -and $xml -match 'text="Share to"'
$hasStorySaveAction = $xml -match 'text="Save (video|photo)"' -or
        $xml -match 'content-desc="Save (video|photo)"'

if (-not $hasShareSheet) {
    Write-Host 'STORY_SHARE_MENU_CHECK=INCONCLUSIVE'
    Write-Host 'The expected story share sheet is not currently open.'
    exit 2
}

if (-not $hasStorySaveAction) {
    Write-Host 'STORY_SHARE_MENU_CHECK=FAIL'
    Write-Host 'The open story share sheet has no Save video or Save photo action.'
    exit 1
}

Write-Host 'STORY_SHARE_MENU_CHECK=PASS'
Write-Host 'The open story share sheet contains a native story save action.'
