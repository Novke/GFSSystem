package tri.novica.gfssystem.dto.pregled;

import tri.novica.gfssystem.dto.ocenjivanje.MaxPoeniStudentaNaTestuInfo;
import tri.novica.gfssystem.dto.predmet.PredmetInfo;

import java.util.List;

/**
 * S2: kartica studenta na jednom predmetu. Poeni ({@code testovi}, {@code ukupno}, {@code predlogOcene}) su isti kao
 * red studenta u {@code POST /ocenjivanje/predmet/{id}/rezultati} za njegovu grupu.
 *
 * @param prisutan       različita predavanja predmeta na kojima student ima aktivnost
 * @param predavanja     predavanja grupe na predmetu plus druga na kojima je student bio
 * @param zadaci         aktivnosti tipa {@code ZADATAK}
 * @param zvezdice       aktivnosti tipa {@code SA_ZVEZDICOM}
 * @param domaciUradjeno urađeni domaći grupe na predmetu (i oslobođeni, kao u ocenjivanju)
 * @param domaciUkupno   domaći grupe na predmetu
 * @param domaciProsek   prosek bodova (0-10) urađenih domaćih; null bez njih
 * @param testovi        poeni po aktivnom tipu testa (najbolji ili poslednji, po koeficijentima; normalizovani)
 * @param doSledeceOcene null kad je predlog 10
 */
public record StudentPredmetKarticaInfo(PredmetInfo predmet, long prisutan, long predavanja, long zadaci,
                                        long zvezdice, long domaciUradjeno, long domaciUkupno, Double domaciProsek,
                                        List<MaxPoeniStudentaNaTestuInfo> testovi, double ukupno,
                                        Integer predlogOcene, Double doSledeceOcene) {
}
