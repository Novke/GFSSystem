package tri.novica.gfssystem.dto.fajlovi;

import java.util.List;

/** Uživo stanje: kod i kratak link ({@code gfs.trif.rs/f/1234}) uz fajlove trenutnog slajda. */
public record FajloviSlajda(String kod, String link, List<FajlKratko> fajlovi) {
}
