package app.morphe.patches.tiktok.interaction.autoscroll

import app.morphe.patcher.Fingerprint

// TikTok 47.5.3 names (46.2.3: Lczc/o1; and LX/0oi7;->LJI).
internal object AutoScrollFeatureGateFingerprint : Fingerprint(
    definingClass = "Lrld/o1;",
    name = "LIZ",
    returnType = "Z",
    parameters = emptyList(),
    strings = listOf("fyp_auto_scroll"),
)

internal object AutoScrollActionFactoryFingerprint : Fingerprint(
    definingClass = "LX/10Id;",
    name = "LJIIIIZZ",
    returnType = "LX/10J4;",
    parameters = listOf("LX/1074;"),
    strings = listOf("panel_auto_scroll", "auto_scroll"),
)
