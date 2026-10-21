package tri.novica.gfssystem.utility;

import org.springframework.http.HttpStatus;
import tri.novica.gfssystem.exceptions.SystemException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Link za poziv u WhatsApp grupu. Čuva se i šalje samo u kanonskom obliku {@code https://chat.whatsapp.com/<kod>}:
 * prikazuje se na javnoj formi za upis, pa se nikad ne prihvata proizvoljan URL (drugi domen, {@code javascript:}).
 * Prihvata ono što WhatsApp daje pri deljenju: sa ili bez {@code https://}, sa {@code www.}, sa upitom
 * ({@code ?mode=...}) ili kosom crtom na kraju; upit se odbacuje. Domen bez obzira na velika slova, kod tačno kako je.
 */
public final class WaLink {

    private static final Pattern OBLIK = Pattern.compile(
            "^(?i:(?:https?://)?(?:www\\.)?chat\\.whatsapp\\.com/(?:invite/)?)([A-Za-z0-9]{10,40})/?(?:[?#].*)?$");
    public static final String NEISPRAVAN =
            "Link mora biti poziv u WhatsApp grupu (https://chat.whatsapp.com/...). U WhatsApp-u: Info grupe → Pozovi putem linka.";

    private WaLink() {
    }

    /** Prazno ili null je null (link se uklanja); ispravan link u kanonski oblik; sve ostalo 400. */
    public static String normalizuj(String link) {
        if (link == null || link.isBlank()) {
            return null;
        }
        Matcher m = OBLIK.matcher(link.strip());
        if (!m.matches()) {
            throw new SystemException(NEISPRAVAN, HttpStatus.BAD_REQUEST);
        }
        return "https://chat.whatsapp.com/" + m.group(1);
    }
}
