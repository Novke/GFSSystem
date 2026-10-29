package tri.novica.gfssystem.dto.uzivo;

/**
 * Vrsta prezentacije, izračunata iz slajdova (ne čuva se): ZADACI = bar jedan slajd ZADATAK, PITANJA = bar jedno
 * pitanje i nijedan zadatak, GRADIVO = samo INFO i PLATNO slajdovi (i prazna prezentacija).
 */
public enum VrstaPrezentacije {
    GRADIVO, PITANJA, ZADACI;

    /** {@code brojPitanja} broji slajdove sa rundama (PITANJE i ZADATAK), {@code brojZadataka} samo ZADATAK. */
    public static VrstaPrezentacije od(long brojPitanja, long brojZadataka) {
        if (brojZadataka > 0) {
            return ZADACI;
        }
        return brojPitanja > 0 ? PITANJA : GRADIVO;
    }
}
