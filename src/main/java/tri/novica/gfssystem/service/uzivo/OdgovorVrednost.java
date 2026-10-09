package tri.novica.gfssystem.service.uzivo;

import java.util.List;

/** Sadržaj odgovora učesnika; popunjeno je samo polje koje odgovara tipu pitanja. */
public record OdgovorVrednost(List<Long> opcije, Double broj, String tekst, Integer skala) {
}
