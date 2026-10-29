package tri.novica.gfssystem.dto.raspored;

/**
 * Posle otkazivanja ili vraćanja: termin, koliko mejlova je stvarno stavljeno u red za slanje ({@code obavesteno}; 0 bez
 * promene ili kad je slanje isključeno u zahtevu), koliko studenata sa adresom mejl nije dobilo ({@code neposlato}: pun
 * red ili slanje nije podešeno) i koliko push poruka je stavljeno u red ({@code pushPoslato}, jedna po pretplaćenom
 * uređaju; aditivno, postojeća polja i dalje znače mejl).
 */
public record PromenaTerminaOdgovor(TerminInfo termin, int obavesteno, int neposlato, int pushPoslato) {
}
