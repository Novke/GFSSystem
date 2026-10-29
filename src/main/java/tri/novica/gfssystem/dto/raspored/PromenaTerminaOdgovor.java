package tri.novica.gfssystem.dto.raspored;

/**
 * Posle otkazivanja ili vraćanja: termin, koliko poruka je stvarno stavljeno u red za slanje ({@code obavesteno}; 0 bez
 * promene ili kad je slanje isključeno u zahtevu) i koliko studenata sa adresom poruku nije dobilo ({@code neposlato}: pun
 * red ili slanje nije podešeno).
 */
public record PromenaTerminaOdgovor(TerminInfo termin, int obavesteno, int neposlato) {
}
