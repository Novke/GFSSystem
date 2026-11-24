package tri.novica.gfssystem.dto.portal;

import tri.novica.gfssystem.dto.fajlovi.FajlKratko;
import tri.novica.gfssystem.entity.uzivo.Tema;

import java.util.List;

/** Pregled prezentacije za ponavljanje (spec 6.1): samo slajdovi koje je grupa studenta videla, redom; {@code fajlovi} su svi fajlovi prezentacije kad pravilo naloga važi, inače prazno. */
public record PregledPrezentacije(Long id, String naziv, Tema tema, Long predmetId, List<SlajdPregleda> slajdovi,
                                List<FajlKratko> fajlovi) {
}
