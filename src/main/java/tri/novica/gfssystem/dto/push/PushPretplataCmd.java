package tri.novica.gfssystem.dto.push;

/**
 * Pretplata browsera ({@code PUT /public/nalog/push}, {@code PUT /push}) iz {@code PushSubscription.toJSON()}:
 * {@code endpoint} (https, host sa liste push servisa, najviše 1000 znakova), {@code p256dh} (base64url, P-256 tačka od 65
 * bajtova) i {@code auth} (base64url, 16 bajtova). Vlasnik se uzima iz sesije, nikad iz tela.
 */
public record PushPretplataCmd(String endpoint, String p256dh, String auth) {
}
