package app.morphe.patches.tiktok.misc.navigation

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.value.ArrayEncodedValue
import com.android.tools.smali.dexlib2.iface.value.StringEncodedValue

// TabAbilityAssem getters are renamed every release (46.2.3: W52/OA, 47.5.3: Oc2/BB, 47.6.3: Zc2/IB).
// Both return the tab *model* lists (elements expose tag()), not the TopTabProtocol/BottomTabProtocol
// lists that sit next to them, so they are matched by shape.

/** Element type from the generic Signature annotation, e.g. "LX/05F8;" for List<LX/05F8;>. */
private fun Method.listElementType(): String? {
    val signature = annotations.firstOrNull { it.type == "Ldalvik/annotation/Signature;" } ?: return null
    val parts = signature.elements.firstOrNull { it.name == "value" }?.value as? ArrayEncodedValue ?: return null
    val text = parts.value.joinToString("") { (it as? StringEncodedValue)?.value ?: "" }
    return text.substringAfter("Ljava/util/List<", "").removeSuffix(">;").takeIf { it.isNotEmpty() }
}

// Top tab models: returns the cached CopyOnWriteArrayList, preferring a ThreadLocal override.
internal object TopTabModelListFingerprint : Fingerprint(
    definingClass = "/TabAbilityAssem;",
    returnType = "Ljava/util/List;",
    parameters = listOf(),
    custom = { method, _ ->
        method.listElementType() == null &&
            method.implementation?.instructions?.any { instruction ->
                ((instruction as? ReferenceInstruction)?.reference as? FieldReference)
                    ?.type == "Ljava/lang/ThreadLocal;"
            } == true
    },
)

// Bottom tab models: the only getter typed as List<obfuscated tab model>.
internal object BottomTabModelListFingerprint : Fingerprint(
    definingClass = "/TabAbilityAssem;",
    returnType = "Ljava/util/List;",
    parameters = listOf(),
    custom = { method, _ ->
        method.listElementType()?.startsWith("LX/") == true
    },
)
