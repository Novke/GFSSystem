package tri.novica.gfssystem.dto.prijava;

import java.util.List;

/**
 * Nalog prijavljenog studenta ({@code GET /public/nalog/ja}); mejl samo kao maska ({@code null} kad ga nema), bez
 * tokena i heševa.
 */
public record StudentJaInfo(String indeks, String ime, String prezime, String maskaMejla, List<UredjajInfo> uredjaji,
                            boolean moodlePovezan) {
}
