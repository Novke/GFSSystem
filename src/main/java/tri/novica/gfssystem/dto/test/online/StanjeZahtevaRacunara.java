package tri.novica.gfssystem.dto.test.online;

/** Stanje zahteva na računaru: {@code CEKA}, {@code ODOBREN} ili {@code ISTEKAO}. */
public record StanjeZahtevaRacunara(String stanje, String broj) {
}
