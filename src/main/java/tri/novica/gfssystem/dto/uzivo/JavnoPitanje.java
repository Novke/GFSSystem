package tri.novica.gfssystem.dto.uzivo;

import tri.novica.gfssystem.entity.uzivo.Faza;
import tri.novica.gfssystem.entity.uzivo.TipPitanja;

import java.util.List;

/**
 * Pitanje u javnom stanju (spec 4.4). U fazi CEKA popunjeni su samo {@code tip} i {@code faza}. Od otvaranja: runda,
 * broj i id-jevi opcija, tajmer; tekstovi ({@code tekst}, tekstovi opcija, {@code slikaId}, {@code jedinica}, oznake
 * skale) samo kad je tekst dozvoljen (telefon u režimu PITANJE ili dozvoljeni Detalji); tačan odgovor
 * ({@code tacneOpcije}, {@code tacanBroj}, {@code prihvatljiviOdgovori}) samo kad je prikazan, na zatvorenom pitanju.
 */
public record JavnoPitanje(TipPitanja tip, Faza faza, Long rundaId, Integer brojOpcija, List<JavnaOpcija> opcije,
                           String tekst, String slikaId, String jedinica, String skalaMinOznaka, String skalaMaxOznaka,
                           Long rokMs, Long preostaloMs, List<Long> tacneOpcije, Double tacanBroj,
                           List<String> prihvatljiviOdgovori) {

    /** Pitanje pre otvaranja (ili bez runde): samo tip i faza. */
    public static JavnoPitanje samoTip(TipPitanja tip, Faza faza) {
        return new JavnoPitanje(tip, faza, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
