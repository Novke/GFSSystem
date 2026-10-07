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
