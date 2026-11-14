package app.morphe.extension.tiktok.spoof.sim;

import java.util.Locale;

/**
 * Remembers the store region TikTok's server assigned (store-country-code cookie). The server
 * decides which features exist (Ask Tako, Shop, beauty/shade search modules) by this region,
 * and for a logged-in account it is bound to the account (src = uid), so a SIM spoof alone
 * can't change it. Shown in the region settings so it's clear what the server sees.
 */
@SuppressWarnings("unused")
public final class StoreRegion {
    private static volatile String region;
    private static volatile String source;

    private StoreRegion() {
    }

    /** Called right after TikTok parses the store-country-code cookie. */
    public static void capture(String countryCode, String src, String legacy) {
        if (countryCode == null || countryCode.isEmpty()) return;
        region = countryCode.toUpperCase(Locale.US);
        source = src;
    }

    public static String describe() {
        String r = region;
        if (r == null) return "Ещё неизвестен: открой ленту или поиск и зайди сюда снова";
        String src = source == null ? "" : source.toLowerCase(Locale.US);
        String by;
        switch (src) {
            case "uid": by = "привязан к аккаунту"; break;
            case "did": by = "по устройству"; break;
            case "ip": by = "по IP (VPN)"; break;
            default: by = src.isEmpty() ? "" : src; break;
        }
        return by.isEmpty() ? r : r + " · " + by;
    }
}
