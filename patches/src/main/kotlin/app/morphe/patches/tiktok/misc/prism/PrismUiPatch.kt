package app.morphe.patches.tiktok.misc.prism

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.MainActivityOnCreateFingerprint
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private const val PRISM_UI_CLASS = "Lapp/morphe/extension/tiktok/prism/PrismUi;"

// Made by tools/prism-decor/generate_decor.py; keep in sync with PrismDecor.
private val DECOR_IMAGES = listOf(
    "web_tr", "web_bl", "spider", "bow_tr", "hearts_bl", "neon_tr", "neon_bl", "rays_tr", "bubble",
    "bauble_tr", "glitter_bl", "sparkle", "branch_tr", "petals_bl", "petal", "prism_tr", "leak_bl",
)

/** Pre-rendered theme decorations, drawn by PrismDecor. */
private val decorImagesPatch = resourcePatch {
    execute {
        val drawables = get("res").resolve("drawable-nodpi").apply { mkdirs() }
        DECOR_IMAGES.forEach { key ->
            val name = "prism_decor_$key.png"
            val stream = object {}.javaClass.classLoader.getResourceAsStream("tiktok/decor/$name")
                ?: throw PatchException("Prism UI: missing bundled resource $name")
            stream.use { Files.copy(it, drawables.resolve(name).toPath(), StandardCopyOption.REPLACE_EXISTING) }
        }
    }
}

@Suppress("unused")
val prismUiPatch = bytecodePatch(
    name = "Prism UI",
    description = "Liquid glass bottom bar, color themes and nickname badges with a password protected Admin Panel.",
    default = true,
) {
    dependsOn(sharedExtensionPatch, settingsPatch, prismUpdatePermissionPatch, decorImagesPatch)

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
