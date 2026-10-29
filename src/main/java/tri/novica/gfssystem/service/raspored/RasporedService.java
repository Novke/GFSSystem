package tri.novica.gfssystem.service.raspored;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.grupa.GrupaInfo;
import tri.novica.gfssystem.dto.predmet.PredmetInfo;
import tri.novica.gfssystem.dto.raspored.*;
import tri.novica.gfssystem.entity.Grupa;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.raspored.OtkazanTermin;
import tri.novica.gfssystem.entity.raspored.Raspored;
import tri.novica.gfssystem.entity.raspored.RasporedStavka;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.GrupaRepository;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.repository.raspored.OtkazanTerminRepository;
import tri.novica.gfssystem.repository.raspored.RasporedRepository;
import tri.novica.gfssystem.repository.raspored.RasporedStavkaRepository;
import tri.novica.gfssystem.utility.Brojaci;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Raspored po periodu (spec najave odsustva, odeljak 3): lista, detalj, upis celog perioda sa stavkama, brisanje. Izmena
 * zaključava red perioda, pa je provera duplikata stavki (u bazi nema jedinstvenog ključa) tačna. Stavka sa {@code id}-jem
 * zadržava red (i najave i otkazivanja) dok joj se ne promene predmet ili grupa; tada se briše i pravi nova (najave jedne
 * grupe ne prelaze na drugu).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RasporedService {

    static final int MAX_NAZIV = 100;
    static final int MAX_DANA = 400;
    static final int MAX_STAVKI = 50;
    static final int MAX_PRAG = 100;
    static final int PODRAZUMEVANI_PRAG = 5;
    static final String NE_POSTOJI = "Raspored ne postoji.";

    private final RasporedRepository rasporedi;
    private final RasporedStavkaRepository stavkeRepo;
    private final OtkazanTerminRepository otkazani;
    private final PredmetRepository predmeti;
    private final GrupaRepository grupe;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<RasporedInfo> lista() {
        Map<Long, Long> broj = Brojaci.mapa(rasporedi.brojStavki());
        return rasporedi.findAllByOrderByVaziOdDescIdDesc().stream()
                .map(r -> new RasporedInfo(r.getId(), r.getNaziv(), r.getVaziOd(), r.getVaziDo(), r.getPragNajava(),
                        broj.getOrDefault(r.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public RasporedDetails detalj(Long id) {
        return details(rasporedi.findById(id).orElseThrow(RasporedService::nePostoji), 0);
    }

    public RasporedDetails kreiraj(RasporedCmd cmd) {
        Ocisceno c = proveri(cmd);
        proveriPreklapanje(null, c);
        Raspored r = new Raspored();
        r.setKreirano(LocalDateTime.now(clock));
        primeni(r, c);
        for (Nova n : c.stavke()) {
            r.getStavke().add(nova(r, n));
        }
        return details(rasporedi.saveAndFlush(r), 0);
    }

    /**
     * Izmena celog perioda. Otkazani termini od danas koje izmena ukida (stavka obrisana ili napravljena iznova, drugi dan,
     * datum van novog perioda) se brišu, da se ne vrate sami kad se raspored opet promeni, i broje u
     * {@code izgubljenaOtkazivanja}; forma na to upozorava pre čuvanja (iz {@code StavkaInfo.otkazani}).
     */
    public RasporedDetails izmeni(Long id, RasporedCmd cmd) {
        Raspored r = rasporedi.findByIdForUpdate(id).orElseThrow(RasporedService::nePostoji);
        Ocisceno c = proveri(cmd);
        List<OtkazanTermin> buduca = otkazani.odDatumaZaRaspored(id, LocalDate.now(clock));
        Map<Long, RasporedStavka> postojece = r.getStavke().stream()
                .collect(Collectors.toMap(RasporedStavka::getId, Function.identity()));
        Set<Long> zadrzane = new HashSet<>();
        List<Nova> nove = new ArrayList<>();
        for (Nova n : c.stavke()) {
            if (n.id() == null) {
                nove.add(n);
                continue;
            }
            RasporedStavka s = postojece.get(n.id());
            if (s == null) {
                throw new SystemException("Termin " + n.rb() + " ne pripada ovom rasporedu.", HttpStatus.BAD_REQUEST);
            }
            if (!zadrzane.add(n.id())) {
                throw new SystemException("Termin " + n.rb() + " je poslat dvaput.", HttpStatus.BAD_REQUEST);
            }
            if (s.getPredmet().getId().equals(n.predmet().getId()) && s.getGrupa().getId().equals(n.grupa().getId())) {
                s.setDan(n.dan());
                s.setPocetak(n.pocetak());
                s.setKraj(n.kraj());
            } else {
                // drugi predmet ili grupa: stara stavka (sa najavama i otkazivanjima) odlazi, pravi se nova
                zadrzane.remove(n.id());
                nove.add(n);
            }
        }
        proveriPreklapanje(id, c);
        primeni(r, c);
        // stavka odlazi ili termin više nije termin: otkazivanje se briše (i učitano, da flush ne vidi vezu na obrisanu stavku)
        List<OtkazanTermin> izgubljena = buduca.stream()
                .filter(o -> !zadrzane.contains(o.getStavka().getId()) || !Termini.jeTermin(o.getStavka(), o.getDatum()))
                .toList();
        otkazani.deleteAll(izgubljena);
        otkazani.flush();
        r.getStavke().removeIf(s -> !zadrzane.contains(s.getId()));
        rasporedi.flush();
        for (Nova n : nove) {
            r.getStavke().add(nova(r, n));
        }
        return details(rasporedi.saveAndFlush(r), izgubljena.size());
    }

    /** Briše period sa stavkama, otkazivanjima i najavama (FK CASCADE); obaveštenja ostaju. */
    public void obrisi(Long id) {
        Raspored r = rasporedi.findByIdForUpdate(id).orElseThrow(RasporedService::nePostoji);
        rasporedi.delete(r);
    }

    // ------------------------------------------------------------------ provera

    /** Stavka posle provere: {@code rb} je redni broj u zahtevu (za poruku), predmet i grupa su učitani. */
    record Nova(int rb, Long id, Predmet predmet, Grupa grupa, int dan, LocalTime pocetak, LocalTime kraj) {
    }

    record Ocisceno(String naziv, java.time.LocalDate vaziOd, java.time.LocalDate vaziDo, int prag, List<Nova> stavke) {
    }

    private Ocisceno proveri(RasporedCmd cmd) {
        if (cmd == null) {
            throw greska("Neispravan zahtev.");
        }
        String naziv = cmd.naziv() == null ? "" : cmd.naziv().strip().replaceAll("\\s+", " ");
        if (naziv.isEmpty()) {
            throw greska("Naziv rasporeda je obavezan.");
        }
        if (naziv.codePointCount(0, naziv.length()) > MAX_NAZIV) {
            throw greska("Naziv rasporeda može imati najviše " + MAX_NAZIV + " znakova.");
        }
        if (cmd.vaziOd() == null || cmd.vaziDo() == null) {
            throw greska("Datumi važenja su obavezni.");
        }
        if (cmd.vaziOd().isAfter(cmd.vaziDo())) {
            throw greska("Raspored mora da počne pre nego što se završi.");
        }
        if (ChronoUnit.DAYS.between(cmd.vaziOd(), cmd.vaziDo()) + 1 > MAX_DANA) {
            throw greska("Raspored može da traje najviše " + MAX_DANA + " dana.");
        }
        int prag = cmd.pragNajava() == null ? PODRAZUMEVANI_PRAG : cmd.pragNajava();
        if (prag < 1 || prag > MAX_PRAG) {
            throw greska("Prag najava mora biti od 1 do " + MAX_PRAG + ".");
        }
        List<StavkaCmd> ulaz = cmd.stavke() == null ? List.of() : cmd.stavke();
        if (ulaz.size() > MAX_STAVKI) {
            throw greska("Raspored može imati najviše " + MAX_STAVKI + " termina.");
        }
        Map<Long, Predmet> pm = predmeti.findAllById(ulaz.stream().filter(Objects::nonNull).map(StavkaCmd::predmetId)
                .filter(Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Predmet::getId, Function.identity()));
        Map<Long, Grupa> gm = grupe.findAllById(ulaz.stream().filter(Objects::nonNull).map(StavkaCmd::grupaId)
                .filter(Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Grupa::getId, Function.identity()));
        List<Nova> stavke = new ArrayList<>();
        Set<List<Object>> kljucevi = new HashSet<>();
        int rb = 0;
        for (StavkaCmd s : ulaz) {
            rb++;
            String t = "Termin " + rb;
            if (s == null) {
                throw greska(t + " je prazan.");
            }
            if (s.predmetId() == null) {
                throw greska(t + ": izaberi predmet.");
            }
            if (s.grupaId() == null) {
                throw greska(t + ": izaberi grupu.");
            }
            Predmet p = pm.get(s.predmetId());
            if (p == null) {
                throw greska(t + ": predmet ne postoji.");
            }
            Grupa g = gm.get(s.grupaId());
            if (g == null) {
                throw greska(t + ": grupa ne postoji.");
            }
            if (s.dan() == null || s.dan() < 1 || s.dan() > 7) {
                throw greska(t + ": dan mora biti od ponedeljka do nedelje.");
            }
            if (s.pocetak() == null || s.kraj() == null) {
                throw greska(t + ": vreme početka i kraja je obavezno.");
            }
            LocalTime pocetak = s.pocetak().truncatedTo(ChronoUnit.MINUTES);
            LocalTime kraj = s.kraj().truncatedTo(ChronoUnit.MINUTES);
            if (!pocetak.isBefore(kraj)) {
                throw greska(t + ": početak mora biti pre kraja.");
            }
            if (!kljucevi.add(List.of(p.getId(), g.getId(), s.dan(), pocetak))) {
                throw greska(t + " se ponavlja (isti predmet, grupa, dan i početak).");
            }
            stavke.add(new Nova(rb, s.id(), p, g, s.dan(), pocetak, kraj));
        }
        return new Ocisceno(naziv, cmd.vaziOd(), cmd.vaziDo(), prag, stavke);
    }

    /**
     * Ista stavka (predmet, grupa, dan, početak) ne sme da postoji u drugom periodu kad se periodi preklapaju u bar jednom
     * takvom danu: 400 sa nazivom i datumima tog perioda. "Kopiraj u novi" sa preklopljenim datumima zato pada ovde.
     * (Dva istovremena upisa novih perioda mogu da prođu oba; nastavnik je jedan, pa brava nije uvedena.)
     */
    private void proveriPreklapanje(Long id, Ocisceno c) {
        List<RasporedStavka> druge = stavkeRepo.uDrugimPeriodima(id == null ? -1L : id, c.vaziOd(), c.vaziDo());
        if (druge.isEmpty()) {
            return;
        }
        for (Nova n : c.stavke()) {
            for (RasporedStavka d : druge) {
                Raspored dr = d.getRaspored();
                if (!d.getPredmet().getId().equals(n.predmet().getId()) || !d.getGrupa().getId().equals(n.grupa().getId())
                        || d.getDan() != n.dan() || !d.getPocetak().equals(n.pocetak())) {
                    continue;
                }
                LocalDate od = c.vaziOd().isAfter(dr.getVaziOd()) ? c.vaziOd() : dr.getVaziOd();
                LocalDate doDatuma = c.vaziDo().isBefore(dr.getVaziDo()) ? c.vaziDo() : dr.getVaziDo();
                if (!Termini.uProzoru(od, doDatuma, n.dan(), od, doDatuma).isEmpty()) {
                    throw greska("Termin " + n.rb() + " već postoji u rasporedu „" + dr.getNaziv() + "“ ("
                            + DATUM.format(dr.getVaziOd()) + " – " + DATUM.format(dr.getVaziDo())
                            + "): isti predmet, grupa, dan i početak u periodima koji se preklapaju. Promeni datume "
                            + "važenja ili ukloni termin.");
                }
            }
        }
    }

    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("d. M. yyyy.");

    private static void primeni(Raspored r, Ocisceno c) {
        r.setNaziv(c.naziv());
        r.setVaziOd(c.vaziOd());
        r.setVaziDo(c.vaziDo());
        r.setPragNajava(c.prag());
    }

    private static RasporedStavka nova(Raspored r, Nova n) {
        RasporedStavka s = new RasporedStavka();
        s.setRaspored(r);
        s.setPredmet(n.predmet());
        s.setGrupa(n.grupa());
        s.setDan(n.dan());
        s.setPocetak(n.pocetak());
        s.setKraj(n.kraj());
        return s;
    }

    // ------------------------------------------------------------------ mapiranje

    private RasporedDetails details(Raspored r, int izgubljena) {
        Map<Long, List<LocalDate>> otk = new HashMap<>();
        if (r.getId() != null) {
            for (OtkazanTermin o : otkazani.odDatumaZaRaspored(r.getId(), LocalDate.now(clock))) {
                if (Termini.jeTermin(o.getStavka(), o.getDatum())) {
                    otk.computeIfAbsent(o.getStavka().getId(), k -> new ArrayList<>()).add(o.getDatum());
                }
            }
        }
        List<StavkaInfo> stavke = r.getStavke().stream()
                .sorted(Comparator.comparingInt(RasporedStavka::getDan).thenComparing(RasporedStavka::getPocetak)
                        .thenComparing(RasporedStavka::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(s -> new StavkaInfo(s.getId(), predmet(s.getPredmet()), grupa(s.getGrupa()), s.getDan(),
                        s.getPocetak(), s.getKraj(), otk.getOrDefault(s.getId(), List.of())))
                .toList();
        return new RasporedDetails(r.getId(), r.getNaziv(), r.getVaziOd(), r.getVaziDo(), r.getPragNajava(), stavke,
                izgubljena);
    }

    static PredmetInfo predmet(Predmet p) {
        return new PredmetInfo(p.getId(), p.getNaziv());
    }

    static GrupaInfo grupa(Grupa g) {
        return new GrupaInfo(g.getId(), g.getNaziv(), g.getGodinaUpisa());
    }

    private static SystemException greska(String poruka) {
        return new SystemException(poruka, HttpStatus.BAD_REQUEST);
    }

    private static SystemException nePostoji() {
        return new SystemException(NE_POSTOJI, HttpStatus.NOT_FOUND);
    }
}
