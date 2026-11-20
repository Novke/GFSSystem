package tri.novica.gfssystem.dto.fajlovi;

import java.time.LocalDateTime;

/** Fajl uz prezentaciju za nastavnika; {@code vrsta} je DOCX, XLSX, PPTX, PDF ili ZIP, {@code slajdId} null = uz celu prezentaciju. */
public record FajlPrezentacijeInfo(Long id, Long prezentacijaId, Long slajdId, Integer slajdRb, String naziv, String vrsta,
                                   long velicina, int rb, LocalDateTime kreirano) {
}
