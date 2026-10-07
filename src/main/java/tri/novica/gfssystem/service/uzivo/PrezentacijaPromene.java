package tri.novica.gfssystem.service.uzivo;

/**
 * Obaveštenje o izmenama slajdova prezentacije, da izvođenje u toku prati izmene (trenutni slajd se prati po id-ju).
 * Poziva ga {@link PrezentacijaService} u istoj transakciji, posle izmene i {@code flush}-a, kroz
 * {@code ObjectProvider.ifAvailable}: bez implementacije (bean-a) poziv se preskače.
 */
public interface PrezentacijaPromene {

    /** Slajdovi su dodati, izmenjeni ili premešteni (ili su promenjeni podaci prezentacije). */
    void slajdoviPromenjeni(Long prezentacijaId);

    /** Slajd je obrisan; {@code stariIndeks} je njegova pozicija (od 0) po {@code rb} pre brisanja. */
    void slajdObrisan(Long prezentacijaId, Long slajdId, int stariIndeks);
}
