package tri.novica.gfssystem.dto.uzivo;

import java.util.Map;

/** Učesnik runde zadatka u pregledu: dodeljena varijanta, da li je odgovorio, tačnost i tačnost po polju. */
public record UcesnikZadatka(Long ucesnikId, String ime, Long varijantaId, String varijantaNaziv, boolean odgovorio,
                             Boolean tacno, Map<String, Boolean> polja) {
}
