package tri.novica.gfssystem.dto.banka;

import org.springframework.http.HttpStatus;
import tri.novica.gfssystem.entity.banka.IzvorVarijante;
import tri.novica.gfssystem.entity.banka.Namena;
import tri.novica.gfssystem.entity.banka.StatusVarijante;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.utility.Nivo;
import tri.novica.gfssystem.utility.Serija;

/**
 * Filteri pretrage banke ({@code GET /banka/pretraga}); svako polje je opciono, {@code null} = bez filtera. {@code q}
 * se traži u nazivu i tekstu varijante i u nazivu njene grupe, bez obzira na velika i mala slova. Zadat {@code q} (i
 * prazan) mora imati bar {@value #MIN_UPIT} znaka posle trim-a, brojano u code point-ima: inače 400. Konstruktor
 * trimuje {@code q}, pa filter koji postoji uvek ima ispravan upit. {@code izvor} i {@code serija} su izvor i Klodova
 * serija varijante; zadata serija (i prazna) mora odgovarati {@link Serija#OBRAZAC}, inače 400 {@link Serija#PORUKA}.
 * {@code nivo} je nivo varijante, 1-3 (inače 400 {@link Nivo#GRANICE}).
 */
public record BankaFilter(Long predmetId, Namena namena, String q, StatusVarijante status, IzvorVarijante izvor,
                          String serija, Integer nivo) {

    public static final int MIN_UPIT = 2;
    static final String UPIT_PREKRATAK = "Upit mora imati bar 2 znaka.";

    public BankaFilter {
        if (q != null) {
            q = q.strip();
            if (q.codePointCount(0, q.length()) < MIN_UPIT) {
                throw new SystemException(UPIT_PREKRATAK, HttpStatus.BAD_REQUEST);
            }
        }
        if (serija != null) {
            Serija.proveri(serija);
        }
        Nivo.proveri(nivo);
    }

    /** Bez filtera po nivou. */
    public BankaFilter(Long predmetId, Namena namena, String q, StatusVarijante status, IzvorVarijante izvor,
                       String serija) {
        this(predmetId, namena, q, status, izvor, serija, null);
    }

    /** Bez filtera po izvoru i seriji. */
    public BankaFilter(Long predmetId, Namena namena, String q, StatusVarijante status) {
        this(predmetId, namena, q, status, null, null, null);
    }
}
