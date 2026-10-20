package tri.novica.gfssystem.dto.prijava;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Zahtev za kod ({@code POST /public/nalog/kod}); indeks se normalizuje u servisu. */
public record KodCmd(
        @NotBlank(message = "Indeks je obavezan.")
        @Size(max = 30, message = "Indeks je predugačak.")
        String indeks) {

    /** Bez indeksa u {@code toString} (log: indeks samo kao heš). */
    @Override
    public String toString() {
        return "KodCmd[indeks=***]";
    }
}
