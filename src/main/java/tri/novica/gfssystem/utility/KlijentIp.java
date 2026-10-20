package tri.novica.gfssystem.utility;

import jakarta.servlet.http.HttpServletRequest;

/**
 * IP klijenta (log i ograničenja po IP-u). Backend je iza dva nginx-a, a pravu adresu postavlja Tomcat
 * ({@code server.forward-headers-strategy=native}, RemoteIpValve): ide {@code X-Forwarded-For} zdesna, preskače pouzdane
 * (privatne) proksije i staje na prvoj javnoj adresi, pa zaglavlje koje klijent sam upiše ne može da lažira IP.
 * Zato se ovde čita samo {@code getRemoteAddr()}, nikad sirovo zaglavlje.
 */
public final class KlijentIp {

    /** Kolona {@code ip varchar(64)}; dovoljno i za IPv6 u tekstualnom obliku. */
    private static final int MAX_IP = 64;

    private KlijentIp() {}

    public static String iz(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        return ip != null && ip.length() > MAX_IP ? ip.substring(0, MAX_IP) : ip;
    }
}
