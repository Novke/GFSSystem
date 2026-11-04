package tri.novica.gfssystem.dto.uzivo;

import tri.novica.gfssystem.entity.uzivo.VrstaIzmene;

import java.time.LocalDateTime;

/** Unos istorije slajda; {@code imaSnimak} kaže da li se unos može vratiti, {@code obrisan} da slajda više nema. */
public record SlajdIzmenaInfo(Long id, Long prezentacijaId, Long slajdId, int rb, VrstaIzmene vrsta, String serija, String opis,
                              LocalDateTime kreirano, boolean imaSnimak, boolean obrisan) {
}
