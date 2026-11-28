package tri.novica.gfssystem.service.test;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.banka.GrupaPutanja;
import tri.novica.gfssystem.dto.banka.Polje;
import tri.novica.gfssystem.dto.test.online.IzvorStavke;
import tri.novica.gfssystem.dto.test.online.KandidatiStavke;
import tri.novica.gfssystem.dto.test.online.TipStavke;
import tri.novica.gfssystem.entity.banka.GrupaZadataka;
import tri.novica.gfssystem.entity.banka.Namena;
import tri.novica.gfssystem.entity.banka.Varijanta;
import tri.novica.gfssystem.entity.test.RezimTesta;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.service.banka.KandidatiZadatka;
import tri.novica.gfssystem.service.banka.KandidatiZadatka.KandidatVarijante;
import tri.novica.gfssystem.service.banka.KandidatiZadatka.Opis;
import tri.novica.gfssystem.service.banka.KandidatiZadatka.StabloPredavanja;
import tri.novica.gfssystem.service.banka.PoljaJson;
import tri.novica.gfssystem.utility.Nivo;

import java.util.*;

/**
 * Kandidati stavke šablona ili testa iz banke namene TEST istog predmeta (spec 7, 8.2): unija listova ispod izvora, bez
 * isključenih tema, sa poljima za odgovor (osim stavke „ručno“), AKTIVNE varijante sa SVG-om. Isti opis služi ekranu
 * šablona (broj kandidata i upozorenja dok se kuca), pregledu pripreme („Izaberi tip“) i izvlačenju.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class KandidatiTesta {

    public static final String IZVOR_VAN_BANKE = "Izvor mora biti čvor banke za test istog predmeta.";

    private final KandidatiZadatka kandidatiZadatka;

    /** Opis stavke: ulaz za izvlačenje, izvori i tipovi za prikaz, broj kandidata, upozorenja, kandidati po id-ju. */
    public record OpisStavke(IzvlacenjeTesta.Stavka stavka, List<IzvorStavke> izvori, List<TipStavke> tipovi,
                             KandidatiStavke kandidati, List<String> upozorenja, Map<Long, KandidatVarijante> varijante) {
    }

    public StabloPredavanja stablo(Long predmetId) {
        return kandidatiZadatka.stablo(predmetId, Namena.TEST);
    }

    /** Izvori koji nisu čvorovi stabla TEST predmeta su 400 (provera pri čuvanju šablona). */
    public void proveriIzvore(StabloPredavanja stablo, Collection<Long> izvori) {
        for (Long id : izvori) {
            if (id == null || stablo.opis(id) == null) {
                throw new SystemException(IZVOR_VAN_BANKE, HttpStatus.BAD_REQUEST);
            }
        }
    }

    /**
     * Opis stavke nad već izgrađenim stablom. {@code potrebno}: koliko različitih varijanti jednog nivoa treba (grupe ×
     * broj u režimu GRUPE, broj u POJEDINACNO); upozorenje kad nijedan dozvoljen nivo nema toliko.
     */
    public OpisStavke opis(StabloPredavanja stablo, long stavkaId, String naziv, int broj, boolean istiTip, boolean rucno,
                           Set<Integer> nivoi, Collection<Long> izvori, RezimTesta rezim, int brojGrupa) {
        List<String> upozorenja = new ArrayList<>();
        List<IzvorStavke> izvoriDto = new ArrayList<>();
        LinkedHashSet<Long> listovi = new LinkedHashSet<>();
        for (Long id : izvori) {
            Opis o = id == null ? null : stablo.opis(id);
            if (o == null) {
                izvoriDto.add(new IzvorStavke(id, "Obrisan čvor", List.of(), true));
                upozorenja.add("Izvor je obrisan iz banke.");
                continue;
            }
            izvoriDto.add(new IzvorStavke(id, o.naziv(), o.putanja().stream().map(GrupaPutanja::naziv).toList(), false));
            if (o.iskljucena()) {
                upozorenja.add("Izvor „" + o.naziv() + "“ je isključena tema.");
                continue;
            }
            listovi.addAll(o.listovi());
        }
        LinkedHashMap<Long, List<IzvlacenjeTesta.Kandidat>> poTipu = new LinkedHashMap<>();
        Map<Long, KandidatVarijante> varijante = new HashMap<>();
        List<TipStavke> tipovi = new ArrayList<>();
        Set<Integer> dozvoljeni = new HashSet<>(IzvlacenjeTesta.dozvoljeni(nivoi));
        for (Long list : listovi) {
            Opis o = stablo.opis(list);
            if (o == null) continue;
            if (o.polja() == null || o.polja().isEmpty()) {
                if (!rucno) {
                    if (o.brojKandidata() > 0) {
                        upozorenja.add("Tip „" + o.naziv() + "“ nema polja za odgovor; takav zadatak ide samo kao „ručno“.");
                    }
                    continue;
                }
            }
            if (o.brojKandidata() == 0) continue;
            List<IzvlacenjeTesta.Kandidat> kandidati = new ArrayList<>();
            for (List<KandidatVarijante> l : kandidatiZadatka.kandidati(stablo, list, null).poListovima()) {
                for (KandidatVarijante k : l) {
                    if (!k.grupaId().equals(list)) continue;
                    kandidati.add(new IzvlacenjeTesta.Kandidat(k.varijantaId(), k.grupaId(), k.nivo()));
                    varijante.put(k.varijantaId(), k);
                }
            }
            if (kandidati.isEmpty()) continue;
            poTipu.put(list, kandidati);
            if (kandidati.stream().anyMatch(k -> dozvoljeni.contains(k.nivo()))) {
                tipovi.add(new TipStavke(list, o.naziv()));
            }
        }
        IzvlacenjeTesta.Stavka stavka = new IzvlacenjeTesta.Stavka(stavkaId, naziv, broj, istiTip,
                nivoi == null ? Set.of() : Set.copyOf(nivoi), poTipu);
        int[] poNivou = IzvlacenjeTesta.poNivou(stavka);
        int ukupno = 0;
        for (int n : dozvoljeni) ukupno += poNivou[n - 1];
        KandidatiStavke k = new KandidatiStavke(ukupno, Arrays.stream(poNivou).boxed().toList(), tipovi.size());

        int potrebno = (rezim == RezimTesta.GRUPE ? Math.max(1, brojGrupa) : 1) * Math.max(1, broj);
        if (ukupno == 0) {
            upozorenja.add("Stavka nema kandidata u banci.");
        } else {
            int najbolji = 0;
            int najboljiNivo = Nivo.PODRAZUMEVANI;
            for (int n : IzvlacenjeTesta.dozvoljeni(nivoi)) {
                if (poNivou[n - 1] > najbolji) {
                    najbolji = poNivou[n - 1];
                    najboljiNivo = n;
                }
            }
            if (najbolji < potrebno) {
                String nivo = Nivo.oznaka(najboljiNivo);
                upozorenja.add(rezim == RezimTesta.GRUPE
                        ? "Samo " + varijanti(najbolji) + " nivoa " + nivo + ", a za " + grupa(brojGrupa) + " "
                        + (potrebno == 1 ? "treba " : "trebaju ") + potrebno + "."
                        : "Samo " + varijanti(najbolji) + " nivoa " + nivo + ", a stavka traži " + potrebno + " različitih.");
            }
        }
        return new OpisStavke(stavka, List.copyOf(izvoriDto), List.copyOf(tipovi), k, List.copyOf(upozorenja), varijante);
    }

    /** Snimak zadatka iz varijante banke (spec 8.3). */
    public static SnimakZadatkaTesta snimak(Varijanta v, String stavka, double maxPoena, boolean rucno) {
        GrupaZadataka tip = v.getGrupa();
        List<Polje> polja = PoljaJson.izKolone(tip.getPolja());
        return new SnimakZadatkaTesta(v.getId(), tip.getId(), tip.getNaziv(), v.getNaziv(), v.getNivo(),
                v.getRenderSvg() == null ? null : v.getRenderSvg().getId(),
                v.getRenderResenjeSvg() == null ? null : v.getRenderResenjeSvg().getId(), polja,
                PoljaJson.odgovoriIzKolone(v.getOdgovori()), tip.getBodovanje(), tip.isKonacnoPriznajeKorake(), maxPoena,
                rucno, stavka);
    }

    static String varijanti(int n) {
        return n + " " + oblik(n, "varijanta", "varijante", "varijanti");
    }

    static String grupa(int n) {
        return n + " " + oblik(n, "grupu", "grupe", "grupa");
    }

    static String oblik(int n, String jedan, String dvaDoCetiri, String vise) {
        int d = n % 10;
        int s = n % 100;
        if (d == 1 && s != 11) return jedan;
        if (d >= 2 && d <= 4 && (s < 12 || s > 14)) return dvaDoCetiri;
        return vise;
    }
}
