package tri.novica.gfssystem.dto.uzivo;

import tri.novica.gfssystem.entity.uzivo.TelefonPrikaz;
import tri.novica.gfssystem.entity.uzivo.Tema;

/** Naziv, opis i podešavanja prezentacije (slajdovi se menjaju posebno); {@code tema == null} ne menja temu. */
public record UpdatePrezentacijaCmd(String naziv, String opis, boolean takmicenje, TelefonPrikaz telefonPrikaz,
                                    boolean detaljiDozvoljeni, Tema tema) {
}
