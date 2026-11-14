/*
 * Copyright 2026 icysymmetra/tiktok-patches-for-morphe contributors
 * https://github.com/icysymmetra/tiktok-patches-for-morphe
 */
package app.morphe.patches.tiktok.misc.absettings

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

// The AB int getter is renamed every release (46.2.3: LX/0BYX;->LJFF, 47.5.3: LX/031i;->LJIIJJI,
// 47.6.3: LX/032w;->LJIIJJI). Match it structurally: the public (IILjava/lang/String;Z)I getter
// that falls back to its own (IIILjava/lang/String;Z)I overload.
// The constants below are only used by the unported Feature Gate Lab patch.
internal const val APP_AB_DESCRIPTOR = "LX/032w;"
internal const val APP_AB_INT_METHOD = "LJIIJJI"
internal const val APP_AB_INT_KEY_REGISTER = "p3"
internal val APP_AB_INT_PARAMETERS = listOf("I", "I", "Ljava/lang/String;", "Z")

internal object AppAbIntBoundaryFingerprint : Fingerprint(
    returnType = "I",
    parameters = APP_AB_INT_PARAMETERS,
    custom = { method, classDef ->
        method.implementation?.instructions?.any { instruction ->
            val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                ?: return@any false
            reference.definingClass == classDef.type &&
                reference.returnType == "I" &&
                reference.parameterTypes.map { it.toString() } == listOf("I", "I", "I", "Ljava/lang/String;", "Z")
        } == true
    },
)

internal fun BytecodePatchContext.hookAppAbIntBoundary(
    extensionDescriptor: String,
    extensionMethod: String,
) {
    AppAbIntBoundaryFingerprint.method.apply {
        implementation!!.instructions.withIndex()
            .filter { it.value.opcode == Opcode.RETURN }
            .map { it.index }
            .asReversed()
            .forEach { returnIndex ->
                val register = getInstruction<OneRegisterInstruction>(returnIndex).registerA
                addInstructions(
                    returnIndex,
                    """
                        invoke-static {$APP_AB_INT_KEY_REGISTER, v$register}, $extensionDescriptor->$extensionMethod(Ljava/lang/String;I)I
                        move-result v$register
                    """,
                )
            }
    }
}
