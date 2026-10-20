package tri.novica.gfssystem.dto.prijava;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Prijava kodom sa mejla ({@code POST /public/nalog/prijava}); kod se nikad ne loguje. */
public record PrijavaKodomCmd(
        @NotBlank(message = "Indeks je obavezan.")
        @Size(max = 30, message = "Indeks je predugačak.")
        String indeks,
        @NotNull(message = "Kod je obavezan.")
        @Pattern(regexp = "^[0-9]{6}$", message = "Kod ima 6 cifara.")
        String kod) {

    /** Bez indeksa i koda u {@code toString} (log, izuzeci). */
    @Override
    public String toString() {
        return "PrijavaKodomCmd[indeks=***, kod=***]";
    }
}
