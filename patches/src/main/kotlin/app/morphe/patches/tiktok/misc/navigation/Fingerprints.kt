package app.morphe.patches.tiktok.misc.navigation

import app.morphe.patcher.Fingerprint

internal object TopTabModelListFingerprint : Fingerprint(
    definingClass = "/TabAbilityAssem;",
    name = "Oc2", // W52 on 46.2.3
    returnType = "Ljava/util/List;",
    parameters = listOf(),
)

internal object BottomTabModelListFingerprint : Fingerprint(
    definingClass = "/TabAbilityAssem;",
    name = "BB", // OA on 46.2.3
    returnType = "Ljava/util/List;",
    parameters = listOf(),
)
