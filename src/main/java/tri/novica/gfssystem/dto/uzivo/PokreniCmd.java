package tri.novica.gfssystem.dto.uzivo;

/**
 * Pokretanje izvođenja. Sa {@code predavanjeId} grupa je grupa predavanja i rezultati se čuvaju; bez pitanja (PITANJE
 * ni ZADATAK slajda) u prezentaciji ništa se ne čuva bez obzira na {@code cuvanje}. {@code boduje} (bodovan run: prijava
 * studenata obavezna, prisustvo i rešeni zadaci idu u aktivnosti) traži predavanje i prijavu studenata i uvek čuva.
 */
public record PokreniCmd(boolean cuvanje, Long grupaId, Long predavanjeId, boolean boduje) {

    /** Nebodovan run (oblik pre bodovanja). */
    public PokreniCmd(boolean cuvanje, Long grupaId, Long predavanjeId) {
        this(cuvanje, grupaId, predavanjeId, false);
    }
}
