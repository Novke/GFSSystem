package tri.novica.gfssystem.dto.test.online;

import tri.novica.gfssystem.entity.test.VrstaIspravke;

/** Ispravka ključa: PRIHVATI ({@code vrednost} = ključ grupe odgovora), KLJUC ({@code vrednost} = JSON OdgovorPolja), PONISTI ({@code null}). */
public record IspravkaCmd(Long varijantaId, String polje, VrstaIspravke vrsta, String vrednost) {
}
