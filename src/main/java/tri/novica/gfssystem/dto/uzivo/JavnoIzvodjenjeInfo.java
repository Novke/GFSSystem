package tri.novica.gfssystem.dto.uzivo;

/**
 * Javni podaci izvođenja po kodu (pre prijave): naziv prezentacije, da li je prijava studenta obavezna (bodovan run) i
 * da li zahtev već nosi prijavljenog studenta.
 */
public record JavnoIzvodjenjeInfo(String naziv, boolean prijavaObavezna, boolean prijavljenStudent) {

    /** Nebodovan run, bez prijavljenog studenta. */
    public JavnoIzvodjenjeInfo(String naziv) {
        this(naziv, false, false);
    }
}
