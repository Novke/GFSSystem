package tri.novica.gfssystem.dto.banka;

/**
 * Jedna stavka poništavanja serije ({@link PonistavanjeInfo}): {@code vrsta} je {@value #VARIJANTA}, {@value #GRUPA},
 * {@value #SLAJD} ili {@value #PREZENTACIJA} (A2 dodaje {@code IZMENA_SLAJDA}), {@code id} i {@code naziv} su objekta,
 * a {@code razlog} ima samo preskočena stavka ({@code null} kod obrisane).
 */
public record StavkaPonistavanja(String vrsta, Long id, String naziv, String razlog) {

    public static final String VARIJANTA = "VARIJANTA";
    public static final String GRUPA = "GRUPA";
    public static final String SLAJD = "SLAJD";
    public static final String PREZENTACIJA = "PREZENTACIJA";
}
