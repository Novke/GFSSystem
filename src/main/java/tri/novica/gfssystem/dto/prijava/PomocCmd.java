package tri.novica.gfssystem.dto.prijava;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** "Ne stiže mi kod" ({@code POST /public/nalog/pomoc}); indeks se normalizuje u servisu. */
public record PomocCmd(
        @NotBlank(message = "Indeks je obavezan.")
        @Size(max = 30, message = "Indeks je predugačak.")
        String indeks) {

    /** Bez indeksa u {@code toString} (log: indeks samo kao heš). */
    @Override
    public String toString() {
        return "PomocCmd[indeks=***]";
    }
}
