package tri.novica.gfssystem.dto.prijava;

import tri.novica.gfssystem.entity.prijava.VrstaObavestenja;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Obaveštenje za nastavnika (spec 6). {@code studentIme} je "Ime Prezime" ili {@code null} (indeks bez studenta);
 * {@code indeks} je studentov, a bez studenta onaj koji je student uneo; {@code podaci} su polja obaveštenja kao objekat
 * ({@code indeks}, {@code godina}, {@code imaMejl}, {@code ip}, {@code uredjaj}: {@code ZAPAMCEN|NEPOZNAT}). {@code godina} je
 * studentova godina upisa, a bez studenta ona koju je student uneo ({@code null} za zahtev starog oblika bez godine).
 */
public record ObavestenjeInfo(Long id, VrstaObavestenja vrsta, Long studentId, String studentIme, String indeks,
                              Map<String, Object> podaci, LocalDateTime kreirano, LocalDateTime procitano,
                              LocalDateTime reseno, Integer godina) {
}
