package tri.novica.gfssystem.service.test;

import tri.novica.gfssystem.dto.banka.OdgovorPolja;
import tri.novica.gfssystem.dto.banka.Polje;
import tri.novica.gfssystem.entity.banka.NacinBodovanja;

import java.util.List;
import java.util.Map;

/**
 * Snimak zadatka primerka (spec 8.3): sve od čega posle zavisi ocena i prikaz, prepisano iz banke kad primerak nastane.
 * Izmena varijante, šeme ili tipa u banci posle toga ne dira test; ocenu menjaju samo ispravke ključa tog testa.
 * {@code naziv} je naziv varijante, {@code tip} naziv lista, {@code stavka} naziv stavke testa.
 */
public record SnimakZadatkaTesta(Long varijantaId, Long tipId, String tip, String naziv, int nivo, String svgId,
                                 String resenjeSvgId, List<Polje> polja, Map<String, OdgovorPolja> odgovori,
                                 NacinBodovanja bodovanje, boolean konacnoPriznajeKorake, double maxPoena, boolean rucno,
                                 String stavka) {

    public SnimakZadatkaTesta {
        polja = polja == null ? List.of() : List.copyOf(polja);
        odgovori = odgovori == null ? Map.of() : Map.copyOf(odgovori);
        bodovanje = bodovanje == null ? NacinBodovanja.PO_POLJIMA : bodovanje;
    }

    /** Polja koja student popunjava: zadatak stavke „ručno“ ih nema (radi se na papiru). */
    public List<Polje> poljaZaUnos() {
        return rucno ? List.of() : polja;
    }
}
