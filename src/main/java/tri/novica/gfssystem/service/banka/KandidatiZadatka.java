package tri.novica.gfssystem.service.banka;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.banka.GrupaPutanja;
import tri.novica.gfssystem.dto.banka.OdgovorPolja;
import tri.novica.gfssystem.dto.banka.Polje;
import tri.novica.gfssystem.entity.banka.GrupaZadataka;
import tri.novica.gfssystem.entity.banka.Namena;
import tri.novica.gfssystem.entity.banka.Varijanta;
import tri.novica.gfssystem.repository.banka.GrupaZadatakaRepository;
import tri.novica.gfssystem.repository.banka.IskljucenaTemaRepository;
import tri.novica.gfssystem.repository.banka.VarijantaRepository;

import java.util.*;

/**
 * Kandidati za slajd Zadatak (spec MCP 11.2, 11.3; banka spec 4): stablo PREDAVANJE jednog predmeta sa isključenim
 * temama i AKTIVNE varijante sa SVG-om u listovima koje čvor obuhvata. Isti izvor koristi opis Zadatak slajda u editoru,
 * konzoli i MCP-u ({@code ZadaciSlajdova}), izvlačenje pri otvaranju runde ({@code ZadatakDodelaService}) i budući "novi rok".
 * <p>
 * {@link #stablo} čita celo stablo u tri upita (grupe, brojači kandidata po grupi, teme), a {@link StabloPredavanja#opis}
 * posle toga radi u memoriji, pa batch opis više slajdova košta jedno stablo. {@link #kandidati} dodaje jedan upit za
 * same varijante. Samo čitanje; ne zaključava ništa.
 * <p>
 * <b>Obilazak čvora:</b> kreće od zadate grupe naniže. Dete sa sopstvenom temom se preskače sa celim podstablom i ulazi u
 * {@code iskljucenePodgrupe}; dosegnuti čvorovi bez dece su listovi. Sama grupa se ne preskače ni kad je isključena
 * (ni kad je isključen njen predak): ZADATAK slajd nad isključenim čvorom je samo upozorenje, Novica odlučuje.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class KandidatiZadatka {

    private static final Kandidati BEZ_GRUPE = new Kandidati(null, List.of());

    private final GrupaZadatakaRepository grupaRepository;
    private final VarijantaRepository varijantaRepository;
    private final IskljucenaTemaRepository temaRepository;

    /**
     * Opis grupe u stablu PREDAVANJE. {@code list}: grupa nema podgrupa. {@code polja}: šema kad je grupa list, inače
     * {@code null} (prazna lista = rad na papiru). {@code iskljucena}: grupa ili neki njen predak ima sopstvenu temu.
     * {@code listovi}: id-jevi listova koje grupa obuhvata, redom stabla (grupa koja je list daje sebe). {@code
     * brojKandidata}: AKTIVNE varijante sa SVG-om u tim listovima.
     */
    public record Opis(Long grupaId, String naziv, List<GrupaPutanja> putanja, boolean list, List<Polje> polja,
                       boolean iskljucena, List<GrupaPutanja> iskljucenePodgrupe, List<Long> listovi, int brojKandidata) {
    }

    /** Aktivna varijanta sa SVG-om: slike su id-jevi medija, {@code polja} šema njenog lista, {@code odgovori} tačne vrednosti. */
    public record KandidatVarijante(Long varijantaId, Long grupaId, String naziv, String svgId, String resenjeSvgId,
                                    List<Polje> polja, Map<String, OdgovorPolja> odgovori) {
    }

    /** {@code opis == null} kad grupe nema u stablu PREDAVANJE; {@code poListovima}: listovi redom stabla, bez onih bez kandidata. */
    public record Kandidati(Opis opis, List<List<KandidatVarijante>> poListovima) {
    }

    /** Stablo PREDAVANJE predmeta u memoriji (bez entiteta), posle {@link KandidatiZadatka#stablo} ne pravi nijedan upit. */
    public static final class StabloPredavanja {

        private record Cvor(Long id, String naziv, Long roditeljId, String polja, List<Long> deca) {
        }

        private final Map<Long, Cvor> cvorovi;
        private final Map<Long, Integer> aktivni;
        private final Set<Long> iskljucene;

        private StabloPredavanja(Map<Long, Cvor> cvorovi, Map<Long, Integer> aktivni, Set<Long> iskljucene) {
            this.cvorovi = cvorovi;
            this.aktivni = aktivni;
            this.iskljucene = iskljucene;
        }

        /** Opis grupe, {@code null} kad grupe nema u ovom stablu (nepostojeća, drugi predmet ili namena TEST). */
        public Opis opis(Long grupaId) {
            Cvor cvor = grupaId == null ? null : cvorovi.get(grupaId);
            if (cvor == null) {
                return null;
            }
            List<GrupaPutanja> putanja = new ArrayList<>();
            boolean iskljucena = false;
            Set<Long> vidjeni = new HashSet<>();
            for (Cvor c = cvor; c != null && vidjeni.add(c.id()); c = roditelj(c)) {
                putanja.add(0, new GrupaPutanja(c.id(), c.naziv()));
                iskljucena |= iskljucene.contains(c.id());
            }
            List<Long> listovi = new ArrayList<>();
            List<GrupaPutanja> iskljucenePodgrupe = new ArrayList<>();
            obidji(cvor, true, listovi, iskljucenePodgrupe);
            int brojKandidata = listovi.stream().mapToInt(id -> aktivni.getOrDefault(id, 0)).sum();
            boolean list = cvor.deca().isEmpty();
            return new Opis(cvor.id(), cvor.naziv(), List.copyOf(putanja), list, list ? polja(cvor.id()) : null,
                    iskljucena, List.copyOf(iskljucenePodgrupe), List.copyOf(listovi), brojKandidata);
        }

        private Cvor roditelj(Cvor c) {
            return c.roditeljId() == null ? null : cvorovi.get(c.roditeljId());
        }

        /** Šema odgovora grupe ({@code GrupaZadataka.polja}); prazna lista za rad na papiru i za grupu koje nema u stablu. */
        public List<Polje> polja(Long grupaId) {
            Cvor cvor = grupaId == null ? null : cvorovi.get(grupaId);
            return cvor == null ? List.of() : PoljaJson.izKolone(cvor.polja());
        }

        /** Dubina je najviše 6, pa rekurzija ne preti; {@code koren} je prva grupa obilaska i nikad se ne preskače. */
        private void obidji(Cvor cvor, boolean koren, List<Long> listovi, List<GrupaPutanja> iskljucenePodgrupe) {
            if (!koren && iskljucene.contains(cvor.id())) {
                iskljucenePodgrupe.add(new GrupaPutanja(cvor.id(), cvor.naziv()));
                return;
            }
            if (cvor.deca().isEmpty()) {
                listovi.add(cvor.id());
                return;
            }
            for (Long dete : cvor.deca()) {
                obidji(cvorovi.get(dete), false, listovi, iskljucenePodgrupe);
            }
        }
    }

    /** Stablo PREDAVANJE predmeta sa brojačima kandidata i isključenim temama, tri upita. */
    public StabloPredavanja stablo(Long predmetId) {
        List<GrupaZadataka> grupe = grupaRepository.findByPredmetIdAndNamenaOrderByRoditeljIdAscRbAsc(predmetId, Namena.PREDAVANJE);
        Map<Long, StabloPredavanja.Cvor> cvorovi = new LinkedHashMap<>();
        Map<Long, List<Long>> deca = new HashMap<>();
        for (GrupaZadataka g : grupe) {
            Long roditeljId = g.getRoditelj() == null ? null : g.getRoditelj().getId();
            // upit daje decu jednog roditelja po rb, pa lista dece ostaje po rb
            List<Long> dete = deca.computeIfAbsent(g.getId(), id -> new ArrayList<>());
            if (roditeljId != null) {
                deca.computeIfAbsent(roditeljId, id -> new ArrayList<>()).add(g.getId());
            }
            cvorovi.put(g.getId(), new StabloPredavanja.Cvor(g.getId(), g.getNaziv(), roditeljId, g.getPolja(), dete));
        }
        Map<Long, Integer> aktivni = new HashMap<>();
        Set<Long> iskljucene = Set.of();
        if (!cvorovi.isEmpty()) {
            for (Object[] red : varijantaRepository.aktivneSaSvgPoGrupi(cvorovi.keySet())) {
                aktivni.put(((Number) red[0]).longValue(), ((Number) red[1]).intValue());
            }
            iskljucene = temaRepository.iskljuceneGrupe(predmetId).keySet();
        }
        return new StabloPredavanja(cvorovi, aktivni, iskljucene);
    }

    /**
     * Kandidati čvora: AKTIVNE varijante sa SVG-om u listovima koje obuhvata, po listovima redom stabla (varijante po
     * id-ju), bez listova bez kandidata. Nepostojeća grupa ili grupa namene TEST: {@code opis == null} i prazna lista.
     */
    public Kandidati kandidati(Long grupaId) {
        Optional<Long> predmetId = grupaId == null ? Optional.empty() : grupaRepository.findPredmetId(grupaId);
        if (predmetId.isEmpty()) {
            return BEZ_GRUPE;
        }
        StabloPredavanja stablo = stablo(predmetId.get());
        Opis opis = stablo.opis(grupaId);
        return opis == null ? BEZ_GRUPE : new Kandidati(opis, poListovima(stablo, opis));
    }

    private List<List<KandidatVarijante>> poListovima(StabloPredavanja stablo, Opis opis) {
        if (opis.brojKandidata() == 0) {
            return List.of();
        }
        Map<Long, List<Varijanta>> poGrupi = new HashMap<>();
        for (Varijanta v : varijantaRepository.aktivneZaGrupe(opis.listovi())) {
            poGrupi.computeIfAbsent(v.getGrupa().getId(), id -> new ArrayList<>()).add(v);
        }
        List<List<KandidatVarijante>> rezultat = new ArrayList<>();
        for (Long list : opis.listovi()) {
            List<Varijanta> varijante = poGrupi.get(list);
            if (varijante != null) {
                List<Polje> polja = stablo.polja(list);
                rezultat.add(varijante.stream().map(v -> kandidat(v, polja)).toList());
            }
        }
        return List.copyOf(rezultat);
    }

    private static KandidatVarijante kandidat(Varijanta v, List<Polje> polja) {
        return new KandidatVarijante(v.getId(), v.getGrupa().getId(), v.getNaziv(), v.getRenderSvg().getId(),
                v.getRenderResenjeSvg() == null ? null : v.getRenderResenjeSvg().getId(), polja,
                PoljaJson.odgovoriIzKolone(v.getOdgovori()));
    }
}
