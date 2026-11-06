package app.morphe.patches.tiktok.interaction.autoscroll

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

// Owners are renamed every release (46.2.3: Lczc/o1; + LX/0oi7;, 47.5.3: Lrld/o1; + LX/10Id;,
// 47.6.3: Lzld/o1; + LX/1PCk;), so match by shape and strings only.
internal object AutoScrollFeatureGateFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Z",
    parameters = emptyList(),
    strings = listOf("fyp_auto_scroll"),
)

internal object AutoScrollActionFactoryFingerprint : Fingerprint(
    returnType = "LX/",
    parameters = listOf("LX/"),
    strings = listOf("panel_auto_scroll", "auto_scroll"),
)
