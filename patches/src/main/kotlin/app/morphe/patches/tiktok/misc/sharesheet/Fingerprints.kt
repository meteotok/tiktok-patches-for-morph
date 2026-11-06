package app.morphe.patches.tiktok.misc.sharesheet

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

// Share panel snapshot built from its builder (46.2.3: 0oVo <- 0oVp, 47.5.3: 0G4Y <- 0Fy9,
// 47.6.3: 0G46 <- 0Fxi). Match by shape: the constructor copies dozens of builder fields,
// including the List fields LIZ/LJFF and the "support IM" flag LJJIIJZLJL that the extension edits.
internal object SharePanelSnapshotConstructorFingerprint : Fingerprint(
    name = "<init>",
    returnType = "V",
    parameters = listOf("LX/"),
    custom = { method, _ ->
        val builderType = method.parameterTypes.single().toString()
        val builderFields = method.implementation?.instructions?.mapNotNull { instruction ->
            ((instruction as? ReferenceInstruction)?.reference as? FieldReference)
                ?.takeIf { it.definingClass == builderType }
                ?.let { "${it.name}:${it.type}" }
        }?.toSet() ?: emptySet()
        builderFields.size >= 30 &&
            "LJJIIJZLJL:Z" in builderFields &&
            "LIZ:Ljava/util/List;" in builderFields &&
            "LJFF:Ljava/util/List;" in builderFields
    },
)
