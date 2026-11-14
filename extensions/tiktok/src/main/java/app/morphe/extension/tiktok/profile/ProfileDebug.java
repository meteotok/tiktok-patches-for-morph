package app.morphe.extension.tiktok.profile;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Records the profile component tree the server sends, plus the layout flags TikTok
 * resolved, so it can be copied from the mod settings when the new profile looks wrong.
 */
@SuppressWarnings("unused")
public final class ProfileDebug {
    private static volatile String last = "Профиль ещё не открывался с момента запуска.";

    private ProfileDebug() {
    }

    public static String get() {
        return last;
    }

    /** Called with the ProfileUser that TikTok is about to show. */
    public static void onProfile(Object profileUser) {
        try {
            if (profileUser == null) return;
            StringBuilder sb = new StringBuilder();
            sb.append("Prisma profile debug\n");
            sb.append(flags()).append('\n');
            for (String part : new String[]{"profileBackground", "navBarComponents", "headerComponents"}) {
                Object node = field(profileUser, part);
                sb.append("== ").append(part).append(node == null ? ": null\n" : "\n");
                if (node != null) tree(node, 1, sb);
            }
            last = sb.toString();
        } catch (Throwable t) {
            last = "Profile debug failed: " + t;
        }
    }

    private static void tree(Object node, int depth, StringBuilder sb) throws Exception {
        if (depth > 12 || sb.length() > 12000) return;
        for (int i = 0; i < depth; i++) sb.append("  ");
        sb.append(field(node, "componentName"));
        Object ui = field(node, "uiData");
        if (ui != null) {
            Object style = field(ui, "displayStyle");
            Object type = field(ui, "type");
            if (style != null || type != null) sb.append(" [").append(style).append('/').append(type).append(']');
        }
        Object biz = field(node, "bizData");
        if (biz != null) {
            String s = String.valueOf(biz);
            sb.append(" ").append(s.length() > 160 ? s.substring(0, 160) + "…" : s);
        }
        sb.append('\n');
        Object children = field(node, "components");
        if (children instanceof List) {
            for (Object c : (List<?>) children) {
                if (c != null) tree(c, depth + 1, sb);
            }
        }
    }

    // Getters of TikTok 47.6.3's profile layout config class (X.0Bwu).
    private static String flags() {
        StringBuilder sb = new StringBuilder("flags:");
        try {
            Class<?> c = Class.forName("X.0Bwu");
            String[][] names = {
                    {"LJIIL", "leftAlign"}, {"LIZ", "group"}, {"LIZJ", "avatarRight"},
                    {"LIZLLL", "avatarOpt"}, {"LJIILIIL", "merge"}, {"LJIILL", "smallAvatar"},
                    {"LJIIIZ", "LJIIIZ"}, {"LJIIJ", "LJIIJ"}, {"LIZIZ", "LIZIZ"},
            };
            for (String[] n : names) {
                try {
                    Method m = c.getDeclaredMethod(n[0]);
                    m.setAccessible(true);
                    sb.append(' ').append(n[1]).append('=').append(m.invoke(null));
                } catch (Throwable t) {
                    sb.append(' ').append(n[1]).append("=?");
                }
            }
        } catch (Throwable t) {
            sb.append(" unavailable ").append(t);
        }
        return sb.toString();
    }

    private static Object field(Object o, String name) {
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(o);
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable t) {
                return null;
            }
        }
        return null;
    }
}
