package tri.novica.gfssystem.service.raspored;

/**
 * Šta se desilo sa terminom, za obaveštavanje studenata ({@link ObavestavanjeStudenata}): {@code OTKAZAN} (uz napomenu
 * nastavnika ili {@code null}) ili {@code VRACEN} ("čas se ipak održava").
 */
public record PromenaTermina(Vrsta vrsta, TerminOpis termin, String napomena) {

    public enum Vrsta { OTKAZAN, VRACEN }
}
