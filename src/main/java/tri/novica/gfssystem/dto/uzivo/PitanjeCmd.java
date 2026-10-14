package tri.novica.gfssystem.dto.uzivo;

import tri.novica.gfssystem.entity.uzivo.OdstupanjeTip;
import tri.novica.gfssystem.entity.uzivo.TekstPrikaz;
import tri.novica.gfssystem.entity.uzivo.TipPitanja;

import java.util.List;

/** Pitanje slajda (puna zamena); polja koja ne važe za {@code tip} server postavlja na {@code null}. */
public record PitanjeCmd(TipPitanja tip, String tekst, String slikaId, Integer vremeSekunde, List<OpcijaCmd> opcije,
                         Double brojTacno, Double brojOdstupanje, OdstupanjeTip odstupanjeTip, String jedinica,
                         TekstPrikaz tekstPrikaz, List<String> prihvatljiviOdgovori, String skalaMinOznaka,
                         String skalaMaxOznaka) {
}
