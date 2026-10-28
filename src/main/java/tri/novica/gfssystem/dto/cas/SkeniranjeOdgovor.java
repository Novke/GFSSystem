package tri.novica.gfssystem.dto.cas;

/**
 * Odgovor na skeniranje QR-a: prijavljen student dobija {@code stanje} (čekiran je), neprijavljen jednokratni {@code kljuc}
 * čekanja (32 znaka, 15 min) koji posle prijave šalje na {@code potvrdi}. Tačno jedno nije {@code null}.
 */
public record SkeniranjeOdgovor(String kljuc, CasStanje stanje) {
}
