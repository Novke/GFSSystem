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
import tri.novica.gfssystem.utility.Nivo;

import java.util.*;

/**
 * Kandidati za slajd Zadatak (spec MCP 11.2, 11.3; banka spec 4): stablo PREDAVANJE jednog predmeta sa isključenim
 * temama i AKTIVNE varijante sa SVG-om u listovima koje čvor obuhvata. Isti izvor koristi opis Zadatak slajda u editoru,
 * konzoli i MCP-u ({@code ZadaciSlajdova}), izvlačenje pri otvaranju runde ({@code ZadatakDodelaService}) i budući "novi rok".
 * <p>
 * {@link #stablo} čita celo stablo u tri upita (grupe, brojači kandidata po grupi i nivou, teme), a {@link StabloPredavanja#opis}
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
     * brojKandidata}: AKTIVNE varijante sa SVG-om u tim listovima, {@code poNivou} isto po nivou (tačno tri broja, za nivo 1,
     * 2 i 3).
     */
    public record Opis(Long grupaId, String naziv, List<GrupaPutanja> putanja, boolean list, List<Polje> polja,
                       boolean iskljucena, List<GrupaPutanja> iskljucenePodgrupe, List<Long> listovi, int brojKandidata,
                       List<Integer> poNivou) {

        /** Kandidati nivoa {@code nivo} (1-3); {@code null} = svi nivoi ({@link #brojKandidata}). */
        public int brojKandidata(Integer nivo) {
            return nivo == null ? brojKandidata : poNivou.get(nivo - 1);
        }
    }

    /** Aktivna varijanta sa SVG-om: slike su id-jevi medija, {@code polja} šema njenog lista, {@code odgovori} tačne vrednosti. */
    public record KandidatVarijante(Long varijantaId, Long grupaId, String naziv, String svgId, String resenjeSvgId,
                                    List<Polje> polja, Map<String, OdgovorPolja> odgovori, int nivo) {
    }

    /** {@code opis == null} kad grupe nema u stablu PREDAVANJE; {@code poListovima}: listovi redom stabla, bez onih bez kandidata. */
    public record Kandidati(Opis opis, List<List<KandidatVarijante>> poListovima) {
    }

    /** Stablo PREDAVANJE predmeta u memoriji (bez entiteta), posle {@link KandidatiZadatka#stablo} ne pravi nijedan upit. */
    public static final class StabloPredavanja {

        private record Cvor(Long id, String naziv, Long roditeljId, String polja, List<Long> deca) {
        }

        private final Map<Long, Cvor> cvorovi;
        /** Grupa -> broj kandidata po nivou (indeks = nivo - 1). */
        private final Map<Long, int[]> aktivni;
        /** Grupa -> najmanji id kandidata po nivou (indeks = nivo - 1; 0 = nema kandidata tog nivoa). */
        private final Map<Long, long[]> prvi;
        private final Set<Long> iskljucene;

        private StabloPredavanja(Map<Long, Cvor> cvorovi, Map<Long, int[]> aktivni, Map<Long, long[]> prvi,
                                 Set<Long> iskljucene) {
            this.cvorovi = cvorovi;
            this.aktivni = aktivni;
            this.prvi = prvi;
            this.iskljucene = iskljucene;
        }

        /**
         * Primer za editor: kandidat nivoa {@code nivo} ({@code null} = bilo kog) sa najmanjim id-jem u prvom listu
         * opisa (redom stabla) koji ima takvog kandidata, tj. prva varijanta koju izvlačenje razmatra. {@code null} kad
         * kandidata nema.
         */
        public Long primer(Opis opis, Integer nivo) {
            if (opis == null || opis.brojKandidata(nivo) == 0) {
                return null;
            }
            for (Long list : opis.listovi()) {
                long[] ids = prvi.get(list);
                if (ids == null) {
                    continue;
                }
                long najmanji = 0;
                for (int i = 0; i < ids.length; i++) {
                    boolean nivoOdgovara = nivo == null || nivo == i + 1;
                    if (nivoOdgovara && ids[i] > 0 && (najmanji == 0 || ids[i] < najmanji)) {
                        najmanji = ids[i];
                    }
                }
                if (najmanji > 0) {
                    return najmanji;
                }
            }
            return null;
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
            int[] poNivou = new int[Nivo.MAX];
            for (Long id : listovi) {
                int[] broj = aktivni.get(id);
                if (broj != null) {
                    Arrays.setAll(poNivou, i -> poNivou[i] + broj[i]);
                }
            }
            boolean list = cvor.deca().isEmpty();
            return new Opis(cvor.id(), cvor.naziv(), List.copyOf(putanja), list, list ? polja(cvor.id()) : null,
                    iskljucena, List.copyOf(iskljucenePodgrupe), List.copyOf(listovi), Arrays.stream(poNivou).sum(),
                    Arrays.stream(poNivou).boxed().toList());
        }

        /** Svi čvorovi stabla redom stabla (koreni po {@code rb}, pa dubinski), za redosled tipova na nalogu studenta. */
        public List<Long> redom() {
            List<Long> ids = new ArrayList<>();
            for (Cvor c : cvorovi.values()) {
                if (c.roditeljId() == null || !cvorovi.containsKey(c.roditeljId())) {
                    podstabloDubinski(c, ids, new HashSet<>());
                }
            }
            return List.copyOf(ids);
        }

        private void podstabloDubinski(Cvor c, List<Long> ids, Set<Long> vidjeni) {
            if (c == null || !vidjeni.add(c.id())) {
                return;
            }
            ids.add(c.id());
            for (Long d : c.deca()) {
                podstabloDubinski(cvorovi.get(d), ids, vidjeni);
            }
        }

        /**
         * Svi čvorovi podstabla, sa samim čvorom i sa isključenim podgrupama (zatvaranje za vežbanje briše zapise celog
         * podstabla); prazno kad čvora nema u stablu.
         */
        public List<Long> podstablo(Long grupaId) {
            Cvor cvor = grupaId == null ? null : cvorovi.get(grupaId);
            if (cvor == null) {
                return List.of();
            }
            List<Long> ids = new ArrayList<>();
            Deque<Cvor> red = new ArrayDeque<>(List.of(cvor));
            while (!red.isEmpty()) {
                Cvor c = red.removeFirst();
                if (ids.contains(c.id())) {
                    continue;
                }
                ids.add(c.id());
                c.deca().forEach(d -> red.addLast(cvorovi.get(d)));
            }
            return List.copyOf(ids);
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
        Map<Long, int[]> aktivni = new HashMap<>();
        Map<Long, long[]> prvi = new HashMap<>();
        Set<Long> iskljucene = Set.of();
        if (!cvorovi.isEmpty()) {
            for (Object[] red : varijantaRepository.aktivneSaSvgPoGrupiINivou(cvorovi.keySet())) {
                int nivo = ((Number) red[1]).intValue();
                if (nivo >= Nivo.MIN && nivo <= Nivo.MAX) {   // kolona je tinyint; van opsega upisuje samo ručni SQL
                    Long grupaId = ((Number) red[0]).longValue();
                    aktivni.computeIfAbsent(grupaId, id -> new int[Nivo.MAX])[nivo - 1] = ((Number) red[2]).intValue();
                    prvi.computeIfAbsent(grupaId, id -> new long[Nivo.MAX])[nivo - 1] = ((Number) red[3]).longValue();
                }
            }
            iskljucene = temaRepository.iskljuceneGrupe(predmetId).keySet();
        }
        return new StabloPredavanja(cvorovi, aktivni, prvi, iskljucene);
    }

    /** SVG teksta varijanti po id-ju (jedan upit; prazan skup ne ide u bazu). */
    public Map<Long, String> svgTeksta(Collection<Long> varijantaIds) {
        if (varijantaIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> svg = new HashMap<>();
        for (Object[] red : varijantaRepository.svgTeksta(varijantaIds)) {
            svg.put(((Number) red[0]).longValue(), (String) red[1]);
        }
        return svg;
    }

    /** Kandidati čvora svih nivoa ({@link #kandidati(Long, Integer)} sa {@code null}). */
    public Kandidati kandidati(Long grupaId) {
        return kandidati(grupaId, null);
    }

    /**
     * Kandidati čvora: AKTIVNE varijante sa SVG-om nivoa {@code nivo} ({@code null} = svi nivoi) u listovima koje
     * obuhvata, po listovima redom stabla (varijante po id-ju), bez listova bez kandidata tog nivoa: list bez varijanti
     * traženog nivoa ne daje varijante drugog nivoa. Nepostojeća grupa ili grupa namene TEST: {@code opis == null} i
     * prazna lista.
     */
    public Kandidati kandidati(Long grupaId, Integer nivo) {
        Optional<Long> predmetId = grupaId == null ? Optional.empty() : grupaRepository.findPredmetId(grupaId);
        if (predmetId.isEmpty()) {
            return BEZ_GRUPE;
        }
        StabloPredavanja stablo = stablo(predmetId.get());
        Opis opis = stablo.opis(grupaId);
        return opis == null ? BEZ_GRUPE : new Kandidati(opis, poListovima(stablo, opis, nivo));
    }

    private List<List<KandidatVarijante>> poListovima(StabloPredavanja stablo, Opis opis, Integer nivo) {
        if (opis.brojKandidata(nivo) == 0) {
            return List.of();
        }
        Map<Long, List<Varijanta>> poGrupi = new HashMap<>();
        for (Varijanta v : varijantaRepository.aktivneZaGrupe(opis.listovi())) {
            if (nivo == null || v.getNivo() == nivo) {
                poGrupi.computeIfAbsent(v.getGrupa().getId(), id -> new ArrayList<>()).add(v);
            }
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
                PoljaJson.odgovoriIzKolone(v.getOdgovori()), v.getNivo());
    }
}
