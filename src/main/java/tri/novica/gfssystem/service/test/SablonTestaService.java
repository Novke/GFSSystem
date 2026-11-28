package tri.novica.gfssystem.service.test;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.test.online.*;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.TipTesta;
import tri.novica.gfssystem.entity.test.SablonStavka;
import tri.novica.gfssystem.entity.test.SablonTesta;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.repository.TipTestaRepository;
import tri.novica.gfssystem.repository.test.SablonTestaRepository;
import tri.novica.gfssystem.service.banka.KandidatiZadatka.StabloPredavanja;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Šabloni testa (spec 7, plan 4.1): predmet, opcioni tip testa, trajanje, podrazumevana raspodela, stavke sa izvorima iz
 * banke TEST istog predmeta. Izmena šablona ne menja napravljene testove (test nosi svoj prepis stavki); brisanje
 * ostavlja testovima prepis ({@code sablon_id} -> NULL).
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class SablonTestaService {

    public static final String NEMA_SABLONA = "Šablon ne postoji.";

    private final SablonTestaRepository sablonRepository;
    private final PredmetRepository predmetRepository;
    private final TipTestaRepository tipTestaRepository;
    private final KandidatiTesta kandidatiTesta;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<SablonInfo> spisak(Long predmetId, boolean arhivirani) {
        List<SablonTesta> sabloni = predmetId == null ? sablonRepository.svi(arhivirani)
                : sablonRepository.spisak(predmetId, arhivirani);
        return sabloni.stream().map(SablonTestaService::info).toList();
    }

    @Transactional(readOnly = true)
    public SablonDetails detalji(Long id) {
        return details(sablonRepository.findById(id).orElseThrow(SablonTestaService::nema));
    }

    public SablonDetails kreiraj(SablonCmd cmd) {
        Predmet predmet = predmet(cmd);
        SablonTesta s = new SablonTesta();
        s.setPredmet(predmet);
        LocalDateTime sada = LocalDateTime.now(clock);
        s.setKreirano(sada);
        primeni(s, cmd, predmet);
        s.setIzmenjeno(sada);
        SablonTesta sacuvan = sablonRepository.saveAndFlush(s);
        log.info("Šablon testa napravljen: id={}, predmet={}", sacuvan.getId(), predmet.getId());
        return details(sacuvan);
    }

    public SablonDetails izmeni(Long id, SablonCmd cmd) {
        SablonTesta s = sablonRepository.findByIdForUpdate(id).orElseThrow(SablonTestaService::nema);
        if (cmd != null && cmd.predmetId() != null && !cmd.predmetId().equals(s.getPredmet().getId())) {
            throw new SystemException("Predmet šablona se ne menja.", HttpStatus.BAD_REQUEST);
        }
        primeni(s, cmd, s.getPredmet());
        s.setIzmenjeno(LocalDateTime.now(clock));
        return details(sablonRepository.saveAndFlush(s));
    }

    public SablonDetails kopija(Long id) {
        SablonTesta izvor = sablonRepository.findById(id).orElseThrow(SablonTestaService::nema);
        SablonTesta s = new SablonTesta();
        s.setPredmet(izvor.getPredmet());
        s.setTipTesta(izvor.getTipTesta());
        s.setNaziv(skrati(izvor.getNaziv() + " (kopija)", 120));
        s.setOpis(izvor.getOpis());
        s.setTrajanjeMin(izvor.getTrajanjeMin());
        s.setRezim(izvor.getRezim());
        s.setBrojGrupa(izvor.getBrojGrupa());
        s.setPragProlaza(izvor.getPragProlaza());
        LocalDateTime sada = LocalDateTime.now(clock);
        s.setKreirano(sada);
        s.setIzmenjeno(sada);
        for (SablonStavka st : izvor.getStavke()) {
            SablonStavka n = new SablonStavka();
            n.setSablon(s);
            n.setRb(st.getRb());
            n.setNaziv(st.getNaziv());
            n.setPoeni(st.getPoeni());
            n.setBroj(st.getBroj());
            n.setNivoi(st.getNivoi());
            n.setIstiTip(st.isIstiTip());
            n.setRucno(st.isRucno());
            n.setIzvori(new LinkedHashSet<>(st.getIzvori()));
            s.getStavke().add(n);
        }
        return details(sablonRepository.saveAndFlush(s));
    }

    public SablonDetails arhiviran(Long id, ArhiviranCmd cmd) {
        if (cmd == null || cmd.arhiviran() == null) {
            throw new SystemException("Polje arhiviran je obavezno.", HttpStatus.BAD_REQUEST);
        }
        SablonTesta s = sablonRepository.findByIdForUpdate(id).orElseThrow(SablonTestaService::nema);
        s.setArhiviran(cmd.arhiviran());
        s.setIzmenjeno(LocalDateTime.now(clock));
        return details(sablonRepository.saveAndFlush(s));
    }

    public void obrisi(Long id) {
        SablonTesta s = sablonRepository.findByIdForUpdate(id).orElseThrow(SablonTestaService::nema);
        sablonRepository.delete(s);
        log.info("Šablon testa obrisan: id={}", id);
    }

    /** Provera bez čuvanja: isti oblik kao detalji, {@code id == null}. */
    @Transactional(readOnly = true)
    public SablonDetails provera(SablonCmd cmd) {
        Predmet predmet = predmet(cmd);
        SablonTesta s = new SablonTesta();
        s.setPredmet(predmet);
        primeni(s, cmd, predmet);
        s.setIzmenjeno(LocalDateTime.now(clock));
        return details(s);
    }

    // ---------------------------------------------------------------- pravila

    private Predmet predmet(SablonCmd cmd) {
        if (cmd == null || cmd.predmetId() == null) {
            throw new SystemException("Predmet je obavezan.", HttpStatus.BAD_REQUEST);
        }
        return predmetRepository.findById(cmd.predmetId())
                .orElseThrow(() -> new SystemException("Predmet ne postoji! ID = " + cmd.predmetId(), HttpStatus.NOT_FOUND));
    }

    /** Proverava komandu i prepisuje je u šablon (stavke se zamenjuju cele). */
    private void primeni(SablonTesta s, SablonCmd cmd, Predmet predmet) {
        String naziv = cmd.naziv() == null ? "" : cmd.naziv().strip();
        if (naziv.isEmpty() || naziv.length() > 120) {
            throw new SystemException("Naziv šablona je obavezan (najviše 120 znakova).", HttpStatus.BAD_REQUEST);
        }
        if (cmd.trajanjeMin() == null || cmd.trajanjeMin() < 5 || cmd.trajanjeMin() > 300) {
            throw new SystemException("Trajanje mora biti od 5 do 300 minuta.", HttpStatus.BAD_REQUEST);
        }
        if (cmd.rezim() == null) {
            throw new SystemException("Raspodela je obavezna.", HttpStatus.BAD_REQUEST);
        }
        int brojGrupa = cmd.brojGrupa() == null ? 1 : cmd.brojGrupa();
        if (brojGrupa < 1 || brojGrupa > 4) {
            throw new SystemException("Broj grupa testa mora biti od 1 do 4.", HttpStatus.BAD_REQUEST);
        }
        if (cmd.opis() != null && cmd.opis().length() > 5000) {
            throw new SystemException("Opis je predugačak.", HttpStatus.BAD_REQUEST);
        }
        TipTesta tip = null;
        if (cmd.tipTestaId() != null) {
            tip = tipTestaRepository.findById(cmd.tipTestaId())
                    .orElseThrow(() -> new SystemException("Tip testa ne postoji! ID = " + cmd.tipTestaId(), HttpStatus.NOT_FOUND));
            if (!tip.getPredmet().getId().equals(predmet.getId())) {
                throw new SystemException("Tip testa ne pripada predmetu šablona.", HttpStatus.BAD_REQUEST);
            }
        }
        List<StavkaSablonaCmd> stavke = cmd.stavke() == null ? List.of() : cmd.stavke();
        if (stavke.isEmpty() || stavke.size() > 30) {
            throw new SystemException("Šablon ima od 1 do 30 stavki.", HttpStatus.BAD_REQUEST);
        }
        StabloPredavanja stablo = kandidatiTesta.stablo(predmet.getId());
        List<SablonStavka> nove = new ArrayList<>();
        int max = 0;
        int rb = 1;
        for (StavkaSablonaCmd sc : stavke) {
            SablonStavka st = stavka(sc, stablo);
            st.setSablon(s);
            st.setRb(rb++);
            max += st.getPoeni() * st.getBroj();
            nove.add(st);
        }
        if (cmd.pragProlaza() != null && (cmd.pragProlaza() < 0 || cmd.pragProlaza() > max)) {
            throw new SystemException("Prag prolaza mora biti između 0 i maksimalnog broja poena.", HttpStatus.BAD_REQUEST);
        }
        s.setNaziv(naziv);
        s.setOpis(cmd.opis() == null || cmd.opis().isBlank() ? null : cmd.opis().strip());
        s.setTipTesta(tip);
        s.setTrajanjeMin(cmd.trajanjeMin());
        s.setRezim(cmd.rezim());
        s.setBrojGrupa(brojGrupa);
        s.setPragProlaza(cmd.pragProlaza());
        s.getStavke().clear();
        s.getStavke().addAll(nove);
    }

    private SablonStavka stavka(StavkaSablonaCmd sc, StabloPredavanja stablo) {
        if (sc == null) throw new SystemException("Stavka je obavezna.", HttpStatus.BAD_REQUEST);
        String naziv = sc.naziv() == null ? "" : sc.naziv().strip();
        if (naziv.isEmpty() || naziv.length() > 120) {
            throw new SystemException("Naziv stavke je obavezan (najviše 120 znakova).", HttpStatus.BAD_REQUEST);
        }
        if (sc.poeni() == null || sc.poeni() < 1 || sc.poeni() > 100) {
            throw new SystemException("Poeni stavke moraju biti ceo broj od 1 do 100.", HttpStatus.BAD_REQUEST);
        }
        if (sc.broj() == null || sc.broj() < 1 || sc.broj() > 20) {
            throw new SystemException("Broj zadataka stavke mora biti od 1 do 20.", HttpStatus.BAD_REQUEST);
        }
        TreeSet<Integer> nivoi = new TreeSet<>();
        for (Integer n : sc.nivoi() == null ? List.<Integer>of() : sc.nivoi()) {
            if (n == null || n < 1 || n > 3) throw new SystemException("Nivo mora biti od 1 do 3.", HttpStatus.BAD_REQUEST);
            nivoi.add(n);
        }
        List<Long> izvori = sc.izvori() == null ? List.of() : sc.izvori();
        LinkedHashSet<Long> bezDuplikata = new LinkedHashSet<>(izvori);
        if (bezDuplikata.isEmpty() || bezDuplikata.size() > 10 || bezDuplikata.contains(null)) {
            throw new SystemException("Stavka ima od 1 do 10 izvora iz banke.", HttpStatus.BAD_REQUEST);
        }
        kandidatiTesta.proveriIzvore(stablo, bezDuplikata);
        SablonStavka st = new SablonStavka();
        st.setNaziv(naziv);
        st.setPoeni(sc.poeni());
        st.setBroj(sc.broj());
        st.setNivoi(nivoiUKolonu(nivoi));
        st.setIstiTip(sc.istiTip() == null || sc.istiTip());
        st.setRucno(Boolean.TRUE.equals(sc.rucno()));
        st.setIzvori(bezDuplikata);
        return st;
    }

    // ---------------------------------------------------------------- DTO

    private static SablonInfo info(SablonTesta s) {
        return new SablonInfo(s.getId(), s.getPredmet().getId(), s.getTipTesta() == null ? null : s.getTipTesta().getId(),
                s.getTipTesta() == null ? null : s.getTipTesta().getNaziv(), s.getNaziv(), s.getTrajanjeMin(), s.getRezim(),
                s.getBrojGrupa(), maxPoena(s), s.getStavke().size(), s.isArhiviran(), s.getIzmenjeno());
    }

    private SablonDetails details(SablonTesta s) {
        StabloPredavanja stablo = kandidatiTesta.stablo(s.getPredmet().getId());
        List<StavkaSablona> stavke = new ArrayList<>();
        List<String> upozorenja = new ArrayList<>();
        for (SablonStavka st : s.getStavke()) {
            var o = kandidatiTesta.opis(stablo, st.getId() == null ? 0 : st.getId(), st.getNaziv(), st.getBroj(),
                    st.isIstiTip(), st.isRucno(), nivoiIzKolone(st.getNivoi()), st.getIzvori(), s.getRezim(), s.getBrojGrupa());
            stavke.add(new StavkaSablona(st.getId(), st.getRb(), st.getNaziv(), st.getPoeni(), st.getBroj(),
                    List.copyOf(nivoiIzKolone(st.getNivoi())), st.isIstiTip(), st.isRucno(), o.izvori(), o.kandidati(),
                    o.upozorenja()));
            o.upozorenja().forEach(u -> upozorenja.add(st.getNaziv() + ": " + u));
        }
        return new SablonDetails(s.getId(), s.getPredmet().getId(), s.getTipTesta() == null ? null : s.getTipTesta().getId(),
                s.getTipTesta() == null ? null : s.getTipTesta().getNaziv(), s.getNaziv(), s.getTrajanjeMin(), s.getRezim(),
                s.getBrojGrupa(), maxPoena(s), s.isArhiviran(), s.getIzmenjeno(), s.getOpis(), s.getPragProlaza(),
                List.copyOf(stavke), List.copyOf(upozorenja));
    }

    static int maxPoena(SablonTesta s) {
        return s.getStavke().stream().mapToInt(st -> st.getPoeni() * st.getBroj()).sum();
    }

    /** Kolona {@code nivoi}: "2,3"; prazno ili {@code null} = svi. */
    public static TreeSet<Integer> nivoiIzKolone(String s) {
        TreeSet<Integer> nivoi = new TreeSet<>();
        if (s == null || s.isBlank()) return nivoi;
        for (String d : s.split(",")) {
            try {
                int n = Integer.parseInt(d.strip());
                if (n >= 1 && n <= 3) nivoi.add(n);
            } catch (NumberFormatException ignored) {
                // ručni SQL; preskače se
            }
        }
        return nivoi;
    }

    public static String nivoiUKolonu(Collection<Integer> nivoi) {
        if (nivoi == null || nivoi.isEmpty()) return null;
        return String.join(",", new TreeSet<>(nivoi).stream().map(String::valueOf).toList());
    }

    private static String skrati(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static SystemException nema() {
        return new SystemException(NEMA_SABLONA, HttpStatus.NOT_FOUND);
    }

    /** Za podrazumevani režim i broj grupa pri pravljenju testa. */
    @Transactional(readOnly = true)
    public SablonTesta ucitaj(Long id) {
        SablonTesta s = sablonRepository.findById(id).orElseThrow(SablonTestaService::nema);
        s.getStavke().forEach(st -> st.getIzvori().size());
        return s;
    }
}
