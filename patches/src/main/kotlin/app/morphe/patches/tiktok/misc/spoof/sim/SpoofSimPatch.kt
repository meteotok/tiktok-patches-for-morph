/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/tiktok/misc/spoof/sim/SpoofSimPatch.kt
 */

package app.morphe.patches.tiktok.misc.spoof.sim

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.patches.tiktok.misc.settings.SettingsStatusLoadFingerprint
import app.morphe.patches.tiktok.misc.settings.settingsPatch
import app.morphe.util.findMutableMethodOf
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/tiktok/spoof/sim/SpoofSimPatch;"
private const val REGION_EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/tiktok/spoof/sim/RegionSpoof;"

@Suppress("unused")
val simSpoofPatch = bytecodePatch(
    name = "Region spoof",
    description = "Adds in-app controls for changing the region TikTok reads.",
    default = true,
) {
    dependsOn(
        sharedExtensionPatch,
        settingsPatch,
    )

    compatibleWith(*AppCompatibilities.tiktok4763())

    execute {
        val replacements = mapOf(
            "getSimCountryIso" to "getCountryIso",
            "getNetworkCountryIso" to "getCountryIso",
            "getSimOperator" to "getOperator",
            "getNetworkOperator" to "getOperator",
            "getSimOperatorName" to "getOperatorName",
            "getNetworkOperatorName" to "getOperatorName",
        )

        // System region and time zone are reported next to the SIM values (sys_region, tz_name,
        // timezone_name), so spoof them too or a Russian phone still looks Russian.
        val regionReplacements = mapOf(
            Triple("Ljava/util/Locale;", "getCountry", "Ljava/lang/String;") to
                "$REGION_EXTENSION_CLASS_DESCRIPTOR->getCountry(Ljava/lang/String;)Ljava/lang/String;",
            Triple("Ljava/util/TimeZone;", "getID", "Ljava/lang/String;") to
                "$REGION_EXTENSION_CLASS_DESCRIPTOR->getTimeZoneId(Ljava/lang/String;)Ljava/lang/String;",
            Triple("Ljava/util/TimeZone;", "getRawOffset", "I") to
                "$REGION_EXTENSION_CLASS_DESCRIPTOR->getRawOffset(I)I",
        )

        val patchesByMethod = linkedMapOf<Method, ArrayDeque<Pair<Int, String>>>()
        classDefForEach { classDef ->
            // Never rewrite the extension itself (RegionSpoof calls TimeZone.getID()).
            if (classDef.type.startsWith("Lapp/morphe/")) return@classDefForEach
            for (method in classDef.methods) {
                val implementation = method.implementation ?: continue
                implementation.instructions.forEachIndexed { index, instruction ->
                    if (
                        instruction.opcode != Opcode.INVOKE_VIRTUAL &&
                        instruction.opcode != Opcode.INVOKE_VIRTUAL_RANGE
                    ) return@forEachIndexed

                    val methodReference = instruction.getReference<MethodReference>()
                        ?: return@forEachIndexed
                    if (methodReference.parameterTypes.isEmpty()) {
                        regionReplacements[
                            Triple(methodReference.definingClass, methodReference.name, methodReference.returnType)
                        ]?.let { target ->
                            patchesByMethod.getOrPut(method) { ArrayDeque() }.add(index to "@$target")
                            return@forEachIndexed
                        }
                    }
                    val replacement = when {
                        methodReference.definingClass == "Landroid/telephony/TelephonyManager;" ->
                            replacements[methodReference.name]
                        methodReference.definingClass.startsWith("Landroid/telephony/CellIdentity") &&
                            methodReference.name == "getMccString" -> "getMcc"
                        methodReference.definingClass.startsWith("Landroid/telephony/CellIdentity") &&
                            methodReference.name == "getMncString" -> "getMnc"
                        else -> null
                    }
                    if (replacement == null || methodReference.returnType != "Ljava/lang/String;") {
                        return@forEachIndexed
                    }
                    patchesByMethod.getOrPut(method) { ArrayDeque() }.add(index to replacement)
                }
            }
        }

        patchesByMethod.forEach { (method, patches) ->
            val mutableMethod = mutableClassDefBy(method.definingClass).findMutableMethodOf(method)
            while (patches.isNotEmpty()) {
                val (index, replacement) = patches.removeLast()
                val resultInstruction = mutableMethod.getInstruction(index + 1)
                if (resultInstruction.opcode != Opcode.MOVE_RESULT &&
                    resultInstruction.opcode != Opcode.MOVE_RESULT_OBJECT
                ) continue // Result unused.
                val resultRegister = (resultInstruction as OneRegisterInstruction).registerA

                if (replacement.startsWith("@")) {
                    val target = replacement.substring(1)
                    val move = if (resultInstruction.opcode == Opcode.MOVE_RESULT) "move-result" else "move-result-object"
                    mutableMethod.addInstructions(
                        index + 2,
                        """
                            invoke-static/range { v$resultRegister .. v$resultRegister }, $target
                            $move v$resultRegister
                        """,
                    )
                    continue
                }

                mutableMethod.addInstructions(
                    index + 2,
                    """
                        invoke-static/range { v$resultRegister .. v$resultRegister }, $EXTENSION_CLASS_DESCRIPTOR->$replacement(Ljava/lang/String;)Ljava/lang/String;
                        move-result-object v$resultRegister
                    """,
                )
            }
        }

        SettingsStatusLoadFingerprint.method.addInstruction(
            0,
            "invoke-static {}, Lapp/morphe/extension/tiktok/settings/SettingsStatus;->enableSimSpoof()V",
        )
    }
}
