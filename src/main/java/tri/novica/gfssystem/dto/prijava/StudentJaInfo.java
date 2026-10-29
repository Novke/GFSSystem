package tri.novica.gfssystem.dto.prijava;

import java.util.List;

/**
 * Nalog prijavljenog studenta ({@code GET /public/nalog/ja}); mejl samo kao maska ({@code null} kad ga nema), bez
 * tokena i heševa. {@code godina} je godina upisa (indeks je jednoznačan tek uz nju). {@code grupa} i {@code generacija}
 * (godina upisa grupe) za profil "Ja" (spec studentskog dela 12.5), {@code null} za studenta bez grupe.
 */
public record StudentJaInfo(String indeks, String ime, String prezime, String maskaMejla, List<UredjajInfo> uredjaji,
                            boolean moodlePovezan, int godina, String grupa, Integer generacija) {
}
