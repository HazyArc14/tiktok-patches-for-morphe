package app.morphe.patches.tiktok.misc.sharesheet

import app.morphe.patcher.Fingerprint

// Real dex class is X.0oVo; jadx renamed it to C1475990oVo for decompilation only.
internal object SharePanelSnapshotConstructorFingerprint : Fingerprint(
    definingClass = "LX/0oVo;",
    name = "<init>",
    returnType = "V",
    parameters = listOf("LX/0oVp;"),
)

internal object AutoScrollFeatureGateFingerprint : Fingerprint(
    definingClass = "Lczc/o1;",
    name = "LIZ",
    returnType = "Z",
    parameters = emptyList(),
    strings = listOf("fyp_auto_scroll"),
)

internal object AutoScrollActionFactoryFingerprint : Fingerprint(
    definingClass = "LX/0oi7;",
    name = "LJI",
    returnType = "LX/0oe7;",
    parameters = listOf("LX/0oeK;"),
    strings = listOf("panel_auto_scroll", "auto_scroll"),
)
