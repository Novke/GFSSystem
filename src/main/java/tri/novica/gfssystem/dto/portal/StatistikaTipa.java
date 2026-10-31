package tri.novica.gfssystem.dto.portal;

import java.util.List;

/**
 * Statistika vežbanja po tipu (spec 5.6, samo nastavnik, samo zbirno): koliko studenata je vežbalo, izvučenih zadataka,
 * proverenih, tačnih na prvoj proveri i koliko puta je rešenje otvoreno bez tačne provere.
 */
public record StatistikaTipa(Long tipId, String naziv, List<String> putanja, int studenata, int pokusaja, int provereno,
                             int prvaTacno, int resenjeBezTacne) {
}
