package app.morphe.patches.tiktok.misc.profilelayout

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val EXTENSION = "Lapp/morphe/extension/tiktok/profile/NewProfileLayout;"

// Each experiment value is read once into a static field by a tiny holder class.
internal object ProfileLeftAlignFingerprint : Fingerprint(
    name = "<clinit>",
    returnType = "V",
    parameters = emptyList(),
    strings = listOf("profile_left_align"),
    custom = { _, classDef -> classDef.fields.count() == 1 && classDef.fields.single().type == "Z" },
)

internal object ProfileLeftAlignGroupFingerprint : Fingerprint(
    name = "<clinit>",
    returnType = "V",
    parameters = emptyList(),
    strings = listOf("profile_left_align_ab_group"),
    custom = { _, classDef -> classDef.fields.count() == 1 && classDef.fields.single().type == "I" },
)

// Sub-flags of the new layout: without them the containers that hold the follower
// counts and "Edit profile" are never registered, so the server's blocks are dropped.
internal object ProfileAvatarInfoMergeFingerprint : Fingerprint(
    name = "<clinit>",
    returnType = "V",
    parameters = emptyList(),
    strings = listOf("profile_left_align_avatar_and_info_merge"),
    custom = { _, classDef -> classDef.fields.count() == 1 && classDef.fields.single().type == "Z" },
)

internal object ProfileAvatarOptFingerprint : Fingerprint(
    name = "invoke",
    returnType = "Ljava/lang/Object;",
    parameters = emptyList(),
    strings = listOf("profile_left_align_avatar_opt"),
)

// TikTok's own profile banner ("profile background").
internal object ProfileBgAllowListFingerprint : Fingerprint(
    strings = listOf("profile_bg_in_allow_list", "ProfileBackgroundExp"),
)

internal object ProfileBgConsumptionFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    strings = listOf("profile_bg_enable_consumption_group"),
    custom = { method, _ -> method.name.startsWith("invoke") },
)

/** Passes the value read right after [key] through [hook] (type [type], I or Z). */
private fun MutableMethod.overrideExperimentValue(key: String, hook: String, type: String = "I") {
    val keyIndex = instructions.indexOfFirst {
        it.opcode.name.startsWith("const-string") &&
            ((it as ReferenceInstruction).reference as StringReference).string == key
    }
    if (keyIndex < 0) throw PatchException("New profile layout: $key not found")
    val resultIndex = (keyIndex until instructions.size).firstOrNull {
        instructions[it].opcode == Opcode.MOVE_RESULT
    } ?: throw PatchException("New profile layout: no value read for $key")
    val register = getInstruction<OneRegisterInstruction>(resultIndex).registerA
    addInstructions(
        resultIndex + 1,
        """
            invoke-static { v$register }, $EXTENSION->$hook($type)$type
            move-result v$register
        """,
    )
}

@Suppress("unused")
val newProfileLayoutPatch = bytecodePatch(
    name = "New profile layout",
    description = "Shows TikTok's new left-aligned profile layout even when the server " +
        "has not rolled it out to the account. Can be turned off in the mod settings.",
    default = true,
) {
    dependsOn(sharedExtensionPatch, settingsPatch)

    compatibleWith(*AppCompatibilities.tiktok4763())

    execute {
        ProfileLeftAlignFingerprint.method.overrideExperimentValue("profile_left_align", "leftAlign")
        ProfileLeftAlignGroupFingerprint.method.overrideExperimentValue("profile_left_align_ab_group", "leftAlignGroup")
        ProfileAvatarInfoMergeFingerprint.method.overrideExperimentValue(
            "profile_left_align_avatar_and_info_merge",
            "leftAlign",
        )
        ProfileAvatarOptFingerprint.method.overrideExperimentValue("profile_left_align_avatar_opt", "forceOn", "Z")
        ProfileBgAllowListFingerprint.method.overrideExperimentValue("profile_bg_in_allow_list", "banner")
        ProfileBgConsumptionFingerprint.method.overrideExperimentValue("profile_bg_enable_consumption_group", "banner")

        // Diagnostics: remember the component tree the server sent, copyable from settings.
        mutableClassDefByOrNull("Lcom/ss/android/ugc/profile/platform/base/viewmodel/ProfilePlatformViewModel;")
            ?.methods
            ?.firstOrNull {
                it.parameterTypes.size == 5 &&
                    it.parameterTypes[1] == "Lcom/ss/android/ugc/profile/platform/base/data/ProfileUser;" &&
                    it.returnType == "V"
            }
            ?.addInstruction(
                0,
                "invoke-static/range { p2 .. p2 }, Lapp/morphe/extension/tiktok/profile/ProfileDebug;->onProfile(Ljava/lang/Object;)V",
            )
    }
}
