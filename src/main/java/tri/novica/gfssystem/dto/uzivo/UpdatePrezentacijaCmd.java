package tri.novica.gfssystem.dto.uzivo;

import tri.novica.gfssystem.entity.uzivo.TelefonPrikaz;

/** Naziv, opis i podešavanja prezentacije (slajdovi se menjaju posebno). */
public record UpdatePrezentacijaCmd(String naziv, String opis, boolean takmicenje, TelefonPrikaz telefonPrikaz,
                                    boolean detaljiDozvoljeni) {
}
