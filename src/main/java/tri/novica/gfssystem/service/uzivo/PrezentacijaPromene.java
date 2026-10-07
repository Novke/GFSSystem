package tri.novica.gfssystem.service.uzivo;

/**
 * Obaveštenje o izmenama slajdova prezentacije, da izvođenje u toku prati izmene (trenutni slajd se prati po id-ju).
 * Poziva ga {@link PrezentacijaService} u istoj transakciji, posle izmene i {@code flush}-a, kroz
 * {@code ObjectProvider.ifAvailable}: bez implementacije (bean-a) poziv se preskače.
 */
public interface PrezentacijaPromene {

    /**
     * Poziva se odmah posle zaključavanja reda prezentacije, pre bilo kakvog upisa slajdova: implementacija zaključa
     * aktivna izvođenja te prezentacije (rastuće po id-ju). Red zaključavanja je tako uvek prezentacija -> izvođenje ->
     * redovi slajdova i rundi, isti kao kod komandi (izvođenje -> slajd/runda), pa izmena slajda i komanda koja otvara
     * pitanje na tom slajdu ne mogu da se zaključaju uzajamno (deadlock).
     */
    void zakljucaj(Long prezentacijaId);

    /** Slajdovi su dodati, izmenjeni ili premešteni (ili su promenjeni podaci prezentacije). */
    void slajdoviPromenjeni(Long prezentacijaId);

    /** Slajd je obrisan; {@code stariIndeks} je njegova pozicija (od 0) po {@code rb} pre brisanja. */
    void slajdObrisan(Long prezentacijaId, Long slajdId, int stariIndeks);
}
