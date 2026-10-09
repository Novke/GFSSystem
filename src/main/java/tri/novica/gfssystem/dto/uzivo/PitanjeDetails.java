package tri.novica.gfssystem.dto.uzivo;

import tri.novica.gfssystem.entity.uzivo.OdstupanjeTip;
import tri.novica.gfssystem.entity.uzivo.TekstPrikaz;
import tri.novica.gfssystem.entity.uzivo.TipPitanja;

import java.util.List;

/** Pitanje za nastavnika (sa tačnim odgovorima); {@code opcije} je prazna lista za tipove bez opcija. */
public record PitanjeDetails(Long id, TipPitanja tip, String tekst, MedijInfo slika, Integer vremeSekunde,
                             List<OpcijaDetails> opcije, Double brojTacno, Double brojOdstupanje,
                             OdstupanjeTip odstupanjeTip, String jedinica, TekstPrikaz tekstPrikaz,
                             List<String> prihvatljiviOdgovori, String skalaMinOznaka, String skalaMaxOznaka) {
}
