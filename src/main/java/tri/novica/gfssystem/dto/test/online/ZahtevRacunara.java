package tri.novica.gfssystem.dto.test.online;

/** Novi zahtev za prijavu računara: token za QR, broj za ekran, važenje. */
public record ZahtevRacunara(String token, String broj, long isticeMs, long serverVremeMs) {
}
