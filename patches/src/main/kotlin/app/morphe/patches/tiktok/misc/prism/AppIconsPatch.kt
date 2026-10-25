package app.morphe.patches.tiktok.misc.prism

import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import org.w3c.dom.Element
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
private const val LAUNCHER_ALIAS = "com.ss.android.ugc.aweme.splash.SplashActivity"

// Keep in sync with app.morphe.extension.tiktok.prism.PrismIcons.
private const val ALIAS_PREFIX = "com.ss.android.ugc.aweme.splash.PrismIcon_"
private val CUSTOM_ICONS = listOf("prism", "halloween", "femboy", "neon", "ocean", "gold", "sakura")

@Suppress("unused")
val appIconsPatch = resourcePatch(
    name = "App icons",
    description = "Adds Prism launcher icons (Prism, Halloween, Femboy, Neon, Ocean, Gold, Sakura) " +
        "that can be switched in the mod settings.",
    default = true,
) {
    compatibleWith(*AppCompatibilities.tiktok4753())

    execute {
        val res = get("res")

        // Adaptive icon layers plus a legacy square icon for old launchers.
        val bitmaps = res.resolve("mipmap-xxxhdpi").apply { mkdirs() }
        val adaptive = res.resolve("mipmap-anydpi-v26").apply { mkdirs() }
        CUSTOM_ICONS.forEach { key ->
            listOf("", "_background", "_foreground").forEach { suffix ->
                val name = "prism_icon_$key$suffix.png"
                val stream = object {}.javaClass.classLoader.getResourceAsStream("tiktok/icons/$name")
                    ?: throw PatchException("App icons: missing bundled resource $name")
                stream.use { Files.copy(it, bitmaps.resolve(name).toPath(), StandardCopyOption.REPLACE_EXISTING) }
            }
            adaptive.resolve("prism_icon_$key.xml").writeText(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <adaptive-icon xmlns:android="$ANDROID_NS">
                    <background android:drawable="@mipmap/prism_icon_${key}_background" />
                    <foreground android:drawable="@mipmap/prism_icon_${key}_foreground" />
                </adaptive-icon>
                """.trimIndent(),
            )
        }

        document("AndroidManifest.xml").use { document ->
            val application = document.getElementsByTagName("application").item(0) as Element
            val aliases = document.getElementsByTagName("activity-alias")
            val launcher = (0 until aliases.length)
                .map { aliases.item(it) as Element }
                .firstOrNull { it.getAttribute("android:name") == LAUNCHER_ALIAS }
                ?: throw PatchException("App icons: launcher alias $LAUNCHER_ALIAS not found")

            // The stock alias stays (other code and shortcuts start it by name) but stops being a
            // launcher entry: one PrismIcon alias per icon takes over, exactly one enabled.
            val categories = launcher.getElementsByTagName("category")
            (0 until categories.length)
                .map { categories.item(it) as Element }
                .filter { it.getAttribute("android:name") == "android.intent.category.LAUNCHER" }
                .forEach { it.parentNode.removeChild(it) }

            val stockIcon = application.getAttribute("android:icon")
            val stockRoundIcon = application.getAttribute("android:roundIcon").ifEmpty { stockIcon }
            val label = application.getAttribute("android:label")
            val targetActivity = launcher.getAttribute("android:targetActivity")

            var anchor: org.w3c.dom.Node = launcher
            (listOf("classic") + CUSTOM_ICONS).forEach { key ->
                val alias = document.createElement("activity-alias")
                alias.setAttribute("android:name", ALIAS_PREFIX + key)
                alias.setAttribute("android:enabled", (key == "classic").toString())
                alias.setAttribute("android:exported", "true")
                alias.setAttribute("android:targetActivity", targetActivity)
                if (label.isNotEmpty()) alias.setAttribute("android:label", label)
                if (key == "classic") {
                    alias.setAttribute("android:icon", stockIcon)
                    alias.setAttribute("android:roundIcon", stockRoundIcon)
                } else {
                    alias.setAttribute("android:icon", "@mipmap/prism_icon_$key")
                    alias.setAttribute("android:roundIcon", "@mipmap/prism_icon_$key")
                }

                val filter = document.createElement("intent-filter")
                val action = document.createElement("action")
                action.setAttribute("android:name", "android.intent.action.MAIN")
                val category = document.createElement("category")
                category.setAttribute("android:name", "android.intent.category.LAUNCHER")
                filter.appendChild(action)
                filter.appendChild(category)
                alias.appendChild(filter)

                anchor.parentNode.insertBefore(alias, anchor.nextSibling)
                anchor = alias
            }
        }
    }
}
