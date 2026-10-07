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

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Izvođenje prezentacije uživo: pokretanje, nastavničke komande (stanje-mašina iz spec-a 2.3-2.5 i 4.5), tajmer,
 * moderacija i završetak. Svaka izmena prvo zaključa red izvođenja ({@code findByIdForUpdate}), poveća
 * {@code verzija} za 1 i objavi {@link IzvodjenjePromenjeno} (klijentima ide tek posle commit-a). Redosled
 * zaključavanja je uvek prezentacija -> izvođenje ({@code pokreni} i izmene slajdova kroz {@link PrezentacijaPromene}).
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
    static final String NIJE_TAKMICENJE = "Takmičenje nije uključeno.";
    static final String TAJMER_VAN_PITANJA = "Tajmer radi samo dok je pitanje otvoreno.";
    static final String TAJMER_NIJE_POKRENUT = "Tajmer nije pokrenut.";
    static final String NIJE_POSTAVLJENO = "Pitanje još nije postavljeno.";
    static final String UCESNIK_NIJE_PRONADJEN = "Učesnik nije pronađen.";
    static final String RUNDA_NIJE_PRONADJENA = "Runda nije pronađena.";
    static final String PREDAVANJE_DRUGI_PREDMET = "Predavanje nije iz predmeta ove prezentacije.";

    /** Odgovori se primaju i pitanje se zatvara tek 1 s posle roka (mreža). */
    static final Duration TOLERANCIJA = Duration.ofSeconds(1);
    static final long POMAK_TAJMERA_MS = 10_000;
    static final long NAJMANJE_PREOSTALO_MS = 5_000;
    static final int PODRAZUMEVANI_TAJMER_S = 30;
    static final Duration NAJDUZE_TRAJANJE = Duration.ofHours(12);

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
    private final ImenaUcesnika imenaUcesnika;
    private final ApplicationEventPublisher publisher;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    // ---------------------------------------------------------------- pokretanje, lista, rezultati, brisanje

    /**
     * Novo izvođenje u fazi prijave. Prvo zaključa prezentaciju (isti red kao izmene slajdova; brisanje prezentacije
     * tada ne može proći između provere "nema aktivnog izvođenja" i ovog upisa).
     */
    public IzvodjenjeInfo pokreni(Long prezentacijaId, PokreniCmd cmd) {
        PokreniCmd c = cmd == null ? new PokreniCmd(false, null, null) : cmd;
        Prezentacija p = prezentacijaRepository.findByIdForUpdate(prezentacijaId)
                .orElseThrow(() -> new SystemException(PrezentacijaService.NIJE_PRONADJENA, HttpStatus.NOT_FOUND));

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
        if (slajdRepository.countByPrezentacijaIdAndTip(p.getId(), TipSlajda.PITANJE) == 0) {
            cuvanje = false;   // bez pitanja nema šta da se čuva
        }

        String kod = kodGenerator.novi();
        Izvodjenje iz = new Izvodjenje();
        iz.setPrezentacija(p);
        iz.setKod(kod);
        iz.setAktivanKod(kod);
        iz.setGrupa(grupa);
        iz.setPredavanje(predavanje);
        iz.setCuvanje(cuvanje);
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
            // isti kod je u međuvremenu dobilo drugo izvođenje (UNIQUE aktivan_kod)
            throw new SystemException(KodGenerator.NEMA_KODA, HttpStatus.SERVICE_UNAVAILABLE);
        }
        log.info("Izvođenje pokrenuto: id={}, prezentacija={}, cuvanje={}", iz.getId(), p.getId(), cuvanje);
        return IzvodjenjeMapper.info(iz, 0, 0);
    }

    @Transactional(readOnly = true)
    public List<IzvodjenjeInfo> lista(Long prezentacijaId, StatusIzvodjenja status) {
        return stanjeService.lista(prezentacijaId, status);
    }

    @Transactional(readOnly = true)
    public IzvodjenjeRezultati rezultati(Long id) {
        return stanjeService.rezultati(id);
    }

    /** Briše završeno izvođenje (runde, odgovori i učesnici kaskadom u bazi); aktivno -> 409. */
    public void obrisi(Long id) {
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
            case RANG_LISTA -> {
                if (!iz.isTakmicenje() && !iz.isRangListaPrikazana()) {
                    throw new SystemException(NIJE_TAKMICENJE, HttpStatus.CONFLICT);
                }
                iz.setRangListaPrikazana(!iz.isRangListaPrikazana());
            }
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
        }
    }

    /** → : sledeća stavka, pa otvori/zatvori pitanje, pa sledeći slajd; na kraju ništa. */
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
        if (s.getTip() == TipSlajda.INFO) {
            if (iz.getKorak() < TokIzvodjenja.brojStavki(s)) {
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
        if (s.getTip() == TipSlajda.PITANJE && iz.getFaza() == Faza.OTVORENO) {
            zatvori(iz);
        } else if (s.getTip() == TipSlajda.INFO && iz.getKorak() > 0) {
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
     * Prelazak na slajd {@code j} (-1 prijava, n kraj). Otvoreno pitanje se zatvara; rezultati, tačan odgovor i
     * rang-lista se gase (ekran i QR ostaju). INFO sa postepenim počinje od prve stavke ({@code napred}) ili sa svim
     * otkrivenim (unazad); pitanje sa rundom prikazuje poslednju rundu kao zatvorenu, inače čeka.
     */
    private void idiNa(Izvodjenje iz, List<Slajd> sl, int j, boolean napred) {
        if (iz.getFaza() == Faza.OTVORENO) {
            zatvori(iz);
        }
        iz.setRezultatiPrikazani(false);
        iz.setTacanPrikazan(false);
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
        if (s.getTip() == TipSlajda.INFO) {
            iz.setKorak(napred ? 0 : TokIzvodjenja.brojStavki(s));
        } else {
            postaviFazuPitanja(iz, s);
        }
    }

    private void postaviFazuPitanja(Izvodjenje iz, Slajd s) {
        Optional<PitanjeRunda> poslednja =
                rundaRepository.findFirstByIzvodjenjeIdAndSlajdIdOrderByRedniBrojDesc(iz.getId(), s.getId());
        iz.setFaza(poslednja.isPresent() ? Faza.ZATVORENO : Faza.CEKA);
        iz.setTrenutnaRundaId(poslednja.map(PitanjeRunda::getId).orElse(null));
    }

    private void otvoriZatvori(Izvodjenje iz, List<Slajd> sl) {
        Slajd s = pitanjeIliGreska(iz, sl);
        switch (faza(iz)) {
            case CEKA -> otvori(iz, s);
            case OTVORENO -> zatvori(iz);
            case ZATVORENO -> {
                PitanjeRunda r = trenutnaRunda(iz).orElse(null);
                if (r == null) {
                    otvori(iz, s);
                    return;
                }
                // ponovo otvara istu rundu, bez tajmera; tačan odgovor se ne sme videti dok se odgovara
                r.setZatvoreno(null);
                r.setRok(null);
                r.setPreostaloMs(null);
                iz.setFaza(Faza.OTVORENO);
                iz.setRezultatiPrikazani(false);
                iz.setTacanPrikazan(false);
            }
        }
    }

    /** Nova runda sa snimkom pitanja; tajmer kreće ako pitanje ima ograničenje. */
    private void otvori(Izvodjenje iz, Slajd s) {
        LocalDateTime sada = sada();
        PitanjeRunda r = new PitanjeRunda();
        r.setIzvodjenje(iz);
        r.setSlajdId(s.getId());
        r.setRedniBroj(rundaRepository.findFirstByIzvodjenjeIdAndSlajdIdOrderByRedniBrojDesc(iz.getId(), s.getId())
                .map(p -> p.getRedniBroj() + 1).orElse(1));
        r.setSnimak(jsonMapper.writeValueAsString(PitanjeSnimak.od(s)));
        r.setOtvoreno(sada);
        Integer sekunde = s.getPitanje().getVremeSekunde();
        if (sekunde != null) {
            r.setRok(sada.plusSeconds(sekunde));
            r.setTrajanjeMs(sekunde * 1000L);
        }
        r = rundaRepository.save(r);
        iz.setTrenutnaRundaId(r.getId());
        iz.setFaza(Faza.OTVORENO);
        iz.setRezultatiPrikazani(false);
        iz.setTacanPrikazan(false);
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
            boolean imaRundu = s != null && s.getTip() == TipSlajda.PITANJE && iz.getTrenutnaRundaId() != null
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
        Slajd s = pitanjeIliGreska(iz, sl);
        PitanjeSnimak snimak = trenutnaRunda(iz).map(this::snimak).orElseGet(() -> PitanjeSnimak.od(s));
        if (!snimak.imaTacanOdgovor()) {
            throw new SystemException(NEMA_TACNOG, HttpStatus.CONFLICT);
        }
        switch (faza(iz)) {
            case CEKA -> throw new SystemException(NIJE_POSTAVLJENO, HttpStatus.CONFLICT);
            case OTVORENO -> throw new SystemException(PRVO_ZATVORI, HttpStatus.CONFLICT);
            case ZATVORENO -> iz.setTacanPrikazan(true);
        }
    }

    /** Nova runda istog pitanja (stari odgovori ostaju u prethodnoj); otvorena se prvo zatvori. */
    private void ponovi(Izvodjenje iz, List<Slajd> sl) {
        Slajd s = pitanjeIliGreska(iz, sl);
        Faza f = faza(iz);
        if (f == Faza.CEKA) {
            throw new SystemException(NIJE_POSTAVLJENO, HttpStatus.CONFLICT);
        }
        if (f == Faza.OTVORENO) {
            zatvori(iz);
        }
        otvori(iz, s);
    }

    /** Pauza (pamti preostalo), nastavak, ili 30 s kad pitanje nema ograničenje. */
    private void tajmer(Izvodjenje iz) {
        PitanjeRunda r = otvorenaRunda(iz);
        LocalDateTime sada = sada();
        if (r.getRok() != null) {
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
     * čuvanja se brišu odgovori, runde i učesnici; ostaje samo red izvođenja. Ne menja verziju (to radi pozivalac).
     */
    public void zavrsi(Izvodjenje iz) {
        if (iz.getFaza() == Faza.OTVORENO) {
            zatvori(iz);
        }
        iz.setStatus(StatusIzvodjenja.ZAVRSENO);
        iz.setAktivanKod(null);
        iz.setKraj(sada());
        if (!iz.isCuvanje()) {
            iz.setTrenutnaRundaId(null);
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

    /** Trenutni slajd je možda promenio tip ili broj stavki: faza i korak se usklađuju sa slajdom. */
    private void uskladi(Izvodjenje iz, List<Slajd> sl) {
        if (iz.getPrikaz() != Prikaz.SLAJD) return;
        int i = TokIzvodjenja.pozicija(sl, iz.getTrenutniSlajdId());
        if (i < 0) return;
        Slajd s = sl.get(i);
        if (s.getTip() == TipSlajda.INFO) {
            if (iz.getFaza() == Faza.OTVORENO) {
                zatvori(iz);
            }
            iz.setFaza(null);
            iz.setTrenutnaRundaId(null);
            iz.setRezultatiPrikazani(false);
            iz.setTacanPrikazan(false);
            iz.setKorak(Math.min(iz.getKorak(), TokIzvodjenja.brojStavki(s)));
        } else {
            iz.setKorak(0);
            if (iz.getFaza() == null) {
                postaviFazuPitanja(iz, s);
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

    private Slajd pitanjeIliGreska(Izvodjenje iz, List<Slajd> sl) {
        Slajd s = TokIzvodjenja.trenutni(iz, sl);
        if (s == null || s.getTip() != TipSlajda.PITANJE || s.getPitanje() == null) {
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

    private PitanjeSnimak snimak(PitanjeRunda r) {
        return jsonMapper.readValue(r.getSnimak(), PitanjeSnimak.class);
    }

    private Ucesnik ucesnik(Izvodjenje iz, Long ucesnikId) {
        return ucesnikRepository.findByIdAndIzvodjenjeId(ucesnikId, iz.getId())
                .filter(u -> !u.isIzbacen())
                .orElseThrow(() -> new SystemException(UCESNIK_NIJE_PRONADJEN, HttpStatus.NOT_FOUND));
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
