package tri.novica.gfssystem.dto.prijava;

import java.time.LocalDateTime;

/**
 * Kod za diktiranje ({@code POST /studenti/{id}/nalog/kod-za-diktiranje}, samo nastavnik): {@code kod} (6 cifara, prikazuje
 * se jednom i nigde se ne čuva), {@code istice} (lokalno vreme servera, 10 minuta), {@code indeks} i {@code godina} koje
 * student unosi u formu ("Imam kod od nastavnika"), i {@code upozorenje} kad indeks studenta ne može da se unese u formu
 * u tri dela (inače null). {@code toString} bez koda.
 */
public record KodZaDiktiranjeInfo(String kod, LocalDateTime istice, String indeks, int godina, String upozorenje) {

    @Override
    public String toString() {
        return "KodZaDiktiranjeInfo[kod=***, istice=" + istice + "]";
    }
}
