package tri.novica.gfssystem.dto.prijava;

/** {@code GET /public/nalog/stanje} (anonimno): samo da li je ovaj pregledač prijavljen kao student. */
public record NalogStanjeInfo(boolean prijavljen) {
}
