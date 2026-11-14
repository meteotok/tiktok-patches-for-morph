/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.patches.tiktok.interaction.quickactions

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode

private const val FEATURE_CONTROLS_DESCRIPTOR =
    "Lapp/morphe/extension/tiktok/featurecontrols/FeatureControls;"

@Suppress("unused")
// Not ported to TikTok 47.5.3 yet: kept out of the bundle so it can't break patching.
private val disableLongPressQuickSharePatch = bytecodePatch(
    name = "Disable long-press quick share",
    description = "Keeps long-pressing Share from opening TikTok's quick-share interaction.",
    default = true,
) {
    dependsOn(sharedExtensionPatch)
    // Not yet ported to TikTok 47.5.3; its fingerprints only match 46.2.3.
    compatibleWith(*AppCompatibilities.tiktok4623())

    execute {
        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, " +
                "Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableDisableLongPressQuickShare()V",
        )

        LongPressQuickShareGateFingerprint.method.apply {
            val returnIndex = indexOfFirstInstructionOrThrow {
                opcode == Opcode.RETURN
            }
            addInstructions(
                returnIndex,
                """
                    invoke-static {v0}, $FEATURE_CONTROLS_DESCRIPTOR->overrideLongPressQuickShare(I)I
                    move-result v0
                """,
            )
        }
    }
}
