package tri.novica.gfssystem.service.mejl;

/**
 * Jedan mejl, čist tekst. {@code toString} namerno izostavlja {@code za} i {@code telo} (adresa i kod ne smeju u log).
 * Objavljuje se kao događaj ({@code ApplicationEventPublisher.publishEvent}) i šalje posle commit-a.
 */
public record MejlPoruka(String za, String naslov, String telo) {

    @Override
    public String toString() {
        return "MejlPoruka[naslov=" + naslov + "]";
    }
}
