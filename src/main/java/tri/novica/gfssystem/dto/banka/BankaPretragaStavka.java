package tri.novica.gfssystem.dto.banka;

import tri.novica.gfssystem.entity.banka.RenderStatus;
import tri.novica.gfssystem.entity.banka.StatusVarijante;

import java.util.List;

/**
 * Red pretrage banke: varijanta sa grupom u kojoj je ({@code putanja} od korena do te grupe) i isečkom teksta
 * ({@code isecak}: prvih 160 znakova, novi redovi su razmaci). {@code naziv} je {@code null} kad varijanta nema naziv.
 */
public record BankaPretragaStavka(Long varijantaId, Long grupaId, String grupaNaziv, List<GrupaPutanja> putanja,
                                  String naziv, StatusVarijante status, RenderStatus renderStatus, String isecak) {
}
