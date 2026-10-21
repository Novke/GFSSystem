package tri.novica.gfssystem.service.mejl;

/**
 * Slanje mejla (spec 7). {@link #dostupan()} je {@code false} kad SMTP nije podešen: tada se ništa ne šalje, a pozivalac
 * (kod za prijavu studenta) odgovara 503 umesto da obeća kod koji nikad neće stići.
 */
public interface MejlServis {

    boolean dostupan();

    /** Šalje odmah, u pozivaočevoj niti; baca izuzetak pri neuspehu. Asinhrono posle commit-a ide kroz {@link MejlPosleCommita}. */
    void posalji(MejlPoruka poruka);
}
