package tri.novica.gfssystem.dto.prijava;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Prijava nastavnika ({@code POST /auth/prijava}); lozinka se nikad ne loguje ni ne čuva. */
public record PrijavaNastavnikaCmd(
        @NotBlank(message = "Korisničko ime je obavezno.")
        @Size(max = 100, message = "Korisničko ime je predugačko.")
        String korisnik,
        @NotBlank(message = "Lozinka je obavezna.")
        @Size(max = 200, message = "Lozinka je predugačka.")
        String lozinka) {

    /** Bez lozinke u {@code toString} (log, izuzeci). */
    @Override
    public String toString() {
        return "PrijavaNastavnikaCmd[korisnik=***, lozinka=***]";
    }
}
