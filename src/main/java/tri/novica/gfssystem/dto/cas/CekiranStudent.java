package tri.novica.gfssystem.dto.cas;

import java.time.LocalDateTime;

/**
 * Čekiran student za nastavnika: {@code kasnio} kad je čekiran više od 15 min posle prvog čekiranja na predavanju,
 * {@code istiTelefonKao}: id studenta koji se ranije čekirao istim telefonom (kolačić {@code gfs_uredjaj}), inače {@code null}.
 */
public record CekiranStudent(Long studentId, String ime, String prezime, String indeks, LocalDateTime vreme,
                             boolean kasnio, Long istiTelefonKao) {
}
