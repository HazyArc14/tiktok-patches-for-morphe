param(
    [string]$ApkPath
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$fingerprintsPath = Join-Path $repoRoot 'patches/src/main/kotlin/app/morphe/patches/tiktok/misc/sharesheet/Fingerprints.kt'
$patchPath = Join-Path $repoRoot 'patches/src/main/kotlin/app/morphe/patches/tiktok/misc/sharesheet/ShareSheetPatch.kt'
$filterPath = Join-Path $repoRoot 'extensions/tiktok/src/main/java/app/morphe/extension/tiktok/sharesheet/ShareSheetFilter.java'
$settingsPath = Join-Path $repoRoot 'extensions/tiktok/src/main/java/app/morphe/extension/tiktok/settings/Settings.java'
$preferencePath = Join-Path $repoRoot 'extensions/tiktok/src/main/java/app/morphe/extension/tiktok/settings/preference/categories/ShareSheetPreferenceCategory.java'

function Assert-Contains {
    param(
        [string]$Path,
        [string]$Needle,
        [string]$Description
    )

    $content = Get-Content -Raw -LiteralPath $Path
    if (-not $content.Contains($Needle)) {
        throw "FAIL: $Description"
    }
    Write-Host "PASS: $Description"
}

Assert-Contains $settingsPath 'SHARE_SHEET_FORCE_AUTO_SCROLL' 'force-show setting is declared'
Assert-Contains $preferencePath 'Force show Auto scroll' 'force-show toggle is exposed in Share sheet settings'
Assert-Contains $fingerprintsPath '"fyp_auto_scroll"' 'TikTok fyp_auto_scroll gate is fingerprinted'
Assert-Contains $fingerprintsPath '"panel_auto_scroll"' 'TikTok panel_auto_scroll gate is fingerprinted'
Assert-Contains $patchPath 'AutoScrollFeatureGateFingerprint' 'account rollout gate is hooked'
Assert-Contains $patchPath 'AutoScrollActionFactoryFingerprint' 'panel rollout gate is hooked'
Assert-Contains $filterPath 'shouldForceAutoScroll' 'force-show logic reads the setting'
Assert-Contains $filterPath 'forceAutoScrollPanelAvailability' 'panel availability preserves the stock result'

if ($ApkPath) {
    $resolvedApk = (Resolve-Path -LiteralPath $ApkPath).Path
    $apkAnalyzer = Join-Path $env:LOCALAPPDATA 'Android/Sdk/cmdline-tools/latest/bin/apkanalyzer.bat'
    if (-not (Test-Path -LiteralPath $apkAnalyzer)) {
        throw "FAIL: apkanalyzer was not found at $apkAnalyzer"
    }

    $featureGateCode = (& $apkAnalyzer dex code --class 'czc.o1' $resolvedApk) -join "`n"
    $actionFactoryCode = (& $apkAnalyzer dex code --class 'X.0oi7' $resolvedApk) -join "`n"
    $extensionCode = (& $apkAnalyzer dex code --class 'app.morphe.extension.tiktok.sharesheet.ShareSheetFilter' $resolvedApk) -join "`n"

    if (-not $featureGateCode.Contains('ShareSheetFilter;->shouldForceAutoScroll()Z')) {
        throw 'FAIL: patched fyp_auto_scroll gate hook is absent from the APK'
    }
    Write-Host 'PASS: patched fyp_auto_scroll gate hook is present in the APK'

    if (-not $actionFactoryCode.Contains('ShareSheetFilter;->forceAutoScrollPanelAvailability(Z)Z')) {
        throw 'FAIL: patched panel_auto_scroll gate hook is absent from the APK'
    }
    Write-Host 'PASS: patched panel_auto_scroll gate hook is present in the APK'

    if (-not $actionFactoryCode.Contains('IAutoAScrollAbility;')) {
        throw 'FAIL: TikTok native Auto scroll ability path is absent from the patched action factory'
    }
    Write-Host 'PASS: TikTok native Auto scroll ability path remains in the APK'

    if ($extensionCode.Contains('IAutoAScrollAbility;') -or $extensionCode.Contains('LX/0oe7;')) {
        throw 'FAIL: extension directly references TikTok Auto scroll ability or action types'
    }
    Write-Host 'PASS: extension does not construct a replacement Auto scroll action'
}

Write-Host 'Force-show Auto scroll verification passed.'
