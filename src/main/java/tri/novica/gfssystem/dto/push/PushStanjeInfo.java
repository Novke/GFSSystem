package tri.novica.gfssystem.dto.push;

/**
 * Push na ovom uređaju ({@code GET /public/nalog/push}, {@code GET /push}): javni VAPID ključ (base64url, 65 bajtova
 * nekompresovano) za {@code PushManager.subscribe} i da li uređaj (ili sesija nastavnika) iz zahteva ima pretplatu.
 */
public record PushStanjeInfo(String javniKljuc, boolean pretplacen) {
}
