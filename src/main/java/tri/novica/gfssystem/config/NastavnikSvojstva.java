package tri.novica.gfssystem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.regex.Pattern;

/**
 * Nastavnik (jedini administrator) iz podešavanja okruženja: {@code gfs.nastavnik.korisnik},
 * {@code gfs.nastavnik.lozinka-bcrypt} (bcrypt heš, nikad lozinka) i {@code gfs.nastavnik.mejl} (adresa za obaveštenja).
 * Prazno korisnik/heš = prijava nastavnika nije podešena ({@link #podeseno()}), start prolazi; neprazan heš pogrešnog
 * oblika (npr. sirova lozinka) ruši start.
 */
@ConfigurationProperties("gfs.nastavnik")
public record NastavnikSvojstva(String korisnik, String lozinkaBcrypt, String mejl) {

    private static final Pattern BCRYPT = Pattern.compile("^\\$2[aby]\\$(1[0-4])\\$[./A-Za-z0-9]{53}$");

    public NastavnikSvojstva {
        korisnik = korisnik == null ? "" : korisnik.trim();
        lozinkaBcrypt = lozinkaBcrypt == null ? "" : lozinkaBcrypt.trim();
        mejl = mejl == null ? "" : mejl.trim();
        if (!lozinkaBcrypt.isEmpty() && !BCRYPT.matcher(lozinkaBcrypt).matches()) {
            throw new IllegalStateException("gfs.nastavnik.lozinka-bcrypt nije bcrypt heš");
        }
    }

    /** Korisnik i heš su podešeni; inače prijava nastavnika vraća 503. */
    public boolean podeseno() {
        return !korisnik.isEmpty() && !lozinkaBcrypt.isEmpty();
    }
}
