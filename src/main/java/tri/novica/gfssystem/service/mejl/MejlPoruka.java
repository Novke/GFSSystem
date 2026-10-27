package tri.novica.gfssystem.service.mejl;

/**
 * Jedan mejl: čist tekst ({@code telo}) i, kad nije null, isti sadržaj kao HTML ({@code html}; šalje se kao
 * {@code multipart/alternative}). {@code toString} namerno izostavlja {@code za}, {@code telo} i {@code html} (adresa,
 * ime i kod ne smeju u log). Objavljuje se kao događaj ({@code ApplicationEventPublisher.publishEvent}) i šalje posle commit-a.
 */
public record MejlPoruka(String za, String naslov, String telo, String html) {

    /** Samo čist tekst. */
    public MejlPoruka(String za, String naslov, String telo) {
        this(za, naslov, telo, null);
    }

    @Override
    public String toString() {
        return "MejlPoruka[naslov=" + naslov + "]";
    }
}
