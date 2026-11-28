package tri.novica.gfssystem.dto.prijava;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Prijava kodom sa mejla ({@code POST /public/nalog/prijava}); kod se nikad ne loguje. */
public record PrijavaKodomCmd(
        @NotBlank(message = "Indeks je obavezan.")
        @Size(max = 30, message = "Indeks je predugačak.")
        String indeks,
        /** Godina upisa (forma u tri dela); null = stari SPA sa jednim poljem (PRIVREMENO, vidi {@code KodServis}). */
        @Min(value = 2000, message = "Godina upisa nije ispravna.")
        @Max(value = 2100, message = "Godina upisa nije ispravna.")
        Integer godina,
        @NotNull(message = "Kod je obavezan.")
        @Pattern(regexp = "^[0-9]{6}$", message = "Kod ima 6 cifara.")
        String kod,
        /** Prijava sa strane /r (računar u učionici): sesija RACUNAR od 4 h umesto uređaja od 365 dana. */
        Boolean racunar) {

    /** Bez oznake računara (prijava telefona ili kućnog računara). */
    public PrijavaKodomCmd(String indeks, Integer godina, String kod) {
        this(indeks, godina, kod, null);
    }

    /** Stari oblik (jedno polje, bez godine). */
    public PrijavaKodomCmd(String indeks, String kod) {
        this(indeks, null, kod, null);
    }

    /** Bez indeksa i koda u {@code toString} (log, izuzeci). */
    @Override
    public String toString() {
        return "PrijavaKodomCmd[indeks=***, godina=" + godina + ", kod=***, racunar=" + racunar + "]";
    }
}
