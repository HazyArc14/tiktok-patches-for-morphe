param(
    [Parameter(Mandatory = $true)]
    [string]$DexDirectory,

    [string]$RepositoryRoot = (Split-Path -Parent $PSScriptRoot),

    [Parameter(Mandatory = $true)]
    [string]$DexInspectDirectory,

    [Parameter(Mandatory = $true)]
    [string]$MorpheJar
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $DexDirectory -PathType Container)) {
    throw "DEX directory not found: $DexDirectory"
}
if (-not (Test-Path -LiteralPath $MorpheJar -PathType Leaf)) {
    throw "Morphe jar not found: $MorpheJar"
}

$classPath = $DexInspectDirectory + [IO.Path]::PathSeparator + $MorpheJar
$failures = [System.Collections.Generic.List[string]]::new()

function Invoke-DexTool {
    param([string]$Tool, [string[]]$Arguments)

    $output = & java -cp $classPath $Tool @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "$Tool failed: $($output -join [Environment]::NewLine)"
    }
    return $output -join [Environment]::NewLine
}

function Find-DexForClass {
    param([string]$ClassNeedle)

    $match = Invoke-DexTool 'FindDexClass' @($DexDirectory, $ClassNeedle)
    $firstLine = ($match -split "`r?`n" | Select-Object -First 1)
    if (-not $firstLine) {
        $failures.Add("Missing target class: $ClassNeedle")
        return $null
    }
    return Join-Path $DexDirectory ($firstLine -split "`t")[0]
}

function Require-Text {
    param([string]$Text, [string]$Pattern, [string]$Message)

    if ($Text -notmatch $Pattern) {
        $failures.Add($Message)
    }
}

function Require-SourceText {
    param([string]$RelativePath, [string]$Pattern, [string]$Message)

    $path = Join-Path $RepositoryRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        $failures.Add("Missing source file: $RelativePath")
        return
    }
    Require-Text (Get-Content -LiteralPath $path -Raw) $Pattern $Message
}

$builderDex = Find-DexForClass 'LX/0oi3;'
$saveActionDex = Find-DexForClass 'LX/0odG;'
$extensionDex = Find-DexForClass 'Lapp/morphe/extension/tiktok/download/StoryDownloadsPatch;'

if ($builderDex) {
    $builder = Invoke-DexTool 'DumpDexMethodControlFlow' @($builderDex, 'LX/0oi3;', 'LJJIFFI')
    Require-Text $builder 'AwemeExtKt;->isSharedStoryVisible\(' `
        'The exact 46.2.3 story exclusion boundary is no longer present in the video save-action builder.'
    Require-Text $builder 'new-instance ref=LX/0odG;' `
        'The story hook no longer reuses TikTok''s native video save action.'
    Require-Text $builder 'StoryDownloadsPatch;->shouldShowForAweme\(' `
        'The story share action builder does not call the runtime story gate.'
    $builderLines = $builder -split "`r?`n"
    $runtimeGateIndex = [Array]::FindIndex($builderLines, [Predicate[string]] { param($line) $line -match 'StoryDownloadsPatch;->shouldShowForAweme\(' })
    $saveAclIndex = [Array]::FindIndex($builderLines, [Predicate[string]] { param($line) $line -match 'const-string ref=save$' })
    $sharePanelIndex = [Array]::FindIndex($builderLines, [Predicate[string]] { param($line) $line -match 'const-string ref=share_panel$' })
    if ($runtimeGateIndex -lt 0 -or $saveAclIndex -lt 0 -or $runtimeGateIndex -ge $saveAclIndex) {
        $failures.Add('The story action hook does not run before TikTok''s story-blocking ACL checks.')
    }
    if ($runtimeGateIndex -ge 0 -and $sharePanelIndex -gt 0) {
        $storyBranch = $builderLines | Select-Object -Skip ($runtimeGateIndex + 1) -First 3 |
            Where-Object { $_ -match 'if-nez target=([0-9a-f]+)' } | Select-Object -First 1
        $actionAddress = if ($builderLines[$sharePanelIndex - 1] -match '^\s*([0-9a-f]+):') { $Matches[1] } else { $null }
        $branchTarget = if ($storyBranch -match 'target=([0-9a-f]+)') { $Matches[1] } else { $null }
        if (-not $branchTarget -or -not $actionAddress -or $branchTarget -ne $actionAddress) {
            $failures.Add('Enabled stories do not branch directly to TikTok''s native save-action construction.')
        }
    }
    Require-Text $builder 'LX/0SDQ;->LJJIIZI\(' `
        'TikTok''s native non-story checks were unexpectedly removed from the builder.'
    Require-Text $builder 'LX/0SDQ;->LJIL\(' `
        'TikTok''s native non-story checks were unexpectedly removed from the builder.'
}

if ($saveActionDex) {
    $enable = Invoke-DexTool 'DumpDexMethodControlFlow' @($saveActionDex, 'LX/0odG;', 'enable')
    Require-Text $enable 'StoryDownloadsPatch;->shouldShowForAweme\(' `
        'TikTok''s native save action is not enabled for the current story Aweme.'
    $enableLines = $enable -split "`r?`n"
    $runtimeGateIndex = [Array]::FindIndex($enableLines, [Predicate[string]] { param($line) $line -match 'StoryDownloadsPatch;->shouldShowForAweme\(' })
    $nativeAclIndex = [Array]::FindIndex($enableLines, [Predicate[string]] { param($line) $line -match 'awemeACLShareInfo' })
    if ($runtimeGateIndex -lt 0 -or $nativeAclIndex -lt 0 -or $runtimeGateIndex -ge $nativeAclIndex) {
        $failures.Add('The story enable hook does not run before TikTok''s story-blocking native checks.')
    }
    $storyEnableWindow = if ($runtimeGateIndex -ge 0) {
        ($enableLines | Select-Object -Skip $runtimeGateIndex -First 6) -join "`n"
    } else {
        ''
    }
    Require-Text $storyEnableWindow 'if-eqz[\s\S]*const/4 literal=1[\s\S]*return' `
        'The native save action is not returned as enabled for an opted-in story.'
    Require-Text $enable 'AwemeExtKt;->isSharedStoryVisible\(' `
        'TikTok''s native non-story enable checks were unexpectedly removed.'
}

