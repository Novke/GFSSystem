package tri.novica.gfssystem.utility;

import jakarta.servlet.http.HttpServletRequest;

/** IP klijenta za log javnih endpointa. Backend je iza dva nginx-a: prvi element X-Forwarded-For je klijent. */
public final class KlijentIp {

    /** Dovoljno za IPv6 u tekstualnom obliku; zaglavlje šalje klijent, pa u log ne ide neograničeno. */
    private static final int MAX_IP = 64;

    private KlijentIp() {}

    public static String iz(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        String ip = xff != null && !xff.isBlank() ? xff.split(",")[0].trim() : request.getRemoteAddr();
        return ip != null && ip.length() > MAX_IP ? ip.substring(0, MAX_IP) : ip;
    }
}
