/*
 * Forked from:
 * https://gitlab.com/ReVanced/revanced-patches/-/blob/main/patches/src/main/kotlin/app/revanced/patches/tiktok/interaction/downloads/Fingerprints.kt
 */
package app.morphe.patches.tiktok.interaction.downloads

import app.morphe.patcher.Fingerprint
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal object AclCommonShareFingerprint : Fingerprint(
    definingClass = "/ACLCommonShare;",
    name = "getCode",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "I",
)

internal object AclCommonShare2Fingerprint : Fingerprint(
    definingClass = "/ACLCommonShare;",
    name = "getShowType",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "I",
)

internal object AclCommonShare3Fingerprint : Fingerprint(
    definingClass = "/ACLCommonShare;",
    name = "getTranscode",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "I",
)

// Obfuscated names below are for TikTok 47.5.3 (46.2.3: 0L4Q, 0L4G, 0ULN, 0ULM, 0UL9, 0GSy, 0ULU, 16zb).
internal object VideoDownloadUriFingerprint : Fingerprint(
    definingClass = "/01BF;",
    name = "LIZLLL",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Landroid/net/Uri;",
    parameters = listOf("Landroid/content/Context;", "Ljava/lang/String;"),
    strings = listOf("/", "/Camera", "/Camera/", "video/mp4"),
)

internal object AwemeGetVideoFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "Lcom/ss/android/ugc/aweme/feed/model/Video;",
    custom = { method, classDef ->
        classDef.endsWith("/Aweme;") &&
            method.name == "getVideo" &&
            method.parameterTypes.isEmpty()
    },
)

internal object CommentImageWatermarkFingerprint : Fingerprint(
    // 47.5.3 moved the MediaStore write out of the watermark renderer; only the logo marker stays.
    strings = listOf("[tiktok_logo]"),
    parameters = listOf("Landroid/graphics/Bitmap;"),
    returnType = "V",
)

internal object StickerPreviewBinderFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(
        "L",
        "Z",
        "Ljava/lang/String;",
        "Ljava/util/Map;",
    ),
    custom = { method, classDef ->
        if (!classDef.endsWith("/14fk;") || method.name != "LIZ") {
            false
        } else {
            val instructions = method.implementation?.instructions
            if (instructions == null) {
                false
            } else {
                var readsUrlModel = false
                var bindsActionButton = false
                var loadsStickerImage = false

                instructions.forEach { instruction ->
                    instruction.getReference<FieldReference>()?.let { field ->
                        if (field.type == "Lcom/ss/android/ugc/aweme/base/model/UrlModel;") {
                            readsUrlModel = true
                        }
                    }

                    instruction.getReference<MethodReference>()?.let { methodReference ->
                        if (methodReference.definingClass == "LX/14fk;" &&
                            methodReference.name == "LIZIZ" &&
                            methodReference.parameterTypes == listOf("LX/0C3w;", "LX/14fl;") &&
                            methodReference.returnType == "V"
                        ) {
                            bindsActionButton = true
                        }

                        if (methodReference.definingClass == "LX/0LWZ;" &&
                            methodReference.name == "LIZJ"
                        ) {
                            loadsStickerImage = true
                        }
                    }
                }

                readsUrlModel && bindsActionButton && loadsStickerImage
            }
        }
    },
)

internal object PhotoDownloadUriFingerprint : Fingerprint(
    definingClass = "/01BF;",
    name = "LIZIZ",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Landroid/net/Uri;",
    parameters = listOf("Landroid/content/Context;", "Ljava/lang/String;", "Ljava/lang/String;"),
    strings = listOf("/", "/Camera", "/Camera/"),
)

internal object VideoLookupUriFingerprint : Fingerprint(
    definingClass = "/01BF;",
    name = "LJIIIIZZ",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Landroid/net/Uri;",
    parameters = listOf("Landroid/content/Context;", "Ljava/lang/String;"),
    strings = listOf("/Camera/", "video/*", "(relative_path=? OR relative_path=?) AND _display_name=?"),
)

internal object PhotoLookupUriFingerprint : Fingerprint(
    definingClass = "/01BF;",
    name = "LJFF",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Landroid/net/Uri;",
    parameters = listOf("Landroid/content/Context;", "Ljava/lang/String;", "Ljava/lang/String;"),
    strings = listOf("/Camera/", "image/*", "(relative_path=? OR relative_path=?) AND _display_name=?"),
)

internal object VideoMediaStoreInsertFingerprint : Fingerprint(
    definingClass = "/01BF;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Landroid/net/Uri;",
    parameters = listOf(
        "Landroid/content/Context;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
    ),
    custom = { method, _ ->
        method.implementation?.instructions?.any { instruction ->
            instruction.getReference<MethodReference>()?.let { reference ->
                reference.definingClass == "Landroid/provider/MediaStore\$Video\$Media;" &&
                    reference.name == "getContentUri"
            } == true
        } == true
    },
)

internal object PhotoMediaStoreInsertFingerprint : Fingerprint(
    definingClass = "/01BF;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Landroid/net/Uri;",
    parameters = listOf(
        "Landroid/content/Context;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
    ),
    custom = { method, _ ->
        method.implementation?.instructions?.any { instruction ->
            instruction.getReference<MethodReference>()?.let { reference ->
                reference.definingClass == "Landroid/provider/MediaStore\$Images\$Media;" &&
                    reference.name == "getContentUri"
            } == true
        } == true
    },
)

internal object ImagePostMediaCopyFingerprint : Fingerprint(
    definingClass = "/03q3;",
    name = "LJJIJIIJIL",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Landroid/net/Uri;",
    parameters = listOf(
        "Landroid/content/Context;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Z",
        "Ljava/lang/String;",
        "I",
    ),
    strings = listOf("/Camera/", "video/mp4", "image/jpeg"),
    custom = { method, _ ->
        val calls = method.implementation?.instructions?.mapNotNull { instruction ->
            instruction.getReference<MethodReference>()
        } ?: emptyList()
        calls.any { it.definingClass == "LX/01BF;" && it.name == "LJ" } &&
            calls.any { it.definingClass == "LX/01BF;" && it.name == "LIZJ" }
    },
)

internal object StickerPreviewSourceFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(
        "Ljava/lang/String;",
        "Lcom/ss/android/ugc/aweme/im/common/model/StickerItem;",
        "Landroid/view/View;",
        "Z",
        "Ljava/lang/String;",
        "Ljava/util/Map;",
        "Lkotlin/jvm/functions/Function0;",
        "Lkotlin/jvm/functions/Function0;",
        "Lkotlin/jvm/functions/Function0;",
    ),
    custom = { method, classDef ->
        classDef.endsWith("/14fh;") &&
            method.name == "LJ" &&
            method.implementation?.instructions?.any { instruction ->
                instruction.getReference<MethodReference>()?.let { reference ->
                    reference.definingClass == "LX/14fk;" &&
                        reference.name == "LIZ" &&
                        reference.parameterTypes == listOf(
                            "LX/14fj;",
                            "Z",
                            "Ljava/lang/String;",
                            "Ljava/util/Map;",
                        ) &&
                        reference.returnType == "V"
                } == true
            } == true
    },
)

internal object DownloadSuccessCoroutineFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;"),
    strings = listOf(
        "DownloadAction@71a5.startDownload\$globalListener\$1\$onSuccess\$1",
        "filePath",
        "fileExist",
    ),
    custom = { method, _ -> method.name == "invokeSuspend" },
)

