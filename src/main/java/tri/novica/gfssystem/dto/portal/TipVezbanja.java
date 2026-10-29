package tri.novica.gfssystem.dto.portal;

import java.util.List;

/**
 * Tip zadatka otvoren za vežbanje (list): naziv, put od teme do tipa ({@code putanja}, bez teme i bez samog tipa; prazno kad
 * je tip odmah ispod teme), broj zadataka po nivou (tačno tri broja), da li se radi na papiru (bez polja) i sopstveni
 * učinak: od {@code provereno} proverenih zadataka {@code tacno} je tačno.
 */
public record TipVezbanja(Long tipId, String naziv, List<String> putanja, List<Integer> poNivou, int ukupno,
                          boolean papir, int provereno, int tacno) {
}
