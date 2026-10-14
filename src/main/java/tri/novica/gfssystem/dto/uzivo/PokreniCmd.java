package tri.novica.gfssystem.dto.uzivo;

/**
 * Pokretanje izvođenja. Sa {@code predavanjeId} grupa je grupa predavanja i rezultati se čuvaju; bez pitanja u
 * prezentaciji ništa se ne čuva bez obzira na {@code cuvanje}.
 */
public record PokreniCmd(boolean cuvanje, Long grupaId, Long predavanjeId) {
}
