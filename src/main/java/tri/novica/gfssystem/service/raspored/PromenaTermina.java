package tri.novica.gfssystem.service.raspored;

/**
 * Šta se desilo sa terminom, za obaveštavanje studenata ({@link ObavestavanjeStudenata}): {@code OTKAZAN} (uz napomenu
 * nastavnika ili {@code null}) ili {@code VRACEN} ("čas se ipak održava"). {@code kljuc} je ključ redova zvona
 * ({@code studentska_obavestenja}) upisanih u transakciji promene; push se šalje iz tih redova ({@code null} = bez redova,
 * pa i bez push-a).
 */
public record PromenaTermina(Vrsta vrsta, TerminOpis termin, String napomena, String kljuc) {

    public enum Vrsta { OTKAZAN, VRACEN }

    public PromenaTermina(Vrsta vrsta, TerminOpis termin, String napomena) {
        this(vrsta, termin, napomena, null);
    }
}
