package app.morphe.patches.tiktok.misc.prism

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.MainActivityOnCreateFingerprint
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.settingsPatch

private const val PRISM_UI_CLASS = "Lapp/morphe/extension/tiktok/prism/PrismUi;"

@Suppress("unused")
val prismUiPatch = bytecodePatch(
    name = "Prism UI",
    description = "Liquid glass bottom bar, color themes and nickname badges with a password protected Admin Panel.",
    default = true,
) {
    dependsOn(sharedExtensionPatch, settingsPatch, prismUpdatePermissionPatch)

    compatibleWith(*AppCompatibilities.tiktok4753())

    execute {
        // The extension only registers activity lifecycle callbacks here and decorates views
        // once activities resume, so no obfuscated TikTok view classes are needed.
        MainActivityOnCreateFingerprint.method.addInstruction(
            0,
            "invoke-static/range { p0 .. p0 }, $PRISM_UI_CLASS->onMainActivityCreate(Landroid/app/Activity;)V",
        )
    }
}
