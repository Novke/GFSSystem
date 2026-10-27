package tri.novica.gfssystem.dto.uzivo;

/**
 * Javni podaci izvođenja po kodu (pre prijave): naziv prezentacije, da li je prijava studenta obavezna (bodovan run) i
 * da li zahtev već nosi prijavljenog studenta. {@code godinaUpisa} je godina upisa grupe predavanja izvođenja (null bez
 * predavanja ili grupe): front je prosleđuje na {@code /nalog?godina=Y} kao predlog godine u formi za prijavu.
 */
public record JavnoIzvodjenjeInfo(String naziv, boolean prijavaObavezna, boolean prijavljenStudent, Integer godinaUpisa) {

    /** Nebodovan run, bez prijavljenog studenta. */
    public JavnoIzvodjenjeInfo(String naziv) {
        this(naziv, false, false, null);
    }

    /** Bez godine upisa (izvođenje bez predavanja ili grupe). */
    public JavnoIzvodjenjeInfo(String naziv, boolean prijavaObavezna, boolean prijavljenStudent) {
        this(naziv, prijavaObavezna, prijavljenStudent, null);
    }
}
