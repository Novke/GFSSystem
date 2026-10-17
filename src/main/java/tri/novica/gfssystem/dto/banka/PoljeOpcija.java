package tri.novica.gfssystem.dto.banka;

/** Ponuđeni odgovor polja; {@code id} je stabilan u grupi (tačan odgovor varijante ga pominje), tekst sme da ima {@code $formule$}. */
public record PoljeOpcija(Long id, String tekst) {
}