if ($extensionDex) {
    $gate = Invoke-DexTool 'DumpDexMethodControlFlow' @(
        $extensionDex,
        'Lapp/morphe/extension/tiktok/download/StoryDownloadsPatch;',
        'shouldShowForAweme'
    )
    Require-Text $gate 'Settings;->DOWNLOAD_STORIES:' `
        'The story share action is not controlled by the Downloads setting.'
    Require-Text $gate 'Aweme;->getIsTikTokStory\(\)Z' `
        'The runtime gate does not prove that the current share item is a TikTok story.'
}

$formatter = 'extensions\tiktok\src\main\java\app\morphe\extension\tiktok\download\DownloadFilenameFormatter.java'
Require-SourceText $formatter 'ensureStoryIdentityTemplate\s*\(' `
    'Story filenames do not enforce an item-identity suffix when a custom template omits one.'
Require-SourceText $formatter '\.replace\("\{story_id\}"' `
    'The {story_id} filename token is missing.'
Require-SourceText $formatter 'Map<String, ArrayDeque<PendingName>>' `
    'Concurrent downloads with the same staging name can overwrite each other in the pending-name map.'
Require-SourceText $formatter 'created_" \+ createdAt' `
    'A story whose aid is missing has no per-item creation-time fallback identity.'

$category = 'extensions\tiktok\src\main\java\app\morphe\extension\tiktok\settings\preference\categories\DownloadsPreferenceCategory.java'
Require-SourceText $category 'group\(context, "Story downloads"\)' `
    'The Downloads settings menu is missing its Story downloads section.'
Require-SourceText $category 'Settings\.DOWNLOAD_STORIES' `
    'The Story downloads setting is not wired into the Downloads menu.'

if ($failures.Count -gt 0) {
    Write-Host 'STORY_DOWNLOAD_CHECK=FAIL'
    foreach ($failure in $failures) {
        Write-Host (' - ' + $failure)
    }
    exit 1
}

Write-Host 'STORY_DOWNLOAD_CHECK=PASS'
Write-Host 'ACTION_CONTRACT=current Aweme -> story-only bypass -> TikTok native save action -> existing Downloads pipeline'
Write-Host 'MULTI_STORY_CONTRACT=aid/story_id per item, creation-time fallback, queued pending names'
