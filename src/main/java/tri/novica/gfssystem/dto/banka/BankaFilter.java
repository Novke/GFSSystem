package tri.novica.gfssystem.dto.banka;

import org.springframework.http.HttpStatus;
import tri.novica.gfssystem.entity.banka.Namena;
import tri.novica.gfssystem.entity.banka.StatusVarijante;
import tri.novica.gfssystem.exceptions.SystemException;

/**
 * Filteri pretrage banke ({@code GET /banka/pretraga}); svako polje je opciono, {@code null} = bez filtera. {@code q}
 * se traži u nazivu i tekstu varijante i u nazivu njene grupe, bez obzira na velika i mala slova. Zadat {@code q} (i
 * prazan) mora imati bar {@value #MIN_UPIT} znaka posle trim-a, brojano u code point-ima: inače 400. Konstruktor
 * trimuje {@code q}, pa filter koji postoji uvek ima ispravan upit.
 */
public record BankaFilter(Long predmetId, Namena namena, String q, StatusVarijante status) {

    public static final int MIN_UPIT = 2;
    static final String UPIT_PREKRATAK = "Upit mora imati bar 2 znaka.";

    public BankaFilter {
        if (q != null) {
            q = q.strip();
            if (q.codePointCount(0, q.length()) < MIN_UPIT) {
                throw new SystemException(UPIT_PREKRATAK, HttpStatus.BAD_REQUEST);
            }
        }
    }
}
