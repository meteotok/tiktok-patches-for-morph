package app.morphe.extension.tiktok.spoof.sim;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.tiktok.settings.Settings;

/**
 * Makes the system region and time zone TikTok reports match the spoofed SIM country.
 * TikTok sends sys_region (Locale country) and tz_name/timezone_name next to the SIM
 * values, so a Russian phone locale and Europe/Moscow still mark the device as Russian
 * when only the SIM is spoofed.
 */
@SuppressWarnings("unused")
public final class RegionSpoof {
    private static final Map<String, String> ZONES = new HashMap<>();

    static {
        String[] pairs = {
                "us", "America/New_York", "gb", "Europe/London", "ca", "America/Toronto",
                "ru", "Europe/Moscow", "de", "Europe/Berlin", "fr", "Europe/Paris",
                "it", "Europe/Rome", "es", "Europe/Madrid", "nl", "Europe/Amsterdam",
                "pl", "Europe/Warsaw", "pt", "Europe/Lisbon", "be", "Europe/Brussels",
                "ch", "Europe/Zurich", "at", "Europe/Vienna", "se", "Europe/Stockholm",
                "no", "Europe/Oslo", "dk", "Europe/Copenhagen", "gr", "Europe/Athens",
                "ua", "Europe/Kyiv", "ro", "Europe/Bucharest", "cz", "Europe/Prague",
                "hu", "Europe/Budapest", "ie", "Europe/Dublin", "tr", "Europe/Istanbul",
                "ae", "Asia/Dubai", "sa", "Asia/Riyadh", "qa", "Asia/Qatar",
                "kw", "Asia/Kuwait", "om", "Asia/Muscat", "jo", "Asia/Amman",
                "iq", "Asia/Baghdad", "lb", "Asia/Beirut", "eg", "Africa/Cairo",
                "ma", "Africa/Casablanca", "dz", "Africa/Algiers", "tn", "Africa/Tunis",
                "in", "Asia/Kolkata", "pk", "Asia/Karachi", "bd", "Asia/Dhaka",
                "lk", "Asia/Colombo", "np", "Asia/Kathmandu", "id", "Asia/Jakarta",
                "ph", "Asia/Manila", "th", "Asia/Bangkok", "vn", "Asia/Ho_Chi_Minh",
                "my", "Asia/Kuala_Lumpur", "sg", "Asia/Singapore", "hk", "Asia/Hong_Kong",
                "tw", "Asia/Taipei", "jp", "Asia/Tokyo", "kr", "Asia/Seoul",
                "au", "Australia/Sydney", "nz", "Pacific/Auckland", "br", "America/Sao_Paulo",
                "mx", "America/Mexico_City", "ar", "America/Argentina/Buenos_Aires",
                "co", "America/Bogota", "cl", "America/Santiago", "pe", "America/Lima",
                "za", "Africa/Johannesburg", "ng", "Africa/Lagos", "ke", "Africa/Nairobi",
                "gh", "Africa/Accra", "et", "Africa/Addis_Ababa",
        };
        for (int i = 0; i + 1 < pairs.length; i += 2) ZONES.put(pairs[i], pairs[i + 1]);
    }

    private RegionSpoof() {
    }

    private static String spoofedIso() {
        // Before the app context is set, touching Settings would initialize it too early.
        if (Utils.getContext() == null) return null;
        try {
            if (!Settings.SIM_SPOOF.get()) return null;
            String iso = Settings.SIM_SPOOF_ISO.get();
            if (iso == null) return null;
            iso = iso.trim().toLowerCase(Locale.US);
            return iso.matches("[a-z]{2}") ? iso : null;
        } catch (Throwable ignored) {
            // Settings not ready yet (very early static init): keep the real value.
            return null;
        }
    }

    private static TimeZone spoofedZone() {
        String iso = spoofedIso();
        String id = iso == null ? null : ZONES.get(iso);
        return id == null ? null : TimeZone.getTimeZone(id);
    }

    /** Replaces Locale.getCountry() results. */
    public static String getCountry(String value) {
        if (value == null || value.isEmpty()) return value;
        String iso = spoofedIso();
        return iso == null ? value : iso.toUpperCase(Locale.US);
    }

    /** Replaces TimeZone.getID() results. */
    public static String getTimeZoneId(String value) {
        TimeZone zone = spoofedZone();
        return zone == null ? value : zone.getID();
    }

    /** Replaces TimeZone.getRawOffset() results. */
    public static int getRawOffset(int value) {
        TimeZone zone = spoofedZone();
        return zone == null ? value : zone.getRawOffset();
    }
}
