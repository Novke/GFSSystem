package tri.novica.gfssystem.service.uzivo;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import tri.novica.gfssystem.dto.uzivo.*;
import tri.novica.gfssystem.entity.uzivo.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.uzivo.*;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

/**
 * Snimci stanja izvođenja (samo čitanje). Svi podaci izvođenja se učitavaju sa po jednim upitom po tabeli
 * ({@link Podaci}); odgovori izbačenih učesnika se ne računaju nigde (rezultat, broj odgovora, poeni). Poeni i
 * rang-lista se računaju samo iz poslednje runde svakog slajda, i to po rundama (ne po odgovorima): posle PONOVI stara
 * runda prestaje da se računa odmah, i pre prvog novog odgovora.
 *
 * <p><b>Zadatak</b> ("ni ranije", spec 11.7): telefoni ({@link JavnoStanje}, {@link LicnoStanje}, {@link PocetnoStanje})
 * nikad ne dobijaju tuđu varijantu (SVG, odgovori), id SVG-a rešenja pre RESENJE na zatvorenoj rundi ni tačne vrednosti
 * pre TACAN na zatvorenoj rundi; javno stanje nema nijedan sadržaj varijante ({@link JavniZadatak}). Projektor
 * ({@code NastavnickiZadatak.javniRezultat}) vidi slike svih izvučenih varijanti, a brojeve, tačnost i rešenje po tri
 * nezavisna prekidača ({@link ZadatakRezultatBuilder}). Runda čiji snimak ne odgovara tipu slajda
 * ({@link TokIzvodjenja#odgovaraSlajdu}) gradi se kao da runde nema.
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
    private final ZadatakDodelaRepository dodelaRepository;
    private final PovezanostRegistar povezanost;
    private final JsonMapper jsonMapper;
    private final PlatnoJson platnoJson;
    private final ZadaciSlajdova zadaci;
    private final Clock clock;

    /**
     * Sve što stanje izvođenja traži, učitano jednom. {@code zadaci}: opisi Zadatak slajdova (po id-ju slajda) među
     * trenutnim i sledećim slajdom; prazna mapa kad nijedan od njih nije ZADATAK. Računa ih {@link #ucitaj} jednom, pa
     * jedan {@link #snimci} košta najviše jedno čitanje stabla banke, a ne po jedno za svaki snimak.
     * {@code dodeleRunde}: učesnik -> dodeljena varijanta u trenutnoj rundi ZADATAK slajda (samo neizbačeni), prazna van nje.
     */
    record Podaci(Izvodjenje iz, List<Slajd> slajdovi, List<PitanjeRunda> runde, List<Ucesnik> ucesnici,
                  List<Odgovor> odgovori, Map<Long, ZadatakSlajdaInfo> zadaci, Map<Long, Long> dodeleRunde) {
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
        return nastavnicko(ucitaj(izvodjenjeId));
    }

    /**
     * Sva tri snimka (javno, lična stanja neizbačenih učesnika, nastavničko) iz jednog čitanja: objavljivanje posle
     * promene i periodično slanje zaprljanih stanja ne čitaju izvođenje tri puta.
     */
    public record Snimci(JavnoStanje javno, Map<Long, LicnoStanje> licna, NastavnickoStanje nastavnicko) {
    }

    public Snimci snimci(Long izvodjenjeId) {
        Podaci p = ucitaj(izvodjenjeId);
        return new Snimci(javno(p), licnaZaSve(p), nastavnicko(p));
    }

    NastavnickoStanje nastavnicko(Podaci p) {
        Izvodjenje iz = p.iz();
        List<Slajd> sl = p.slajdovi();
        int indeks = TokIzvodjenja.indeks(iz, sl);
        Slajd trenutni = TokIzvodjenja.trenutni(iz, sl);
        Slajd sledeci = sledeci(indeks, sl);

        PitanjeRunda runda = p.runda(iz.getTrenutnaRundaId()).orElse(null);
        List<Odgovor> odgovoriRunde = runda == null ? List.of() : p.odgovoriRunde(runda.getId());
        Rezultat rezultat = runda == null ? null : rezultat(snimak(runda), odgovoriRunde, false, true);

        List<Odgovor> bodovni = bodovniOdgovori(p);
        Map<Long, Integer> poeni = RangLista.poeniPoUcesniku(bodovni);
        Set<Long> odgovorili = new HashSet<>();
        odgovoriRunde.forEach(o -> odgovorili.add(o.getUcesnik().getId()));
        ZadatakRunda zr = zadatakRunda(p, trenutni, false);
        Map<Long, Odgovor> odgovorUcesnika = new HashMap<>();
        odgovoriRunde.forEach(o -> odgovorUcesnika.putIfAbsent(o.getUcesnik().getId(), o));
        List<UcesnikStanje> ucesnici = p.ucesnici().stream()
                .map(u -> {
                    Odgovor o = zr == null ? null : odgovorUcesnika.get(u.getId());
                    return new UcesnikStanje(u.getId(), u.getIme(), poeni.getOrDefault(u.getId(), 0),
                            povezanost.jePovezan(u.getId()), odgovorili.contains(u.getId()),
                            zr == null ? null : p.dodeleRunde().get(u.getId()), o == null ? null : o.getTacno());
                })
                .toList();
        List<RangStavka> rang = RangLista.izracunaj(p.ucesnici(), bodovni);

        return new NastavnickoStanje(info(p), iz.getVerzija(), clock.millis(), iz.getPrikaz(), iz.getKorak(),
                TokIzvodjenja.brojKoraka(trenutni), indeks, sl.size(),
                trenutni == null ? null
                        : SlajdMapper.uDetailsZaIzvodjenje(trenutni, platnoJson, p.zadaci().get(trenutni.getId())),
                sledeci == null ? null
                        : SlajdMapper.uDetailsZaIzvodjenje(sledeci, platnoJson, p.zadaci().get(sledeci.getId())),
                iz.getFaza(), runda == null ? null : rundaInfo(iz, runda),
                iz.isRezultatiPrikazani(), iz.isTacanPrikazan(), iz.isRangListaPrikazana(), iz.getEkran(),
                iz.isQrPrikazan(), iz.getTelefonPrikaz(), iz.isDetaljiDozvoljeni(), iz.isTakmicenje(),
                rezultat, odgovoriRunde.size(),
                povezanost.brojPovezanih(p.ucesnici().stream().map(Ucesnik::getId).toList()),
                ucesnici, rang.subList(0, Math.min(RANG_NASTAVNIK, rang.size())),
                javniRezultat(p), javnaRangLista(p), iz.getPrezentacija().getTema(),
                iz.getPocetak().atZone(clock.getZone()).toInstant().toEpochMilli(), nastavnickiZadatak(p, trenutni, zr));
    }

    // ---------------------------------------------------------------- zadatak

    /** Trenutna runda ZADATAK slajda sa snimkom koji mu odgovara. */
    private record ZadatakRunda(PitanjeRunda runda, ZadatakSnimak snimak) {
    }

    /**
     * Trenutna runda trenutnog ZADATAK slajda, samo ako pripada baš tom slajdu i snimak joj je {@link ZadatakSnimak};
     * {@code zaTelefon}: još i samo u fazi OTVORENO ili ZATVORENO (kao {@link #javnaRunda}). Inače {@code null}.
     */
    private ZadatakRunda zadatakRunda(Podaci p, Slajd trenutni, boolean zaTelefon) {
        Izvodjenje iz = p.iz();
        if (trenutni == null || trenutni.getTip() != TipSlajda.ZADATAK) return null;
        if (zaTelefon && iz.getFaza() != Faza.OTVORENO && iz.getFaza() != Faza.ZATVORENO) return null;
        PitanjeRunda r = p.runda(iz.getTrenutnaRundaId())
                .filter(x -> Objects.equals(x.getSlajdId(), trenutni.getId()))
                .orElse(null);
        if (r == null) return null;
        SnimakRunde sn = snimak(r);
        return TokIzvodjenja.odgovaraSlajdu(sn, trenutni) ? new ZadatakRunda(r, (ZadatakSnimak) sn) : null;
    }

    /** Rešenje se vidi samo kad je uključeno i runda je zatvorena (kao TACAN). */
    private static boolean resenjeVidljivo(Izvodjenje iz) {
        return iz.isResenjePrikazano() && iz.getFaza() == Faza.ZATVORENO;
    }

    /**
     * Koliko će se varijanti izvući: {@code min(N, M)}, N iz konzole ili sa slajda ({@code null} = sve), M = kandidati
     * važećeg nivoa (iz konzole ili sa slajda, {@link TokIzvodjenja#nivo}).
     */
    private static int brojZaOtvaranje(Izvodjenje iz, Slajd s, ZadatakSlajdaInfo info) {
        int m = info == null ? 0 : info.brojAktivnih(TokIzvodjenja.nivo(iz, s));
        Integer n = TokIzvodjenja.brojVarijanti(iz, s);
        return n == null ? m : Math.min(n, m);
    }

    /** Odgovori na zadatak u rundi (samo neizbačeni), sa poljima iz {@code odgovori.polja}. */
    private List<ZadatakRezultatBuilder.OdgovorZadatka> odgovoriZadatka(List<Odgovor> odgovori) {
        return odgovori.stream()
                .filter(o -> o.getVarijantaId() != null)
                .map(o -> new ZadatakRezultatBuilder.OdgovorZadatka(o.getUcesnik().getId(), o.getVarijantaId(),
                        polja(o), o.getTacno()))
                .toList();
    }

    private static final TypeReference<LinkedHashMap<String, PoljeOdgovora>> POLJA = new TypeReference<>() {};

    private Map<String, PoljeOdgovora> polja(Odgovor o) {
        return o.getPolja() == null ? Map.of() : jsonMapper.readValue(o.getPolja(), POLJA);
    }

    private static Map<String, Boolean> ishod(Map<String, PoljeOdgovora> polja) {
        Map<String, Boolean> m = new LinkedHashMap<>();
        polja.forEach((k, v) -> m.put(k, v.tacno()));
        return m;
    }

    private NastavnickiZadatak nastavnickiZadatak(Podaci p, Slajd trenutni, ZadatakRunda zr) {
        if (trenutni == null || trenutni.getTip() != TipSlajda.ZADATAK) return null;
        Izvodjenje iz = p.iz();
        ZadatakSlajdaInfo info = p.zadaci().get(trenutni.getId());
        List<VarijantaRunde> varijante = null;
        ZadatakRezultat rezultat = null;
        ZadatakRezultat javni = null;
        if (zr != null) {
            List<ZadatakRezultatBuilder.OdgovorZadatka> odgovori = odgovoriZadatka(p.odgovoriRunde(zr.runda().getId()));
            varijante = ZadatakRezultatBuilder.varijanteRunde(zr.snimak(), p.dodeleRunde(), odgovori);
            rezultat = ZadatakRezultatBuilder.izgradi(zr.snimak(), p.dodeleRunde(), odgovori, true, true, true);
            boolean rez = iz.isRezultatiPrikazani();
            boolean tacan = tacanVidljiv(iz);
            boolean resenje = resenjeVidljivo(iz);
            if (zadatakRunda(p, trenutni, true) != null && (rez || tacan || resenje)) {
                javni = ZadatakRezultatBuilder.izgradi(zr.snimak(), p.dodeleRunde(), odgovori, rez, tacan, resenje);
            }
        }
        return new NastavnickiZadatak(info, iz.getZadatakBrojVarijanti(), iz.getZadatakNivo(),
                TokIzvodjenja.nivo(iz, trenutni), brojZaOtvaranje(iz, trenutni, info),
                iz.isResenjePrikazano(), varijante, rezultat, javni);
    }

    /** Javni zadatak (telefoni): faza, runda, tajmer, broj varijanti i naziv grupe, bez sadržaja varijanti. */
    private JavniZadatak javniZadatak(Podaci p, Slajd trenutni) {
        if (trenutni == null || trenutni.getTip() != TipSlajda.ZADATAK) return null;
        Izvodjenje iz = p.iz();
        Faza faza = iz.getFaza() == null ? Faza.CEKA : iz.getFaza();
        ZadatakSlajdaInfo info = p.zadaci().get(trenutni.getId());
        ZadatakRunda zr = zadatakRunda(p, trenutni, true);
        if (zr == null) {
            return new JavniZadatak(faza, null, null, null, brojZaOtvaranje(iz, trenutni, info),
                    info == null ? null : info.grupaNaziv(), false);
        }
        RundaInfo ri = rundaInfo(iz, zr.runda());
        int izvuceno = zr.snimak().varijante() == null ? 0 : zr.snimak().varijante().size();
        return new JavniZadatak(faza, zr.runda().getId(), ri.rokMs(), ri.preostaloMs(), izvuceno,
                zr.snimak().grupaNaziv(), resenjeVidljivo(iz));
    }

    /**
     * Lični zadatak: samo dodeljena varijanta učesnika, od otvaranja (SVG i polja); ishod u ZATVORENO kad je odgovorio,
     * tačne vrednosti uz vidljiv TACAN, rešenje uz vidljiv RESENJE. {@code null} u CEKA, bez runde i bez dodele.
     */
    private LicniZadatak licniZadatak(Izvodjenje iz, Ucesnik u, LicniPodaci lp) {
        ZadatakRunda zr = lp.zadatak();
        if (zr == null) return null;
        Long varijantaId = lp.dodele().get(u.getId());
        VarijantaSnimak v = zr.snimak().varijanta(varijantaId).orElse(null);
        if (v == null) return null;
        Odgovor o = lp.odgovorRunde().get(u.getId());
        boolean zatvoreno = iz.getFaza() == Faza.ZATVORENO;
        Map<String, Boolean> ishod = zatvoreno && o != null ? ishod(polja(o)) : null;
        return new LicniZadatak(zr.runda().getId(), v.varijantaId(), v.svgId(), v.polja() == null ? List.of() : v.polja(),
                iz.getFaza(), o != null, ishod, zatvoreno && o != null ? o.getTacno() : null,
                lp.tacan() ? v.odgovori() : null, resenjeVidljivo(iz) ? v.resenjeSvgId() : null);
    }

    /** Slajd posle onog na položaju {@code indeks} (-1 = prijava daje prvi), {@code null} na kraju. */
    private static Slajd sledeci(int indeks, List<Slajd> slajdovi) {
        return indeks + 1 >= 0 && indeks + 1 < slajdovi.size() ? slajdovi.get(indeks + 1) : null;
    }

    // ---------------------------------------------------------------- javno i lično stanje (telefoni)

    /**
     * Javno stanje (spec 4.4), jezgro "ni ranije": pre otvaranja pitanja samo {tip, faza}; tekstovi tek kad su
     * dozvoljeni, tačan odgovor tek kad je prikazan na zatvorenom pitanju, rezultat tek kad je prikazan
     * ({@link #javniRezultat(Podaci)}), rang-lista tek kad je prikazana ili na kraju takmičenja
     * ({@link #javnaRangLista}). Izbačeni se ne računaju nigde. Rezultat i rang-lista projektora (u nastavničkom
     * stanju) grade se istim metodama.
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
        return licnaZaSve(ucitaj(izvodjenjeId));
    }

    private Map<Long, LicnoStanje> licnaZaSve(Podaci p) {
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
        if (trenutni != null && trenutni.getTip() == TipSlajda.PITANJE) {
            Faza faza = iz.getFaza() == null ? Faza.CEKA : iz.getFaza();
            PitanjeRunda r = javnaRunda(p, trenutni);
            if (r == null) {
                // pre otvaranja (ili bez runde) ništa osim tipa i faze
                pitanje = trenutni.getPitanje() == null ? null
                        : JavnoPitanje.samoTip(trenutni.getPitanje().getTip(), faza);
            } else if (snimak(r) instanceof PitanjeSnimak s) {
                // privremeno: javno pitanje samo iz PITANJE snimka (ZADATAK runda ga nema)
                pitanje = javnoPitanje(iz, r, s, tacanVidljiv(iz));
            }
        }
        return new JavnoStanje(iz.getId(), iz.getVerzija(), clock.millis(), iz.getStatus(),
                iz.getPrezentacija().getNaziv(), iz.getKod(), iz.getPrikaz(),
                trenutni == null ? null : trenutni.getTip(), iz.getEkran(), iz.isTakmicenje(), iz.getTelefonPrikaz(),
                iz.isDetaljiDozvoljeni(), p.ucesnici().size(), pitanje, rezultatZaTelefon(iz, javniRezultat(p)),
                javnaRangLista(p), javniZadatak(p, trenutni));
    }

    /**
     * Rezultat koji ide telefonima: javni rezultat, a dok tekst nije dozvoljen (DUGMAD bez Detalja) bez tekstova opcija,
     * jer su to tekst pitanja (spec, kriterijum 2). Projektor ({@code NastavnickoStanje.javniRezultat}) ih zadržava: sala
     * ih ionako vidi na platnu.
     */
    private static Rezultat rezultatZaTelefon(Izvodjenje iz, Rezultat javni) {
        return javni == null || tekstDozvoljen(iz) ? javni : bezTekstovaOpcija(javni);
    }

    /** Isti rezultat, sa {@code tekst = null} u svakoj opciji (brojevi, tačnost i ostalo ostaju). */
    static Rezultat bezTekstovaOpcija(Rezultat r) {
        if (r.opcije() == null) return r;
        List<RezultatOpcija> opcije = r.opcije().stream()
                .map(o -> new RezultatOpcija(o.id(), null, o.broj(), o.tacna()))
                .toList();
        return new Rezultat(r.tip(), r.ukupno(), opcije, r.brojevi(), r.tekstovi(), r.skala());
    }

    /**
     * Javni rezultat trenutnog pitanja ({@code NastavnickoStanje.javniRezultat} za projektor, a kroz
     * {@link #rezultatZaTelefon} i {@code JavnoStanje.rezultat}): samo kad su rezultati prikazani i runda je javna
     * ({@link #javnaRunda}), bez sakrivenih tekstova, a tačnost (i broj "u odstupanju", koji govori koliko je tačnih)
     * tek kad je tačan odgovor prikazan na zatvorenom pitanju. Tekstovi opcija su uvek tu (projektor).
     */
    Rezultat javniRezultat(Podaci p) {
        Izvodjenje iz = p.iz();
        if (!iz.isRezultatiPrikazani()) return null;
        PitanjeRunda runda = javnaRunda(p, TokIzvodjenja.trenutni(iz, p.slajdovi()));
        if (runda == null) return null;
        boolean tacan = tacanVidljiv(iz);
        Rezultat r = rezultat(snimak(runda), p.odgovoriRunde(runda.getId()), true, tacan);
        if (r == null) return null;
        if (!tacan && r.brojevi() != null && r.brojevi().uOdstupanju() != null) {
            RezultatBrojevi b = r.brojevi();
            return new Rezultat(r.tip(), r.ukupno(), r.opcije(), new RezultatBrojevi(b.medijana(), null, b.najcesce()),
                    r.tekstovi(), r.skala());
        }
        return r;
    }

    /**
     * Javna rang-lista ({@code JavnoStanje.rangLista} i {@code NastavnickoStanje.javnaRangLista}): top 5 bez id-jeva,
     * samo kad je prikazana ili na kraju takmičenja, i bez poena trenutne runde pre TACAN
     * ({@link #javniBodovniOdgovori}).
     */
    List<RangStavka> javnaRangLista(Podaci p) {
        Izvodjenje iz = p.iz();
        if (!iz.isRangListaPrikazana() && !(iz.getPrikaz() == Prikaz.KRAJ && iz.isTakmicenje())) return null;
        return RangLista.izracunaj(p.ucesnici(), javniBodovniOdgovori(p)).stream()
                .limit(RANG_JAVNO)
                .map(st -> new RangStavka(st.mesto(), null, st.ime(), st.poeni()))
                .toList();
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
                tekst ? s.tekst() : null, tekst ? s.slikaId() : null,
                // jedinica nije tajna (samo kaže u čemu se upisuje broj), pa ide u svakom režimu telefona
                s.tip() == TipPitanja.BROJ ? s.jedinica() : null,
                tekst ? s.skalaMinOznaka() : null, tekst ? s.skalaMaxOznaka() : null,
                ri.rokMs(), ri.preostaloMs(), tacneOpcije, tacanBroj, prihvatljivi);
    }

    /** Ono što lično stanje traži od celog izvođenja, izračunato jednom (i za {@link #licnaZaSve}). */
    private record LicniPodaci(Map<Long, Integer> poeni, Map<Long, Integer> mesto, Long rundaId,
                               Map<Long, Odgovor> odgovorRunde, boolean tacan, ZadatakRunda zadatak,
                               Map<Long, Long> dodele) {
    }

    private LicniPodaci licniPodaci(Podaci p) {
        Izvodjenje iz = p.iz();
        List<Odgovor> bodovni = javniBodovniOdgovori(p);
        Map<Long, Integer> mesto = new HashMap<>();
        if (iz.isTakmicenje()) {
            RangLista.izracunaj(p.ucesnici(), bodovni).forEach(st -> mesto.put(st.ucesnikId(), st.mesto()));
        }
        Slajd trenutni = TokIzvodjenja.trenutni(iz, p.slajdovi());
        PitanjeRunda r = javnaRunda(p, trenutni);
        ZadatakRunda zr = zadatakRunda(p, trenutni, true);
        Map<Long, Odgovor> odgovorRunde = new HashMap<>();
        PitanjeRunda saOdgovorima = r != null ? r : zr == null ? null : zr.runda();
        if (saOdgovorima != null) {
            p.odgovoriRunde(saOdgovorima.getId()).forEach(o -> odgovorRunde.putIfAbsent(o.getUcesnik().getId(), o));
        }
        return new LicniPodaci(RangLista.poeniPoUcesniku(bodovni), mesto, r == null ? null : r.getId(), odgovorRunde,
                tacanVidljiv(iz), zr, zr == null ? Map.of() : p.dodeleRunde());
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

    private LicnoStanje licno(Izvodjenje iz, Ucesnik u, LicniPodaci lp, int brojUcesnika) {
        LicniOdgovor odgovor = null;
        if (lp.rundaId() != null) {
            Odgovor o = lp.odgovorRunde().get(u.getId());
            boolean prikazi = o != null && lp.tacan();
            odgovor = new LicniOdgovor(lp.rundaId(), o != null, prikazi ? o.getTacno() : null,
                    prikazi ? o.getPoeni() : null);
        }
        return new LicnoStanje(iz.getVerzija(), u.getId(), u.getIme(), lp.poeni().getOrDefault(u.getId(), 0),
                iz.isTakmicenje() ? lp.mesto().get(u.getId()) : null, brojUcesnika, false, odgovor,
                licniZadatak(iz, u, lp));
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

        Map<Long, Map<Long, Long>> dodelePoRundi = new HashMap<>();
        if (p.runde().stream().anyMatch(r -> !(snimak(r) instanceof PitanjeSnimak))) {
            Set<Long> aktivni = new HashSet<>();
            p.ucesnici().forEach(u -> aktivni.add(u.getId()));
            for (Object[] red : dodelaRepository.paroviIzvodjenja(izvodjenjeId)) {
                Long ucesnik = ((Number) red[1]).longValue();
                if (aktivni.contains(ucesnik)) {
                    dodelePoRundi.computeIfAbsent(((Number) red[0]).longValue(), k -> new HashMap<>())
                            .put(ucesnik, ((Number) red[2]).longValue());
                }
            }
        }

        List<RezultatPitanja> pitanja = new ArrayList<>();
        for (PitanjeRunda r : p.runde()) {
            SnimakRunde snimak = snimak(r);
            PitanjeSnimak pitanje = snimak instanceof PitanjeSnimak ps ? ps : null;
            List<Odgovor> odgovori = poRundi.getOrDefault(r.getId(), List.of());
            Integer procenat = null;
            if (snimak.imaTacanOdgovor() && !odgovori.isEmpty()) {
                long tacnih = odgovori.stream().filter(o -> Boolean.TRUE.equals(o.getTacno())).count();
                procenat = (int) Math.round(100.0 * tacnih / odgovori.size());
            }
            PregledZadatka zadatak = snimak instanceof ZadatakSnimak zs
                    ? pregledZadatka(zs, p.ucesnici(), dodelePoRundi.getOrDefault(r.getId(), Map.of()), odgovori) : null;
            pitanja.add(new RezultatPitanja(r.getId(), r.getSlajdId(),
                    r.getSlajdId() == null ? null : rbSlajda.get(r.getSlajdId()), r.getRedniBroj(), pitanje,
                    rezultat(snimak, odgovori, false, true), odgovori.size(), procenat, zadatak));
        }
        return new IzvodjenjeRezultati(info(p), p.iz().isTakmicenje(), pitanja, RangLista.izracunaj(p.ucesnici(), bodovniOdgovori(p)));
    }

    /** Runda zadatka u pregledu: rezultat (sve vidljivo) i učesnici sa dodelom, po redu prijave. */
    private PregledZadatka pregledZadatka(ZadatakSnimak s, List<Ucesnik> ucesnici, Map<Long, Long> dodele,
                                          List<Odgovor> odgovori) {
        List<ZadatakRezultatBuilder.OdgovorZadatka> oz = odgovoriZadatka(odgovori);
        Map<Long, ZadatakRezultatBuilder.OdgovorZadatka> poUcesniku = new HashMap<>();
        oz.forEach(o -> poUcesniku.putIfAbsent(o.ucesnikId(), o));
        List<UcesnikZadatka> lista = new ArrayList<>();
        for (Ucesnik u : ucesnici) {
            Long varijantaId = dodele.get(u.getId());
            if (varijantaId == null) continue;
            ZadatakRezultatBuilder.OdgovorZadatka o = poUcesniku.get(u.getId());
            VarijantaSnimak v = s.varijanta(varijantaId).orElse(null);
            String naziv = v == null ? null : v.naziv();
            lista.add(new UcesnikZadatka(u.getId(), u.getIme(), varijantaId, naziv, o != null,
                    o == null ? null : o.tacno(), o == null ? null : ishod(o.polja()),
                    o == null ? null : UnetoZadatka.tekstovi(v, o.polja())));
        }
        Map<Long, Integer> nivoi = new LinkedHashMap<>();
        if (s.varijante() != null) {
            s.varijante().stream().filter(v -> v.nivo() != null).forEach(v -> nivoi.put(v.varijantaId(), v.nivo()));
        }
        return new PregledZadatka(s.slajdId(), s.grupaNaziv(), s.putanja(),
                ZadatakRezultatBuilder.izgradi(s, dodele, oz, true, true, true), List.copyOf(lista), s.nivo(), nivoi);
    }

    // ---------------------------------------------------------------- zajedničko (i za javno/lično stanje)

    /** Izvođenje (404), slajdovi, runde, neizbačeni učesnici, njihovi odgovori i opisi Zadatak slajdova (trenutni, sledeći). */
    Podaci ucitaj(Long izvodjenjeId) {
        Izvodjenje iz = izvodjenjeRepository.findSaVezama(izvodjenjeId)
                .orElseThrow(() -> new SystemException(IzvodjenjeService.NIJE_PRONADJENO, HttpStatus.NOT_FOUND));
        List<Slajd> slajdovi = slajdRepository.findAllByPrezentacijaIdOrderByRbAsc(iz.getPrezentacija().getId());
        List<PitanjeRunda> runde = rundaRepository.findAllByIzvodjenjeIdOrderByOtvorenoAscIdAsc(izvodjenjeId);
        List<Ucesnik> ucesnici = ucesnikRepository.findAllByIzvodjenjeIdOrderByKreiranoAsc(izvodjenjeId).stream()
                .filter(u -> !u.isIzbacen()).toList();
        List<Odgovor> odgovori = odgovorRepository.findAllByRundaIzvodjenjeId(izvodjenjeId).stream()
                .filter(o -> !o.getUcesnik().isIzbacen()).toList();
        return new Podaci(iz, slajdovi, runde, ucesnici, odgovori, opisiZadataka(iz, slajdovi),
                dodeleRunde(iz, slajdovi, ucesnici));
    }

    /** Dodele trenutne runde kad je trenutni slajd ZADATAK (jedan upit), samo neizbačeni; inače prazno, bez upita. */
    private Map<Long, Long> dodeleRunde(Izvodjenje iz, List<Slajd> slajdovi, List<Ucesnik> ucesnici) {
        Slajd trenutni = TokIzvodjenja.trenutni(iz, slajdovi);
        if (trenutni == null || trenutni.getTip() != TipSlajda.ZADATAK || iz.getTrenutnaRundaId() == null) return Map.of();
        Set<Long> aktivni = new HashSet<>();
        ucesnici.forEach(u -> aktivni.add(u.getId()));
        Map<Long, Long> m = new HashMap<>();
        for (Object[] red : dodelaRepository.parovi(iz.getTrenutnaRundaId())) {
            Long ucesnik = ((Number) red[0]).longValue();
            if (aktivni.contains(ucesnik)) m.put(ucesnik, ((Number) red[1]).longValue());
        }
        return Map.copyOf(m);
    }

    /** Opisi ZADATAK slajdova među trenutnim i sledećim, jednim batch pozivom; bez ZADATAK slajda bez ijednog upita. */
    private Map<Long, ZadatakSlajdaInfo> opisiZadataka(Izvodjenje iz, List<Slajd> slajdovi) {
        List<Slajd> zadaciSlajdovi = Stream
                .of(TokIzvodjenja.trenutni(iz, slajdovi), sledeci(TokIzvodjenja.indeks(iz, slajdovi), slajdovi))
                .filter(s -> s != null && s.getTip() == TipSlajda.ZADATAK).toList();
        return zadaciSlajdovi.isEmpty() ? Map.of()
                : zadaci.za(iz.getPrezentacija().getPredmet().getId(), zadaciSlajdovi);
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

    /**
     * Bodovni odgovori za ono što vide telefoni i projektor (lični poeni i mesto, javna rang-lista): odgovori trenutne
     * runde ulaze tek kad je tačan odgovor prikazan, inače bi skok poena odmah posle odgovora odao tačnost. Ranije
     * runde i slajdovi se računaju uvek; nastavničko stanje koristi {@link #bodovniOdgovori} (vidi sve uživo).
     */
    List<Odgovor> javniBodovniOdgovori(Podaci p) {
        List<Odgovor> bodovni = bodovniOdgovori(p);
        Long trenutna = p.iz().getTrenutnaRundaId();
        if (trenutna == null || tacanVidljiv(p.iz())) return bodovni;
        return bodovni.stream().filter(o -> !trenutna.equals(o.getRunda().getId())).toList();
    }

    /** Slajd runde; posle brisanja slajda ({@code slajdId = null}) slajd iz snimka, pa runde istog slajda ostaju zajedno. */
    private Object kljucSlajda(PitanjeRunda r) {
        if (r.getSlajdId() != null) return r.getSlajdId();
        Long izSnimka = snimak(r).slajdId();
        return izSnimka != null ? izSnimka : "runda-" + r.getId();
    }

    SnimakRunde snimak(PitanjeRunda r) {
        return jsonMapper.readValue(r.getSnimak(), SnimakRunde.class);
    }

    /**
     * Rezultat runde iz snimka ({@link RezultatBuilder}); privremeno samo za PITANJE, ZADATAK runda još nema rezultat
     * ({@code null}) dok ne dobije svoju ocenu i projekcije.
     */
    private static Rezultat rezultat(SnimakRunde s, List<Odgovor> odgovori, boolean javno, boolean tacan) {
        return s instanceof PitanjeSnimak p ? RezultatBuilder.izgradi(p, odgovori, javno, tacan) : null;
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
