package tri.novica.gfssystem.utility;

import org.springframework.http.HttpStatus;
import tri.novica.gfssystem.exceptions.SystemException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;

/**
 * Školska godina {@code Y} traje od 1. oktobra {@code Y} do 30. septembra {@code Y+1} (prikaz "2025/26").
 * Filteri lista primaju godinu kao broj ({@code godina=2025}) i pretvaraju je u opseg datuma.
 */
public final class SkolskaGodina {

    /** Razuman opseg za query parametar; van njega {@link LocalDate} puca ili upit nema smisla. */
    public static final int MIN = 1900;
    public static final int MAX = 9998;

    private SkolskaGodina() {
    }

    /** Prvi dan školske godine: 1. 10. {@code godina}. */
    public static LocalDate pocetak(int godina) {
        return LocalDate.of(godina, Month.OCTOBER, 1);
    }

    /** Poslednji dan školske godine (uključivo): 30. 9. {@code godina + 1}. */
    public static LocalDate kraj(int godina) {
        return LocalDate.of(godina + 1, Month.SEPTEMBER, 30);
    }

    /** Školska godina kojoj pripada datum. */
    public static int za(LocalDate datum) {
        return datum.getMonthValue() >= Month.OCTOBER.getValue() ? datum.getYear() : datum.getYear() - 1;
    }

    /** Tekuća školska godina po {@code Clock} bean-u (u testovima {@code Clock.fixed}). */
    public static int tekuca(Clock clock) {
        return za(LocalDate.now(clock));
    }

    /** Provera query parametra {@code godina}: null je bez filtera, van [{@value #MIN}, {@value #MAX}] je 400. */
    public static void proveri(Integer godina) {
        if (godina != null && (godina < MIN || godina > MAX)) {
            throw new SystemException("Neispravan parametar: godina.", HttpStatus.BAD_REQUEST);
        }
    }
}
