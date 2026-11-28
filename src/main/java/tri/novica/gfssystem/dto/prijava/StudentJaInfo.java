package tri.novica.gfssystem.dto.prijava;

import tri.novica.gfssystem.entity.prijava.NacinPrijave;

import java.util.List;

/**
 * Nalog prijavljenog studenta ({@code GET /public/nalog/ja}); mejl samo kao maska ({@code null} kad ga nema), bez
 * tokena i heševa. {@code godina} je godina upisa (indeks je jednoznačan tek uz nju). {@code grupa} i {@code generacija}
 * (godina upisa grupe) za profil "Ja" (spec studentskog dela 12.5), {@code null} za studenta bez grupe. {@code waLink}:
 * WhatsApp grupa studentove grupe (samo kanonski {@code https://chat.whatsapp.com/<kod>}, {@code utility/WaLink}) za
 * dugme na Početnoj, {@code null} kad grupa nema link. {@code nacin}: kako je prijavljen ovaj uređaj ({@code KOD},
 * {@code LTI}, {@code RACUNAR}); na računaru front ne nudi instalaciju ni push i pokazuje "Nisi ti? Odjavi".
 */
public record StudentJaInfo(String indeks, String ime, String prezime, String maskaMejla, List<UredjajInfo> uredjaji,
                            boolean moodlePovezan, int godina, String grupa, Integer generacija, String waLink,
                            NacinPrijave nacin) {
}
