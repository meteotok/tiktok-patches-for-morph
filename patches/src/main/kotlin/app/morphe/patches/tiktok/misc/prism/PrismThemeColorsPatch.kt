package app.morphe.patches.tiktok.misc.prism

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.patches.tiktok.misc.extension.sharedExtensionPatch
import app.morphe.util.findMutableMethodOf
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction35c
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction3rc
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference

private const val PRISM_COLORS = "Lapp/morphe/extension/tiktok/prism/PrismColors;"
private const val CONTEXT = "Landroid/content/Context;"
private const val RESOURCES = "Landroid/content/res/Resources;"
private const val TYPED_ARRAY = "Landroid/content/res/TypedArray;"

// Framework types known to be a Context, so the receiver can be passed as one.
private val CONTEXT_TYPES = setOf(
    CONTEXT,
    "Landroid/content/ContextWrapper;",
    "Landroid/view/ContextThemeWrapper;",
    "Landroid/app/Activity;",
    "Landroid/app/Application;",
    "Landroid/app/Service;",
)

/** The bridge receiver type for a color lookup, or null if the call is not one. */
private fun MethodReference.colorBridgeReceiver(): String? {
    val parameters = parameterTypes.map { it.toString() }
    return when {
        definingClass in CONTEXT_TYPES && name == "getColor" && parameters == listOf("I") -> CONTEXT
        definingClass == RESOURCES && name == "getColor" &&
            (parameters == listOf("I") || parameters == listOf("I", "$RESOURCES\$Theme;")) -> RESOURCES
        definingClass == RESOURCES && name == "getColorStateList" &&
            (parameters == listOf("I") || parameters == listOf("I", "$RESOURCES\$Theme;")) -> RESOURCES
        definingClass == TYPED_ARRAY && name == "getColor" && parameters == listOf("I", "I") -> TYPED_ARRAY
        definingClass == TYPED_ARRAY && name == "getColorStateList" && parameters == listOf("I") -> TYPED_ARRAY
        else -> null
    }
}

@Suppress("unused")
val prismThemeColorsPatch = bytecodePatch(
    name = "Prism theme colors",
    description = "Applies the Prism theme chosen in the mod settings to TikTok's own buttons, " +
        "highlights and accents by recoloring TikTok's brand red and cyan.",
    default = true,
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(*AppCompatibilities.tiktok4753())

    execute {
        val sites = mutableListOf<Triple<com.android.tools.smali.dexlib2.iface.ClassDef,
            com.android.tools.smali.dexlib2.iface.Method, List<Int>>>()
        classDefForEach { classDef ->
            if (classDef.type.startsWith("Lapp/morphe/")) return@classDefForEach
            classDef.methods.forEach { method ->
                val indexes = method.implementation?.instructions?.mapIndexedNotNull { index, instruction ->
                    if (instruction.opcode != Opcode.INVOKE_VIRTUAL &&
                        instruction.opcode != Opcode.INVOKE_VIRTUAL_RANGE
                    ) {
                        return@mapIndexedNotNull null
                    }
                    val reference = (instruction as ReferenceInstruction).reference as? MethodReference
                        ?: return@mapIndexedNotNull null
                    reference.colorBridgeReceiver() ?: return@mapIndexedNotNull null
                    index
                }.orEmpty()
                if (indexes.isNotEmpty()) sites += Triple(classDef, method, indexes)
            }
        }

        sites.forEach { (classDef, method, indexes) ->
            val mutableMethod = mutableClassDefBy(classDef).findMutableMethodOf(method)
            indexes.forEach { index ->
                val instruction = mutableMethod.implementation!!.instructions[index]
                val reference = (instruction as ReferenceInstruction).reference as MethodReference
                val bridge = ImmutableMethodReference(
                    PRISM_COLORS,
                    reference.name,
                    listOf(reference.colorBridgeReceiver()!!) + reference.parameterTypes.map { it.toString() },
                    reference.returnType,
                )
                // Same registers and code-unit width: the receiver becomes static argument 0.
                val replacement = when (instruction.opcode) {
                    Opcode.INVOKE_VIRTUAL -> (instruction as FiveRegisterInstruction).let {
                        BuilderInstruction35c(Opcode.INVOKE_STATIC, it.registerCount,
                            it.registerC, it.registerD, it.registerE, it.registerF, it.registerG, bridge)
                    }
                    else -> (instruction as RegisterRangeInstruction).let {
                        BuilderInstruction3rc(Opcode.INVOKE_STATIC_RANGE, it.startRegister, it.registerCount, bridge)
                    }
                }
                mutableMethod.replaceInstruction(index, replacement)
            }
        }
    }
}
