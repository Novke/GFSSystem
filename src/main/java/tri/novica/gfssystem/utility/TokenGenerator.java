package tri.novica.gfssystem.utility;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.regex.Pattern;

/** Token javnog linka za onboarding: 32 nasumična znaka, bez sličnih (0/O/o, 1/l/I/i). */
@Component
public class TokenGenerator {
    public static final String ALFABET = "23456789abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ";
    public static final int DUZINA = 32;
    public static final Pattern FORMAT = Pattern.compile("^[A-Za-z0-9]{32}$");

    private final SecureRandom random = new SecureRandom();

    public String novi() {
        StringBuilder sb = new StringBuilder(DUZINA);
        for (int i = 0; i < DUZINA; i++) {
            sb.append(ALFABET.charAt(random.nextInt(ALFABET.length())));
        }
        return sb.toString();
    }
}
