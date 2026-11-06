package app.morphe.extension.tiktok.profile;

import app.morphe.extension.tiktok.settings.Settings;

/**
 * Turns on TikTok's new profile layout (left-aligned header, the "profile_left_align"
 * experiment) for everyone, instead of only for accounts the server put in that experiment.
 * TikTok reads both values once per app start, so a change needs a restart.
 */
@SuppressWarnings("unused")
public final class NewProfileLayout {
    private NewProfileLayout() {
    }

    /** Value of "profile_left_align": 1 turns the new layout on. */
    public static int leftAlign(int serverValue) {
        try {
            return Settings.NEW_PROFILE_LAYOUT.get() ? 1 : serverValue;
        } catch (Throwable ignored) {
            return serverValue;
        }
    }

    /**
     * Flags that real accounts in the experiment get together with the layout
     * ("profile_left_align_avatar_opt", "profile_left_align_avatar_and_info_merge").
     * Without them TikTok drops the server's follower counts and "Edit profile" block,
     * because it only registers the containers that hold them when these are on.
     */
    public static boolean forceOn(boolean serverValue) {
        try {
            return Settings.NEW_PROFILE_LAYOUT.get() || serverValue;
        } catch (Throwable ignored) {
            return serverValue;
        }
    }

    /**
     * TikTok's own profile banner (the "profile background" experiment:
     * "profile_bg_in_allow_list" and "profile_bg_enable_consumption_group").
     * 1 lets you set one on your profile and see other people's.
     */
    public static int banner(int serverValue) {
        try {
            return Settings.NEW_PROFILE_LAYOUT.get() ? 1 : serverValue;
        } catch (Throwable ignored) {
            return serverValue;
        }
    }

    /** Value of "profile_left_align_ab_group": which variant of the new layout to show (1 to 4). */
    public static int leftAlignGroup(int serverValue) {
        try {
            if (!Settings.NEW_PROFILE_LAYOUT.get()) {
                return serverValue;
            }
            int variant = Settings.NEW_PROFILE_LAYOUT_VARIANT.get();
            return variant >= 1 && variant <= 4 ? variant : 1;
        } catch (Throwable ignored) {
            return serverValue;
        }
    }
}
