package tri.novica.gfssystem.service.uzivo;

/**
 * Novi učesnik se prijavio (broj i imena učesnika). Za razliku od {@link IzvodjenjePromenjeno} ne šalje se odmah:
 * talas prijava (QR, 300 telefona) objavljivač skuplja u slanje na 250 ms (spec 4.3: najviše 4 puta u sekundi).
 */
public record UcesnikPrijavljen(Long izvodjenjeId) {
}
