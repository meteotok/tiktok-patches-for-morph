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

// Obfuscated names below are for TikTok 47.6.3 (46.2.3: 0L4Q, 0L4G, 0ULN, 0ULM, 0UL9, 0GSy, 0ULU, 16zb).
// MediaStore helper: LX/01BF; on 47.5.3, LX/01BG; on 47.6.3 (method names unchanged).
// Both are accepted so one bundle patches either release.
internal val MEDIA_STORE_HELPER_DESCRIPTORS = setOf("LX/01BF;", "LX/01BG;")

internal object VideoDownloadUriFingerprint : Fingerprint(
    name = "LIZLLL",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Landroid/net/Uri;",
    parameters = listOf("Landroid/content/Context;", "Ljava/lang/String;"),
    strings = listOf("/", "/Camera", "/Camera/", "video/mp4"),
    custom = { _, classDef -> classDef.type in MEDIA_STORE_HELPER_DESCRIPTORS },
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

// Sticker preview binder (46.2.3/47.5.3: LX/14fk;->LIZ, 47.6.3: LX/0qLW;->LIZ). Matched by shape:
// binds a preview model (its first parameter) whose UrlModel field is loaded into the image view.
internal object StickerPreviewBinderFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(
        "LX/",
        "Z",
        "Ljava/lang/String;",
        "Ljava/util/Map;",
    ),
    custom = { method, _ ->
        val previewType = method.parameterTypes.first().toString()
        method.name != "<init>" &&
            method.implementation?.instructions?.any { instruction ->
                instruction.getReference<FieldReference>()?.let { field ->
                    field.definingClass == previewType &&
                        field.type == "Lcom/ss/android/ugc/aweme/base/model/UrlModel;"
                } == true
            } == true
    },
)

internal object PhotoDownloadUriFingerprint : Fingerprint(
    name = "LIZIZ",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Landroid/net/Uri;",
    parameters = listOf("Landroid/content/Context;", "Ljava/lang/String;", "Ljava/lang/String;"),
    strings = listOf("/", "/Camera", "/Camera/"),
    custom = { _, classDef -> classDef.type in MEDIA_STORE_HELPER_DESCRIPTORS },
)

internal object VideoLookupUriFingerprint : Fingerprint(
    name = "LJIIIIZZ",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Landroid/net/Uri;",
    parameters = listOf("Landroid/content/Context;", "Ljava/lang/String;"),
    strings = listOf("/Camera/", "video/*", "(relative_path=? OR relative_path=?) AND _display_name=?"),
    custom = { _, classDef -> classDef.type in MEDIA_STORE_HELPER_DESCRIPTORS },
)

internal object PhotoLookupUriFingerprint : Fingerprint(
    name = "LJFF",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Landroid/net/Uri;",
    parameters = listOf("Landroid/content/Context;", "Ljava/lang/String;", "Ljava/lang/String;"),
    strings = listOf("/Camera/", "image/*", "(relative_path=? OR relative_path=?) AND _display_name=?"),
    custom = { _, classDef -> classDef.type in MEDIA_STORE_HELPER_DESCRIPTORS },
)

internal object VideoMediaStoreInsertFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Landroid/net/Uri;",
    parameters = listOf(
        "Landroid/content/Context;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
    ),
    custom = { method, classDef ->
        classDef.type in MEDIA_STORE_HELPER_DESCRIPTORS &&
        method.implementation?.instructions?.any { instruction ->
            instruction.getReference<MethodReference>()?.let { reference ->
                reference.definingClass == "Landroid/provider/MediaStore\$Video\$Media;" &&
                    reference.name == "getContentUri"
            } == true
        } == true
    },
)

internal object PhotoMediaStoreInsertFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "Landroid/net/Uri;",
    parameters = listOf(
        "Landroid/content/Context;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
    ),
    custom = { method, classDef ->
        classDef.type in MEDIA_STORE_HELPER_DESCRIPTORS &&
        method.implementation?.instructions?.any { instruction ->
            instruction.getReference<MethodReference>()?.let { reference ->
                reference.definingClass == "Landroid/provider/MediaStore\$Images\$Media;" &&
                    reference.name == "getContentUri"
            } == true
        } == true
    },
)

internal object ImagePostMediaCopyFingerprint : Fingerprint(
    // LX/03q3;->LJJIJIIJIL on 47.5.3, LX/03rK;->LJJIJIL on 47.6.3; matched by shape.
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
        calls.any { it.definingClass in MEDIA_STORE_HELPER_DESCRIPTORS && it.name == "LJ" } &&
            calls.any { it.definingClass in MEDIA_STORE_HELPER_DESCRIPTORS && it.name == "LIZJ" }
    },
)

// Owner renamed every release (47.5.3: LX/14fh;->LJ, 47.6.3: LX/0qLO;->LJ); the parameter list is unique.
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

