package app.morphe.patches.tiktok.feedfilter

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val AD_BLOCK = "Lapp/morphe/extension/tiktok/feedfilter/AdBlock;"
private const val AWEME = "Lcom/ss/android/ugc/aweme/feed/model/Aweme;"
private const val PAGER_ADAPTER = "Landroidx/viewpager/widget/PagerAdapter;"

/**
 * The base adapter of every vertical video pager (For You, Following, detail pages opened
 * from profiles/search/sounds/hashtags, Series episodes). Its setData() writes the list into
 * a small store object and then notifies the PagerAdapter.
 */
internal object VideoPagerAdapterSetDataFingerprint : Fingerprint(
    name = "setData",
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "V",
    parameters = listOf("Ljava/util/List;"),
    custom = custom@{ method, classDef ->
        if (AccessFlags.FINAL.isSet(method.accessFlags)) return@custom false
        if (!AccessFlags.ABSTRACT.isSet(classDef.accessFlags)) return@custom false
        val instructions = method.implementation?.instructions?.toList() ?: return@custom false
        val notifiesPager = instructions.any {
            it.opcode == Opcode.INVOKE_SUPER &&
                it.getReference<MethodReference>()?.let { ref ->
                    ref.definingClass == PAGER_ADAPTER && ref.name == "notifyDataSetChanged"
                } == true
        }
        val storeField = instructions.firstNotNullOfOrNull { instruction ->
            if (instruction.opcode != Opcode.IGET_OBJECT) return@firstNotNullOfOrNull null
            instruction.getReference<FieldReference>()?.takeIf { it.definingClass == classDef.type }
        } ?: return@custom false
        val storeWrites = instructions.any {
            it.getReference<MethodReference>()?.let { ref ->
                ref.definingClass == storeField.type &&
                    ref.parameterTypes.map(CharSequence::toString) == listOf("Ljava/util/List;") &&
                    ref.returnType == "V"
            } == true
        }
        notifiesPager && storeWrites
    },
)

/** Cold/warm start TopView and splash ad decision. */
internal object SplashAdCanShowFingerprint : Fingerprint(
    returnType = "Z",
    parameters = listOf("I", "Landroid/content/Context;"),
    strings = listOf("warmCanShowTopView has no splash"),
)

/** Series (mini drama) blocking ad check. */
internal object SeriesBlockingAdFingerprint : Fingerprint(
    definingClass = "Lcom/ss/android/ugc/aweme/minidrama/common/component/impl/SeriesAdServiceImpl;",
    returnType = "Z",
    parameters = listOf(AWEME),
)

internal fun BytecodePatchContext.installAdBlockHooks() {
    // 1. Vertical video pager store: every list and single item written to it.
    val setData = VideoPagerAdapterSetDataFingerprint.method
    val adapterType = setData.definingClass
    val storeField = setData.implementation!!.instructions.firstNotNullOf { instruction ->
        if (instruction.opcode != Opcode.IGET_OBJECT) return@firstNotNullOf null
        instruction.getReference<FieldReference>()?.takeIf { it.definingClass == adapterType }
    }
    val storeClass = mutableClassDefBy(storeField.type)
    val listSetters = storeClass.methods.filter {
        !AccessFlags.STATIC.isSet(it.accessFlags) && it.returnType == "V" &&
            it.parameterTypes.map(CharSequence::toString) == listOf("Ljava/util/List;")
    }
    val itemWriters = storeClass.methods.filter {
        !AccessFlags.STATIC.isSet(it.accessFlags) && it.returnType == "V" &&
            it.parameterTypes.map(CharSequence::toString) == listOf("I", AWEME)
    }
    val listGetters = storeClass.methods.filter {
        !AccessFlags.STATIC.isSet(it.accessFlags) && it.returnType == "Ljava/util/List;" &&
            it.parameterTypes.isEmpty()
    }
    if (listSetters.size != 1 || itemWriters.isEmpty() || listGetters.size != 1) {
        throw PatchException(
            "Video pager store ${storeField.type} changed: ${listSetters.size} list setters, " +
                "${itemWriters.size} item writers, ${listGetters.size} list getters",
        )
    }
    listSetters.single().addInstructions(
        0,
        """
            invoke-static/range { p1 .. p1 }, $AD_BLOCK->filterList(Ljava/util/List;)Ljava/util/List;
            move-result-object p1
        """,
    )
    itemWriters.forEach { it.dropAdItemAtStart(awemeParameter = "p2") }

    // Bulk range inserts write straight into the store's live list; purge right after.
    val listGetter = listGetters.single()
    mutableClassDefBy(adapterType).methods.filter {
        !AccessFlags.STATIC.isSet(it.accessFlags) && !AccessFlags.ABSTRACT.isSet(it.accessFlags) &&
            it.returnType == "V" &&
            it.parameterTypes.map(CharSequence::toString) == listOf("I", "I", "Ljava/util/List;") &&
            it.implementation != null
    }.forEach { method ->
        val implementation = method.implementation!!
        if (implementation.registerCount - method.parameterTypes.size - 1 < 1) return@forEach
        val returns = implementation.instructions.withIndex()
            .filter { it.value.opcode == Opcode.RETURN_VOID }
            .map { it.index }
        returns.asReversed().forEach { index ->
            method.addInstructionsAtControlFlowLabel(
                index,
                """
                    iget-object v0, p0, $storeField
                    if-eqz v0, :morphe_adblock_no_store_$index
                    invoke-virtual { v0 }, $listGetter
                    move-result-object v0
                    invoke-static { v0 }, $AD_BLOCK->purgeInPlace(Ljava/util/List;)V
                    :morphe_adblock_no_store_$index
                    nop
                """,
            )
        }
    }

    // 2. Splash / TopView ads on app start.
    SplashAdCanShowFingerprint.method.hookBooleanReturns("allowSplashAd")

    // 3. Series blocking ads (an episode is held behind an ad countdown).
    SeriesBlockingAdFingerprint.methodOrNull?.hookBooleanReturns("allowBlockingAd")
}

private fun MutableMethod.dropAdItemAtStart(awemeParameter: String) {
    val implementation = implementation ?: return
    val locals = implementation.registerCount - parameterTypes.size - 1
    if (locals < 1) throw PatchException("No free register to check pager item in $definingClass->$name")
    addInstructionsWithLabels(
        0,
        """
            invoke-static/range { $awemeParameter .. $awemeParameter }, $AD_BLOCK->shouldDrop(Ljava/lang/Object;)Z
            move-result v0
            if-eqz v0, :morphe_adblock_keep_item
            return-void
        """,
        ExternalLabel("morphe_adblock_keep_item", getInstruction(0)),
    )
}

private fun MutableMethod.hookBooleanReturns(extensionMethod: String) {
    val returns = implementation!!.instructions.withIndex()
        .filter { it.value.opcode == Opcode.RETURN }
        .map { it.index }
    if (returns.isEmpty()) throw PatchException("$definingClass->$name has no boolean return")
    returns.asReversed().forEach { index ->
        val register = getInstruction<OneRegisterInstruction>(index).registerA
        addInstructionsAtControlFlowLabel(
            index,
            """
                invoke-static/range { v$register .. v$register }, $AD_BLOCK->$extensionMethod(Z)Z
                move-result v$register
            """,
        )
    }
}
