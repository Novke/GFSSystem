package tri.novica.gfssystem.dto.uzivo;

/** Runda izvođenja za preocenu: {@code slajdId} je {@code null} kad je slajd u međuvremenu obrisan. */
public record RundaZaPreocenu(Long id, Long slajdId, int redniBroj, long brojOdgovora, long brojTacnih) {
}
