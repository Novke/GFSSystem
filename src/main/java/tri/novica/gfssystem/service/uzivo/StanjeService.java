package tri.novica.gfssystem.service.uzivo;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import tri.novica.gfssystem.dto.uzivo.*;
import tri.novica.gfssystem.entity.uzivo.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.uzivo.*;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Snimci stanja izvođenja (samo čitanje). Svi podaci izvođenja se učitavaju sa po jednim upitom po tabeli
 * ({@link Podaci}); odgovori izbačenih učesnika se ne računaju nigde (rezultat, broj odgovora, poeni). Poeni i
 * rang-lista se računaju samo iz poslednje runde svakog slajda, i to po rundama (ne po odgovorima): posle PONOVI stara
 * runda prestaje da se računa odmah, i pre prvog novog odgovora.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StanjeService {

    static final int RANG_NASTAVNIK = 10;
    static final int RANG_JAVNO = 5;

    private final IzvodjenjeRepository izvodjenjeRepository;
    private final SlajdRepository slajdRepository;
    private final PitanjeRundaRepository rundaRepository;
    private final OdgovorRepository odgovorRepository;
    private final UcesnikRepository ucesnikRepository;
    private final PovezanostRegistar povezanost;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    /** Sve što stanje izvođenja traži, učitano jednom. */
    record Podaci(Izvodjenje iz, List<Slajd> slajdovi, List<PitanjeRunda> runde, List<Ucesnik> ucesnici,
                  List<Odgovor> odgovori) {
        /** Odgovori trenutne runde (po vremenu prijema). */
        List<Odgovor> odgovoriRunde(Long rundaId) {
            if (rundaId == null) return List.of();
            return odgovori.stream().filter(o -> rundaId.equals(o.getRunda().getId())).toList();
        }

        Optional<PitanjeRunda> runda(Long rundaId) {
            return rundaId == null ? Optional.empty()
                    : runde.stream().filter(r -> rundaId.equals(r.getId())).findFirst();
        }
    }

    // ---------------------------------------------------------------- nastavničko stanje

    public NastavnickoStanje nastavnicko(Long izvodjenjeId) {
        Podaci p = ucitaj(izvodjenjeId);
        Izvodjenje iz = p.iz();
        List<Slajd> sl = p.slajdovi();
        int indeks = TokIzvodjenja.indeks(iz, sl);
        Slajd trenutni = TokIzvodjenja.trenutni(iz, sl);
        Slajd sledeci = indeks + 1 >= 0 && indeks + 1 < sl.size() ? sl.get(indeks + 1) : null;

        PitanjeRunda runda = p.runda(iz.getTrenutnaRundaId()).orElse(null);
        List<Odgovor> odgovoriRunde = runda == null ? List.of() : p.odgovoriRunde(runda.getId());
        Rezultat rezultat = runda == null ? null : RezultatBuilder.izgradi(snimak(runda), odgovoriRunde, false, true);

        List<Odgovor> bodovni = bodovniOdgovori(p);
        Map<Long, Integer> poeni = RangLista.poeniPoUcesniku(bodovni);
        Set<Long> odgovorili = new HashSet<>();
        odgovoriRunde.forEach(o -> odgovorili.add(o.getUcesnik().getId()));
        List<UcesnikStanje> ucesnici = p.ucesnici().stream()
                .map(u -> new UcesnikStanje(u.getId(), u.getIme(), poeni.getOrDefault(u.getId(), 0),
                        povezanost.jePovezan(u.getId()), odgovorili.contains(u.getId())))
                .toList();
        List<RangStavka> rang = RangLista.izracunaj(p.ucesnici(), bodovni);

        return new NastavnickoStanje(info(p), iz.getVerzija(), clock.millis(), iz.getPrikaz(), iz.getKorak(),
                TokIzvodjenja.brojStavki(trenutni), indeks, sl.size(),
                trenutni == null ? null : SlajdMapper.uDetails(trenutni),
                sledeci == null ? null : SlajdMapper.uDetails(sledeci),
                iz.getFaza(), runda == null ? null : rundaInfo(iz, runda),
                iz.isRezultatiPrikazani(), iz.isTacanPrikazan(), iz.isRangListaPrikazana(), iz.getEkran(),
                iz.isQrPrikazan(), iz.getTelefonPrikaz(), iz.isDetaljiDozvoljeni(), iz.isTakmicenje(),
                rezultat, odgovoriRunde.size(),
                povezanost.brojPovezanih(p.ucesnici().stream().map(Ucesnik::getId).toList()),
                ucesnici, rang.subList(0, Math.min(RANG_NASTAVNIK, rang.size())));
    }

    // ---------------------------------------------------------------- javno i lično stanje (telefoni)

    /**
     * Javno stanje (spec 4.4), jezgro "ni ranije": pre otvaranja pitanja samo {tip, faza}; tekstovi tek kad su
     * dozvoljeni, tačan odgovor tek kad je prikazan na zatvorenom pitanju, rezultat tek kad je prikazan, rang-lista
     * (top 5, bez id-jeva) tek kad je prikazana ili na kraju takmičenja. Izbačeni se ne računaju nigde.
     */
    public JavnoStanje javno(Long izvodjenjeId) {
        return javno(ucitaj(izvodjenjeId));
    }

    /** Lično stanje jednog učesnika; izbačeni dobija {@code izbacen = true} (bez poena i odgovora), nepoznat 404. */
    public LicnoStanje licno(Long izvodjenjeId, Long ucesnikId) {
        Podaci p = ucitaj(izvodjenjeId);
        return licno(p, ucesnikId, licniPodaci(p));
    }

    /** Lična stanja svih neizbačenih učesnika (po redu prijave) iz jednog čitanja; rang se računa jednom. */
    public Map<Long, LicnoStanje> licnaZaSve(Long izvodjenjeId) {
        Podaci p = ucitaj(izvodjenjeId);
        LicniPodaci lp = licniPodaci(p);
        Map<Long, LicnoStanje> sva = new LinkedHashMap<>();
        for (Ucesnik u : p.ucesnici()) {
            sva.put(u.getId(), licno(p.iz(), u, lp, p.ucesnici().size()));
        }
        return sva;
    }

    /** Prvi snimak posle (ponovnog) povezivanja: javno i lično stanje iz istog čitanja. */
    public PocetnoStanje pocetno(Long izvodjenjeId, Long ucesnikId) {
        Podaci p = ucitaj(izvodjenjeId);
        return new PocetnoStanje(javno(p), licno(p, ucesnikId, licniPodaci(p)));
    }

    JavnoStanje javno(Podaci p) {
        Izvodjenje iz = p.iz();
        Slajd trenutni = TokIzvodjenja.trenutni(iz, p.slajdovi());
        JavnoPitanje pitanje = null;
        Rezultat rezultat = null;
        if (trenutni != null && trenutni.getTip() == TipSlajda.PITANJE) {
            Faza faza = iz.getFaza() == null ? Faza.CEKA : iz.getFaza();
            PitanjeRunda r = javnaRunda(p, trenutni);
            if (r == null) {
                // pre otvaranja (ili bez runde) ništa osim tipa i faze
                pitanje = trenutni.getPitanje() == null ? null
                        : JavnoPitanje.samoTip(trenutni.getPitanje().getTip(), faza);
            } else {
                PitanjeSnimak s = snimak(r);
                boolean tacan = tacanVidljiv(iz);
                pitanje = javnoPitanje(iz, r, s, tacan);
                if (iz.isRezultatiPrikazani()) {
                    rezultat = javniRezultat(s, p.odgovoriRunde(r.getId()), tacan);
                }
            }
        }
        List<RangStavka> rang = null;
        if (iz.isRangListaPrikazana() || (iz.getPrikaz() == Prikaz.KRAJ && iz.isTakmicenje())) {
            rang = RangLista.izracunaj(p.ucesnici(), bodovniOdgovori(p)).stream()
                    .limit(RANG_JAVNO)
                    .map(st -> new RangStavka(st.mesto(), null, st.ime(), st.poeni()))
                    .toList();
        }
        return new JavnoStanje(iz.getId(), iz.getVerzija(), clock.millis(), iz.getStatus(),
                iz.getPrezentacija().getNaziv(), iz.getKod(), iz.getPrikaz(),
                trenutni == null ? null : trenutni.getTip(), iz.getEkran(), iz.isTakmicenje(), iz.getTelefonPrikaz(),
                iz.isDetaljiDozvoljeni(), p.ucesnici().size(), pitanje, rezultat, rang);
    }

    /**
     * Runda koju telefoni vide: trenutna runda trenutnog slajda-pitanja, samo u fazi OTVORENO ili ZATVORENO (u CEKA
     * nikad, ni ako bi id runde zaostao) i samo ako pripada baš tom slajdu.
     */
    private PitanjeRunda javnaRunda(Podaci p, Slajd trenutni) {
        Izvodjenje iz = p.iz();
        if (trenutni == null || trenutni.getTip() != TipSlajda.PITANJE) return null;
        if (iz.getFaza() != Faza.OTVORENO && iz.getFaza() != Faza.ZATVORENO) return null;
        return p.runda(iz.getTrenutnaRundaId())
                .filter(r -> Objects.equals(r.getSlajdId(), trenutni.getId()))
                .orElse(null);
    }

    /** "Tekst dozvoljen" (spec 4.4): telefon prikazuje celo pitanje ili su Detalji dozvoljeni. */
    private static boolean tekstDozvoljen(Izvodjenje iz) {
        return iz.getTelefonPrikaz() == TelefonPrikaz.PITANJE || iz.isDetaljiDozvoljeni();
    }

    /** Tačan odgovor se vidi samo kad je prikazan i pitanje je zatvoreno (nikad dok se odgovara). */
    private static boolean tacanVidljiv(Izvodjenje iz) {
        return iz.isTacanPrikazan() && iz.getFaza() == Faza.ZATVORENO;
    }

    private static boolean saOpcijama(TipPitanja tip) {
        return switch (tip) {
            case JEDAN_TACAN, VISE_TACNIH, ANKETA, TACNO_NETACNO -> true;
            case KRATAK_TEKST, BROJ, SKALA -> false;
        };
    }

    private JavnoPitanje javnoPitanje(Izvodjenje iz, PitanjeRunda r, PitanjeSnimak s, boolean tacan) {
        boolean tekst = tekstDozvoljen(iz);
        List<OpcijaSnimak> sortirane = s.opcije() == null ? List.of()
                : s.opcije().stream().sorted(Comparator.comparingInt(OpcijaSnimak::rb)).toList();
        boolean opcijski = saOpcijama(s.tip());
        List<JavnaOpcija> opcije = !opcijski ? null
                : sortirane.stream().map(o -> new JavnaOpcija(o.id(), tekst ? o.tekst() : null)).toList();
        List<Long> tacneOpcije = tacan && opcijski && s.imaTacanOdgovor()
                ? sortirane.stream().filter(OpcijaSnimak::tacna).map(OpcijaSnimak::id).toList() : null;
        Double tacanBroj = tacan && s.tip() == TipPitanja.BROJ ? s.brojTacno() : null;
        List<String> prihvatljivi = tacan && s.tip() == TipPitanja.KRATAK_TEKST && s.imaTacanOdgovor()
                ? List.copyOf(s.prihvatljiviOdgovori()) : null;
        RundaInfo ri = rundaInfo(iz, r);
        return new JavnoPitanje(s.tip(), iz.getFaza(), r.getId(), opcije == null ? null : opcije.size(), opcije,
                tekst ? s.tekst() : null, tekst ? s.slikaId() : null, tekst ? s.jedinica() : null,
                tekst ? s.skalaMinOznaka() : null, tekst ? s.skalaMaxOznaka() : null,
                ri.rokMs(), ri.preostaloMs(), tacneOpcije, tacanBroj, prihvatljivi);
    }

    /**
     * Javni rezultat: bez sakrivenih tekstova, tačnost tek posle TACAN. Broj "u odstupanju" govori koliko je tačnih,
     * pa i on čeka TACAN.
     */
    private static Rezultat javniRezultat(PitanjeSnimak s, List<Odgovor> odgovori, boolean tacan) {
        Rezultat r = RezultatBuilder.izgradi(s, odgovori, true, tacan);
        if (!tacan && r.brojevi() != null && r.brojevi().uOdstupanju() != null) {
            RezultatBrojevi b = r.brojevi();
            return new Rezultat(r.tip(), r.ukupno(), r.opcije(), new RezultatBrojevi(b.medijana(), null, b.najcesce()),
                    r.tekstovi(), r.skala());
        }
        return r;
    }

    /** Ono što lično stanje traži od celog izvođenja, izračunato jednom (i za {@link #licnaZaSve}). */
    private record LicniPodaci(Map<Long, Integer> poeni, Map<Long, Integer> mesto, Long rundaId,
                               Map<Long, Odgovor> odgovorRunde, boolean tacan) {
    }

    private LicniPodaci licniPodaci(Podaci p) {
        Izvodjenje iz = p.iz();
        List<Odgovor> bodovni = bodovniOdgovori(p);
        Map<Long, Integer> mesto = new HashMap<>();
        if (iz.isTakmicenje()) {
            RangLista.izracunaj(p.ucesnici(), bodovni).forEach(st -> mesto.put(st.ucesnikId(), st.mesto()));
        }
        PitanjeRunda r = javnaRunda(p, TokIzvodjenja.trenutni(iz, p.slajdovi()));
        Map<Long, Odgovor> odgovorRunde = new HashMap<>();
        if (r != null) {
            p.odgovoriRunde(r.getId()).forEach(o -> odgovorRunde.putIfAbsent(o.getUcesnik().getId(), o));
        }
        return new LicniPodaci(RangLista.poeniPoUcesniku(bodovni), mesto, r == null ? null : r.getId(), odgovorRunde,
                tacanVidljiv(iz));
    }

    private LicnoStanje licno(Podaci p, Long ucesnikId, LicniPodaci lp) {
        Izvodjenje iz = p.iz();
        int brojUcesnika = p.ucesnici().size();
        Optional<Ucesnik> aktivan = p.ucesnici().stream().filter(u -> u.getId().equals(ucesnikId)).findFirst();
        if (aktivan.isPresent()) {
            return licno(iz, aktivan.get(), lp, brojUcesnika);
        }
        Ucesnik izbacen = ucesnikRepository.findByIdAndIzvodjenjeId(ucesnikId, iz.getId())
                .filter(Ucesnik::isIzbacen)
                .orElseThrow(() -> new SystemException(IzvodjenjeService.UCESNIK_NIJE_PRONADJEN, HttpStatus.NOT_FOUND));
        return new LicnoStanje(iz.getVerzija(), izbacen.getId(), izbacen.getIme(), 0, null, brojUcesnika, true, null);
    }

    private static LicnoStanje licno(Izvodjenje iz, Ucesnik u, LicniPodaci lp, int brojUcesnika) {
        LicniOdgovor odgovor = null;
        if (lp.rundaId() != null) {
            Odgovor o = lp.odgovorRunde().get(u.getId());
            boolean prikazi = o != null && lp.tacan();
            odgovor = new LicniOdgovor(lp.rundaId(), o != null, prikazi ? o.getTacno() : null,
                    prikazi ? o.getPoeni() : null);
        }
        return new LicnoStanje(iz.getVerzija(), u.getId(), u.getIme(), lp.poeni().getOrDefault(u.getId(), 0),
                iz.isTakmicenje() ? lp.mesto().get(u.getId()) : null, brojUcesnika, false, odgovor);
    }

    // ---------------------------------------------------------------- lista i rezultati

    /** Izvođenja (filteri opcioni), najnovija prva; brojevi učesnika i pitanja sa po jednim upitom za celu listu. */
    public List<IzvodjenjeInfo> lista(Long prezentacijaId, StatusIzvodjenja status) {
        List<Izvodjenje> lista = izvodjenjeRepository.findZaListu(prezentacijaId, status);
        if (lista.isEmpty()) return List.of();
        List<Long> ids = lista.stream().map(Izvodjenje::getId).toList();
        Map<Long, Long> ucesnika = brojevi(ucesnikRepository.brojPoIzvodjenju(ids));
        Map<Long, Long> pitanja = brojevi(rundaRepository.brojPoIzvodjenju(ids));
        return lista.stream()
                .map(i -> IzvodjenjeMapper.info(i, ucesnika.getOrDefault(i.getId(), 0L), pitanja.getOrDefault(i.getId(), 0L)))
                .toList();
    }

    /** Pregled: svaka runda po redu otvaranja sa snimkom i nastavničkim rezultatom, i cela rang-lista. */
    public IzvodjenjeRezultati rezultati(Long izvodjenjeId) {
        Podaci p = ucitaj(izvodjenjeId);
        Map<Long, Integer> rbSlajda = new HashMap<>();
        p.slajdovi().forEach(s -> rbSlajda.put(s.getId(), s.getRb()));
        Map<Long, List<Odgovor>> poRundi = new HashMap<>();
        p.odgovori().forEach(o -> poRundi.computeIfAbsent(o.getRunda().getId(), k -> new ArrayList<>()).add(o));

        List<RezultatPitanja> pitanja = new ArrayList<>();
        for (PitanjeRunda r : p.runde()) {
            PitanjeSnimak snimak = snimak(r);
            List<Odgovor> odgovori = poRundi.getOrDefault(r.getId(), List.of());
            Integer procenat = null;
            if (snimak.imaTacanOdgovor() && !odgovori.isEmpty()) {
                long tacnih = odgovori.stream().filter(o -> Boolean.TRUE.equals(o.getTacno())).count();
                procenat = (int) Math.round(100.0 * tacnih / odgovori.size());
            }
            pitanja.add(new RezultatPitanja(r.getId(), r.getSlajdId(),
                    r.getSlajdId() == null ? null : rbSlajda.get(r.getSlajdId()), r.getRedniBroj(), snimak,
                    RezultatBuilder.izgradi(snimak, odgovori, false, true), odgovori.size(), procenat));
        }
        return new IzvodjenjeRezultati(info(p), pitanja, RangLista.izracunaj(p.ucesnici(), bodovniOdgovori(p)));
    }

    // ---------------------------------------------------------------- zajedničko (i za javno/lično stanje)

    /** Izvođenje (404), slajdovi, runde, neizbačeni učesnici i njihovi odgovori. */
    Podaci ucitaj(Long izvodjenjeId) {
        Izvodjenje iz = izvodjenjeRepository.findSaVezama(izvodjenjeId)
                .orElseThrow(() -> new SystemException(IzvodjenjeService.NIJE_PRONADJENO, HttpStatus.NOT_FOUND));
        List<Slajd> slajdovi = slajdRepository.findAllByPrezentacijaIdOrderByRbAsc(iz.getPrezentacija().getId());
        List<PitanjeRunda> runde = rundaRepository.findAllByIzvodjenjeIdOrderByOtvorenoAscIdAsc(izvodjenjeId);
        List<Ucesnik> ucesnici = ucesnikRepository.findAllByIzvodjenjeIdOrderByKreiranoAsc(izvodjenjeId).stream()
                .filter(u -> !u.isIzbacen()).toList();
        List<Odgovor> odgovori = odgovorRepository.findAllByRundaIzvodjenjeId(izvodjenjeId).stream()
                .filter(o -> !o.getUcesnik().isIzbacen()).toList();
        return new Podaci(iz, slajdovi, runde, ucesnici, odgovori);
    }

    /** Odgovori koji nose poene: samo iz poslednje runde svakog slajda (po rundama izvođenja, ne po odgovorima). */
    List<Odgovor> bodovniOdgovori(Podaci p) {
        Map<Object, PitanjeRunda> poslednja = new HashMap<>();
        for (PitanjeRunda r : p.runde()) {
            poslednja.merge(kljucSlajda(r), r, (a, b) -> b.getRedniBroj() > a.getRedniBroj() ? b : a);
        }
        Set<Long> vazece = new HashSet<>();
        poslednja.values().forEach(r -> vazece.add(r.getId()));
        return p.odgovori().stream().filter(o -> vazece.contains(o.getRunda().getId())).toList();
    }

    /** Slajd runde; posle brisanja slajda ({@code slajdId = null}) slajd iz snimka, pa runde istog slajda ostaju zajedno. */
    private Object kljucSlajda(PitanjeRunda r) {
        if (r.getSlajdId() != null) return r.getSlajdId();
        Long izSnimka = snimak(r).slajdId();
        return izSnimka != null ? izSnimka : "runda-" + r.getId();
    }

    PitanjeSnimak snimak(PitanjeRunda r) {
        return jsonMapper.readValue(r.getSnimak(), PitanjeSnimak.class);
    }

    IzvodjenjeInfo info(Podaci p) {
        return IzvodjenjeMapper.info(p.iz(), p.ucesnici().size(), p.runde().size());
    }

    /** Rok i preostalo vreme samo dok je runda otvorena; rok u epoch ms (ne zavisi od vremenske zone klijenta). */
    RundaInfo rundaInfo(Izvodjenje iz, PitanjeRunda r) {
        boolean otvorena = iz.getFaza() == Faza.OTVORENO && r.getZatvoreno() == null;
        Long rokMs = otvorena && r.getRok() != null ? epochMs(r.getRok()) : null;
        Long preostaloMs = otvorena ? r.getPreostaloMs() : null;
        return new RundaInfo(r.getId(), r.getRedniBroj(), rokMs, preostaloMs, rokMs != null);
    }

    long epochMs(LocalDateTime t) {
        return t.atZone(clock.getZone()).toInstant().toEpochMilli();
    }

    private static Map<Long, Long> brojevi(List<Object[]> redovi) {
        Map<Long, Long> m = new HashMap<>();
        for (Object[] red : redovi) {
            m.put(((Number) red[0]).longValue(), ((Number) red[1]).longValue());
        }
        return m;
    }
}
