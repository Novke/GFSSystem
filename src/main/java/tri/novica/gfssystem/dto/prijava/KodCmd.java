package tri.novica.gfssystem.dto.prijava;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Zahtev za kod ({@code POST /public/nalog/kod}): indeks ({@code GD5}) i godina upisa; indeks se svodi na kanonski u servisu. */
public record KodCmd(
        @NotBlank(message = "Indeks je obavezan.")
        @Size(max = 30, message = "Indeks je predugačak.")
        String indeks,
        /** Godina upisa (forma u tri dela); null = stari SPA sa jednim poljem (PRIVREMENO, vidi {@code KodServis}). */
        @Min(value = 2000, message = "Godina upisa nije ispravna.")
        @Max(value = 2100, message = "Godina upisa nije ispravna.")
        Integer godina) {

    /** Bez indeksa u {@code toString} (log: indeks samo kao heš). */
    /** Stari oblik (jedno polje, bez godine). */
    public KodCmd(String indeks) {
        this(indeks, null);
    }

    @Override
    public String toString() {
        return "KodCmd[indeks=***, godina=" + godina + "]";
    }
}
