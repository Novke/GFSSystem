package tri.novica.gfssystem.service.uzivo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.uzivo.*;
import tri.novica.gfssystem.entity.Predavanje;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.uzivo.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.PredavanjeRepository;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.repository.uzivo.IzvodjenjeRepository;
import tri.novica.gfssystem.repository.uzivo.MedijRepository;
import tri.novica.gfssystem.repository.uzivo.PrezentacijaRepository;
import tri.novica.gfssystem.repository.uzivo.SlajdRepository;
import tri.novica.gfssystem.dto.uzivo.platno.Platno;
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

    private static final int MAX_NAZIV = 200;
    private static final int MAX_OPIS = 1000;
    private static final String SUFIKS_KOPIJE = " (kopija)";
    private static final int MAX_PREDAVANJA = 30;

    private final PrezentacijaRepository prezentacijaRepository;
    private final SlajdRepository slajdRepository;
    private final IzvodjenjeRepository izvodjenjeRepository;
    private final PredmetRepository predmetRepository;
    private final PredavanjeRepository predavanjeRepository;
    private final MedijService medijService;
    private final MedijRepository medijRepository;
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
        p.setKreirano(sada);
        p.setIzmenjeno(sada);
        p = prezentacijaRepository.save(p);
        log.info("Prezentacija kreirana: id={}, predmet={}", p.getId(), predmet.getId());
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
                throw new SystemException("Slajd " + (i + 1) + ": " + e.getMessage(),
                        Objects.requireNonNullElse(e.getCode(), HttpStatus.BAD_REQUEST.value()));
            }
        }
        Prezentacija p = zakljucaj(prezentacijaId);
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
        primeni(s, n);
        slajdovi.add(mesto, s);
        prenumerisi(slajdovi);
        slajdRepository.save(s);
        sacuvajIzmenu(p);
        javi(pr -> pr.slajdoviPromenjeni(prezentacijaId));
        return SlajdMapper.uDetails(s, platnoJson);
    }

    /** Puna zamena sadržaja slajda; pitanje ostaje isti red (id), opcije se zamenjuju novim. */
    public SlajdDetails izmeniSlajd(Long id, SlajdCmd cmd) {
        ProverenSlajd n = proveren(cmd);
        Prezentacija p = zakljucajZaSlajd(id);
        Slajd s = slajdRepository.findById(id).orElseThrow(PrezentacijaService::slajdNijePronadjen);
        primeni(s, n);
        sacuvajIzmenu(p);
        javi(pr -> pr.slajdoviPromenjeni(p.getId()));
        return SlajdMapper.uDetails(s, platnoJson);
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
        return SlajdMapper.uDetails(k, platnoJson);
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
        Prezentacija p = prezentacijaRepository.findByIdForUpdate(id).orElseThrow(PrezentacijaService::nijePronadjena);
        javi(pr -> pr.zakljucaj(id));
        return p;
    }

    private Prezentacija zakljucajZaSlajd(Long slajdId) {
        Long prezentacijaId = slajdRepository.findPrezentacijaId(slajdId).orElseThrow(PrezentacijaService::slajdNijePronadjen);
        return zakljucaj(prezentacijaId);
    }

    /** Normalizovana komanda i (samo za PLATNO) normalizovan dokument platna. */
    private record ProverenSlajd(SlajdCmd cmd, Platno platno) {
    }

    /**
     * Normalizuje i proverava komandu (400 pri grešci), uključujući čitanje dokumenta platna i postojanje slika. Radi
     * se pre zaključavanja (red zaključavanja), kao i upit o slikama.
     */
    private ProverenSlajd proveren(SlajdCmd cmd) {
        SlajdCmd n = SlajdPP.normalizuj(cmd);
        SlajdPP.proveri(n);
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
            if (!ids.isEmpty() && medijRepository.countByIdIn(ids) != ids.size()) {
                throw new SystemException(MedijService.NIJE_PRONADJENA, HttpStatus.BAD_REQUEST);
            }
        }
        return new ProverenSlajd(n, platno);
    }

    private void proveriSliku(String slikaId) {
        if (slikaId != null && !medijService.postoji(slikaId)) {
            throw new SystemException(MedijService.NIJE_PRONADJENA, HttpStatus.BAD_REQUEST);
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

    private static Slajd kopija(Slajd s) {
        Slajd k = new Slajd();
        k.setTip(s.getTip());
        k.setNaslov(s.getNaslov());
        k.setSadrzaj(s.getSadrzaj());
        k.setSlika(s.getSlika());
        k.setBeleske(s.getBeleske());
        k.setPlatno(s.getPlatno());
        k.setPostepeno(s.isPostepeno());
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
                slajdRepository.countByPrezentacijaIdAndTip(id, TipSlajda.PITANJE),
                p.getIzmenjeno(), p.isTakmicenje(), p.getTelefonPrikaz(), p.isDetaljiDozvoljeni(),
                izvodjenjeRepository.countByPrezentacijaId(id), aktivnoIzvodjenjeId(id), p.getTema());
    }

    private PrezentacijaDetails details(Prezentacija p, List<Slajd> slajdovi) {
        long brojPitanja = slajdovi.stream().filter(s -> s.getTip() == TipSlajda.PITANJE).count();
        return new PrezentacijaDetails(p.getId(), p.getNaziv(), p.getOpis(), predmet(p), slajdovi.size(), brojPitanja,
                p.getIzmenjeno(), p.isTakmicenje(), p.getTelefonPrikaz(), p.isDetaljiDozvoljeni(),
                izvodjenjeRepository.countByPrezentacijaId(p.getId()), aktivnoIzvodjenjeId(p.getId()), p.getTema(),
                slajdovi.stream().map(s -> SlajdMapper.uDetails(s, platnoJson)).toList());
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
