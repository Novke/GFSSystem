package tri.novica.gfssystem.service.prijava;

/**
 * Kratak opis uređaja iz {@code User-Agent} za spisak sesija ("Android · Chrome"): operativni sistem i pregledač,
 * bez verzija. Namerno jednostavno prepoznavanje redom, ne biblioteka; sve nepoznato je "Nepoznat uređaj".
 */
public final class OpisUredjaja {

    public static final String NEPOZNAT = "Nepoznat uređaj";
    private static final int MAX = 120;

    private OpisUredjaja() {}

    public static String iz(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return NEPOZNAT;
        }
        String os = sistem(userAgent);
        String pregledac = pregledac(userAgent);
        String opis;
        if (os != null && pregledac != null) {
            opis = os + " · " + pregledac;
        } else if (os != null) {
            opis = os;
        } else if (pregledac != null) {
            opis = pregledac;
        } else {
            opis = NEPOZNAT;
        }
        return opis.length() > MAX ? opis.substring(0, MAX) : opis;
    }

    /** Redosled je bitan: iPhone/iPad i Android sadrže "Mac OS X" odnosno "Linux". */
    private static String sistem(String ua) {
        if (ua.contains("iPhone")) return "iPhone";
        if (ua.contains("iPad")) return "iPad";
        if (ua.contains("Android")) return "Android";
        if (ua.contains("Windows")) return "Windows";
        if (ua.contains("CrOS")) return "ChromeOS";
        if (ua.contains("Macintosh") || ua.contains("Mac OS X")) return "macOS";
        if (ua.contains("Linux") || ua.contains("X11")) return "Linux";
        return null;
    }

    /** Redosled je bitan: skoro svi sadrže "Chrome" i "Safari". */
    private static String pregledac(String ua) {
        if (ua.contains("Edg/") || ua.contains("EdgA/") || ua.contains("EdgiOS/")) return "Edge";
        if (ua.contains("OPR/") || ua.contains("Opera")) return "Opera";
        if (ua.contains("SamsungBrowser")) return "Samsung Internet";
        if (ua.contains("Firefox/") || ua.contains("FxiOS/")) return "Firefox";
        if (ua.contains("Chrome/") || ua.contains("CriOS/")) return "Chrome";
        if (ua.contains("Safari/")) return "Safari";
        return null;
    }
}
