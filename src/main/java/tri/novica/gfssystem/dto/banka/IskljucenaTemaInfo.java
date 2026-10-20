package tri.novica.gfssystem.dto.banka;

import tri.novica.gfssystem.entity.banka.IzvorVarijante;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Isključena tema predmeta. {@code grupaId} i {@code putanja} (od korena do grupe) su {@code null} i prazna za temu bez
 * grupe, i kad je grupa u međuvremenu obrisana (tema ostaje kao tekst). {@code izvor} i {@code serija}: ko je isključio
 * temu i Klodova serija upisa ({@code null} za ručno isključenu).
 */
public record IskljucenaTemaInfo(Long id, Long predmetId, String opis, Long grupaId, List<GrupaPutanja> putanja,
                                 IzvorVarijante izvor, String serija, LocalDateTime kreirano) {
}
