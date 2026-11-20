package tri.novica.gfssystem.service.mejl;

import java.util.List;

/**
 * Jedan mejl: čist tekst ({@code telo}) i, kad nije null, isti sadržaj kao HTML ({@code html}; šalje se kao
 * {@code multipart/alternative}), plus opcioni {@code prilozi} (tada je spoljašnji deo {@code multipart/mixed}).
 * {@code toString} namerno izostavlja {@code za}, {@code telo}, {@code html} i priloge (adresa, ime, kod i sadržaj
 * fajla ne smeju u log). Objavljuje se kao događaj ({@code ApplicationEventPublisher.publishEvent}) i šalje posle commit-a.
 */
public record MejlPoruka(String za, String naslov, String telo, String html, List<Prilog> prilozi) {

    /** Prilog: {@code naziv} (UTF-8, RFC 2231), {@code mime} i bajtovi. {@code toString} ne ispisuje ni ime ni sadržaj. */
    public record Prilog(String naziv, String mime, byte[] sadrzaj) {
        @Override
        public String toString() {
            return "Prilog[" + (sadrzaj == null ? 0 : sadrzaj.length) + " B]";
        }
    }

    public MejlPoruka {
        prilozi = prilozi == null ? List.of() : List.copyOf(prilozi);
    }

    /** Čist tekst i HTML, bez priloga. */
    public MejlPoruka(String za, String naslov, String telo, String html) {
        this(za, naslov, telo, html, List.of());
    }

    /** Samo čist tekst. */
    public MejlPoruka(String za, String naslov, String telo) {
        this(za, naslov, telo, null, List.of());
    }

    @Override
    public String toString() {
        return "MejlPoruka[naslov=" + naslov + "]";
    }
}
