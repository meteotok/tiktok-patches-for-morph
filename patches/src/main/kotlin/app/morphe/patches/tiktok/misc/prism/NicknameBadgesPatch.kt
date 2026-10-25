package app.morphe.patches.tiktok.misc.prism

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private const val PRISM_BADGES = "Lapp/morphe/extension/tiktok/prism/PrismBadges;"

// User models whose name getters feed the feed, profiles, comments and messages.
// These classes keep their names because the server JSON is mapped onto them.
private val NAME_GETTERS = mapOf(
    "Lcom/ss/android/ugc/aweme/profile/model/User;" to listOf("getNickname"),
    "Lcom/ss/android/ugc/profile/platform/base/data/UserProfileInfo;" to listOf("getNickname"),
    "Lcom/ss/android/ugc/aweme/im/common/model/BaseContact;" to listOf("getNickName", "getDisplayName"),
    "Lcom/ss/android/ugc/aweme/im/contacts/api/model/IMUserBaseInfo;" to listOf("getNickName", "getDisplayName"),
    "Lcom/ss/android/ugc/aweme/message/model/SimpleUser;" to listOf("getNickName"),
)

// Keep in sync with app.morphe.extension.tiktok.prism.PrismBadges.Type.
private val BADGE_IMAGES = listOf(
    "verified", "crown", "star", "heart", "bolt", "comet", "diamond", "flame", "pumpkin", "ghost", "sakura",
)

/** Sticker images shown after the name where TikTok draws it in a TextView. */
private val badgeImagesPatch = resourcePatch {
    execute {
        val drawables = get("res").resolve("drawable-nodpi").apply { mkdirs() }
        BADGE_IMAGES.forEach { key ->
            val name = "prism_badge_$key.png"
            val stream = object {}.javaClass.classLoader.getResourceAsStream("tiktok/badges/$name")
                ?: throw PatchException("Nickname badges: missing bundled resource $name")
            stream.use { Files.copy(it, drawables.resolve(name).toPath(), StandardCopyOption.REPLACE_EXISTING) }
        }
    }
}

@Suppress("unused")
val nicknameBadgesPatch = bytecodePatch(
    name = "Nickname badges",
    description = "Shows Prism badges after the names of badge holders everywhere TikTok shows a name: " +
        "feed, profiles, comments and messages.",
    default = true,
) {
    dependsOn(sharedExtensionPatch, badgeImagesPatch)

    compatibleWith(*AppCompatibilities.tiktok4753())

    execute {
        var hooked = 0
        NAME_GETTERS.forEach { (type, getters) ->
            val classDef = mutableClassDefByOrNull(type) ?: return@forEach
            classDef.methods
                .filter {
                    it.name in getters && it.parameterTypes.isEmpty() &&
                        it.returnType == "Ljava/lang/String;" && it.implementation != null
                }
                .forEach { method ->
                    // Hook every return, last first so earlier indices stay valid.
                    method.instructions
                        .withIndex()
                        .filter { it.value.opcode == Opcode.RETURN_OBJECT }
                        .map { it.index to (it.value as OneRegisterInstruction).registerA }
                        .reversed()
                        .forEach { (index, register) ->
                            method.addInstructions(
                                index,
                                """
                                    invoke-static { v$register, p0 }, $PRISM_BADGES->nickname(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/String;
                                    move-result-object v$register
                                """,
                            )
                        }
                    hooked++
                }
        }
        if (hooked == 0) throw PatchException("Nickname badges: no user model name getters found")
    }
}
