package app.morphe.patches.tiktok.misc.prism

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private const val INSTALL_PERMISSION = "android.permission.REQUEST_INSTALL_PACKAGES"

/** Lets the Prism update prompt hand a downloaded APK to Android's installer. */
internal val prismUpdatePermissionPatch = resourcePatch {
    execute {
        document("AndroidManifest.xml").use { document ->
            val permissions = document.getElementsByTagName("uses-permission")
            val present = (0 until permissions.length)
                .map { permissions.item(it) as Element }
                .any { it.getAttribute("android:name") == INSTALL_PERMISSION }
            if (present) return@execute

            val manifest = document.documentElement
            val permission = document.createElement("uses-permission")
            permission.setAttribute("android:name", INSTALL_PERMISSION)
            manifest.insertBefore(permission, manifest.firstChild)
        }
    }
}
