package tri.novica.gfssystem.dto.prijava;

/**
 * Odgovor na zahtev za kod, isti za postojeći i nepostojeći nalog (spec 5.1): {@code maska} je maska studentovog mejla
 * ({@code n***@gmail.com}) ili deterministička lažna maska kad nalog ili mejl ne postoji.
 */
public record KodOdgovor(String poruka, String maska) {
}
