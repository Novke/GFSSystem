package tri.novica.gfssystem.service.banka;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.banka.*;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.banka.GrupaZadataka;
import tri.novica.gfssystem.entity.banka.IzvorVarijante;
import tri.novica.gfssystem.entity.banka.Namena;
import tri.novica.gfssystem.entity.banka.RenderStatus;
import tri.novica.gfssystem.entity.banka.StatusVarijante;
import tri.novica.gfssystem.entity.banka.Varijanta;
import tri.novica.gfssystem.entity.uzivo.Medij;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.repository.banka.GrupaZadatakaRepository;
import tri.novica.gfssystem.repository.banka.IskljucenaTemaRepository;
import tri.novica.gfssystem.repository.banka.VarijantaRepository;
import tri.novica.gfssystem.repository.uzivo.PitanjeRundaRepository;
import tri.novica.gfssystem.repository.uzivo.ZadatakDodelaRepository;
import tri.novica.gfssystem.service.uzivo.MedijService;
import tri.novica.gfssystem.utility.Serija;
import tri.novica.gfssystem.validation.BankaPP;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

/**
 * Stablo grupa zadataka banke (spec 2.1, 2.5): posebno stablo po predmetu i {@link Namena}; podgrupa nasleđuje predmet
 * i namenu roditelja, dubina je najviše {@value #MAX_DUBINA} (koren = 1), naziv je jedinstven među braćom bez obzira na
 * velika slova (posle trim-a), {@code rb} braće je 1..n. Čvor je ili list (varijante) ili unutrašnji (podgrupe): ovde
 * se odbija podgrupa grupi sa varijantama, {@code VarijantaService} odbija varijantu grupi sa podgrupama.
 * <p>
 * Oznaka {@code iskljucena} u {@link GrupaCvor} i {@link GrupaDetails}: grupa ili neki njen predak ima isključenu temu
 * ({@code IskljuceneTemeService}), {@code iskljucenjeId} je tema najbližeg takvog čvora. Teme se čitaju direktno iz
 * {@link IskljucenaTemaRepository} (jedan upit po stablu ili grupi), ne kroz {@code IskljuceneTemeService}: on koristi
 * ovaj servis za putanju grupe, pa bi to bio ciklus zavisnosti.
 * <p>
 * <b>Red zaključavanja (invarijanta):</b> red predmeta -> red(ovi) grupa (novi roditelj pre grupe koja mu prelazi) ->
 * redovi varijanti. Svaka izmena stabla prvo zaključa red predmeta ({@code PredmetRepository.findByIdForUpdate}), pa
 * izmene jednog stabla (braća, {@code rb}, dubina, premeštanje) idu redom, a više redova grupa odjednom drži samo ko
 * drži red predmeta (među njima nema ciklusa). Red grupe se zaključava kad grupa dobija dete (kreiraj, premesti) ili se
 * menja/briše. Koreni nemaju red roditelja, zato ključ predmeta. Pre zaključavanja se čita samo id predmeta
 * ({@code findPredmetId}): entitet učitan pre zaključavanja Hibernate ne osvežava. Izmene koje MCP fasada zove posle
 * čitanja u istom zahtevu ({@link #izmeniDelimicno}, {@link #izmeniPolja}, {@link #premesti}, {@link #obrisiPraznu}) zato
 * počinju praznim persistence context-om ({@link #zaboraviProcitano}): uz open-in-view jedan EntityManager traje ceo
 * zahtev, pa bi ključ i upiti braće i stabla vratili entitete pročitane pre ključa. Izmene rade u READ COMMITTED (kao
 * {@code PrezentacijaService}): čitanje posle čekanja na ključ vidi ono što je prethodnik upisao. Ključ predmeta traje
 * koliko i izmena stabla i za to vreme kratko zadržava i upise drugih tabela sa FK na taj predmet (InnoDB pri upisu
 * deteta uzima deljeni ključ reda roditelja: novo predavanje, test, prezentacija tog predmeta).
 * <p>
 * <b>Ugovor za {@code VarijantaService}:</b> nikad ne upisuje kolone {@code grupe_zadataka} (ni {@code izmenjeno}):
 * redove grupa piše samo ovaj servis, pod ključem predmeta. Pre dodavanja ili izmene varijante zaključa red njene grupe
 * ({@code GrupaZadatakaRepository.findByIdForUpdate}), pa tek onda varijantu; drži najviše jednu grupu i nikad ne
 * zaključava predmet posle grupe. Tako se pravilo list/unutrašnji i odgovori naspram šeme polja ne trkaju sa ovim izmenama.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(isolation = Isolation.READ_COMMITTED)
public class GrupaZadatakaService {

    static final String NIJE_PRONADJENA = "Grupa nije pronađena.";
    static final String PREDMET_NIJE_PRONADJEN = "Predmet nije pronađen.";
    static final String PREDMET_OBAVEZAN = "Predmet je obavezan.";
    static final String NAMENA_OBAVEZNA = "Namena je obavezna.";
    static final String NAMENA_ZA_KOREN = "Namena je obavezna za koren.";
    static final String NAZIV_POSTOJI = "Grupa sa tim nazivom već postoji.";
    static final String PREDUBOKO = "Najveća dubina stabla je 6.";
    static final String LIST_SA_VARIJANTAMA = "Grupa sa varijantama ne može imati podgrupe.";
    static final String TEZINA_OBAVEZNA = "Težina je obavezna za grupu sa varijantama.";
    static final String SEMA_PREVELIKA = "Šema polja je prevelika.";
    static final String NEISPRAVAN_OPIS = "Opis može imati najviše 5 000 znakova.";
    static final String IMA_PODGRUPE = "Grupa ima podgrupe.";
    static final String SAMO_PRAZNA = "Briše se samo prazna grupa.";
    static final String KORISCENO_NA_CASU = "Korišćeno na času; arhiviraj.";
    static final String SOPSTVENO_PODSTABLO = "Grupa se ne može premestiti u sopstveno podstablo.";
    static final String ISTO_STABLO = "Grupa se premešta samo unutar istog predmeta i namene.";
    static final String NEISPRAVAN_REDOSLED = "Redosled mora sadržati sve podgrupe.";
    static final String NEISPRAVAN_FORMAT = "Neispravan format podataka.";

    static final int MAX_DUBINA = 6;
    /** Patch bez ijednog polja: {@link #izmeniPolja} menja samo šemu. */
    private static final GrupaPatch BEZ_IZMENE = new GrupaPatch(null, null, null, null);
    private static final int MAX_OPIS = 5_000;
    /** Kolona {@code polja} je MySQL {@code text}: najviše 65 535 bajtova. */
    private static final int MAX_POLJA_BAJTOVA = 65_535;

    private final GrupaZadatakaRepository grupaRepository;
    private final VarijantaRepository varijantaRepository;
    private final PredmetRepository predmetRepository;
    private final IskljucenaTemaRepository temaRepository;
    private final ZadatakDodelaRepository dodelaRepository;
    private final PitanjeRundaRepository rundaRepository;
    private final MedijService medijService;
    private final EntityManager em;
    private final Clock clock;

    // ---------------------------------------------------------------- čitanje

    /** Celo stablo predmeta i namene: upit grupa, upiti brojača i upit isključenih grupa, stablo se slaže u memoriji. */
    @Transactional(readOnly = true)
    public List<GrupaCvor> stablo(Long predmetId, Namena namena) {
        if (predmetId == null) {
            throw new SystemException(PREDMET_OBAVEZAN, HttpStatus.BAD_REQUEST);
        }
        if (namena == null) {
            throw new SystemException(NAMENA_OBAVEZNA, HttpStatus.BAD_REQUEST);
        }
        if (!predmetRepository.existsById(predmetId)) {
            throw predmetNijePronadjen();
        }
        Stablo stablo = ucitajStablo(predmetId, namena, true);
        return stablo.koreni.stream().map(stablo::cvor).toList();
    }

    @Transactional(readOnly = true)
    public GrupaDetails detalji(Long id) {
        return details(grupaRepository.findById(id).orElseThrow(GrupaZadatakaService::nijePronadjena));
    }

    /** Putanja od korena do same grupe (uključujući nju), za zaglavlje grupe i varijante. */
    public List<GrupaPutanja> putanja(GrupaZadataka grupa) {
        Deque<GrupaPutanja> putanja = new ArrayDeque<>();
        Set<Long> vidjene = new HashSet<>();
        for (GrupaZadataka g = grupa; g != null && vidjene.add(g.getId()); g = g.getRoditelj()) {
            putanja.addFirst(new GrupaPutanja(g.getId(), g.getNaziv()));
        }
        return List.copyOf(putanja);
    }

    /**
     * Da li je neka varijanta grupe ikad izvučena na času: ima dodelu (red u {@code zadatak_dodele}) ili je u snimku neke
     * runde (izvučena, a nijednom učesniku nije dodeljena); takva grupa se ne briše, njene varijante se arhiviraju. FK
     * RESTRICT štiti samo dodeljenu varijantu. Zaključavanje i trka sa otvaranjem runde: {@link VarijantaService#koriscenoNaCasu}.
     */
    public boolean koriscenoNaCasu(Long grupaId) {
        return dodelaRepository.existsByVarijantaGrupaId(grupaId) || rundaRepository.varijanteGrupeUSnimku(grupaId);
    }

    // ---------------------------------------------------------------- izmene

    /** Nova grupa koju je napravio nastavnik u UI-ju: {@link #kreiraj(GrupaCmd, IzvorVarijante, String)} sa RUCNO, bez serije. */
    public GrupaDetails kreiraj(GrupaCmd cmd) {
        return kreiraj(cmd, IzvorVarijante.RUCNO, null);
    }

    /**
     * Nova grupa na kraj braće ({@code rb} = najveći + 1). Koren traži {@code predmetId} i {@code namena}; podgrupa ih
     * uzima od roditelja, koji ne sme imati varijante (409) i ne sme biti na dnu dubine. {@code izvor} ({@code null} =
     * RUCNO) i {@code serija} (Klodova serija upisa, {@code null} za ručno napravljenu; van {@link Serija#OBRAZAC} 400) se
     * upisuju na grupu i posle se ne menjaju. Roditelj može biti bilo kog izvora (Klod dopunjuje i Novicino stablo).
     * Ne prazni persistence context ({@link #zaboraviProcitano}): radi i unutar transakcije
     * {@code KopijaGrupeService.kopiraj}, čije bi neupisane izmene to odbacilo; pozivalac koji je u istom zahtevu već čitao
     * grupe prazni ga sam, pre svog prvog čitanja.
     */
    public GrupaDetails kreiraj(GrupaCmd cmd, IzvorVarijante izvor, String serija) {
        if (serija != null) {
            Serija.proveri(serija);
        }
        Sadrzaj s = proveren(cmd);
        Predmet predmet;
        Namena namena;
        GrupaZadataka roditelj = null;
        if (cmd.roditeljId() == null) {
            if (cmd.predmetId() == null) {
                throw new SystemException(PREDMET_OBAVEZAN, HttpStatus.BAD_REQUEST);
            }
            if (cmd.namena() == null) {
                throw new SystemException(NAMENA_ZA_KOREN, HttpStatus.BAD_REQUEST);
            }
            predmet = zakljucajPredmet(cmd.predmetId());
            namena = cmd.namena();
        } else {
            predmet = zakljucajPredmet(predmetGrupe(cmd.roditeljId()));
            roditelj = zakljucaj(cmd.roditeljId());
            namena = roditelj.getNamena();
            proveriBezVarijanti(roditelj);
            if (putanja(roditelj).size() + 1 > MAX_DUBINA) {
                throw new SystemException(PREDUBOKO, HttpStatus.BAD_REQUEST);
            }
        }
        List<GrupaZadataka> braca = braca(predmet.getId(), namena, roditelj);
        proveriNaziv(braca, s.naziv(), null);

        LocalDateTime sada = sada();
        GrupaZadataka g = new GrupaZadataka();
        g.setPredmet(predmet);
        g.setRoditelj(roditelj);
        g.setNamena(namena);
        g.setRb(braca.stream().mapToInt(GrupaZadataka::getRb).max().orElse(0) + 1);
        primeni(g, s);
        g.setIzvor(izvor == null ? IzvorVarijante.RUCNO : izvor);
        g.setSerija(serija);
        g.setKreirano(sada);
        g.setIzmenjeno(sada);
        g = grupaRepository.save(g);
        log.info("Grupa zadataka kreirana: id={}, predmet={}, roditelj={}, izvor={}, serija={}", g.getId(), predmet.getId(),
                roditelj == null ? null : roditelj.getId(), g.getIzvor(), serija);
        return details(g);
    }

    /**
     * Puna zamena naziva, opisa, težine i uputstva (predmet, roditelj i namena se ne menjaju); {@code polja == null}
     * ostavlja šemu i varijante netaknute, a brisanje šeme traži {@code []}. Promena šeme prilagođava varijante grupe
     * (vidi {@link #isprobaj}); grupa sa varijantama mora imati težinu. Opcije bez id-ja dobijaju id pod ključem,
     * iznad najvećeg u sačuvanoj šemi za to polje ({@code BankaPP.normalizujPolja(polja, sacuvana)}).
     */
    public GrupaDetails izmeni(Long id, GrupaCmd cmd) {
        Sadrzaj s = proveren(cmd);
        Long predmetId = predmetGrupe(id);
        zakljucajPredmet(predmetId);
        GrupaZadataka g = zakljucaj(id);
        return izmeniZakljucanu(g, predmetId, s, cmd.polja(), false);
    }

    /**
     * Klodova delimična izmena (MCP {@code izmeni_grupu}, spec MCP 4.1): pod ključevima predmet -> grupa patch se spaja nad
     * zaključanim stanjem (izostavljeno ostaje, {@code null} briše), pa ista pravila kao {@link #izmeni}; šema polja i
     * varijante ostaju netaknute ({@link #izmeniPolja}). Ko sme da menja koju grupu (samo CLAUDE) proverava fasada, čitanjem
     * grupe u istom zahtevu: zato prvo {@link #zaboraviProcitano}.
     */
    public GrupaDetails izmeniDelimicno(Long id, GrupaPatch patch) {
        if (patch == null) {
            throw new SystemException(NEISPRAVAN_FORMAT, HttpStatus.BAD_REQUEST);
        }
        zaboraviProcitano();
        Long predmetId = predmetGrupe(id);
        zakljucajPredmet(predmetId);
        GrupaZadataka g = zakljucaj(id);
        return izmeniZakljucanu(g, predmetId, proveren(spoji(g, patch, null)), null, false);
    }

    /**
     * Klodova izmena šeme polja (MCP {@code izmeni_polja_grupe}, spec MCP 4.1): pod ključevima predmet -> grupa ->
     * varijante prvo isproba prilagođavanje ({@link #isprobaj}) bez upisa; kad bi ijedna varijanta (i arhivirana)
     * izgubila postojeći odgovor ili ijedna AKTIVNA pala u NACRT, 409 sa id-jevima pogođenih rastuće i ništa se ne upisuje.
     * Inače kao {@link #izmeni} sa tom šemom i trenutnim nazivom, opisom, težinom i uputstvom. Opcije bez id-ja dobijaju nov
     * id; opcija sa id-jem zadržava odgovore koji je pominju (preimenovanje prolazi). Počinje sa {@link #zaboraviProcitano}.
     */
    public GrupaDetails izmeniPolja(Long id, List<Polje> polja) {
        if (polja == null) {
            throw new SystemException(NEISPRAVAN_FORMAT, HttpStatus.BAD_REQUEST);
        }
        zaboraviProcitano();
        Long predmetId = predmetGrupe(id);
        zakljucajPredmet(predmetId);
        GrupaZadataka g = zakljucaj(id);
        return izmeniZakljucanu(g, predmetId, proveren(spoji(g, BEZ_IZMENE, polja)), polja, true);
    }

    /**
     * Izmena zaključane grupe proverenim sadržajem; {@code polja} su poslata šema ({@code null} = šema ostaje). Uz
     * {@code bezPogodjenih} šema koja bi pogodila varijante je 409 {@code Izmena polja bi pogodila varijante: ...}.
     */
    private GrupaDetails izmeniZakljucanu(GrupaZadataka g, Long predmetId, Sadrzaj s, List<Polje> polja,
                                          boolean bezPogodjenih) {
        Long id = g.getId();
        proveriNaziv(braca(predmetId, g.getNamena(), g.getRoditelj()), s.naziv(), id);

        List<Polje> stara = PoljaJson.izKolone(g.getPolja());
        if (polja != null) {
            s = s.saPoljima(proverenaPolja(polja, stara));
        }
        boolean novaSema = s.polja() != null && !stara.equals(s.polja());
        List<Varijanta> varijante = novaSema ? varijantaRepository.findByGrupaIdForUpdate(id) : List.of();
        long brojVarijanti = novaSema ? varijante.size() : varijantaRepository.countByGrupaId(id);
        if (brojVarijanti > 0 && s.tezina() == null) {
            throw new SystemException(TEZINA_OBAVEZNA, HttpStatus.BAD_REQUEST);
        }
        if (novaSema) {
            List<Prilagodjavanje> plan = isprobaj(varijante, stara, s.polja());
            if (bezPogodjenih) {
                proveriNepogodjene(plan);
            }
            prilagodiVarijante(plan);
        }
        primeni(g, s);
        g.setIzmenjeno(sada());
        log.info("Grupa zadataka izmenjena: id={}, novaSema={}", id, novaSema);
        return details(g);
    }

    /**
     * Briše list (409 za grupu sa podgrupama i za korišćenu na času, vidi {@link #koriscenoNaCasu}): varijante sa prilozima
     * (kaskada), pa grupu, pa SVG medije rendera varijanti koje nikad nisu bile odobrene. Varijante se zaključavaju pre
     * provere "korišćeno na času" (trka sa otvaranjem runde, vidi {@link VarijantaService#koriscenoNaCasu}). SVG varijante
     * sa {@code ikadOdobrena} ostaje: ona je mogla ući u snimak runde Zadatak slajda (snimak čuva samo id-jeve SVG-ova), a
     * pregled časa ga prikazuje i posle brisanja; to važi i kad je runda otvorena u trenutku brisanja, a provera je prošla
     * (sigurnosna mreža iza nje); siročad {@code RENDER_SVG} su prihvaćena. {@link MedijService#obrisi} briše red na
     * commit-u, a fajl tek posle njega, pa pad commit-a ne ostavlja red medija bez fajla.
     */
    public void obrisi(Long id) {
        Long predmetId = predmetGrupe(id);
        zakljucajPredmet(predmetId);
        GrupaZadataka g = zakljucaj(id);
        if (grupaRepository.countByRoditeljId(id) > 0) {
            throw new SystemException(IMA_PODGRUPE, HttpStatus.CONFLICT);
        }
        List<Varijanta> varijante = varijantaRepository.findByGrupaIdForUpdate(id);
        if (koriscenoNaCasu(id)) {
            throw new SystemException(KORISCENO_NA_CASU, HttpStatus.CONFLICT);
        }
        obrisiZakljucanu(g, predmetId, varijante);
    }

    /**
     * Briše grupu samo kad je prazna (MCP i poništavanje serije): pod ključevima predmet -> grupa 409 {@value #SAMO_PRAZNA}
     * kad ima varijanti ili podgrupa, inače kao {@link #obrisi}. Ključ grupe je isti koji {@code VarijantaService} uzima pre
     * dodavanja varijante, pa istovremeno dodavanje ili vidi obrisanu grupu (404) ili brisanje vidi njegovu varijantu (409).
     * Počinje sa {@link #zaboraviProcitano} (fasada i poništavanje serije čitaju pre njega u istom zahtevu).
     */
    public void obrisiPraznu(Long id) {
        zaboraviProcitano();
        Long predmetId = predmetGrupe(id);
        zakljucajPredmet(predmetId);
        GrupaZadataka g = zakljucaj(id);
        if (varijantaRepository.countByGrupaId(id) > 0 || grupaRepository.countByRoditeljId(id) > 0) {
            throw new SystemException(SAMO_PRAZNA, HttpStatus.CONFLICT);
        }
        obrisiZakljucanu(g, predmetId, List.of());
    }

    /**
     * Briše zaključanu grupu bez podgrupa sa njenim (zaključanim) varijantama i SVG medijima neodobrenih varijanti (vidi
     * {@link #obrisi}), pa prenumeriše braću.
     */
    private void obrisiZakljucanu(GrupaZadataka g, Long predmetId, List<Varijanta> varijante) {
        Long id = g.getId();
        List<String> svg = varijante.stream()
                .filter(v -> !v.isIkadOdobrena())
                .flatMap(v -> Stream.of(v.getRenderSvg(), v.getRenderResenjeSvg()))
                .filter(Objects::nonNull).map(Medij::getId).distinct().toList();
        List<GrupaZadataka> braca = new ArrayList<>(braca(predmetId, g.getNamena(), g.getRoditelj()));
        braca.remove(g);

        varijantaRepository.deleteAll(varijante);
        varijantaRepository.flush();
        grupaRepository.delete(g);
        grupaRepository.flush();
        prenumerisi(braca);
        svg.forEach(medijService::obrisi);
        log.info("Grupa zadataka obrisana: id={}, varijanti={}", id, varijante.size());
    }

    /**
     * Premešta grupu (sa celim podstablom) pod drugog roditelja istog predmeta i namene ili u korene, na mesto
     * {@code rb} među novom braćom; stara i nova braća se prenumerišu 1..n. Počinje sa {@link #zaboraviProcitano} (MCP
     * fasada čita grupu pre njega u istom zahtevu).
     */
    public GrupaDetails premesti(Long id, PremestiCmd cmd) {
        if (cmd == null) {
            throw new SystemException(NEISPRAVAN_FORMAT, HttpStatus.BAD_REQUEST);
        }
        zaboraviProcitano();
        Long predmetId = predmetGrupe(id);
        Long ciljId = cmd.roditeljId();
        // tuđ predmet se odbija pre ključa, da se ne drže redovi dva stabla
        if (ciljId != null && !predmetId.equals(predmetGrupe(ciljId))) {
            throw new SystemException(ISTO_STABLO, HttpStatus.BAD_REQUEST);
        }
        zakljucajPredmet(predmetId);
        GrupaZadataka cilj = ciljId == null ? null : zakljucaj(ciljId);
        GrupaZadataka g = zakljucaj(id);
        int dubinaCilja = 0;
        if (cilj != null) {
            if (cilj.getNamena() != g.getNamena()) {
                throw new SystemException(ISTO_STABLO, HttpStatus.BAD_REQUEST);
            }
            List<GrupaPutanja> putanjaCilja = putanja(cilj);
            if (putanjaCilja.stream().anyMatch(p -> p.id().equals(id))) {
                throw new SystemException(SOPSTVENO_PODSTABLO, HttpStatus.BAD_REQUEST);
            }
            proveriBezVarijanti(cilj);
            dubinaCilja = putanjaCilja.size();
        }
        if (dubinaCilja + ucitajStablo(predmetId, g.getNamena(), false).visina(g) > MAX_DUBINA) {
            throw new SystemException(PREDUBOKO, HttpStatus.BAD_REQUEST);
        }
        List<GrupaZadataka> novaBraca = new ArrayList<>(braca(predmetId, g.getNamena(), cilj));
        novaBraca.remove(g);
        proveriNaziv(novaBraca, g.getNaziv(), id);

        if (!Objects.equals(roditeljId(g), ciljId)) {
            List<GrupaZadataka> staraBraca = new ArrayList<>(braca(predmetId, g.getNamena(), g.getRoditelj()));
            staraBraca.remove(g);
            prenumerisi(staraBraca);
            g.setRoditelj(cilj);
        }
        novaBraca.add(cmd.rb() == null ? novaBraca.size() : Math.clamp(cmd.rb() - 1L, 0, novaBraca.size()), g);
        prenumerisi(novaBraca);
        g.setIzmenjeno(sada());
        log.info("Grupa zadataka premeštena: id={}, roditelj={}, rb={}", id, ciljId, g.getRb());
        return details(g);
    }

    /** Novi redosled dece grupe {@code roditeljId} ili korena predmeta i namene ({@code roditeljId == null}). */
    public void redosled(Long roditeljId, Long predmetId, Namena namena, RedosledGrupaCmd cmd) {
        if (predmetId == null) {
            throw new SystemException(PREDMET_OBAVEZAN, HttpStatus.BAD_REQUEST);
        }
        if (namena == null) {
            throw new SystemException(NAMENA_OBAVEZNA, HttpStatus.BAD_REQUEST);
        }
        zakljucajPredmet(predmetId);
        GrupaZadataka roditelj = null;
        if (roditeljId != null) {
            roditelj = grupaRepository.findById(roditeljId)
                    .filter(r -> r.getPredmet().getId().equals(predmetId) && r.getNamena() == namena)
                    .orElseThrow(GrupaZadatakaService::nijePronadjena);
        }
        List<GrupaZadataka> deca = braca(predmetId, namena, roditelj);
        List<Long> ids = cmd == null ? null : cmd.ids();
        if (ids == null || ids.size() != deca.size()) {
            throw new SystemException(NEISPRAVAN_REDOSLED, HttpStatus.BAD_REQUEST);
        }
        Map<Long, GrupaZadataka> poId = new HashMap<>();
        deca.forEach(d -> poId.put(d.getId(), d));
        List<GrupaZadataka> novi = new ArrayList<>();
        for (Long grupaId : ids) {
            GrupaZadataka d = poId.remove(grupaId);   // remove: duplikat drugi put ne nalazi ništa
            if (d == null) {
                throw new SystemException(NEISPRAVAN_REDOSLED, HttpStatus.BAD_REQUEST);
            }
            novi.add(d);
        }
        prenumerisi(novi);
    }

    // ---------------------------------------------------------------- šema polja nad varijantama

    /**
     * Šta bi nova šema uradila jednoj varijanti: vrednosti koje i u novoj šemi važe, da li gubi neki postojeći odgovor i da
     * li AKTIVNA pada u NACRT.
     */
    private record Prilagodjavanje(Varijanta varijanta, Map<String, OdgovorPolja> vazeci, boolean gubiOdgovor,
                                   boolean padaUNacrt) {
        boolean pogadja() {
            return gubiOdgovor || padaUNacrt;
        }
    }

    /**
     * Prilagođavanje odgovora varijanti novoj šemi, bez upisa, tek pošto su varijante zaključane. Promena tipa postojećeg
     * polja je 409 dok grupa ima AKTIVNIH varijanti (ništa se ne upisuje). Inače svaka varijanta zadržava samo vrednosti
     * koje i u novoj šemi važe ({@code BankaPP.proveriOdgovore} za to polje): uklonjeno polje, promenjen tip i uklonjena
     * opcija brišu vrednost (u svakoj varijanti, i arhiviranoj). AKTIVNA varijanta kojoj posle toga fali vrednost (npr.
     * dodato polje) pada u NACRT; NACRT i ARHIVIRANA zadržavaju status.
     */
    private static List<Prilagodjavanje> isprobaj(List<Varijanta> varijante, List<Polje> stara, List<Polje> nova) {
        Map<String, TipPolja> stariTipovi = new HashMap<>();
        stara.forEach(p -> stariTipovi.putIfAbsent(p.kljuc(), p.tip()));
        boolean imaAktivnih = varijante.stream().anyMatch(v -> v.getStatus() == StatusVarijante.AKTIVNA);
        for (Polje p : nova) {
            TipPolja stari = stariTipovi.get(p.kljuc());
            if (imaAktivnih && stari != null && stari != p.tip()) {
                throw new SystemException("Tip polja '" + p.kljuc() + "' se ne menja dok grupa ima aktivne varijante.",
                        HttpStatus.CONFLICT);
            }
        }
        List<Prilagodjavanje> plan = new ArrayList<>();
        for (Varijanta v : varijante) {
            Map<String, OdgovorPolja> odgovori = PoljaJson.odgovoriIzKolone(v.getOdgovori());
            Map<String, OdgovorPolja> vazeci = new LinkedHashMap<>();
            for (Polje p : nova) {
                OdgovorPolja o = odgovori.get(p.kljuc());
                if (o != null && vazi(p, o)) {
                    vazeci.put(p.kljuc(), o);
                }
            }
            plan.add(new Prilagodjavanje(v, vazeci, !vazeci.equals(odgovori),
                    vazeci.size() < nova.size() && v.getStatus() == StatusVarijante.AKTIVNA));
        }
        return plan;
    }

    /** 409 kad bi šema pogodila ijednu varijantu ({@link #isprobaj}); id-jevi rastuće, razdvojeni zarezom. */
    private static void proveriNepogodjene(List<Prilagodjavanje> plan) {
        List<Long> pogodjene = plan.stream().filter(Prilagodjavanje::pogadja).map(p -> p.varijanta().getId()).sorted().toList();
        if (!pogodjene.isEmpty()) {
            throw new SystemException("Izmena polja bi pogodila varijante: "
                    + String.join(", ", pogodjene.stream().map(String::valueOf).toList()) + ".", HttpStatus.CONFLICT);
        }
    }

    /** Upisuje prilagođavanje iz {@link #isprobaj}: važeće vrednosti i NACRT umesto AKTIVNE. Render se ne dira. */
    private void prilagodiVarijante(List<Prilagodjavanje> plan) {
        LocalDateTime sada = sada();
        for (Prilagodjavanje p : plan) {
            Varijanta v = p.varijanta();
            if (p.gubiOdgovor()) {
                v.setOdgovori(PoljaJson.uJson(p.vazeci()));
            }
            if (p.padaUNacrt()) {
                v.setStatus(StatusVarijante.NACRT);
            }
            if (p.gubiOdgovor() || p.padaUNacrt()) {
                v.setIzmenjeno(sada);
            }
        }
    }

    private static boolean vazi(Polje polje, OdgovorPolja odgovor) {
        try {
            BankaPP.proveriOdgovore(List.of(polje), Map.of(polje.kljuc(), odgovor));
            return true;
        } catch (SystemException e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- pomoćno

    /** Proverena i normalizovana komanda; sve se proverava pre zaključavanja. {@code polja} null = nisu poslata. */
    private record Sadrzaj(String naziv, String opis, Integer tezina, String uputstvo, List<Polje> polja, String poljaJson) {
        Sadrzaj saPoljima(Polja p) {
            return new Sadrzaj(naziv, opis, tezina, uputstvo, p.lista(), p.json());
        }
    }

    /** Normalizovana i proverena šema polja i njen JSON za kolonu. */
    private record Polja(List<Polje> lista, String json) {
    }

    private static Sadrzaj proveren(GrupaCmd cmd) {
        if (cmd == null) {
            throw new SystemException(NEISPRAVAN_FORMAT, HttpStatus.BAD_REQUEST);
        }
        String naziv = cmd.naziv() == null ? null : cmd.naziv().strip();
        String opis = prazanUNull(cmd.opis());
        String uputstvo = prazanUNull(cmd.uputstvo());
        BankaPP.proveriGrupu(naziv, cmd.tezina(), uputstvo);
        if (opis != null && opis.codePointCount(0, opis.length()) > MAX_OPIS) {
            throw new SystemException(NEISPRAVAN_OPIS, HttpStatus.BAD_REQUEST);
        }
        Sadrzaj s = new Sadrzaj(naziv, opis, cmd.tezina(), uputstvo, null, null);
        return cmd.polja() == null ? s : s.saPoljima(proverenaPolja(cmd.polja(), null));
    }

    /** Šema iz komande, normalizovana prema sačuvanoj ({@code null} za novu grupu ili proveru pre ključa) i proverena. */
    private static Polja proverenaPolja(List<Polje> polja, List<Polje> sacuvana) {
        List<Polje> normalizovana = BankaPP.normalizujPolja(polja, sacuvana);
        BankaPP.proveriPolja(normalizovana);
        String json = PoljaJson.uJson(normalizovana);
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_POLJA_BAJTOVA) {
            throw new SystemException(SEMA_PREVELIKA, HttpStatus.BAD_REQUEST);
        }
        return new Polja(normalizovana, json);
    }

    /**
     * Komanda iz zaključanog stanja grupe i Klodovog patch-a: polje koje patch ne nosi ostaje kakvo jeste, {@code null} ga
     * briše (pravila brisanja nosi alat; servis spojeno proverava kao punu komandu). {@code polja} su šema koja se šalje
     * ({@code null} = ostaje).
     */
    private static GrupaCmd spoji(GrupaZadataka g, GrupaPatch p, List<Polje> polja) {
        return new GrupaCmd(null, null, null, p.naziv().primeni(g.getNaziv()), p.opis().primeni(g.getOpis()),
                p.tezina().primeni(g.getTezina()), p.uputstvo().primeni(g.getUputstvo()), polja);
    }

    /** Upisuje sadržaj; bez poslatih polja kolona ostaje (nova grupa: {@code "[]"}). */
    private static void primeni(GrupaZadataka g, Sadrzaj s) {
        g.setNaziv(s.naziv());
        g.setOpis(s.opis());
        g.setTezina(s.tezina());
        g.setUputstvo(s.uputstvo());
        if (s.poljaJson() != null) {
            g.setPolja(s.poljaJson());
        }
    }

    private Long predmetGrupe(Long grupaId) {
        return grupaRepository.findPredmetId(grupaId).orElseThrow(GrupaZadatakaService::nijePronadjena);
    }

    private Predmet zakljucajPredmet(Long predmetId) {
        return predmetRepository.findByIdForUpdate(predmetId).orElseThrow(GrupaZadatakaService::predmetNijePronadjen);
    }

    private GrupaZadataka zakljucaj(Long id) {
        return grupaRepository.findByIdForUpdate(id).orElseThrow(GrupaZadatakaService::nijePronadjena);
    }

    /**
     * Prazan persistence context pre prvog čitanja izmene (obrazac {@code VarijantaService}): fasada (MCP) u istom zahtevu
     * već čita grupu i celo stablo ({@code detalji}), a uz open-in-view isti EntityManager traje ceo zahtev, pa bi ključ
     * ({@code findByIdForUpdate}) i upiti braće i stabla vratili te entitete sa stanjem od pre ključa (Hibernate ih ne
     * osvežava). Zove se samo na početku metode koju niko ne zove unutar svoje transakcije (REST, MCP fasada, poništavanje
     * serije), pa nema neupisanih izmena koje bi se izgubile.
     */
    private void zaboraviProcitano() {
        em.clear();
    }

    /** Deca roditelja, ili koreni predmeta i namene kad roditelja nema; po {@code rb}. */
    private List<GrupaZadataka> braca(Long predmetId, Namena namena, GrupaZadataka roditelj) {
        return roditelj == null
                ? grupaRepository.findByPredmetIdAndNamenaAndRoditeljIsNullOrderByRb(predmetId, namena)
                : grupaRepository.findByRoditeljIdOrderByRb(roditelj.getId());
    }

    /** Naziv (već bez razmaka na krajevima) ne sme ponoviti naziv brata, bez obzira na velika slova; {@code bezId} je sama grupa. */
    private static void proveriNaziv(List<GrupaZadataka> braca, String naziv, Long bezId) {
        boolean postoji = braca.stream()
                .anyMatch(b -> !b.getId().equals(bezId) && b.getNaziv().strip().equalsIgnoreCase(naziv));
        if (postoji) {
            throw new SystemException(NAZIV_POSTOJI, HttpStatus.BAD_REQUEST);
        }
    }

    private void proveriBezVarijanti(GrupaZadataka roditelj) {
        if (varijantaRepository.countByGrupaId(roditelj.getId()) > 0) {
            throw new SystemException(LIST_SA_VARIJANTAMA, HttpStatus.CONFLICT);
        }
    }

    /** {@code rb} = pozicija + 1 (popravlja i rupe); Hibernate upisuje samo promenjene. */
    private static void prenumerisi(List<GrupaZadataka> grupe) {
        for (int i = 0; i < grupe.size(); i++) {
            grupe.get(i).setRb(i + 1);
        }
    }

    private static Long roditeljId(GrupaZadataka g) {
        return g.getRoditelj() == null ? null : g.getRoditelj().getId();
    }

    private GrupaDetails details(GrupaZadataka g) {
        Stablo stablo = ucitajStablo(g.getPredmet().getId(), g.getNamena(), false);
        List<Long> podstablo = stablo.podstablo(g);
        Map<Long, Brojevi> brojaci = brojaci(podstablo, false);
        Brojevi b = podstablo.stream().map(brojaci::get).filter(Objects::nonNull).reduce(Brojevi.NULA, Brojevi::plus);
        List<GrupaPutanja> putanja = putanja(g);
        Long iskljucenjeId = iskljucenje(putanja, temaRepository.iskljuceneGrupe(g.getPredmet().getId()));
        return new GrupaDetails(g.getId(), g.getPredmet().getId(), roditeljId(g), g.getNamena(), g.getNaziv(),
                g.getOpis(), g.getRb(), g.getTezina(), g.getUputstvo(), PoljaJson.izKolone(g.getPolja()), putanja,
                stablo.deca(g).isEmpty(), b.varijanti(), b.aktivnih(), b.nacrta(), b.gresaka(), g.getIzmenjeno(),
                g.getIzvor(), g.getSerija(), iskljucenjeId != null, iskljucenjeId);
    }

    /** Tema najbližeg isključenog čvora putanje (od korena do grupe): sopstvena tema grupe pre teme pretka, inače null. */
    private static Long iskljucenje(List<GrupaPutanja> putanja, Map<Long, Long> iskljuceneGrupe) {
        for (int i = putanja.size() - 1; i >= 0; i--) {
            Long tema = iskljuceneGrupe.get(putanja.get(i).id());
            if (tema != null) {
                return tema;
            }
        }
        return null;
    }

    /**
     * Sve grupe predmeta i namene jednim upitom; brojači (sa Klodovim nacrtima, još dva upita) i isključene grupe (još
     * jedan) samo kad se traže čvorovi stabla.
     */
    private Stablo ucitajStablo(Long predmetId, Namena namena, boolean saBrojacima) {
        List<GrupaZadataka> grupe = grupaRepository.findByPredmetIdAndNamenaOrderByRoditeljIdAscRbAsc(predmetId, namena);
        Map<Long, Brojevi> brojaci = saBrojacima ? brojaci(grupe.stream().map(GrupaZadataka::getId).toList(), true) : Map.of();
        Map<Long, Long> iskljucene = saBrojacima ? temaRepository.iskljuceneGrupe(predmetId) : Map.of();
        return new Stablo(grupe, brojaci, iskljucene);
    }

    /**
     * Sopstveni brojači grupa (bez podstabla) iz redova {@code [grupaId, status, renderStatus, broj]}; Klodovi nacrti
     * (drugi upit, {@code [grupaId, broj]}) samo kad su traženi ({@code GrupaDetails} ih nema), inače 0.
     */
    private Map<Long, Brojevi> brojaci(Collection<Long> ids, boolean saClaudeNacrtima) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Brojevi> rezultat = new HashMap<>();
        for (Object[] red : varijantaRepository.brojaciPoGrupi(ids)) {
            int n = ((Number) red[3]).intValue();
            rezultat.merge(((Number) red[0]).longValue(), new Brojevi(n,
                    red[1] == StatusVarijante.AKTIVNA ? n : 0,
                    red[1] == StatusVarijante.NACRT ? n : 0,
                    red[2] == RenderStatus.GRESKA ? n : 0, 0), Brojevi::plus);
        }
        if (saClaudeNacrtima) {
            for (Object[] red : varijantaRepository.claudeNacrtiPoGrupi(ids)) {
                rezultat.merge(((Number) red[0]).longValue(), new Brojevi(0, 0, 0, 0, ((Number) red[1]).intValue()),
                        Brojevi::plus);
            }
        }
        return rezultat;
    }

    private record Brojevi(int varijanti, int aktivnih, int nacrta, int gresaka, int claudeNacrta) {
        static final Brojevi NULA = new Brojevi(0, 0, 0, 0, 0);

        Brojevi plus(Brojevi o) {
            return new Brojevi(varijanti + o.varijanti, aktivnih + o.aktivnih, nacrta + o.nacrta, gresaka + o.gresaka,
                    claudeNacrta + o.claudeNacrta);
        }
    }

    /**
     * Stablo jednog predmeta i namene u memoriji: koreni i deca po roditelju, po {@code rb} pa id-ju; {@code iskljucene}
     * je id grupe -> id njene sopstvene isključene teme.
     */
    private static final class Stablo {
        private static final Comparator<GrupaZadataka> PO_RB =
                Comparator.comparingInt(GrupaZadataka::getRb).thenComparing(GrupaZadataka::getId);

        private final List<GrupaZadataka> koreni = new ArrayList<>();
        private final Map<Long, List<GrupaZadataka>> deca = new HashMap<>();
        private final Map<Long, Brojevi> brojaci;
        private final Map<Long, Long> iskljucene;

        Stablo(List<GrupaZadataka> grupe, Map<Long, Brojevi> brojaci, Map<Long, Long> iskljucene) {
            this.brojaci = brojaci;
            this.iskljucene = iskljucene;
            for (GrupaZadataka g : grupe) {
                if (g.getRoditelj() == null) {
                    koreni.add(g);
                } else {
                    deca.computeIfAbsent(g.getRoditelj().getId(), k -> new ArrayList<>()).add(g);
                }
            }
            koreni.sort(PO_RB);
            deca.values().forEach(l -> l.sort(PO_RB));
        }

        List<GrupaZadataka> deca(GrupaZadataka g) {
            return deca.getOrDefault(g.getId(), List.of());
        }

        /** Id grupe i svih njenih potomaka. */
        List<Long> podstablo(GrupaZadataka g) {
            Set<Long> ids = new LinkedHashSet<>();
            Deque<GrupaZadataka> red = new ArrayDeque<>(List.of(g));
            while (!red.isEmpty()) {
                GrupaZadataka t = red.poll();
                if (ids.add(t.getId())) {
                    red.addAll(deca(t));
                }
            }
            return List.copyOf(ids);
        }

        /** Broj nivoa podstabla (sama grupa = 1). */
        int visina(GrupaZadataka g) {
            return 1 + deca(g).stream().mapToInt(this::visina).max().orElse(0);
        }

        /** Čvor korena sa decom (vidi {@link #cvor(GrupaZadataka, Long)}). */
        GrupaCvor cvor(GrupaZadataka g) {
            return cvor(g, null);
        }

        /**
         * Čvor sa decom; brojači unutrašnjeg čvora su zbir podstabla (i sopstvenih, kojih po pravilu nema). Isključenje:
         * sopstvena tema grupe, inače {@code iskljucenjePretka} (tema najbližeg isključenog pretka), koje deca nasleđuju.
         */
        private GrupaCvor cvor(GrupaZadataka g, Long iskljucenjePretka) {
            Long iskljucenjeId = iskljucene.getOrDefault(g.getId(), iskljucenjePretka);
            List<GrupaCvor> podcvorovi = deca(g).stream().map(d -> cvor(d, iskljucenjeId)).toList();
            Brojevi b = podcvorovi.stream()
                    .map(c -> new Brojevi(c.brojVarijanti(), c.brojAktivnih(), c.brojNacrta(), c.brojGresaka(),
                            c.brojClaudeNacrta()))
                    .reduce(brojaci.getOrDefault(g.getId(), Brojevi.NULA), Brojevi::plus);
            return new GrupaCvor(g.getId(), g.getNaziv(), g.getRb(), g.getTezina(), podcvorovi.isEmpty(),
                    b.varijanti(), b.aktivnih(), b.nacrta(), b.gresaka(), podcvorovi, g.getIzvor(), b.claudeNacrta(),
                    iskljucenjeId != null, iskljucenjeId);
        }
    }

    private static String prazanUNull(String s) {
        String t = s == null ? null : s.strip();
        return t == null || t.isEmpty() ? null : t;
    }

    private LocalDateTime sada() {
        return LocalDateTime.now(clock);
    }

    private static SystemException nijePronadjena() {
        return new SystemException(NIJE_PRONADJENA, HttpStatus.NOT_FOUND);
    }

    private static SystemException predmetNijePronadjen() {
        return new SystemException(PREDMET_NIJE_PRONADJEN, HttpStatus.NOT_FOUND);
    }
}
