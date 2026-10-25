package app.morphe.patches.tiktok.misc.sharesheet

import app.morphe.patcher.Fingerprint

// Share panel snapshot built from its builder; TikTok 47.5.3 names (46.2.3: 0oVo built from 0oVp).
internal object SharePanelSnapshotConstructorFingerprint : Fingerprint(
    definingClass = "LX/0G4Y;",
    name = "<init>",
    returnType = "V",
    parameters = listOf("LX/0Fy9;"),
)
