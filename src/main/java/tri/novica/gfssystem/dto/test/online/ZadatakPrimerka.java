package tri.novica.gfssystem.dto.test.online;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import tri.novica.gfssystem.dto.banka.OdgovorPolja;
import tri.novica.gfssystem.dto.banka.Polje;

/** Zadatak primerka za nastavnika (sa ključem i rešenjem). */
public record ZadatakPrimerka(Long zadatakId, int rb, String naziv, String tip, int nivo, Long varijantaId, String svgId, String resenjeSvgId, List<Polje> polja, Map<String, OdgovorPolja> odgovori, Map<String, Object> uneto, Map<String, OcenaPoljaNastavnik> ocena, double maxPoena, Double autoPoeni, Double rucniPoeni, Double poeni, String napomena, LocalDateTime sacuvano) {
}
