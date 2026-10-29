package tri.novica.gfssystem.service.uzivo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import tri.novica.gfssystem.dto.uzivo.*;
import tri.novica.gfssystem.entity.Grupa;
import tri.novica.gfssystem.entity.Predavanje;
import tri.novica.gfssystem.entity.uzivo.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.GrupaRepository;
import tri.novica.gfssystem.repository.PredavanjeRepository;
import tri.novica.gfssystem.repository.uzivo.*;
import tri.novica.gfssystem.security.IdentitetStudenta;
import tri.novica.gfssystem.utility.Nivo;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Izvođenje prezentacije uživo: pokretanje, nastavničke komande (stanje-mašina iz spec-a 2.3-2.5 i 4.5), tajmer,
 * moderacija i završetak. Svaka izmena prvo zaključa red izvođenja ({@code findByIdForUpdate}), poveća
 * {@code verzija} za 1 i objavi {@link IzvodjenjePromenjeno} (klijentima ide tek posle commit-a). Redosled
 * zaključavanja je uvek prezentacija -> izvođenje -> slajdovi/runde: {@code pokreni} drži prezentaciju, izmene slajdova
 * kroz {@link PrezentacijaPromene#zakljucaj} zaključaju izvođenja pre upisa, a komande drže samo izvođenje.
 * READ COMMITTED kao {@link PrezentacijaService}: čitanje posle čekanja na zaključavanje vidi ono što je prethodnik
 * upravo upisao.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(isolation = Isolation.READ_COMMITTED)
public class IzvodjenjeService {

    static final String NIJE_PRONADJENO = "Izvođenje nije pronađeno.";
    static final String ZAVRSENO = "Izvođenje je završeno.";
    static final String U_TOKU = "Izvođenje je u toku.";
    static final String NEISPRAVNA_KOMANDA = "Nepoznata komanda.";
    static final String NEPOSTOJECI_SLAJD = "Nepostojeći slajd.";
    static final String NEMA_PITANJA = "Na ovom slajdu nema pitanja.";
    static final String PRVO_ZATVORI = "Prvo zatvori pitanje.";
    static final String NEMA_TACNOG = "Ovo pitanje nema tačan odgovor.";
    static final String NEMA_REZULTATA = "Nema rezultata za prikaz.";
    static final String NEMA_RANGA_PREDAVANJA = "Predavanje nema druga izvođenja.";
    static final String TAJMER_VAN_PITANJA = "Tajmer radi samo dok je pitanje otvoreno.";
    static final String TAJMER_NIJE_POKRENUT = "Tajmer nije pokrenut.";
    static final String NIJE_POSTAVLJENO = "Pitanje još nije postavljeno.";
    static final String UCESNIK_NIJE_PRONADJEN = "Učesnik nije pronađen.";
    static final String RUNDA_NIJE_PRONADJENA = "Runda nije pronađena.";
    static final String PREDAVANJE_DRUGI_PREDMET = "Predavanje nije iz predmeta ove prezentacije.";
    static final String VEC_U_TOKU = "Prezentacija već ima izvođenje u toku.";
    static final String BODOVANJE_BEZ_PREDAVANJA = "Bodovanje traži predavanje.";
    static final String PRIJAVA_NEDOSTUPNA = "Prijava studenata još nije dostupna.";
    static final String NEMA_ZADATKA = "Na ovom slajdu nema zadatka.";
    static final String BROJ_PRE_OTVARANJA = "Broj različitih zadataka se menja pre otvaranja.";
    static final String BROJ_VARIJANTI_GRANICE = "Broj različitih zadataka mora biti od 1 do 50.";
    static final String NIVO_PRE_OTVARANJA = "Nivo se menja pre otvaranja.";
    static final String NEMA_RESENJA = "Nijedna varijanta nema rešenje.";
    /** Ime UNIQUE ključa kolone {@code aktivan_kod} (V7). */
    static final String UK_AKTIVAN_KOD = "uk_izvodjenja_aktivan_kod";

    /** Odgovori se primaju i pitanje se zatvara tek 1 s posle roka (mreža). */
    static final Duration TOLERANCIJA = Duration.ofSeconds(1);
    static final long POMAK_TAJMERA_MS = 10_000;
    static final long NAJMANJE_PREOSTALO_MS = 5_000;
    static final int PODRAZUMEVANI_TAJMER_S = 30;
    static final Duration NAJDUZE_TRAJANJE = Duration.ofHours(12);
    /** Najveći broj različitih zadataka iz konzole ({@code BROJ_VARIJANTI}), isto kao na slajdu. */
    static final int NAJVISE_VARIJANTI = 50;

    private final IzvodjenjeRepository izvodjenjeRepository;
    private final PrezentacijaRepository prezentacijaRepository;
    private final SlajdRepository slajdRepository;
    private final PitanjeRundaRepository rundaRepository;
    private final OdgovorRepository odgovorRepository;
    private final UcesnikRepository ucesnikRepository;
    private final PredavanjeRepository predavanjeRepository;
    private final GrupaRepository grupaRepository;
    private final KodGenerator kodGenerator;
    private final RokPlaner rokPlaner;
    private final StanjeService stanjeService;
    private final ZadatakDodelaService zadatakDodelaService;
    private final RangTacnostiService rangTacnosti;
    private final RangPrikazRegistar rangPrikazi;
    private final ImenaUcesnika imenaUcesnika;
    private final IdentitetStudenta identitet;
    private final ApplicationEventPublisher publisher;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    // ---------------------------------------------------------------- pokretanje, lista, rezultati, brisanje

    /**
     * Novo izvođenje u fazi prijave; 409 dok prezentacija već ima aktivno izvođenje. Prvo zaključa prezentaciju (isti
     * red kao izmene slajdova; brisanje prezentacije i drugo pokretanje ne mogu proći između provere i ovog upisa).
     * Bodovan run ({@code boduje}) traži predavanje (400) i prijavu studenata u ovom buildu (409), obe provere pre
     * zaključavanja, i uvek čuva (prisustvo se upisuje i kad prezentacija nema rundi).
     */
    public IzvodjenjeInfo pokreni(Long prezentacijaId, PokreniCmd cmd) {
        PokreniCmd c = cmd == null ? new PokreniCmd(false, null, null) : cmd;
        if (c.boduje()) {
            if (c.predavanjeId() == null) {
                throw new SystemException(BODOVANJE_BEZ_PREDAVANJA, HttpStatus.BAD_REQUEST);
            }
            if (!identitet.dostupno()) {
                throw new SystemException(PRIJAVA_NEDOSTUPNA, HttpStatus.CONFLICT);
            }
        }
        Prezentacija p = prezentacijaRepository.findByIdForUpdate(prezentacijaId)
                .orElseThrow(() -> new SystemException(PrezentacijaService.NIJE_PRONADJENA, HttpStatus.NOT_FOUND));
        // jedno aktivno izvođenje po prezentaciji; pod zaključanom prezentacijom, pa dva pokretanja idu redom
        if (izvodjenjeRepository.existsByPrezentacijaIdAndStatus(p.getId(), StatusIzvodjenja.AKTIVNO)) {
            throw new SystemException(VEC_U_TOKU, HttpStatus.CONFLICT);
        }

        boolean cuvanje = c.cuvanje();
        Grupa grupa = null;
        Predavanje predavanje = null;
        if (c.predavanjeId() != null) {
            predavanje = predavanjeRepository.findById(c.predavanjeId())
                    .orElseThrow(() -> new SystemException("Predavanje nije pronađeno.", HttpStatus.NOT_FOUND));
            if (predavanje.getPredmet() == null
                    || !Objects.equals(predavanje.getPredmet().getId(), p.getPredmet().getId())) {
                throw new SystemException(PREDAVANJE_DRUGI_PREDMET, HttpStatus.BAD_REQUEST);
            }
            grupa = predavanje.getGrupa();
            cuvanje = true;
        } else if (c.grupaId() != null) {
            grupa = grupaRepository.findById(c.grupaId())
                    .orElseThrow(() -> new SystemException("Grupa nije pronađena.", HttpStatus.NOT_FOUND));
        }
        if (c.boduje()) {
            cuvanje = true;   // prisustvo se čuva i bez rundi
        } else if (slajdRepository.countByPrezentacijaIdAndTipIn(p.getId(),
                EnumSet.of(TipSlajda.PITANJE, TipSlajda.ZADATAK)) == 0) {
            cuvanje = false;   // bez pitanja i zadataka nema šta da se čuva
        }

        String kod = kodGenerator.novi();
        Izvodjenje iz = new Izvodjenje();
        iz.setPrezentacija(p);
        iz.setKod(kod);
        iz.setAktivanKod(kod);
        iz.setGrupa(grupa);
        iz.setPredavanje(predavanje);
        iz.setCuvanje(cuvanje);
        iz.setBoduje(c.boduje());
        iz.setStatus(StatusIzvodjenja.AKTIVNO);
        iz.setPocetak(sada());
        iz.setPrikaz(Prikaz.PRIJAVA);
        iz.setKorak(0);
        iz.setEkran(Ekran.NORMALAN);
        iz.setQrPrikazan(false);
        iz.setTelefonPrikaz(p.getTelefonPrikaz());
        iz.setDetaljiDozvoljeni(p.isDetaljiDozvoljeni());
        iz.setTakmicenje(p.isTakmicenje());
        iz.setVerzija(1);
        try {
            iz = izvodjenjeRepository.saveAndFlush(iz);
        } catch (DataIntegrityViolationException e) {
            if (zauzetKod(e)) {
                // isti kod je u međuvremenu dobilo drugo izvođenje (UNIQUE aktivan_kod)
                throw new SystemException(KodGenerator.NEMA_KODA, HttpStatus.SERVICE_UNAVAILABLE);
            }
            log.error("Upis izvođenja nije uspeo: prezentacija={}", p.getId(), e);
            throw e;
        }
        log.info("Izvođenje pokrenuto: id={}, prezentacija={}, cuvanje={}, boduje={}", iz.getId(), p.getId(), cuvanje,
                c.boduje());
        return IzvodjenjeMapper.info(iz, 0, 0);
    }

    @Transactional(readOnly = true)
    public List<IzvodjenjeInfo> lista(Long prezentacijaId, StatusIzvodjenja status, Long predavanjeId) {
        return stanjeService.lista(prezentacijaId, status, predavanjeId);
    }

    @Transactional(readOnly = true)
    public IzvodjenjeRezultati rezultati(Long id) {
        return stanjeService.rezultati(id);
    }

    /**
     * Briše završeno izvođenje (runde, odgovori i učesnici kaskadom u bazi); aktivno -> 409. Zaključava istim redom kao
     * izmene prezentacije (prezentacija, pa izvođenje), pa ne može u deadlock sa brisanjem te prezentacije.
     */
    public void obrisi(Long id) {
        Long prezentacijaId = izvodjenjeRepository.findPrezentacijaIdById(id)
                .orElseThrow(() -> new SystemException(NIJE_PRONADJENO, HttpStatus.NOT_FOUND));
        prezentacijaRepository.findByIdForUpdate(prezentacijaId);
        Izvodjenje iz = nadjiZaIzmenu(id);
        if (iz.getStatus() == StatusIzvodjenja.AKTIVNO) {
            throw new SystemException(U_TOKU, HttpStatus.CONFLICT);
        }
        izvodjenjeRepository.delete(iz);
        log.info("Izvođenje obrisano: id={}", id);
    }

    /** Izvođenje zaključano do kraja transakcije ili 404. */
    public Izvodjenje nadjiZaIzmenu(Long id) {
        return izvodjenjeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new SystemException(NIJE_PRONADJENO, HttpStatus.NOT_FOUND));
    }

    // ---------------------------------------------------------------- komande

    public NastavnickoStanje komanda(Long id, KomandaCmd cmd) {
        if (cmd == null || cmd.tip() == null) {
            throw new SystemException(NEISPRAVNA_KOMANDA, HttpStatus.BAD_REQUEST);
        }
        Izvodjenje iz = nadjiZaIzmenu(id);
        proveriAktivno(iz);
        prihvatiKomandu(iz, slajdovi(iz), cmd);
        return objavi(iz);
    }

    private void prihvatiKomandu(Izvodjenje iz, List<Slajd> sl, KomandaCmd cmd) {
        switch (cmd.tip()) {
            case SLEDECI -> sledeci(iz, sl);
            case PRETHODNI -> prethodni(iz, sl);
            case IDI_NA -> idiNaKomanda(iz, sl, cmd.vrednost());
            case OTVORI_ZATVORI -> otvoriZatvori(iz, sl);
            case REZULTATI -> rezultatiPrikaz(iz, sl);
            case TACAN -> tacanPrikaz(iz, sl);
            case RANG_LISTA -> rangPrikaz(iz, RangPrikaz.IZVODJENJE);
            case RANG_PREDAVANJA -> rangPrikaz(iz, RangPrikaz.PREDAVANJE);
            case PONOVI -> ponovi(iz, sl);
            case TAJMER -> tajmer(iz);
            case TAJMER_PLUS -> pomeriTajmer(iz, POMAK_TAJMERA_MS);
            case TAJMER_MINUS -> pomeriTajmer(iz, -POMAK_TAJMERA_MS);
            case TELEFON_PRIKAZ -> iz.setTelefonPrikaz(
                    iz.getTelefonPrikaz() == TelefonPrikaz.PITANJE ? TelefonPrikaz.DUGMAD : TelefonPrikaz.PITANJE);
            case DETALJI -> iz.setDetaljiDozvoljeni(!iz.isDetaljiDozvoljeni());
            case EKRAN_CRN -> iz.setEkran(iz.getEkran() == Ekran.CRN ? Ekran.NORMALAN : Ekran.CRN);
            case EKRAN_BEO -> iz.setEkran(iz.getEkran() == Ekran.BEO ? Ekran.NORMALAN : Ekran.BEO);
            case QR -> iz.setQrPrikazan(!iz.isQrPrikazan());
            case ZAVRSI -> zavrsi(iz);
            case RESENJE -> resenjePrikaz(iz, sl);
            case BROJ_VARIJANTI -> brojVarijantiKomanda(iz, sl, cmd.vrednost());
            case NIVO -> nivoKomanda(iz, sl, cmd.vrednost());
        }
    }

    /**
     * Prekidač ranga na projektoru (spec rang tačnosti 6): isti rang drugi put sklanja listu, drugi rang je zamenjuje.
     * Rang predavanja traži predavanje sa još izvođenja sa rundama (409).
     */
    private void rangPrikaz(Izvodjenje iz, RangPrikaz koji) {
        if (iz.isRangListaPrikazana() && rangPrikazi.prikaz(iz.getId()) == koji) {
            iz.setRangListaPrikazana(false);
            rangPrikazi.ukloni(iz.getId());
            return;
        }
        if (koji == RangPrikaz.PREDAVANJE && !rangTacnosti.imaRangPredavanja(iz)) {
            throw new SystemException(NEMA_RANGA_PREDAVANJA, HttpStatus.CONFLICT);
        }
        iz.setRangListaPrikazana(true);
        rangPrikazi.postavi(iz.getId(), koji);
    }

    /**
     * → : sledeći korak (stavka INFO-a, KLIK platna), pa otvori/zatvori pitanje ili zadatak, pa sledeći slajd; na kraju
     * ništa.
     */
    private void sledeci(Izvodjenje iz, List<Slajd> sl) {
        int n = sl.size();
        int i = TokIzvodjenja.indeks(iz, sl);
        if (i >= n) {
            if (iz.getPrikaz() != Prikaz.KRAJ) idiNa(iz, sl, n, true);   // trenutni slajd je obrisan
            return;
        }
        if (i < 0) {
            idiNa(iz, sl, 0, true);
            return;
        }
        Slajd s = sl.get(i);
        if (!TokIzvodjenja.saRundama(s)) {
            if (iz.getKorak() < TokIzvodjenja.brojKoraka(s)) {
                iz.setKorak(iz.getKorak() + 1);
            } else {
                idiNa(iz, sl, i + 1, true);
            }
            return;
        }
        switch (faza(iz)) {
            case CEKA -> otvori(iz, s);
            case OTVORENO -> zatvori(iz);
            case ZATVORENO -> idiNa(iz, sl, i + 1, true);
        }
    }

    /** ← : uvek korak nazad; otvoreno pitanje se prvo zatvori. */
    private void prethodni(Izvodjenje iz, List<Slajd> sl) {
        int n = sl.size();
        int i = TokIzvodjenja.indeks(iz, sl);
        if (i < 0) return;
        if (i >= n) {
            idiNa(iz, sl, n - 1, false);
            return;
        }
        Slajd s = sl.get(i);
        if (TokIzvodjenja.saRundama(s) && iz.getFaza() == Faza.OTVORENO) {
            zatvori(iz);
        } else if (!TokIzvodjenja.saRundama(s) && iz.getKorak() > 0) {
            iz.setKorak(iz.getKorak() - 1);
        } else {
            idiNa(iz, sl, i - 1, false);
        }
    }

    private void idiNaKomanda(Izvodjenje iz, List<Slajd> sl, Integer vrednost) {
        int n = sl.size();
        if (vrednost == null || vrednost < -1 || vrednost > n) {
            throw new SystemException(NEPOSTOJECI_SLAJD, HttpStatus.BAD_REQUEST);
        }
        int i = TokIzvodjenja.indeks(iz, sl);
        boolean vecTu = vrednost == i && (i < n || iz.getPrikaz() == Prikaz.KRAJ);
        if (!vecTu) {
            idiNa(iz, sl, vrednost, true);
        }
    }

    /**
     * Prelazak na slajd {@code j} (-1 prijava, n kraj). Otvoreno pitanje se zatvara; rezultati, tačan odgovor, rešenje i
     * rang-lista se gase, a izmena broja varijanti i nivoa iz konzole se briše (ekran i QR ostaju). INFO sa postepenim i PLATNO
     * počinju od koraka 0 ({@code napred}) ili sa svim otkrivenim (unazad: sa svim koracima); slajd sa rundama (pitanje,
     * zadatak) prikazuje poslednju rundu kao zatvorenu, inače čeka.
     */
    private void idiNa(Izvodjenje iz, List<Slajd> sl, int j, boolean napred) {
        if (iz.getFaza() == Faza.OTVORENO) {
            zatvori(iz);
        }
        iz.setRezultatiPrikazani(false);
        iz.setTacanPrikazan(false);
        iz.setResenjePrikazano(false);
        iz.setZadatakBrojVarijanti(null);
        iz.setZadatakNivo(null);
        iz.setRangListaPrikazana(false);
        iz.setFaza(null);
        iz.setTrenutnaRundaId(null);
        iz.setKorak(0);
        if (j < 0) {
            iz.setPrikaz(Prikaz.PRIJAVA);
            iz.setTrenutniSlajdId(null);
            return;
        }
        if (j >= sl.size()) {
            iz.setPrikaz(Prikaz.KRAJ);
            iz.setTrenutniSlajdId(null);
            return;
        }
        Slajd s = sl.get(j);
        iz.setPrikaz(Prikaz.SLAJD);
        iz.setTrenutniSlajdId(s.getId());
        if (!TokIzvodjenja.saRundama(s)) {
            iz.setKorak(napred ? 0 : TokIzvodjenja.brojKoraka(s));
        } else {
            postaviFazuPitanja(iz, s);
        }
    }

    /**
     * Faza slajda sa rundama: poslednja runda slajda kao zatvorena, ali samo ako je iste vrste kao slajd
     * ({@link TokIzvodjenja#odgovaraSlajdu}: slajd je možda u međuvremenu promenio tip), inače čeka.
     */
    private void postaviFazuPitanja(Izvodjenje iz, Slajd s) {
        Optional<PitanjeRunda> poslednja = rundaRepository
                .findFirstByIzvodjenjeIdAndSlajdIdOrderByRedniBrojDesc(iz.getId(), s.getId())
                .filter(r -> TokIzvodjenja.odgovaraSlajdu(snimak(r), s));
        iz.setFaza(poslednja.isPresent() ? Faza.ZATVORENO : Faza.CEKA);
        iz.setTrenutnaRundaId(poslednja.map(PitanjeRunda::getId).orElse(null));
    }

    private void otvoriZatvori(Izvodjenje iz, List<Slajd> sl) {
        Slajd s = rundaIliGreska(iz, sl);
        switch (faza(iz)) {
            case CEKA -> otvori(iz, s);
            case OTVORENO -> zatvori(iz);
            case ZATVORENO -> {
                PitanjeRunda r = trenutnaRunda(iz).orElse(null);
                if (r == null) {
                    otvori(iz, s);
                    return;
                }
                // ponovo otvara istu rundu, bez tajmera i bez trajanja (poeni više ne zavise od brzine, ni kad se
                // posle pokrene T); tačan odgovor i rešenje se ne smeju videti dok se odgovara
                r.setZatvoreno(null);
                r.setRok(null);
                r.setPreostaloMs(null);
                r.setTrajanjeMs(null);
                iz.setFaza(Faza.OTVORENO);
                iz.setRezultatiPrikazani(false);
                iz.setTacanPrikazan(false);
                iz.setResenjePrikazano(false);
                // ko je ušao dok je runda bila zatvorena, dobija zadatak sada
                if (snimak(r) instanceof ZadatakSnimak zs) {
                    zadatakDodelaService.dodeliNedostajucima(iz, r, zs);
                }
            }
        }
    }

    /**
     * Nova runda sa snimkom slajda; tajmer kreće ako pitanje ili zadatak ima ograničenje. ZADATAK: prvo izvlačenje
     * ({@link ZadatakDodelaService#snimak}, 409 bez upotrebljive grupe ili kandidata, pa ništa nije upisano), pa posle
     * upisa runde dodela svim neizbačenim učesnicima.
     */
    private void otvori(Izvodjenje iz, Slajd s) {
        boolean zadatak = s.getTip() == TipSlajda.ZADATAK;
        SnimakRunde snimak = zadatak
                ? zadatakDodelaService.snimak(s, TokIzvodjenja.brojVarijanti(iz, s), TokIzvodjenja.nivo(iz, s))
                : PitanjeSnimak.od(s);
        Integer sekunde = zadatak ? s.getZadatakVremeSekunde() : s.getPitanje().getVremeSekunde();
        LocalDateTime sada = sada();
        PitanjeRunda r = new PitanjeRunda();
        r.setIzvodjenje(iz);
        r.setSlajdId(s.getId());
        r.setRedniBroj(rundaRepository.findFirstByIzvodjenjeIdAndSlajdIdOrderByRedniBrojDesc(iz.getId(), s.getId())
                .map(p -> p.getRedniBroj() + 1).orElse(1));
        r.setSnimak(jsonMapper.writeValueAsString(snimak));
        r.setOtvoreno(sada);
        if (sekunde != null) {
            r.setRok(sada.plusSeconds(sekunde));
            r.setTrajanjeMs(sekunde * 1000L);
        }
        r = rundaRepository.save(r);
        iz.setTrenutnaRundaId(r.getId());
        iz.setFaza(Faza.OTVORENO);
        iz.setRezultatiPrikazani(false);
        iz.setTacanPrikazan(false);
        iz.setResenjePrikazano(false);
        if (snimak instanceof ZadatakSnimak zs) {
            zadatakDodelaService.dodeliNedostajucima(iz, r, zs);
        }
        if (r.getRok() != null) {
            rokPlaner.zakazi(iz.getId(), r.getId(), r.getRok());
        }
    }

    /** Zatvara trenutnu rundu (ako je otvorena) i otkazuje zatvaranje po roku. */
    private void zatvori(Izvodjenje iz) {
        trenutnaRunda(iz).ifPresent(r -> {
            if (r.getZatvoreno() == null) {
                r.setZatvoreno(sada());
            }
            rokPlaner.otkazi(r.getId());
        });
        iz.setFaza(Faza.ZATVORENO);
    }

    private void rezultatiPrikaz(Izvodjenje iz, List<Slajd> sl) {
        if (!iz.isRezultatiPrikazani()) {
            Slajd s = TokIzvodjenja.trenutni(iz, sl);
            boolean imaRundu = TokIzvodjenja.saRundama(s) && iz.getTrenutnaRundaId() != null
                    && (iz.getFaza() == Faza.OTVORENO || iz.getFaza() == Faza.ZATVORENO);
            if (!imaRundu) {
                throw new SystemException(NEMA_REZULTATA, HttpStatus.CONFLICT);
            }
        }
        iz.setRezultatiPrikazani(!iz.isRezultatiPrikazani());
    }

    private void tacanPrikaz(Izvodjenje iz, List<Slajd> sl) {
        if (iz.isTacanPrikazan()) {
            iz.setTacanPrikazan(false);
            return;
        }
        Slajd s = rundaIliGreska(iz, sl);
        SnimakRunde snimak = trenutnaRunda(iz).map(this::snimak).orElse(null);
        if (snimak == null) {
            // zadatak bez runde još nema ni izvučene varijante, pa ni tačan odgovor
            if (s.getTip() == TipSlajda.ZADATAK) {
                throw new SystemException(NIJE_POSTAVLJENO, HttpStatus.CONFLICT);
            }
            snimak = PitanjeSnimak.od(s);
        }
        if (!snimak.imaTacanOdgovor()) {
            throw new SystemException(NEMA_TACNOG, HttpStatus.CONFLICT);
        }
        switch (faza(iz)) {
            case CEKA -> throw new SystemException(NIJE_POSTAVLJENO, HttpStatus.CONFLICT);
            case OTVORENO -> throw new SystemException(PRVO_ZATVORI, HttpStatus.CONFLICT);
            case ZATVORENO -> iz.setTacanPrikazan(true);
        }
    }

    /**
     * Rešenje ZADATAK runde na projektoru (prekidač): isključenje uvek, uključenje samo na zatvorenoj rundi Zadatak slajda
     * u kojoj bar jedna izvučena varijanta ima rešenje. U CEKA/OTVORENO kao TACAN.
     */
    private void resenjePrikaz(Izvodjenje iz, List<Slajd> sl) {
        if (iz.isResenjePrikazano()) {
            iz.setResenjePrikazano(false);
            return;
        }
        Slajd s = TokIzvodjenja.trenutni(iz, sl);
        if (s == null || s.getTip() != TipSlajda.ZADATAK) {
            throw new SystemException(NEMA_ZADATKA, HttpStatus.CONFLICT);
        }
        switch (faza(iz)) {
            case CEKA -> throw new SystemException(NIJE_POSTAVLJENO, HttpStatus.CONFLICT);
            case OTVORENO -> throw new SystemException(PRVO_ZATVORI, HttpStatus.CONFLICT);
            case ZATVORENO -> {
                SnimakRunde snimak = trenutnaRunda(iz).map(this::snimak)
                        .orElseThrow(() -> new SystemException(NIJE_POSTAVLJENO, HttpStatus.CONFLICT));
                if (!(snimak instanceof ZadatakSnimak zs) || !zs.imaResenje()) {
                    throw new SystemException(NEMA_RESENJA, HttpStatus.CONFLICT);
                }
                iz.setResenjePrikazano(true);
            }
        }
    }

    /**
     * Broj različitih zadataka za sledeće otvaranje ZADATAK slajda (i za PONOVI), samo pre otvaranja: {@code null} = kao
     * na slajdu, 0 = sve aktivne, 1..50 = toliko. Briše se pri prelasku na drugi slajd ({@link #idiNa}).
     */
    private void brojVarijantiKomanda(Izvodjenje iz, List<Slajd> sl, Integer vrednost) {
        if (vrednost != null && (vrednost < 0 || vrednost > NAJVISE_VARIJANTI)) {
            throw new SystemException(BROJ_VARIJANTI_GRANICE, HttpStatus.BAD_REQUEST);
        }
        Slajd s = TokIzvodjenja.trenutni(iz, sl);
        if (s == null || s.getTip() != TipSlajda.ZADATAK) {
            throw new SystemException(NEMA_ZADATKA, HttpStatus.CONFLICT);
        }
        if (faza(iz) != Faza.CEKA) {
            throw new SystemException(BROJ_PRE_OTVARANJA, HttpStatus.CONFLICT);
        }
        iz.setZadatakBrojVarijanti(vrednost);
    }

    /**
     * Nivo varijanti za sledeće otvaranje ZADATAK slajda (i za PONOVI), samo pre otvaranja: {@code null} = kao na slajdu,
     * 0 = svi nivoi, 1..3 = taj nivo (van toga 400 {@link Nivo#GRANICE}). Briše se pri prelasku na drugi slajd
     * ({@link #idiNa}), kao broj varijanti.
     */
    private void nivoKomanda(Izvodjenje iz, List<Slajd> sl, Integer vrednost) {
        if (vrednost != null && vrednost != 0) {
            Nivo.proveri(vrednost);
        }
        Slajd s = TokIzvodjenja.trenutni(iz, sl);
        if (s == null || s.getTip() != TipSlajda.ZADATAK) {
            throw new SystemException(NEMA_ZADATKA, HttpStatus.CONFLICT);
        }
        if (faza(iz) != Faza.CEKA) {
            throw new SystemException(NIVO_PRE_OTVARANJA, HttpStatus.CONFLICT);
        }
        iz.setZadatakNivo(vrednost);
    }

    /** Nova runda istog pitanja ili zadatka (stari odgovori i dodele ostaju u prethodnoj); otvorena se prvo zatvori. */
    private void ponovi(Izvodjenje iz, List<Slajd> sl) {
        Slajd s = rundaIliGreska(iz, sl);
        Faza f = faza(iz);
        if (f == Faza.CEKA) {
            throw new SystemException(NIJE_POSTAVLJENO, HttpStatus.CONFLICT);
        }
        if (f == Faza.OTVORENO) {
            zatvori(iz);
        }
        otvori(iz, s);
    }

    /**
     * Pauza (pamti preostalo), nastavak, ili 30 s kad pitanje nema ograničenje. Posle roka (u sekundi tolerancije, pre
     * nego što ga {@link RokPlaner} zatvori) pauza bi ostavila rundu otvorenu bez roka, pa bi se odgovori primali bez
     * kraja: tada T zatvara pitanje.
     */
    private void tajmer(Izvodjenje iz) {
        PitanjeRunda r = otvorenaRunda(iz);
        LocalDateTime sada = sada();
        if (r.getRok() != null && !sada.isBefore(r.getRok())) {
            zatvori(iz);
        } else if (r.getRok() != null) {
            r.setPreostaloMs(Math.max(0, Duration.between(sada, r.getRok()).toMillis()));
            r.setRok(null);
            rokPlaner.otkazi(r.getId());
        } else if (r.getPreostaloMs() != null) {
            r.setRok(sada.plus(Duration.ofMillis(r.getPreostaloMs())));
            r.setPreostaloMs(null);
            rokPlaner.zakazi(iz.getId(), r.getId(), r.getRok());
        } else {
            // naknadno pokrenut tajmer ne menja poene (trajanje ostaje prazno: "bez tajmera" = 1000)
            r.setRok(sada.plusSeconds(PODRAZUMEVANI_TAJMER_S));
            rokPlaner.zakazi(iz.getId(), r.getId(), r.getRok());
        }
    }

    /** ±10 s (i dok je pauziran); minus ne spušta preostalo ispod 5 s. Trajanje (za poene) prati promenu. */
    private void pomeriTajmer(Izvodjenje iz, long pomakMs) {
        PitanjeRunda r = otvorenaRunda(iz);
        LocalDateTime sada = sada();
        long preostalo;
        if (r.getRok() != null) {
            preostalo = Math.max(0, Duration.between(sada, r.getRok()).toMillis());
        } else if (r.getPreostaloMs() != null) {
            preostalo = r.getPreostaloMs();
        } else {
            throw new SystemException(TAJMER_NIJE_POKRENUT, HttpStatus.CONFLICT);
        }
        long novo = pomakMs > 0 ? preostalo + pomakMs
                : preostalo <= NAJMANJE_PREOSTALO_MS ? preostalo : Math.max(preostalo + pomakMs, NAJMANJE_PREOSTALO_MS);
        long razlika = novo - preostalo;
        if (razlika == 0) return;
        if (r.getTrajanjeMs() != null) {
            r.setTrajanjeMs(r.getTrajanjeMs() + razlika);
        }
        if (r.getRok() != null) {
            r.setRok(sada.plus(Duration.ofMillis(novo)));
            rokPlaner.zakazi(iz.getId(), r.getId(), r.getRok());
        } else {
            r.setPreostaloMs(novo);
        }
    }

    /**
     * Završava izvođenje (komanda ZAVRSI i automatski posle 12 h): otvoreno pitanje se zatvara, kod se oslobađa. Bez
     * čuvanja se brišu odgovori, runde i učesnici (i trenutna runda i faza se prazne); ostaje samo red izvođenja. Ne
     * menja verziju (to radi pozivalac).
     */
    public void zavrsi(Izvodjenje iz) {
        if (iz.getFaza() == Faza.OTVORENO) {
            zatvori(iz);
        }
        iz.setStatus(StatusIzvodjenja.ZAVRSENO);
        iz.setAktivanKod(null);
        rangPrikazi.ukloni(iz.getId());
        iz.setKraj(sada());
        if (!iz.isCuvanje()) {
            // runde se brišu, pa ni faza pitanja više ne postoji
            iz.setTrenutnaRundaId(null);
            iz.setFaza(null);
            // @Modifying(flushAutomatically): izmene izvođenja se upišu pre brisanja
            odgovorRepository.deleteAllByIzvodjenjeId(iz.getId());
            rundaRepository.deleteAllByIzvodjenjeId(iz.getId());
            ucesnikRepository.deleteAllByIzvodjenjeId(iz.getId());
        }
        log.info("Izvođenje završeno: id={}, cuvanje={}", iz.getId(), iz.isCuvanje());
    }

    /** Održavanje: završava aktivno izvođenje (isti postupak kao ZAVRSI); završeno ostaje kakvo jeste. */
    public void zavrsiAutomatski(Long id) {
        Izvodjenje iz = nadjiZaIzmenu(id);
        if (iz.getStatus() != StatusIzvodjenja.AKTIVNO) return;
        zavrsi(iz);
        promenjeno(iz);
        log.info("Izvođenje automatski završeno posle {} h: id={}", NAJDUZE_TRAJANJE.toHours(), id);
    }

    /**
     * Zatvaranje po roku (iz {@link RokPlaner}-a): samo ako je runda i dalje trenutna, otvorena i rok + 1 s je prošao.
     * Rok pomeren unapred (+10 s) ili pauza znače da ne treba zatvoriti; pre roka (raniji okidač) se samo ponovo zakaže.
     */
    public void zatvoriPoRoku(Long izvodjenjeId, Long rundaId) {
        Izvodjenje iz = izvodjenjeRepository.findByIdForUpdate(izvodjenjeId).orElse(null);
        if (iz == null || iz.getStatus() != StatusIzvodjenja.AKTIVNO || iz.getFaza() != Faza.OTVORENO
                || !Objects.equals(rundaId, iz.getTrenutnaRundaId())) {
            return;
        }
        PitanjeRunda r = rundaRepository.findById(rundaId).orElse(null);
        if (r == null || r.getRok() == null || r.getZatvoreno() != null) return;
        if (sada().isBefore(r.getRok().plus(TOLERANCIJA))) {
            rokPlaner.zakazi(izvodjenjeId, rundaId, r.getRok());
            return;
        }
        zatvori(iz);
        promenjeno(iz);
    }

    // ---------------------------------------------------------------- moderacija

    public NastavnickoStanje preimenuj(Long id, Long ucesnikId, String ime) {
        Izvodjenje iz = nadjiZaIzmenu(id);
        proveriAktivno(iz);
        Ucesnik u = ucesnik(iz, ucesnikId);
        String novo = imenaUcesnika.validiraj(ime);
        u.setIme(imenaUcesnika.jedinstvenoIme(iz.getId(), novo, u.getId()));
        return objavi(iz);
    }

    /** Izbačeni učesnik ne može više da odgovara; ponovo izbacivanje istog je bez izmene. */
    public NastavnickoStanje izbaci(Long id, Long ucesnikId) {
        Izvodjenje iz = nadjiZaIzmenu(id);
        proveriAktivno(iz);
        Ucesnik u = ucesnikRepository.findByIdAndIzvodjenjeId(ucesnikId, iz.getId())
                .orElseThrow(() -> new SystemException(UCESNIK_NIJE_PRONADJEN, HttpStatus.NOT_FOUND));
        if (u.isIzbacen()) {
            return stanjeService.nastavnicko(iz.getId());
        }
        u.setIzbacen(true);
        publisher.publishEvent(new UcesnikIzbacen(iz.getId(), u.getId()));
        log.info("Učesnik izbačen: izvodjenje={}, ucesnik={}", iz.getId(), u.getId());
        return objavi(iz);
    }

    /** Sakriva (ili vraća) sve odgovore runde čiji je normalizovan tekst jednak {@code kljuc}. */
    public NastavnickoStanje sakrij(Long id, Long rundaId, SakrijCmd cmd) {
        if (cmd == null || cmd.kljuc() == null) {
            throw new SystemException("Ključ odgovora je obavezan.", HttpStatus.BAD_REQUEST);
        }
        Izvodjenje iz = nadjiZaIzmenu(id);
        rundaRepository.findByIdAndIzvodjenjeId(rundaId, iz.getId())
                .orElseThrow(() -> new SystemException(RUNDA_NIJE_PRONADJENA, HttpStatus.NOT_FOUND));
        String kljuc = Normalizacija.tekst(cmd.kljuc());
        for (Odgovor o : odgovorRepository.findAllByRundaId(rundaId)) {
            if (o.getTekst() != null && Normalizacija.tekst(o.getTekst()).equals(kljuc)) {
                o.setSakriven(cmd.sakriven());
            }
        }
        return objavi(iz);
    }

    // ---------------------------------------------------------------- izmene prezentacije tokom izvođenja

    /**
     * Zaključava aktivna izvođenja prezentacije rastuće po id-ju. Poziva se (kroz {@link PrezentacijaPromene}) dok je
     * prezentacija već zaključana, a pre upisa slajdova; prvo učitavanje je pod zaključavanjem, pa je stanje sveže.
     */
    public void zakljucajAktivna(Long prezentacijaId) {
        for (Long id : izvodjenjeRepository.findIdsByPrezentacijaIdAndStatus(prezentacijaId, StatusIzvodjenja.AKTIVNO)) {
            izvodjenjeRepository.findByIdForUpdate(id);
        }
    }

    /** Slajdovi ili podaci prezentacije su izmenjeni: faza trenutnog slajda se preračuna, verzija raste. */
    public void prezentacijaPromenjena(Long prezentacijaId) {
        for (Long id : izvodjenjeRepository.findIdsByPrezentacijaIdAndStatus(prezentacijaId, StatusIzvodjenja.AKTIVNO)) {
            Izvodjenje iz = nadjiZaIzmenu(id);
            if (iz.getStatus() != StatusIzvodjenja.AKTIVNO) continue;
            uskladi(iz, slajdovi(iz));
            promenjeno(iz);
        }
    }

    /**
     * Slajd je obrisan ({@code stariIndeks} = njegova pozicija pre brisanja). Ako je bio trenutni, trenutni postaje
     * slajd koji je sada na tom mestu (ili kraj); otvorena runda se zatvara, istorija ostaje u snimku.
     */
    public void slajdObrisan(Long prezentacijaId, Long slajdId, int stariIndeks) {
        for (Long id : izvodjenjeRepository.findIdsByPrezentacijaIdAndStatus(prezentacijaId, StatusIzvodjenja.AKTIVNO)) {
            Izvodjenje iz = nadjiZaIzmenu(id);
            if (iz.getStatus() != StatusIzvodjenja.AKTIVNO) continue;
            List<Slajd> sl = slajdovi(iz);
            if (iz.getPrikaz() == Prikaz.SLAJD && Objects.equals(slajdId, iz.getTrenutniSlajdId())) {
                idiNa(iz, sl, Math.max(0, Math.min(stariIndeks, sl.size())), true);
            } else {
                uskladi(iz, sl);
            }
            promenjeno(iz);
        }
    }

    /**
     * Trenutni slajd je možda promenio tip ili broj koraka (stavki INFO-a, KLIK animacija platna); sve što nema runde
     * ponaša se kao INFO sa koracima: faza i korak se usklađuju sa slajdom. Slajd sa rundama čija trenutna runda nije
     * njegove vrste (PITANJE postao ZADATAK ili obrnuto) ostaje kao da runde nema: otvorena se zatvara, pa CEKA bez
     * trenutne runde i bez prikazanih rezultata, tačnog i rešenja; sledeće otvaranje pravi novu rundu. Tako nijedan
     * graditelj stanja ne dobije snimak druge vrste od tipa slajda.
     */
    private void uskladi(Izvodjenje iz, List<Slajd> sl) {
        if (iz.getPrikaz() != Prikaz.SLAJD) return;
        int i = TokIzvodjenja.pozicija(sl, iz.getTrenutniSlajdId());
        if (i < 0) return;
        Slajd s = sl.get(i);
        if (s.getTip() != TipSlajda.ZADATAK) {
            // rešenje, broj varijanti i nivo postoje samo na Zadatak slajdu
            iz.setResenjePrikazano(false);
            iz.setZadatakBrojVarijanti(null);
            iz.setZadatakNivo(null);
        }
        if (!TokIzvodjenja.saRundama(s)) {
            if (iz.getFaza() == Faza.OTVORENO) {
                zatvori(iz);
            }
            iz.setFaza(null);
            iz.setTrenutnaRundaId(null);
            iz.setRezultatiPrikazani(false);
            iz.setTacanPrikazan(false);
            iz.setKorak(Math.min(iz.getKorak(), TokIzvodjenja.brojKoraka(s)));
        } else {
            iz.setKorak(0);
            if (iz.getFaza() == null) {
                postaviFazuPitanja(iz, s);
            } else if (trenutnaRunda(iz).map(r -> !TokIzvodjenja.odgovaraSlajdu(snimak(r), s)).orElse(false)) {
                if (iz.getFaza() == Faza.OTVORENO) {
                    zatvori(iz);
                }
                iz.setFaza(Faza.CEKA);
                iz.setTrenutnaRundaId(null);
                iz.setRezultatiPrikazani(false);
                iz.setTacanPrikazan(false);
                iz.setResenjePrikazano(false);
            }
        }
    }

    // ---------------------------------------------------------------- pomoćno

    private List<Slajd> slajdovi(Izvodjenje iz) {
        return slajdRepository.findAllByPrezentacijaIdOrderByRbAsc(iz.getPrezentacija().getId());
    }

    /** Faza pitanja na trenutnom slajdu; {@code null} se tretira kao CEKA. */
    private static Faza faza(Izvodjenje iz) {
        return iz.getFaza() == null ? Faza.CEKA : iz.getFaza();
    }

    /** Trenutni slajd sa rundama (pitanje ili zadatak), inače 409 {@value #NEMA_PITANJA}. */
    private Slajd rundaIliGreska(Izvodjenje iz, List<Slajd> sl) {
        Slajd s = TokIzvodjenja.trenutni(iz, sl);
        if (!TokIzvodjenja.saRundama(s)) {
            throw new SystemException(NEMA_PITANJA, HttpStatus.CONFLICT);
        }
        return s;
    }

    private Optional<PitanjeRunda> trenutnaRunda(Izvodjenje iz) {
        return iz.getTrenutnaRundaId() == null ? Optional.empty() : rundaRepository.findById(iz.getTrenutnaRundaId());
    }

    private PitanjeRunda otvorenaRunda(Izvodjenje iz) {
        if (iz.getFaza() != Faza.OTVORENO) {
            throw new SystemException(TAJMER_VAN_PITANJA, HttpStatus.CONFLICT);
        }
        return trenutnaRunda(iz).orElseThrow(() -> new SystemException(TAJMER_VAN_PITANJA, HttpStatus.CONFLICT));
    }

    private SnimakRunde snimak(PitanjeRunda r) {
        return jsonMapper.readValue(r.getSnimak(), SnimakRunde.class);
    }

    private Ucesnik ucesnik(Izvodjenje iz, Long ucesnikId) {
        return ucesnikRepository.findByIdAndIzvodjenjeId(ucesnikId, iz.getId())
                .filter(u -> !u.isIzbacen())
                .orElseThrow(() -> new SystemException(UCESNIK_NIJE_PRONADJEN, HttpStatus.NOT_FOUND));
    }

    /**
     * Da li je greška integriteta baš povreda jedinstvenosti aktivnog koda ({@code uk_izvodjenja_aktivan_kod}); traži
     * ime ključa kroz lanac uzroka (Hibernate ime ograničenja ili poruka MySQL-a "Duplicate entry ... for key ...").
     */
    static boolean zauzetKod(Throwable greska) {
        Set<Throwable> videni = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable t = greska; t != null && videni.add(t); t = t.getCause()) {
            if (t instanceof org.hibernate.exception.ConstraintViolationException c && sadrziKljuc(c.getConstraintName())) {
                return true;
            }
            if (t instanceof java.sql.SQLException && sadrziKljuc(t.getMessage())) {
                return true;
            }
        }
        return false;
    }

    private static boolean sadrziKljuc(String tekst) {
        return tekst != null && tekst.toLowerCase(Locale.ROOT).contains(UK_AKTIVAN_KOD);
    }

    private static void proveriAktivno(Izvodjenje iz) {
        if (iz.getStatus() != StatusIzvodjenja.AKTIVNO) {
            throw new SystemException(ZAVRSENO, HttpStatus.GONE);
        }
    }

    /** Verzija +1, upis, događaj (klijentima posle commit-a). */
    private void promenjeno(Izvodjenje iz) {
        iz.setVerzija(iz.getVerzija() + 1);
        izvodjenjeRepository.flush();
        publisher.publishEvent(new IzvodjenjePromenjeno(iz.getId()));
    }

    private NastavnickoStanje objavi(Izvodjenje iz) {
        promenjeno(iz);
        return stanjeService.nastavnicko(iz.getId());
    }

    private LocalDateTime sada() {
        return LocalDateTime.now(clock);
    }
}
