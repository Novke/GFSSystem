package tri.novica.gfssystem.service.uzivo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.banka.PonistavanjeInfo;
import tri.novica.gfssystem.dto.banka.StavkaPonistavanja;
import tri.novica.gfssystem.dto.uzivo.*;
import tri.novica.gfssystem.entity.Predavanje;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.banka.GrupaZadataka;
import tri.novica.gfssystem.entity.banka.Namena;
import tri.novica.gfssystem.entity.uzivo.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.PredavanjeRepository;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.repository.banka.GrupaZadatakaRepository;
import tri.novica.gfssystem.repository.uzivo.IzvodjenjeRepository;
import tri.novica.gfssystem.repository.uzivo.MedijRepository;
import tri.novica.gfssystem.repository.uzivo.PrezentacijaRepository;
import tri.novica.gfssystem.repository.uzivo.SlajdRepository;
import tri.novica.gfssystem.dto.uzivo.platno.CrtezSloj;
import tri.novica.gfssystem.dto.uzivo.platno.Platno;
import tri.novica.gfssystem.utility.Serija;
import tri.novica.gfssystem.validation.PlatnoPP;
import tri.novica.gfssystem.validation.SlajdPP;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;

/**
 * Prezentacije predmeta i njihovi slajdovi (editor). Redosled je {@code rb} 1..n bez rupa; svaka izmena slajdova prvo
 * zaključa red prezentacije ({@code findByIdForUpdate}), pa dodavanje, brisanje, dupliranje i premeštanje jedne
 * prezentacije idu redom. Izmene rade u READ COMMITTED: kod operacija po id-ju slajda prvo se čita id prezentacije, a
 * tek onda zaključava, pa čitanje posle zaključavanja mora videti ono što je prethodnik upravo upisao (u REPEATABLE
 * READ bi videlo snimak od pre čekanja). Slajdovi se brišu kroz JPA (ne kaskadom u bazi), da {@code orphanRemoval}
 * obriše i pitanje i opcije. Posle zaključavanja prezentacije, a pre upisa, {@link PrezentacijaPromene#zakljucaj}
 * zaključa aktivna izvođenja (red prezentacija -> izvođenje -> slajdovi); posle svake izmene slajdova javlja se
 * {@link PrezentacijaPromene} (izvođenje u toku).
 * <p>
 * Slajd ZADATAK nosi čvor banke ({@code grupaZadataka}), tajmer i broj različitih varijanti; grupa mora biti iz banke
 * predmeta prezentacije, namena PREDAVANJE ({@link #proveriZadatak}, pod ključem prezentacije). Njegov opis sa upozorenjima
 * ({@link ZadaciSlajdova}) ide u svaki {@link SlajdDetails}.
 * <p>
 * Klod (MCP, spec MCP 4.1 i 4.4) piše seriju na prezentacije i slajdove koje napravi ({@link #kreiraj(CreatePrezentacijaCmd,
 * String)}, {@link #dodajSlajdIzSerije}), ne menja prezentaciju koja se upravo izvodi i poništava seriju
 * ({@link #ponistiSeriju}). Dupliranje slajda ili prezentacije seriju ne kopira: kopija je Novicina, pa je poništavanje
 * serije ne briše, a prezentacija serije sa njom ostaje.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(isolation = Isolation.READ_COMMITTED)
public class PrezentacijaService {

    static final String NIJE_PRONADJENA = "Prezentacija nije pronađena.";
    static final String POSLE_NIJE_U_PREZENTACIJI = "Slajd posle kog se dodaje nije u ovoj prezentaciji.";
    static final String NEISPRAVAN_UVOZ = "Uvoz mora imati od 1 do 200 slajdova.";
    static final String SLAJD_NIJE_PRONADJEN = "Slajd nije pronađen.";
    static final String NEISPRAVAN_REDOSLED = "Redosled mora sadržati tačno sve slajdove prezentacije.";
    static final String IZVODJENJE_U_TOKU = "Prezentacija ima izvođenje u toku. Završi ga pre brisanja.";
    static final String NEISPRAVAN_NAZIV = "Naziv je obavezan (najviše 200 znakova).";
    static final String IZVODI_SE = "Prezentacija se upravo izvodi; izmene posle časa.";
    static final String RAZLOG_IZVODJENA = "Prezentacija je izvođena.";
    static final String RAZLOG_IZVODI_SE = "Prezentacija se upravo izvodi.";
    static final String RAZLOG_NOVICINI_SLAJDOVI = "Prezentacija ima Novicine slajdove.";
    static final String GRUPA_NIJE_PRONADJENA = "Grupa zadataka nije pronađena.";
    static final String GRUPA_DRUGOG_PREDMETA = "Grupa mora biti iz banke ovog predmeta, namena Predavanja.";

    private static final int MAX_NAZIV = 200;
    private static final int MAX_OPIS = 1000;
    private static final String SUFIKS_KOPIJE = " (kopija)";
    private static final int MAX_PREDAVANJA = 30;
    /** Opis slajda u nazivu stavke poništavanja (naslov ili tekst pitanja), u znakovima. */
    private static final int MAX_OPIS_SLAJDA = 80;
    /** Slajdovi koji otvaraju runde (broje se kao "pitanja" prezentacije). */
    private static final Set<TipSlajda> SA_RUNDAMA = EnumSet.of(TipSlajda.PITANJE, TipSlajda.ZADATAK);

    private final PrezentacijaRepository prezentacijaRepository;
    private final SlajdRepository slajdRepository;
    private final IzvodjenjeRepository izvodjenjeRepository;
    private final PredmetRepository predmetRepository;
    private final PredavanjeRepository predavanjeRepository;
    private final MedijService medijService;
    private final MedijRepository medijRepository;
    private final GrupaZadatakaRepository grupaZadatakaRepository;
    private final ZadaciSlajdova zadaci;
    private final PlatnoJson platnoJson;
    private final ObjectProvider<PrezentacijaPromene> promene;
    private final Clock clock;

    // ---------------------------------------------------------------- prezentacije

    @Transactional(readOnly = true)
    public List<PrezentacijaInfo> lista(Long predmetId) {
        List<Prezentacija> lista = predmetId == null
                ? prezentacijaRepository.findAllByOrderByIzmenjenoDesc()
                : prezentacijaRepository.findAllByPredmetIdOrderByIzmenjenoDesc(predmetId);
        return lista.stream().map(this::info).toList();
    }

    public PrezentacijaDetails kreiraj(CreatePrezentacijaCmd cmd) {
        return kreiraj(cmd, null);
    }

    /**
     * Nova prezentacija; {@code serija} je Klodova serija upisa (MCP {@code napravi_prezentaciju}), {@code null} = bez
     * (UI), van {@link Serija#OBRAZAC} 400.
     */
    public PrezentacijaDetails kreiraj(CreatePrezentacijaCmd cmd, String serija) {
        if (serija != null) {
            Serija.proveri(serija);
        }
        if (cmd == null || cmd.predmetId() == null) {
            throw new SystemException("Predmet je obavezan.", HttpStatus.BAD_REQUEST);
        }
        String naziv = naziv(cmd.naziv());
        String opis = opis(cmd.opis());
        Predmet predmet = predmetRepository.findById(cmd.predmetId())
                .orElseThrow(() -> new SystemException("Predmet nije pronađen.", HttpStatus.NOT_FOUND));

        LocalDateTime sada = sada();
        Prezentacija p = new Prezentacija();
        p.setPredmet(predmet);
        p.setNaziv(naziv);
        p.setOpis(opis);
        p.setTakmicenje(false);
        p.setTelefonPrikaz(TelefonPrikaz.DUGMAD);
        p.setDetaljiDozvoljeni(true);
        p.setTema(Objects.requireNonNullElse(cmd.tema(), Tema.NACRT));
        p.setSerija(serija);
        p.setKreirano(sada);
        p.setIzmenjeno(sada);
        p = prezentacijaRepository.save(p);
        log.info("Prezentacija kreirana: id={}, predmet={}, serija={}", p.getId(), predmet.getId(), serija);
        return details(p, List.of());
    }

    @Transactional(readOnly = true)
    public PrezentacijaDetails detalji(Long id) {
        Prezentacija p = nadji(id);
        return details(p, slajdRepository.findAllByPrezentacijaIdOrderByRbAsc(id));
    }

    public PrezentacijaDetails izmeni(Long id, UpdatePrezentacijaCmd cmd) {
        String naziv = naziv(cmd.naziv());
        String opis = opis(cmd.opis());
        Prezentacija p = zakljucaj(id);
        p.setNaziv(naziv);
        p.setOpis(opis);
        p.setTakmicenje(cmd.takmicenje());
        if (cmd.telefonPrikaz() != null) {
            p.setTelefonPrikaz(cmd.telefonPrikaz());
        }
        p.setDetaljiDozvoljeni(cmd.detaljiDozvoljeni());
        if (cmd.tema() != null) {
            p.setTema(cmd.tema());
        }
        sacuvajIzmenu(p);
        // naziv se vidi i u stanju izvođenja u toku
        javi(pr -> pr.slajdoviPromenjeni(id));
        return details(p, slajdRepository.findAllByPrezentacijaIdOrderByRbAsc(id));
    }

    /** Briše prezentaciju, slajdove (sa pitanjima i opcijama) i završena izvođenja (kaskada u bazi); 409 dok traje izvođenje. */
    public void obrisi(Long id) {
        Prezentacija p = zakljucaj(id);
        if (izvodjenjeRepository.existsByPrezentacijaIdAndStatus(id, StatusIzvodjenja.AKTIVNO)) {
            throw new SystemException(IZVODJENJE_U_TOKU, HttpStatus.CONFLICT);
        }
        obrisiZakljucanu(p);
    }

    /** Briše zaključanu prezentaciju sa slajdovima (pitanja i opcije kroz JPA) i izvođenjima (kaskada u bazi). */
    private void obrisiZakljucanu(Prezentacija p) {
        Long id = p.getId();
        // kroz JPA, ne kaskadom u bazi: FK slajdovi -> pitanja nema ON DELETE CASCADE
        slajdRepository.deleteAll(slajdRepository.findAllByPrezentacijaIdOrderByRbAsc(id));
        slajdRepository.flush();
        prezentacijaRepository.delete(p);
        log.info("Prezentacija obrisana: id={}", id);
    }

    /** Kopija sa svim slajdovima, pitanjima i opcijama (nove instance); slike se dele. */
    public PrezentacijaDetails dupliraj(Long id) {
        Prezentacija izvor = nadji(id);
        LocalDateTime sada = sada();
        Prezentacija kopija = new Prezentacija();
        kopija.setPredmet(izvor.getPredmet());
        kopija.setNaziv(nazivKopije(izvor.getNaziv()));
        kopija.setOpis(izvor.getOpis());
        kopija.setTakmicenje(izvor.isTakmicenje());
        kopija.setTelefonPrikaz(izvor.getTelefonPrikaz());
        kopija.setDetaljiDozvoljeni(izvor.isDetaljiDozvoljeni());
        kopija.setTema(izvor.getTema());
        kopija.setKreirano(sada);
        kopija.setIzmenjeno(sada);
        kopija = prezentacijaRepository.save(kopija);

        List<Slajd> slajdovi = new ArrayList<>();
        for (Slajd s : slajdRepository.findAllByPrezentacijaIdOrderByRbAsc(id)) {
            Slajd k = kopija(s);
            k.setPrezentacija(kopija);
            k.setRb(slajdovi.size() + 1);
            slajdovi.add(slajdRepository.save(k));
        }
        slajdRepository.flush();
        log.info("Prezentacija duplirana: {} -> {}", id, kopija.getId());
        return details(kopija, slajdovi);
    }

    // ---------------------------------------------------------------- slajdovi

    /**
     * Uvoz više slajdova (spec 7.7): svi se normalizuju i proveravaju pre zaključavanja (greška u bilo kom = 400
     * "Slajd N: ...", ništa se ne upisuje), pa se ubacuju redom posle {@code posle} (ili na kraj) u jednoj transakciji, uz
     * isto zaključavanje i obaveštenje kao {@link #dodajSlajd}.
     */
    public PrezentacijaDetails uveziSlajdove(Long prezentacijaId, UvozSlajdovaCmd cmd) {
        List<SlajdCmd> ulaz = cmd == null ? null : cmd.slajdovi();
        if (ulaz == null || ulaz.isEmpty() || ulaz.size() > SlajdPP.MAX_SLAJDOVA) {
            throw new SystemException(NEISPRAVAN_UVOZ, HttpStatus.BAD_REQUEST);
        }
        List<ProverenSlajd> provereni = new ArrayList<>(ulaz.size());
        for (int i = 0; i < ulaz.size(); i++) {
            try {
                provereni.add(proveren(ulaz.get(i)));
            } catch (SystemException e) {
                throw uSlajdu(i, e);
            }
        }
        Prezentacija p = zakljucaj(prezentacijaId);
        for (int i = 0; i < provereni.size(); i++) {
            try {
                proveriZadatak(p, provereni.get(i).cmd());
            } catch (SystemException e) {
                throw uSlajdu(i, e);
            }
        }
        List<Slajd> slajdovi = slajdRepository.findAllByPrezentacijaIdOrderByRbAsc(prezentacijaId);
        SlajdPP.proveriBrojZaUvoz(slajdovi.size(), provereni.size());
        int mesto = slajdovi.size();
        if (cmd.posle() != null) {
            int i = indeks(slajdovi, cmd.posle());
            if (i < 0) {
                throw new SystemException(POSLE_NIJE_U_PREZENTACIJI, HttpStatus.BAD_REQUEST);
            }
            mesto = i + 1;
        }
        List<Slajd> novi = new ArrayList<>(provereni.size());
        for (ProverenSlajd n : provereni) {
            Slajd s = new Slajd();
            s.setPrezentacija(p);
            primeni(s, n);
            novi.add(s);
        }
        slajdovi.addAll(mesto, novi);
        prenumerisi(slajdovi);
        slajdRepository.saveAll(novi);
        sacuvajIzmenu(p);
        javi(pr -> pr.slajdoviPromenjeni(prezentacijaId));
        log.info("Uvezeno slajdova: {} u prezentaciju {}", novi.size(), prezentacijaId);
        return details(p, slajdovi);
    }

    /** Novi slajd na kraj ili odmah posle slajda {@code posle}. */
    public SlajdDetails dodajSlajd(Long prezentacijaId, SlajdCmd cmd, Long posle) {
        ProverenSlajd n = proveren(cmd);
        Prezentacija p = zakljucaj(prezentacijaId);
        proveriZadatak(p, n.cmd());
        return dodaj(p, n, posle, null);
    }

    /**
     * Klodov nov slajd (MCP {@code dodaj_slajd}) sa serijom (obavezna, van {@link Serija#OBRAZAC} 400), na kraj ili odmah
     * posle {@code posle}. Pod ključem prezentacije: dok se prezentacija izvodi 409 {@value #IZVODI_SE} (REST izmene tada
     * menjaju čas uživo, Klodove ne), pa tek onda pun {@link #proveren} (vrste medija, Markdown slike), za razliku od REST
     * upisa koji proveravaju pre ključa. Granica od 200 slajdova kao {@link #dodajSlajd}.
     */
    public SlajdDetails dodajSlajdIzSerije(Long prezentacijaId, SlajdCmd cmd, Long posle, String serija) {
        Serija.proveri(serija);
        Prezentacija p = zakljucaj(prezentacijaId);
        if (izvodjenjeRepository.existsByPrezentacijaIdAndStatus(prezentacijaId, StatusIzvodjenja.AKTIVNO)) {
            throw new SystemException(IZVODI_SE, HttpStatus.CONFLICT);
        }
        ProverenSlajd n = proveren(cmd);
        proveriZadatak(p, n.cmd());
        SlajdDetails d = dodaj(p, n, posle, serija);
        log.info("Slajd iz serije dodat: id={}, prezentacija={}, serija={}", d.id(), prezentacijaId, serija);
        return d;
    }

    /** Dodaje proveren slajd u zaključanu prezentaciju (na kraj ili posle {@code posle}) i prenumeriše. */
    private SlajdDetails dodaj(Prezentacija p, ProverenSlajd n, Long posle, String serija) {
        Long prezentacijaId = p.getId();
        List<Slajd> slajdovi = slajdRepository.findAllByPrezentacijaIdOrderByRbAsc(prezentacijaId);
        SlajdPP.proveriBrojSlajdova(slajdovi.size());
        int mesto = slajdovi.size();
        if (posle != null) {
            int i = indeks(slajdovi, posle);
            if (i < 0) {
                throw new SystemException(POSLE_NIJE_U_PREZENTACIJI, HttpStatus.BAD_REQUEST);
            }
            mesto = i + 1;
        }
        Slajd s = new Slajd();
        s.setPrezentacija(p);
        s.setSerija(serija);
        primeni(s, n);
        slajdovi.add(mesto, s);
        prenumerisi(slajdovi);
        slajdRepository.save(s);
        sacuvajIzmenu(p);
        javi(pr -> pr.slajdoviPromenjeni(prezentacijaId));
        return SlajdMapper.uDetails(s, platnoJson, zadaci.za(s));
    }

    /** Puna zamena sadržaja slajda; pitanje ostaje isti red (id), opcije se zamenjuju novim. */
    public SlajdDetails izmeniSlajd(Long id, SlajdCmd cmd) {
        ProverenSlajd n = proveren(cmd);
        Prezentacija p = zakljucajZaSlajd(id);
        proveriZadatak(p, n.cmd());
        Slajd s = slajdRepository.findById(id).orElseThrow(PrezentacijaService::slajdNijePronadjen);
        primeni(s, n);
        sacuvajIzmenu(p);
        javi(pr -> pr.slajdoviPromenjeni(p.getId()));
        return SlajdMapper.uDetails(s, platnoJson, zadaci.za(s));
    }

    /** Jedan slajd, pun (PLATNO sa potezima crteža), za {@code GET /slajdovi/{id}}. Samo čitanje, bez zaključavanja. */
    @Transactional(readOnly = true)
    public SlajdDetails slajd(Long id) {
        Slajd s = slajdRepository.findById(id).orElseThrow(PrezentacijaService::slajdNijePronadjen);
        return SlajdMapper.uDetails(s, platnoJson, zadaci.za(s));
    }

    /** Id prezentacije kojoj slajd pripada (MCP {@code slajd}: link u UI); nepostojeći slajd je 404. Samo čitanje. */
    @Transactional(readOnly = true)
    public Long prezentacijaSlajda(Long slajdId) {
        return slajdRepository.findPrezentacijaId(slajdId).orElseThrow(PrezentacijaService::slajdNijePronadjen);
    }

    /** Potezi svih CRTEZ slojeva slajda, redom slojeva; prazno za INFO i PITANJE. Samo čitanje, bez zaključavanja. */
    @Transactional(readOnly = true)
    public List<PoteziSloja> poteziSlajda(Long id) {
        Slajd s = slajdRepository.findById(id).orElseThrow(PrezentacijaService::slajdNijePronadjen);
        Platno p = platnoJson.izKolone(s.getPlatno());
        if (p == null || p.slojevi() == null) return List.of();
        return p.slojevi().stream()
                .filter(CrtezSloj.class::isInstance)
                .map(CrtezSloj.class::cast)
                .map(c -> new PoteziSloja(c.id(), c.medijId(), c.sirina(), c.visina(), c.potezi()))
                .toList();
    }

    public void obrisiSlajd(Long id) {
        Prezentacija p = zakljucajZaSlajd(id);
        List<Slajd> slajdovi = slajdRepository.findAllByPrezentacijaIdOrderByRbAsc(p.getId());
        int stariIndeks = indeks(slajdovi, id);
        if (stariIndeks < 0) {
            throw slajdNijePronadjen();
        }
        slajdRepository.delete(slajdovi.remove(stariIndeks));
        prenumerisi(slajdovi);
        sacuvajIzmenu(p);
        javi(pr -> pr.slajdObrisan(p.getId(), id, stariIndeks));
    }

    /** Kopija slajda (sa pitanjem i opcijama) odmah posle originala. */
    public SlajdDetails duplirajSlajd(Long id) {
        Prezentacija p = zakljucajZaSlajd(id);
        List<Slajd> slajdovi = slajdRepository.findAllByPrezentacijaIdOrderByRbAsc(p.getId());
        int i = indeks(slajdovi, id);
        if (i < 0) {
            throw slajdNijePronadjen();
        }
        SlajdPP.proveriBrojSlajdova(slajdovi.size());
        Slajd k = kopija(slajdovi.get(i));
        k.setPrezentacija(p);
        slajdovi.add(i + 1, k);
        prenumerisi(slajdovi);
        slajdRepository.save(k);
        sacuvajIzmenu(p);
        javi(pr -> pr.slajdoviPromenjeni(p.getId()));
        return SlajdMapper.uDetails(k, platnoJson, zadaci.za(k));
    }

    /** {@code slajdIds} mora biti tačno permutacija id-jeva svih slajdova prezentacije. */
    public PrezentacijaDetails redosled(Long prezentacijaId, List<Long> slajdIds) {
        Prezentacija p = zakljucaj(prezentacijaId);
        List<Slajd> slajdovi = slajdRepository.findAllByPrezentacijaIdOrderByRbAsc(prezentacijaId);
        if (slajdIds == null || slajdIds.size() != slajdovi.size()) {
            throw neispravanRedosled();
        }
        Map<Long, Slajd> poId = new HashMap<>();
        slajdovi.forEach(s -> poId.put(s.getId(), s));
        List<Slajd> novi = new ArrayList<>();
        for (Long slajdId : slajdIds) {
            Slajd s = poId.remove(slajdId);   // remove: duplikat ne nalazi ništa drugi put
            if (s == null) {
                throw neispravanRedosled();
            }
            novi.add(s);
        }
        prenumerisi(novi);
        sacuvajIzmenu(p);
        javi(pr -> pr.slajdoviPromenjeni(prezentacijaId));
        return details(p, novi);
    }

    // ---------------------------------------------------------------- serija (MCP)

    /**
     * Poništava Klodovu seriju u prezentacijama (spec MCP 4.3, {@code ponisti_seriju}; banku poništava
     * {@code SerijaService}). Nijedan slajd van serije se ne briše. Prezentacija serije koja nikad nije izvođena i ima
     * samo slajdove te serije (ili nijedan) briše se cela. Neizvođena prezentacija serije sa ijednim slajdom van serije
     * (Novica ga je dodao ili duplirao u UI-ju, ili ga je dodala druga serija) ostaje i preskočena je
     * ({@value #RAZLOG_NOVICINI_SLAJDOVI}), a brišu se samo njeni slajdovi serije, kao u Novicinoj prezentaciji.
     * Izvođena prezentacija serije se preskače cela, sa svim slajdovima: {@value #RAZLOG_IZVODJENA}, a dok se upravo
     * izvodi {@value #RAZLOG_IZVODI_SE}. Slajdovi serije u drugim prezentacijama se brišu (sa pitanjem i opcijama) i
     * ostali prenumerišu, osim dok se prezentacija izvodi: tada je svaki slajd serije preskočen, {@value #RAZLOG_IZVODI_SE}.
     * Ključevi prezentacija idu rastuće po id-ju (posle svakog, kao i inače, njena aktivna izvođenja), a pre ključa se
     * čitaju samo id-jevi. {@code potvrdi=false} ništa ne zaključava ni menja, a odlučuje istim pravilima: {@code obrisano}
     * je spisak onoga što bi se obrisalo. Stavke idu redom prezentacija (id), u prezentaciji po {@code rb}.
     */
    public PonistavanjeInfo ponistiSeriju(String serija, boolean potvrdi) {
        Serija.proveri(serija);
        Set<Long> ids = new TreeSet<>(prezentacijaRepository.findIdsBySerija(serija));
        ids.addAll(slajdRepository.findPrezentacijaIdsBySerija(serija));
        List<StavkaPonistavanja> obrisano = new ArrayList<>();
        List<StavkaPonistavanja> preskoceno = new ArrayList<>();
        for (Long id : ids) {
            Optional<Prezentacija> nadjena = potvrdi ? zakljucajAkoPostoji(id) : prezentacijaRepository.findById(id);
            if (nadjena.isEmpty()) {
                continue;   // obrisana u međuvremenu
            }
            Prezentacija p = nadjena.get();
            boolean prezentacijaSerije = serija.equals(p.getSerija());
            if (prezentacijaSerije && izvodjenjeRepository.countByPrezentacijaId(id) > 0) {
                preskoceno.add(stavka(p, izvodjenjeRepository.existsByPrezentacijaIdAndStatus(id, StatusIzvodjenja.AKTIVNO)
                        ? RAZLOG_IZVODI_SE : RAZLOG_IZVODJENA));
                continue;
            }
            List<Slajd> slajdovi = slajdRepository.findAllByPrezentacijaIdOrderByRbAsc(id);
            List<Slajd> izSerije = slajdovi.stream().filter(s -> serija.equals(s.getSerija())).toList();
            if (prezentacijaSerije) {
                if (izSerije.size() == slajdovi.size()) {
                    obrisano.add(stavka(p, null));
                    if (potvrdi) {
                        obrisiZakljucanu(p);
                    }
                    continue;
                }
                // Novica je dodao ili duplirao slajd: prezentacija ostaje, brišu se samo slajdovi serije (ispod)
                preskoceno.add(stavka(p, RAZLOG_NOVICINI_SLAJDOVI));
            }
            if (izSerije.isEmpty()) {
                continue;   // nema (više) slajdova serije: obrisani u međuvremenu ili ranijim poništavanjem
            }
            if (izvodjenjeRepository.existsByPrezentacijaIdAndStatus(id, StatusIzvodjenja.AKTIVNO)) {
                izSerije.forEach(s -> preskoceno.add(stavka(p, s, RAZLOG_IZVODI_SE)));
                continue;
            }
            izSerije.forEach(s -> obrisano.add(stavka(p, s, null)));
            if (potvrdi) {
                slajdovi.removeAll(izSerije);
                slajdRepository.deleteAll(izSerije);
                prenumerisi(slajdovi);
                sacuvajIzmenu(p);
                javi(pr -> pr.slajdoviPromenjeni(id));
            }
        }
        if (potvrdi) {
            log.info("Serija poništena u prezentacijama: serija={}, obrisano={}, preskočeno={}", serija, obrisano.size(),
                    preskoceno.size());
        }
        return new PonistavanjeInfo(potvrdi, obrisano, preskoceno);
    }

    private static StavkaPonistavanja stavka(Prezentacija p, String razlog) {
        return new StavkaPonistavanja(StavkaPonistavanja.PREZENTACIJA, p.getId(), p.getNaziv(), razlog);
    }

    /** Slajd u stavci: "{@code <prezentacija>, slajd <rb>: <naslov ili tekst pitanja ili tip>}" (opis skraćen). */
    private static StavkaPonistavanja stavka(Prezentacija p, Slajd s, String razlog) {
        String opis = s.getNaslov() != null ? s.getNaslov()
                : s.getPitanje() != null ? s.getPitanje().getTekst()
                : s.getTip().name();
        if (duzina(opis) > MAX_OPIS_SLAJDA) {
            opis = opis.substring(0, opis.offsetByCodePoints(0, MAX_OPIS_SLAJDA)).stripTrailing() + "…";
        }
        return new StavkaPonistavanja(StavkaPonistavanja.SLAJD, s.getId(),
                p.getNaziv() + ", slajd " + s.getRb() + ": " + opis, razlog);
    }

    // ---------------------------------------------------------------- pokretanje

    /** Predavanja predmeta prezentacije koja nisu završena ili su od danas, najnovija prva, najviše 30. */
    @Transactional(readOnly = true)
    public List<PredavanjeZaPokretanjeInfo> predavanjaZaPokretanje(Long prezentacijaId) {
        Prezentacija p = nadji(prezentacijaId);
        List<Predavanje> predavanja = predavanjeRepository.findZaPokretanje(p.getPredmet().getId(),
                LocalDate.now(clock), PageRequest.of(0, MAX_PREDAVANJA));
        return predavanja.stream().map(pr -> new PredavanjeZaPokretanjeInfo(pr.getId(), pr.getRb(), pr.getDatum(),
                pr.getTema(), pr.getZavrseno(),
                pr.getGrupa() == null ? null : new GrupaKratko(pr.getGrupa().getId(), pr.getGrupa().getNaziv()))).toList();
    }

    /** Prezentacija po id-ju ili 404. */
    public Prezentacija nadji(Long id) {
        return prezentacijaRepository.findById(id).orElseThrow(PrezentacijaService::nijePronadjena);
    }

    // ---------------------------------------------------------------- pomoćno

    /**
     * Zaključava red prezentacije, pa (kroz {@link PrezentacijaPromene#zakljucaj}) i njena aktivna izvođenja, pre bilo
     * kakvog upisa: red zaključavanja prezentacija -> izvođenje -> slajdovi je isti u svim putanjama.
     */
    private Prezentacija zakljucaj(Long id) {
        return zakljucajAkoPostoji(id).orElseThrow(PrezentacijaService::nijePronadjena);
    }

    /** Kao {@link #zakljucaj}, ali bez 404: prazno kad prezentacije nema (izvođenja se tada ne zaključavaju). */
    private Optional<Prezentacija> zakljucajAkoPostoji(Long id) {
        Optional<Prezentacija> p = prezentacijaRepository.findByIdForUpdate(id);
        if (p.isPresent()) {
            javi(pr -> pr.zakljucaj(id));
        }
        return p;
    }

    private Prezentacija zakljucajZaSlajd(Long slajdId) {
        Long prezentacijaId = slajdRepository.findPrezentacijaId(slajdId).orElseThrow(PrezentacijaService::slajdNijePronadjen);
        return zakljucaj(prezentacijaId);
    }

    /**
     * Slajd ZADATAK: grupa postoji (inače 400 {@value #GRUPA_NIJE_PRONADJENA}) i pripada banci predmeta prezentacije u
     * stablu PREDAVANJE (inače 400 {@value #GRUPA_DRUGOG_PREDMETA}); drugi tipovi slajda ništa ne proveravaju. Poziva se
     * pod ključem prezentacije, posle {@link #proveren} (koji već traži {@code zadatak.grupaId}).
     */
    private void proveriZadatak(Prezentacija p, SlajdCmd n) {
        if (n.tip() != TipSlajda.ZADATAK) {
            return;
        }
        GrupaZadataka grupa = grupaZadatakaRepository.findById(n.zadatak().grupaId())
                .orElseThrow(() -> new SystemException(GRUPA_NIJE_PRONADJENA, HttpStatus.BAD_REQUEST));
        if (grupa.getNamena() != Namena.PREDAVANJE || !grupa.getPredmet().getId().equals(p.getPredmet().getId())) {
            throw new SystemException(GRUPA_DRUGOG_PREDMETA, HttpStatus.BAD_REQUEST);
        }
    }

    /** Ista greška sa prefiksom "Slajd N: " (N od 1) za uvoz više slajdova. */
    private static SystemException uSlajdu(int indeks, SystemException e) {
        return new SystemException("Slajd " + (indeks + 1) + ": " + e.getMessage(),
                Objects.requireNonNullElse(e.getCode(), HttpStatus.BAD_REQUEST.value()));
    }

    /** Normalizovana komanda i (samo za PLATNO) normalizovan dokument platna. */
    private record ProverenSlajd(SlajdCmd cmd, Platno platno) {
    }

    /**
     * Normalizuje i proverava komandu (400 pri grešci), uključujući čitanje dokumenta platna, postojanje slika i slike
     * u Markdown-u (spec 6.5). REST upisi ga zovu pre zaključavanja, a {@link #dodajSlajdIzSerije} posle ključa i provere
     * izvođenja; upiti o slikama ne zaključavaju ništa, pa red zaključavanja ostaje isti.
     */
    private ProverenSlajd proveren(SlajdCmd cmd) {
        SlajdCmd n = SlajdPP.normalizuj(cmd);
        SlajdPP.proveri(n);
        proveriSlikeUTekstu(SlajdPP.slikeSlajda(n));
        proveriSliku(n.slikaId());
        if (n.pitanje() != null) {
            proveriSliku(n.pitanje().slikaId());
        }
        Platno platno = null;
        if (n.tip() == TipSlajda.PLATNO) {
            platno = PlatnoPP.normalizuj(platnoJson.procitaj(n.platno()));
            PlatnoPP.proveri(platno);
            PlatnoPP.proveriVelicinu(platnoJson.velicinaBajtova(platno));
            Set<String> ids = PlatnoPP.medijIds(platno);
            if (!ids.isEmpty()) {
                List<VrstaMedija> vrste = medijRepository.vrste(ids);
                if (vrste.size() != ids.size()) {
                    throw new SystemException(MedijService.NIJE_PRONADJENA, HttpStatus.BAD_REQUEST);
                }
                if (vrste.stream().anyMatch(v -> v != VrstaMedija.SLIKA)) {
                    throw new SystemException(MedijService.NIJE_SLIKA, HttpStatus.BAD_REQUEST);
                }
            }
        }
        return new ProverenSlajd(n, platno);
    }

    /**
     * Markdown slike slajda ({@link SlajdPP#slikeSlajda}) su postojeći mediji vrste {@code SLIKA}, jednim upitom; nepostojeći
     * UUID, render SVG i prilozi banke su 400 {@link SlajdPP#SLIKA_VAN_GFS} (kao i cilj van GFS-a).
     */
    private void proveriSlikeUTekstu(Set<String> ids) {
        if (ids.isEmpty()) {
            return;
        }
        List<VrstaMedija> vrste = medijRepository.vrste(ids);
        if (vrste.size() != ids.size() || vrste.stream().anyMatch(v -> v != VrstaMedija.SLIKA)) {
            throw new SystemException(SlajdPP.SLIKA_VAN_GFS, HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Slika slajda ili pitanja postoji i jeste slika ({@code SLIKA}, kroz {@code POST /mediji}): render SVG i prilozi banke
     * nisu (re-render briše stari SVG, prilozi nisu javni), pa su 400 {@code Medij nije slika.}
     */
    private void proveriSliku(String slikaId) {
        if (slikaId == null) {
            return;
        }
        VrstaMedija vrsta = medijService.vrsta(slikaId)
                .orElseThrow(() -> new SystemException(MedijService.NIJE_PRONADJENA, HttpStatus.BAD_REQUEST));
        if (vrsta != VrstaMedija.SLIKA) {
            throw new SystemException(MedijService.NIJE_SLIKA, HttpStatus.BAD_REQUEST);
        }
    }

    /** Upisuje normalizovanu komandu u slajd (puna zamena). INFO gubi pitanje ({@code orphanRemoval} ga briše). */
    private void primeni(Slajd s, ProverenSlajd pn) {
        SlajdCmd n = pn.cmd();
        s.setTip(n.tip());
        s.setPlatno(pn.platno() == null ? null : platnoJson.zapisi(pn.platno()));
        s.setNaslov(n.naslov());
        s.setSadrzaj(n.sadrzaj());
        s.setSlika(medij(n.slikaId()));
        s.setBeleske(n.beleske());
        s.setPostepeno(n.postepeno());
        Prelaz pr = n.prelaz();
        s.setPrelaz(pr == null ? null : new PrelazSlajda(pr.efekat(), pr.smer(), pr.trajanjeMs()));
        ZadatakSlajda zadatak = n.tip() == TipSlajda.ZADATAK ? n.zadatak() : null;
        s.setGrupaZadataka(zadatak == null ? null : grupaZadatakaRepository.getReferenceById(zadatak.grupaId()));
        s.setZadatakVremeSekunde(zadatak == null ? null : zadatak.vremeSekunde());
        s.setZadatakBrojVarijanti(zadatak == null ? null : zadatak.brojVarijanti());
        if (n.tip() != TipSlajda.PITANJE) {
            s.setPitanje(null);
            return;
        }
        Pitanje pitanje = s.getPitanje() != null ? s.getPitanje() : new Pitanje();
        primeni(pitanje, n.pitanje());
        s.setPitanje(pitanje);
    }

    private void primeni(Pitanje p, PitanjeCmd c) {
        p.setTip(c.tip());
        p.setTekst(c.tekst());
        p.setSlika(medij(c.slikaId()));
        p.setVremeSekunde(c.vremeSekunde());
        p.setBrojTacno(c.brojTacno());
        p.setBrojOdstupanje(c.brojOdstupanje());
        p.setOdstupanjeTip(c.odstupanjeTip());
        p.setJedinica(c.jedinica());
        p.setTekstPrikaz(c.tekstPrikaz());
        p.setPrihvatljiviOdgovori(c.prihvatljiviOdgovori() == null ? null : new ArrayList<>(c.prihvatljiviOdgovori()));
        p.setSkalaMinOznaka(c.skalaMinOznaka());
        p.setSkalaMaxOznaka(c.skalaMaxOznaka());
        // ista kolekcija (orphanRemoval): stare opcije Hibernate briše, nove upisuje
        p.getOpcije().clear();
        List<OpcijaCmd> opcije = c.opcije();
        for (int i = 0; i < opcije.size(); i++) {
            p.getOpcije().add(opcija(p, i + 1, opcije.get(i).tekst(), opcije.get(i).tacna()));
        }
    }

    private Medij medij(String id) {
        return id == null ? null : medijRepository.getReferenceById(id);
    }

    private static PitanjeOpcija opcija(Pitanje p, int rb, String tekst, boolean tacna) {
        PitanjeOpcija o = new PitanjeOpcija();
        o.setPitanje(p);
        o.setRb(rb);
        o.setTekst(tekst);
        o.setTacna(tacna);
        return o;
    }

    /**
     * Kopija sadržaja slajda bez serije: kopija je Novicina, pa je poništavanje serije ne briše, a prezentacija serije u
     * kojoj je ostaje ({@value #RAZLOG_NOVICINI_SLAJDOVI}).
     */
    private static Slajd kopija(Slajd s) {
        Slajd k = new Slajd();
        k.setTip(s.getTip());
        k.setNaslov(s.getNaslov());
        k.setSadrzaj(s.getSadrzaj());
        k.setSlika(s.getSlika());
        k.setBeleske(s.getBeleske());
        k.setPlatno(s.getPlatno());
        k.setPostepeno(s.isPostepeno());
        PrelazSlajda p = s.getPrelaz();
        k.setPrelaz(p == null ? null : new PrelazSlajda(p.getEfekat(), p.getSmer(), p.getTrajanjeMs()));
        k.setGrupaZadataka(s.getGrupaZadataka());
        k.setZadatakVremeSekunde(s.getZadatakVremeSekunde());
        k.setZadatakBrojVarijanti(s.getZadatakBrojVarijanti());
        k.setPitanje(s.getPitanje() == null ? null : kopija(s.getPitanje()));
        return k;
    }

    private static Pitanje kopija(Pitanje p) {
        Pitanje k = new Pitanje();
        k.setTip(p.getTip());
        k.setTekst(p.getTekst());
        k.setSlika(p.getSlika());
        k.setVremeSekunde(p.getVremeSekunde());
        k.setBrojTacno(p.getBrojTacno());
        k.setBrojOdstupanje(p.getBrojOdstupanje());
        k.setOdstupanjeTip(p.getOdstupanjeTip());
        k.setJedinica(p.getJedinica());
        k.setTekstPrikaz(p.getTekstPrikaz());
        k.setPrihvatljiviOdgovori(p.getPrihvatljiviOdgovori() == null ? null : new ArrayList<>(p.getPrihvatljiviOdgovori()));
        k.setSkalaMinOznaka(p.getSkalaMinOznaka());
        k.setSkalaMaxOznaka(p.getSkalaMaxOznaka());
        p.getOpcije().stream()
                .sorted(Comparator.comparingInt(PitanjeOpcija::getRb))
                .forEach(o -> k.getOpcije().add(opcija(k, o.getRb(), o.getTekst(), o.isTacna())));
        return k;
    }

    private static int indeks(List<Slajd> slajdovi, Long slajdId) {
        for (int i = 0; i < slajdovi.size(); i++) {
            if (slajdovi.get(i).getId().equals(slajdId)) return i;
        }
        return -1;
    }

    /** {@code rb} = pozicija + 1 (popravlja i eventualne rupe); Hibernate upisuje samo promenjene. */
    private static void prenumerisi(List<Slajd> slajdovi) {
        for (int i = 0; i < slajdovi.size(); i++) {
            slajdovi.get(i).setRb(i + 1);
        }
    }

    /** {@code izmenjeno} prezentacije i flush, da {@link PrezentacijaPromene} čita upisano stanje. */
    private void sacuvajIzmenu(Prezentacija p) {
        p.setIzmenjeno(sada());
        slajdRepository.flush();
    }

    private void javi(Consumer<PrezentacijaPromene> obavestenje) {
        promene.ifAvailable(obavestenje);
    }

    private PrezentacijaInfo info(Prezentacija p) {
        Long id = p.getId();
        return new PrezentacijaInfo(id, p.getNaziv(), p.getOpis(), predmet(p),
                slajdRepository.countByPrezentacijaId(id),
                slajdRepository.countByPrezentacijaIdAndTipIn(id, SA_RUNDAMA),
                p.getIzmenjeno(), p.isTakmicenje(), p.getTelefonPrikaz(), p.isDetaljiDozvoljeni(),
                izvodjenjeRepository.countByPrezentacijaId(id), aktivnoIzvodjenjeId(id), p.getTema());
    }

    private PrezentacijaDetails details(Prezentacija p, List<Slajd> slajdovi) {
        long brojPitanja = slajdovi.stream().filter(s -> SA_RUNDAMA.contains(s.getTip())).count();
        Map<Long, ZadatakSlajdaInfo> zadaciSlajdova = zadaci.za(p.getPredmet().getId(), slajdovi);
        return new PrezentacijaDetails(p.getId(), p.getNaziv(), p.getOpis(), predmet(p), slajdovi.size(), brojPitanja,
                p.getIzmenjeno(), p.isTakmicenje(), p.getTelefonPrikaz(), p.isDetaljiDozvoljeni(),
                izvodjenjeRepository.countByPrezentacijaId(p.getId()), aktivnoIzvodjenjeId(p.getId()), p.getTema(),
                slajdovi.stream().map(s -> SlajdMapper.uDetails(s, platnoJson, zadaciSlajdova.get(s.getId()))).toList());
    }

    private Long aktivnoIzvodjenjeId(Long prezentacijaId) {
        return izvodjenjeRepository
                .findFirstByPrezentacijaIdAndStatusOrderByPocetakDesc(prezentacijaId, StatusIzvodjenja.AKTIVNO)
                .map(Izvodjenje::getId)
                .orElse(null);
    }

    private static PredmetKratko predmet(Prezentacija p) {
        return new PredmetKratko(p.getPredmet().getId(), p.getPredmet().getNaziv());
    }

    private static String naziv(String sirov) {
        String naziv = sirov == null ? "" : sirov.strip();
        if (naziv.isEmpty() || duzina(naziv) > MAX_NAZIV) {
            throw new SystemException(NEISPRAVAN_NAZIV, HttpStatus.BAD_REQUEST);
        }
        return naziv;
    }

    private static String opis(String sirov) {
        String opis = sirov == null ? "" : sirov.strip();
        if (duzina(opis) > MAX_OPIS) {
            throw new SystemException("Opis može imati najviše 1000 znakova.", HttpStatus.BAD_REQUEST);
        }
        return opis.isEmpty() ? null : opis;
    }

    /** Naziv + " (kopija)", najviše 200 znakova: skraćuje se naziv, sufiks ostaje (da se kopija razlikuje). */
    static String nazivKopije(String naziv) {
        int max = MAX_NAZIV - SUFIKS_KOPIJE.length();
        String osnova = duzina(naziv) > max ? naziv.substring(0, naziv.offsetByCodePoints(0, max)).stripTrailing() : naziv;
        return osnova + SUFIKS_KOPIJE;
    }

    private static int duzina(String s) {
        return s.codePointCount(0, s.length());
    }

    private LocalDateTime sada() {
        return LocalDateTime.now(clock);
    }

    private static SystemException nijePronadjena() {
        return new SystemException(NIJE_PRONADJENA, HttpStatus.NOT_FOUND);
    }

    private static SystemException slajdNijePronadjen() {
        return new SystemException(SLAJD_NIJE_PRONADJEN, HttpStatus.NOT_FOUND);
    }

    private static SystemException neispravanRedosled() {
        return new SystemException(NEISPRAVAN_REDOSLED, HttpStatus.BAD_REQUEST);
    }
}
