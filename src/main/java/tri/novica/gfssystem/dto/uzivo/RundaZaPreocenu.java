package tri.novica.gfssystem.dto.uzivo;

/** Runda izvođenja za preocenu: {@code slajdId} je {@code null} kad je slajd u međuvremenu obrisan; {@code zbirUdela} je zbir udela odgovora, zaokružen na 0,01. */
public record RundaZaPreocenu(Long id, Long slajdId, int redniBroj, long brojOdgovora, long brojTacnih, Double zbirUdela) {
}
