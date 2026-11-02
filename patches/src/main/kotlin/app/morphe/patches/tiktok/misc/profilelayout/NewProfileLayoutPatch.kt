package app.morphe.patches.tiktok.misc.profilelayout

import app.morphe.patcher.Fingerprint
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

/** Passes the int read right after [key] through [hook]. */
private fun MutableMethod.overrideExperimentValue(key: String, hook: String) {
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
            invoke-static { v$register }, $EXTENSION->$hook(I)I
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
    }
}
