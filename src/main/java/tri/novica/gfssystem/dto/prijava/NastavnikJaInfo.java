package tri.novica.gfssystem.dto.prijava;

/** Ko je prijavljen ({@code GET /auth/ja}): {@code uloga} je uvek {@code "NASTAVNIK"}. */
public record NastavnikJaInfo(String uloga, String korisnik) {
}
