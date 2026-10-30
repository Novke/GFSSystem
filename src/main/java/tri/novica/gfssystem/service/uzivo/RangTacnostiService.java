package tri.novica.gfssystem.service.uzivo;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import tri.novica.gfssystem.dto.uzivo.*;
import tri.novica.gfssystem.entity.Predavanje;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.entity.uzivo.Izvodjenje;
import tri.novica.gfssystem.entity.uzivo.PitanjeRunda;
import tri.novica.gfssystem.entity.uzivo.Ucesnik;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.GrupaRepository;
import tri.novica.gfssystem.repository.PredavanjeRepository;
import tri.novica.gfssystem.repository.StudentRepository;
import tri.novica.gfssystem.repository.uzivo.*;
import tri.novica.gfssystem.service.uzivo.RangTacnosti.*;
import tri.novica.gfssystem.utility.SkolskaGodina;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Rang tačnosti iz baze (spec rang tačnosti 4, 7, 8): izvođenje, predavanje, student i grupa. Podaci više izvođenja se
 * učitavaju sa po jednim upitom po tabeli ({@link #ucitaj}), a računa {@link RangTacnosti} po izvođenju (ključ slajda važi
 * samo u okviru jednog izvođenja) pa se zbirovi sabiraju po identitetu: student po id-ju, gost po imenu
 * ({@link RangTacnosti#kljucImena}) samo u okviru predavanja.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RangTacnostiService {

    /** Najviše redova ranga na projektoru ("svi"); manje bira nastavnik komandom {@code RANG_BROJ}. */
    public static final int RANG_PROJEKTOR_MAX = 30;
    static final String PREDAVANJE_NIJE_PRONADJENO = "Predavanje ne postoji! ID = ";

    private final IzvodjenjeRepository izvodjenjeRepository;
    private final PitanjeRundaRepository rundaRepository;
    private final UcesnikRepository ucesnikRepository;
    private final OdgovorRepository odgovorRepository;
    private final ZadatakDodelaRepository dodelaRepository;
    private final PredavanjeRepository predavanjeRepository;
    private final StudentRepository studentRepository;
    private final GrupaRepository grupaRepository;
    private final JsonMapper jsonMapper;

    /** Sve što rang jednog izvođenja traži. */
    record PodaciIzvodjenja(Izvodjenje iz, List<Runda> runde, List<Ucesnik> ucesnici, List<Odg> odgovori,
                            Map<Long, Map<Long, Long>> dodele, Map<Long, Integer> poeni) {

        Map<Long, Zbir> zbir(Long izuzetaRunda) {
            List<Clan> clanovi = ucesnici.stream().map(u -> new Clan(u.getId(), u.getKreirano())).toList();
            return RangTacnosti.poUcesniku(runde, clanovi, odgovori, dodele, izuzetaRunda);
        }
    }

    // ---------------------------------------------------------------- izvođenje

    /** Ceo rang izvođenja (konzola, pregled), sve runde uživo; poeni samo u takmičenju. */
    public List<RangTacnostiStavka> zaIzvodjenje(Long izvodjenjeId) {
        Izvodjenje iz = izvodjenjeRepository.findById(izvodjenjeId)
                .orElseThrow(() -> new SystemException(IzvodjenjeService.NIJE_PRONADJENO, HttpStatus.NOT_FOUND));
        return rangIzvodjenja(ucitaj(List.of(iz)).get(iz.getId()), null, iz.isTakmicenje());
    }

    private List<RangTacnostiStavka> rangIzvodjenja(PodaciIzvodjenja p, Long izuzetaRunda, boolean saPoenima) {
        Map<Long, Zbir> zbir = p.zbir(izuzetaRunda);
        List<Kandidat<Ucesnik>> kandidati = p.ucesnici().stream()
                .map(u -> new Kandidat<>(u, u.getIme(), zbir.get(u.getId())))
                .toList();
        return RangTacnosti.rangiraj(kandidati, (k, mesto) -> stavka(mesto, k.kljuc().getId(),
                k.kljuc().getStudent() == null ? null : k.kljuc().getStudent().getId(), k.ime(),
                k.kljuc().getStudent() == null, k.zbir(),
                saPoenima ? p.poeni().getOrDefault(k.kljuc().getId(), 0) : null, null, null,
                k.kljuc().getStudent() == null ? null : k.kljuc().getStudent().getIndeks()));
    }

    // ---------------------------------------------------------------- predavanje

    /** Rang predavanja: izvođenja sa bar jednom rundom i zbir svih njih. */
    public RangPredavanja zaPredavanje(Long predavanjeId) {
        if (!predavanjeRepository.existsById(predavanjeId)) {
            throw new SystemException(PREDAVANJE_NIJE_PRONADJENO + predavanjeId, HttpStatus.NOT_FOUND);
        }
        List<Izvodjenje> izvodjenja = izvodjenjeRepository.findZaRangPredavanja(List.of(predavanjeId));
        Map<Long, PodaciIzvodjenja> podaci = ucitaj(izvodjenja);
        List<IzvodjenjeInfo> saRundama = izvodjenja.stream()
                .map(i -> podaci.get(i.getId()))
                .filter(p -> !p.runde().isEmpty())
                .map(p -> IzvodjenjeMapper.info(p.iz(), p.ucesnici().size(), p.runde().size()))
                .toList();
        return new RangPredavanja(predavanjeId, saRundama, zbirCasa(podaci.values(), null, null));
    }

    /** Da li konzola i projektor nude rang predavanja: izvođenje je vezano za predavanje koje ima još izvođenja sa rundama. */
    public boolean imaRangPredavanja(Izvodjenje iz) {
        return iz.getPredavanje() != null
                && rundaRepository.postojiDrugoIzvodjenjeSaRundama(iz.getPredavanje().getId(), iz.getId());
    }

    /**
     * Rang za projektor (prvih {@code broj}, a {@code null} = svi do {@value #RANG_PROJEKTOR_MAX}; bez id-jeva i indeksa):
     * izvođenja ili njegovog predavanja, bez runde {@code izuzetaRunda} (trenutna runda pre TACAN, "ni ranije"). Ime je
     * kratko ({@link #kratkaImena}: "Ana K."). {@code null} kad predavanja nema.
     */
    public List<RangTacnostiStavka> zaProjektor(Izvodjenje iz, RangPrikaz prikaz, Long izuzetaRunda, Integer broj) {
        List<RangTacnostiStavka> rang;
        Map<Long, PodaciIzvodjenja> podaci;
        if (prikaz == RangPrikaz.PREDAVANJE) {
            if (iz.getPredavanje() == null) return null;
            podaci = ucitaj(izvodjenjeRepository.findZaRangPredavanja(List.of(iz.getPredavanje().getId())));
            rang = zbirCasa(podaci.values(), iz.getId(), izuzetaRunda);
        } else {
            podaci = ucitaj(List.of(iz));
            rang = rangIzvodjenja(podaci.get(iz.getId()), izuzetaRunda, false);
        }
        Map<Long, Student> studenti = new HashMap<>();
        podaci.values().forEach(p -> p.ucesnici().stream().filter(u -> u.getStudent() != null)
                .forEach(u -> studenti.putIfAbsent(u.getStudent().getId(), u.getStudent())));
        List<RangTacnostiStavka> prvi = rang.stream()
                .limit(broj == null ? RANG_PROJEKTOR_MAX : Math.min(broj, RANG_PROJEKTOR_MAX)).toList();
        List<String> imena = kratkaImena(prvi.stream()
                .map(s -> s.studentId() != null && studenti.containsKey(s.studentId())
                        ? kratkoIme(studenti.get(s.studentId())) : kratkoIme(s.ime()))
                .toList(), prvi.stream().map(RangTacnostiStavka::ime).toList());
        List<RangTacnostiStavka> javni = new ArrayList<>(prvi.size());
        for (int i = 0; i < prvi.size(); i++) {
            RangTacnostiStavka s = prvi.get(i);
            javni.add(new RangTacnostiStavka(s.mesto(), null, null, imena.get(i), s.gost(), s.tacnih(), s.dugovanih(),
                    s.odgovorenih(), s.procenat(), s.prosekMs(), s.zvezdice(), null, null, null, null));
        }
        return javni;
    }

    /** "Ime P." studenta: celo ime i prvo slovo prezimena; bez prezimena samo ime (ili ime kao u spisku učesnika). */
    static String kratkoIme(Student s) {
        String ime = s.getIme() == null ? "" : s.getIme().strip().replaceAll("\\s+", " ");
        String prezime = s.getPrezime() == null ? "" : s.getPrezime().strip();
        if (ime.isEmpty()) return ImenaUcesnika.imeStudenta(s);
        return prezime.isEmpty() ? ime : ime + " " + prezime.substring(0, prezime.offsetByCodePoints(0, 1)).toUpperCase() + ".";
    }

    /** "Ime P." gosta iz upisanog imena: prva reč i prvo slovo poslednje; jedna reč ostaje kakva jeste. */
    static String kratkoIme(String ime) {
        String[] reci = ime == null ? new String[0] : ime.strip().split("\\s+");
        if (reci.length < 2) return ime == null ? "" : ime.strip();
        String poslednja = reci[reci.length - 1];
        return reci[0] + " " + poslednja.substring(0, poslednja.offsetByCodePoints(0, 1)).toUpperCase() + ".";
    }

    /** Kratka imena, a gde se dva poklope ("Ana K." i "Ana K."), puna imena za te redove. */
    static List<String> kratkaImena(List<String> kratka, List<String> puna) {
        Map<String, Long> koliko = kratka.stream().collect(Collectors.groupingBy(k -> k, Collectors.counting()));
        List<String> rezultat = new ArrayList<>(kratka.size());
        for (int i = 0; i < kratka.size(); i++) {
            rezultat.add(koliko.get(kratka.get(i)) > 1 ? puna.get(i) : kratka.get(i));
        }
        return rezultat;
    }

    /**
     * Zbir jednog časa (izvođenja jednog predavanja): po studentu, gost po imenu; {@code izvodjenja} = u koliko izvođenja
     * je dugovao bar jedno pitanje. {@code izuzetaRunda} se izuzima samo u izvođenju {@code izuzetoIzvodjenje}.
     */
    private List<RangTacnostiStavka> zbirCasa(Collection<PodaciIzvodjenja> podaci, Long izuzetoIzvodjenje, Long izuzetaRunda) {
        return rangCasa(identiteti(podaci, izuzetoIzvodjenje, izuzetaRunda));
    }

    /** Identiteti časa (ključ {@code s:<studentId>} ili {@code g:<ime>}) sa zbirom; samo oni sa dugovanim pitanjem. */
    private Map<String, Identitet> identiteti(Collection<PodaciIzvodjenja> podaci, Long izuzetoIzvodjenje, Long izuzetaRunda) {
        Map<String, Identitet> poKljucu = new LinkedHashMap<>();
        for (PodaciIzvodjenja p : podaci) {
            Map<Long, Zbir> zbir = p.zbir(p.iz().getId().equals(izuzetoIzvodjenje) ? izuzetaRunda : null);
            Set<String> uIzvodjenju = new HashSet<>();
            for (Ucesnik u : p.ucesnici()) {
                Zbir z = zbir.get(u.getId());
                if (z.dugovanih() == 0) continue;
                Student s = u.getStudent();
                String kljuc = s != null ? "s:" + s.getId() : "g:" + RangTacnosti.kljucImena(u.getIme());
                Identitet id = poKljucu.computeIfAbsent(kljuc, k -> new Identitet(
                        s == null ? null : s.getId(), s == null ? u.getIme() : ImenaUcesnika.imeStudenta(s),
                        s == null ? null : s.getIndeks()));
                id.zbir.dodaj(z);
                if (uIzvodjenju.add(kljuc)) id.izvodjenja++;
            }
        }
        return poKljucu;
    }

    private static List<RangTacnostiStavka> rangCasa(Map<String, Identitet> poKljucu) {
        List<Kandidat<Identitet>> kandidati = poKljucu.values().stream()
                .map(i -> new Kandidat<>(i, i.ime, i.zbir)).toList();
        return RangTacnosti.rangiraj(kandidati, (k, mesto) -> stavka(mesto, null, k.kljuc().studentId, k.ime(),
                k.kljuc().studentId == null, k.zbir(), null, k.kljuc().izvodjenja, null, k.kljuc().indeks));
    }

    /** Identitet u zbiru: student ili gost (po imenu). */
    private static final class Identitet {
        final Long studentId;
        final String ime;
        final String indeks;
        final Zbir zbir = new Zbir();
        int izvodjenja;
        final Set<String> casovi = new HashSet<>();

        Identitet(Long studentId, String ime, String indeks) {
            this.studentId = studentId;
            this.ime = ime;
            this.indeks = indeks;
        }
    }

    // ---------------------------------------------------------------- student

    /**
     * Časovi studenta (predavanja, a izvođenje bez predavanja kao svoj čas), najnoviji prvi, sa mestom u rangu tog časa;
     * filteri opcioni (predmet prezentacije, školska godina po početku izvođenja).
     */
    public UzivoStudenta zaStudenta(Long studentId, Long predmetId, Integer godina) {
        SkolskaGodina.proveri(godina);
        if (!studentRepository.existsById(studentId)) {
            throw new SystemException("Student ne postoji! ID = " + studentId, HttpStatus.NOT_FOUND);
        }
        List<Long> ids = ucesnikRepository.findIzvodjenjaStudenta(studentId);
        if (ids.isEmpty()) return new UzivoStudenta(null, List.of());
        List<Izvodjenje> svoja = izvodjenjeRepository.findZaRang(ids).stream()
                .filter(i -> uFilteru(i, predmetId, godina)).toList();
        Set<Long> predavanjaIds = new LinkedHashSet<>();
        svoja.stream().filter(i -> i.getPredavanje() != null).forEach(i -> predavanjaIds.add(i.getPredavanje().getId()));
        List<Izvodjenje> sva = new ArrayList<>(svoja.stream().filter(i -> i.getPredavanje() == null).toList());
        if (!predavanjaIds.isEmpty()) sva.addAll(izvodjenjeRepository.findZaRangPredavanja(predavanjaIds));
        Map<Long, PodaciIzvodjenja> podaci = ucitaj(sva);

        // časovi po redu (najnoviji prvi): ključ predavanja ili izvođenja
        Map<String, List<PodaciIzvodjenja>> casovi = new LinkedHashMap<>();
        for (Izvodjenje i : svoja) {
            String kljuc = kljucCasa(i);
            if (casovi.containsKey(kljuc)) continue;
            casovi.put(kljuc, i.getPredavanje() == null ? List.of(podaci.get(i.getId()))
                    : podaci.values().stream()
                    .filter(p -> p.iz().getPredavanje() != null
                            && p.iz().getPredavanje().getId().equals(i.getPredavanje().getId()))
                    .toList());
        }
        Zbir ukupno = new Zbir();
        String ime = null;
        String indeks = null;
        List<UzivoStudentaRed> redovi = new ArrayList<>();
        for (Map.Entry<String, List<PodaciIzvodjenja>> cas : casovi.entrySet()) {
            Map<String, Identitet> naCasu = identiteti(cas.getValue(), null, null);
            Identitet ja = naCasu.get("s:" + studentId);
            if (ja == null) continue;
            List<RangTacnostiStavka> rang = rangCasa(naCasu);
            RangTacnostiStavka moja = rang.stream().filter(s -> studentId.equals(s.studentId())).findFirst().orElseThrow();
            ime = moja.ime();
            indeks = moja.indeks();
            ukupno.dodaj(ja.zbir);
            Izvodjenje prvo = cas.getValue().getFirst().iz();
            Predavanje pred = prvo.getPredavanje();
            redovi.add(new UzivoStudentaRed(pred == null ? null : pred.getId(), pred == null ? prvo.getId() : null,
                    pred != null && pred.getDatum() != null ? pred.getDatum() : prvo.getPocetak().toLocalDate(),
                    prvo.getPrezentacija().getPredmet().getId(), prvo.getPrezentacija().getPredmet().getNaziv(),
                    pred != null ? naslovPredavanja(pred) : prvo.getPrezentacija().getNaziv(), moja, rang.size()));
        }
        RangTacnostiStavka zbir = redovi.isEmpty() ? null
                : stavka(null, null, studentId, ime, false, ukupno, null, null, redovi.size(), indeks);
        return new UzivoStudenta(zbir, redovi);
    }

    private static String naslovPredavanja(Predavanje p) {
        String tema = p.getTema() == null || p.getTema().isBlank() ? null : p.getTema().strip();
        return tema != null ? p.getRb() + ". " + tema : p.getRb() + ". predavanje";
    }

    // ---------------------------------------------------------------- grupa

    /**
     * Rang studenata grupe kroz izvođenja grupe (vezana za predavanje grupe ili pokrenuta za grupu), filteri opcioni.
     * Samo studenti grupe; ko nema nijedno dugovano pitanje ide na dno bez mesta.
     */
    public UzivoGrupe zaGrupu(Long grupaId, Long predmetId, Integer godina) {
        SkolskaGodina.proveri(godina);
        if (!grupaRepository.existsById(grupaId)) {
            throw new SystemException("Grupa ne postoji! ID = " + grupaId, HttpStatus.NOT_FOUND);
        }
        List<Izvodjenje> izvodjenja = izvodjenjeRepository.findZaRangGrupe(grupaId, predmetId,
                godina == null ? null : SkolskaGodina.pocetak(godina).atStartOfDay(),
                godina == null ? null : SkolskaGodina.pocetak(godina + 1).atStartOfDay());
        Map<Long, PodaciIzvodjenja> podaci = ucitaj(izvodjenja);
        List<Student> studenti = studentRepository.findAllByGrupaId(grupaId);
        Map<Long, Identitet> poStudentu = new LinkedHashMap<>();
        studenti.forEach(s -> poStudentu.put(s.getId(),
                new Identitet(s.getId(), ImenaUcesnika.imeStudenta(s), s.getIndeks())));
        Set<String> casovi = new HashSet<>();
        for (PodaciIzvodjenja p : podaci.values()) {
            Map<Long, Zbir> zbir = p.zbir(null);
            String cas = kljucCasa(p.iz());
            boolean imaRunde = p.runde().stream().anyMatch(r -> r.zadatak() || r.pitanjeSaTacnim());
            if (imaRunde) casovi.add(cas);
            for (Ucesnik u : p.ucesnici()) {
                if (u.getStudent() == null) continue;
                Identitet id = poStudentu.get(u.getStudent().getId());
                Zbir z = zbir.get(u.getId());
                if (id == null || z.dugovanih() == 0) continue; // student druge grupe ili bez dugovanog pitanja
                id.zbir.dodaj(z);
                id.casovi.add(cas);
            }
        }
        List<Kandidat<Identitet>> kandidati = poStudentu.values().stream()
                .map(i -> new Kandidat<>(i, i.ime, i.zbir)).toList();
        List<RangTacnostiStavka> rang = new ArrayList<>(RangTacnosti.rangiraj(kandidati, (k, mesto) -> stavka(mesto,
                null, k.kljuc().studentId, k.ime(), false, k.zbir(), null, null, k.kljuc().casovi.size(), k.kljuc().indeks)));
        poStudentu.values().stream()
                .filter(i -> i.zbir.dugovanih() == 0)
                .sorted(Comparator.comparing((Identitet i) -> i.ime, String.CASE_INSENSITIVE_ORDER))
                .forEach(i -> rang.add(stavka(null, null, i.studentId, i.ime, false, i.zbir, null, null, 0, i.indeks)));
        return new UzivoGrupe(grupaId, casovi.size(), rang);
    }

    // ---------------------------------------------------------------- zajedničko

    private static boolean uFilteru(Izvodjenje i, Long predmetId, Integer godina) {
        if (predmetId != null && !predmetId.equals(i.getPrezentacija().getPredmet().getId())) return false;
        return godina == null || SkolskaGodina.za(i.getPocetak().toLocalDate()) == godina;
    }

    /** Čas izvođenja: predavanje, a bez predavanja samo izvođenje. */
    private static String kljucCasa(Izvodjenje i) {
        return i.getPredavanje() != null ? "p:" + i.getPredavanje().getId() : "i:" + i.getId();
    }

    private static RangTacnostiStavka stavka(Integer mesto, Long ucesnikId, Long studentId, String ime, boolean gost,
                                             Zbir z, Integer poeni, Integer izvodjenja, Integer casova, String indeks) {
        return new RangTacnostiStavka(mesto, ucesnikId, studentId, ime, gost, z.tacnih(), z.dugovanih(),
                z.odgovorenih(), z.procenat(), z.prosekMs(), z.zvezdice(), poeni, izvodjenja, casova, indeks);
    }

    /** Runde, učesnici, odgovori i dodele svih datih izvođenja, po jedan upit po tabeli; ključ je id izvođenja. */
    Map<Long, PodaciIzvodjenja> ucitaj(List<Izvodjenje> izvodjenja) {
        Map<Long, PodaciIzvodjenja> rezultat = new LinkedHashMap<>();
        if (izvodjenja.isEmpty()) return rezultat;
        List<Long> ids = izvodjenja.stream().map(Izvodjenje::getId).toList();
        Map<Long, List<Runda>> runde = new HashMap<>();
        Map<Long, Long> izvodjenjeRunde = new HashMap<>();
        for (PitanjeRunda r : rundaRepository.findZaRang(ids)) {
            Long izId = r.getIzvodjenje().getId();
            izvodjenjeRunde.put(r.getId(), izId);
            runde.computeIfAbsent(izId, k -> new ArrayList<>()).add(runda(r));
        }
        Map<Long, List<Ucesnik>> ucesnici = new HashMap<>();
        for (Ucesnik u : ucesnikRepository.findZaRang(ids)) {
            ucesnici.computeIfAbsent(u.getIzvodjenje().getId(), k -> new ArrayList<>()).add(u);
        }
        Map<Long, List<Odg>> odgovori = new HashMap<>();
        Map<Long, Map<Long, Integer>> poeni = new HashMap<>();
        Map<Long, Set<Long>> vazece = new HashMap<>();
        runde.forEach((izId, lista) -> {
            Set<Long> s = new HashSet<>();
            RangTacnosti.poslednje(lista).forEach(r -> s.add(r.id()));
            vazece.put(izId, s);
        });
        for (Object[] red : odgovorRepository.findZaRang(ids)) {
            Long rundaId = ((Number) red[0]).longValue();
            Long izId = izvodjenjeRunde.get(rundaId);
            if (izId == null) continue;
            Long ucesnikId = ((Number) red[1]).longValue();
            odgovori.computeIfAbsent(izId, k -> new ArrayList<>()).add(new Odg(rundaId, ucesnikId, (Boolean) red[2],
                    ((Number) red[3]).longValue(), red[4] == null ? null : ((Number) red[4]).longValue()));
            if (vazece.get(izId).contains(rundaId)) {
                poeni.computeIfAbsent(izId, k -> new HashMap<>()).merge(ucesnikId, ((Number) red[5]).intValue(), Integer::sum);
            }
        }
        Map<Long, Map<Long, Map<Long, Long>>> dodele = new HashMap<>();
        boolean imaZadatak = runde.values().stream().flatMap(List::stream).anyMatch(Runda::zadatak);
        if (imaZadatak) {
            for (Object[] red : dodelaRepository.findZaRang(ids)) {
                Long rundaId = ((Number) red[0]).longValue();
                Long izId = izvodjenjeRunde.get(rundaId);
                if (izId == null) continue;
                dodele.computeIfAbsent(izId, k -> new HashMap<>()).computeIfAbsent(rundaId, k -> new HashMap<>())
                        .put(((Number) red[1]).longValue(), ((Number) red[2]).longValue());
            }
        }
        for (Izvodjenje i : izvodjenja) {
            Long id = i.getId();
            rezultat.put(id, new PodaciIzvodjenja(i, runde.getOrDefault(id, List.of()),
                    ucesnici.getOrDefault(id, List.of()), odgovori.getOrDefault(id, List.of()),
                    dodele.getOrDefault(id, Map.of()), poeni.getOrDefault(id, Map.of())));
        }
        return rezultat;
    }

    /** Runda za rang iz snimka: ključ slajda kao u stanju (posle brisanja slajda iz snimka), varijante za ZADATAK. */
    Runda runda(PitanjeRunda r) {
        return runda(r, jsonMapper.readValue(r.getSnimak(), SnimakRunde.class));
    }

    /** Isto iz već pročitanog snimka (lična tačnost u {@code StanjeService}). */
    static Runda runda(PitanjeRunda r, SnimakRunde s) {
        Object kljuc = r.getSlajdId() != null ? r.getSlajdId()
                : s.slajdId() != null ? s.slajdId() : "runda-" + r.getId();
        Map<Long, Varijanta> varijante = null;
        if (s instanceof ZadatakSnimak zs) {
            varijante = new HashMap<>();
            if (zs.varijante() != null) {
                for (VarijantaSnimak v : zs.varijante()) {
                    varijante.put(v.varijantaId(), new Varijanta(v.imaPolja(),
                            v.nivo() == null ? RangTacnosti.PODRAZUMEVANI_NIVO : v.nivo()));
                }
            }
        }
        return new Runda(r.getId(), kljuc, r.getRedniBroj(), r.getZatvoreno(), s.imaTacanOdgovor() && varijante == null,
                varijante);
    }
}
