package tri.novica.gfssystem.dto.banka;

/**
 * Premeštanje grupe: novi roditelj ({@code null} = koren istog predmeta i namene) i mesto među novom braćom
 * ({@code rb} od 1; {@code null} = na kraj, van opsega se svodi na prvo ili poslednje mesto).
 */
public record PremestiCmd(Long roditeljId, Integer rb) {
}
