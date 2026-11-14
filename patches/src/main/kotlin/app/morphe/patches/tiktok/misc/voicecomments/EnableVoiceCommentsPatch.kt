/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.patches.tiktok.misc.voicecomments

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities

@Suppress("unused")
// Not ported to TikTok 47.5.3 yet: kept out of the bundle so it can't break patching.
private val enableVoiceCommentsPatch = bytecodePatch(
    name = "Enable voice comments",
    description = "Enables TikTok's native voice-comment recording and publishing entry points.",
    default = true,
) {
    // Not yet ported to TikTok 47.5.3; its fingerprints only match 46.2.3.
    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        VoiceCommentPublishGateFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """,
        )
    }
}
