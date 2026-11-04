package tri.novica.gfssystem.service.uzivo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import tri.novica.gfssystem.dto.uzivo.IshodPreocene;
import tri.novica.gfssystem.dto.uzivo.IzvodjenjeZaPreocenu;
import tri.novica.gfssystem.dto.uzivo.OdgovorZaPreocenu;
import tri.novica.gfssystem.dto.uzivo.RundaZaPreocenu;
import tri.novica.gfssystem.entity.uzivo.Izvodjenje;
import tri.novica.gfssystem.entity.uzivo.Odgovor;
import tri.novica.gfssystem.entity.uzivo.PitanjeRunda;
import tri.novica.gfssystem.entity.uzivo.PreocenaOdgovora;
import tri.novica.gfssystem.entity.uzivo.StatusIzvodjenja;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.uzivo.IzvodjenjeRepository;
import tri.novica.gfssystem.repository.uzivo.OdgovorRepository;
import tri.novica.gfssystem.repository.uzivo.PitanjeRundaRepository;
import tri.novica.gfssystem.repository.uzivo.PreocenaOdgovoraRepository;
import tri.novica.gfssystem.utility.Serija;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Preocena odgovora posle časa, kad je ocena bila pogrešna ne krivicom studenta (npr. telefon nije imao minus). Menja samo
 * {@code odgovori} završenog izvođenja: tačnost, ocenu po poljima i poene; rang i pregledi se računaju iz tih redova. Svaka
 * promena ostavlja trag u {@code preocene_odgovora}. Dok izvođenje traje ocena
 * se ne dira, jer stanje već ide telefonima. Upisuje uvek pod ključem izvođenja, kao i odgovori. {@link #vrati} tačno vraća
 * tačnost, polja i poene iz traga, a suprotna ocena to ne bi: spisak može da sadrži i odgovore koji se nisu menjali.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(isolation = Isolation.READ_COMMITTED)
public class PreocenaOdgovoraService {

    static final String RUNDA_NIJE_PRONADJENA = "Runda nije pronađena.";
    static final String IZVODJENJE_TRAJE = "Izvođenje je u toku; ocene se menjaju posle časa.";
    static final String RAZLOG_OBAVEZAN = "Razlog je obavezan (1 do 300 znakova).";
    static final String ODGOVORI_OBAVEZNI = "Pošalji bar jedan odgovor.";
    static final String SERIJA_BEZ_PREOCENE = "Serija nema preocena u ovoj rundi.";
    private static final TypeReference<LinkedHashMap<String, PoljeOdgovora>> POLJA = new TypeReference<>() {
    };

    private final IzvodjenjeRepository izvodjenjeRepository;
    private final PitanjeRundaRepository rundaRepository;
    private final OdgovorRepository odgovorRepository;
    private final PreocenaOdgovoraRepository preocenaRepository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    /** Izvođenja prezentacije sa rundama, najnovije prvo. */
    @Transactional(readOnly = true)
    public List<IzvodjenjeZaPreocenu> izvodjenja(Long prezentacijaId) {
        return izvodjenjeRepository.findAllByPrezentacijaIdOrderByPocetakDesc(prezentacijaId).stream()
                .map(iz -> new IzvodjenjeZaPreocenu(iz.getId(), iz.getStatus(), iz.getPocetak(),
                        iz.getGrupa() == null ? null : iz.getGrupa().getNaziv(), runde(iz.getId())))
                .toList();
    }

    private List<RundaZaPreocenu> runde(Long izvodjenjeId) {
        return rundaRepository.findAllByIzvodjenjeIdOrderByOtvorenoAscIdAsc(izvodjenjeId).stream().map(r -> {
            List<Odgovor> odgovori = odgovorRepository.findAllByRundaId(r.getId());
            return new RundaZaPreocenu(r.getId(), r.getSlajdId(), r.getRedniBroj(), odgovori.size(), brojTacnih(odgovori));
        }).toList();
    }

    /** Odgovori jedne runde, redom pristizanja. */
    @Transactional(readOnly = true)
    public List<OdgovorZaPreocenu> odgovori(Long rundaId) {
        if (!rundaRepository.existsById(rundaId)) {
            throw new SystemException(RUNDA_NIJE_PRONADJENA, HttpStatus.NOT_FOUND);
        }
        return odgovorRepository.findAllByRundaId(rundaId).stream()
                .map(o -> new OdgovorZaPreocenu(o.getId(), o.getUcesnik().getId(), o.getUcesnik().getIme(), o.getVarijantaId(),
                        vrednost(o), o.getTacno()))
                .toList();
    }

    private static String vrednost(Odgovor o) {
        if (o.getPolja() != null) return o.getPolja();
        if (o.getTekst() != null) return o.getTekst();
        if (o.getBroj() != null) return o.getBroj().toString();
        if (o.getSkala() != null) return o.getSkala().toString();
        return o.getOpcije();
    }

    /**
     * Postavlja ocenu odgovorima runde završenog izvođenja: za zadatak sva polja, a sa {@code polje} samo to (odgovor je
     * tačan kad su sva polja tačna). Id van runde i odgovor bez tačnosti (anketa) su 400 i ništa se ne menja. Odgovor koji već
     * ima tu ocenu se preskače, pa je ponovljen poziv bez efekta.
     */
    public IshodPreocene preoceni(Long rundaId, List<Long> ids, boolean tacno, String polje, String razlog, String serija) {
        Serija.proveri(serija);
        String obrazlozenje = razlog == null ? "" : razlog.strip();
        if (obrazlozenje.isEmpty() || obrazlozenje.codePointCount(0, obrazlozenje.length()) > 300) {
            throw new SystemException(RAZLOG_OBAVEZAN, HttpStatus.BAD_REQUEST);
        }
        if (ids == null || ids.isEmpty() || ids.contains(null)) {
            throw new SystemException(ODGOVORI_OBAVEZNI, HttpStatus.BAD_REQUEST);
        }
        PitanjeRunda runda = runda(rundaId);
        Izvodjenje iz = zavrseno(runda);
        Map<Long, Odgovor> uRundi = uRundi(rundaId);
        List<Long> izmenjeni = new ArrayList<>();
        for (Long id : new LinkedHashSet<>(ids)) {
            Odgovor o = uRundi.get(id);
            if (o == null) {
                throw new SystemException("Odgovor " + id + " nije iz ove runde.", HttpStatus.BAD_REQUEST);
            }
            if (o.getTacno() == null) {
                throw new SystemException("Odgovor " + id + " nema tačnost: pitanje nema tačan odgovor.", HttpStatus.BAD_REQUEST);
            }
            if (primeni(iz, runda, o, tacno, polje, obrazlozenje, serija)) {
                izmenjeni.add(id);
            }
        }
        odgovorRepository.flush();
        log.info("Preocena odgovora: runda={}, izmenjeno={}, tacno={}, serija={}", rundaId, izmenjeni.size(), tacno, serija);
        return new IshodPreocene(izmenjeni, brojTacnih(uRundi.values().stream().toList()), uRundi.size());
    }

    /**
     * Vraća odgovore runde na stanje pre serije {@code ciljSerija}: tačnost, polja i poene iz prvog traga te serije. Odgovor koji
     * je već u tom stanju se preskače, pa je ponovljen poziv bez efekta.
     */
    public IshodPreocene vrati(Long rundaId, String ciljSerija, String serija) {
        Serija.proveri(serija);
        Serija.proveri(ciljSerija);
        zavrseno(runda(rundaId));
        Map<Long, Odgovor> uRundi = uRundi(rundaId);
        Map<Long, PreocenaOdgovora> prvi = new LinkedHashMap<>();
        if (!uRundi.isEmpty()) {
            preocenaRepository.findAllByOdgovorIdInAndSerijaOrderByIdAsc(uRundi.keySet(), ciljSerija)
                    .forEach(t -> prvi.putIfAbsent(t.getOdgovorId(), t));
        }
        if (prvi.isEmpty()) {
            throw new SystemException(SERIJA_BEZ_PREOCENE, HttpStatus.NOT_FOUND);
        }
        List<Long> izmenjeni = new ArrayList<>();
        prvi.forEach((id, t) -> {
            Odgovor o = uRundi.get(id);
            if (Objects.equals(o.getTacno(), t.getStaroTacno()) && Objects.equals(o.getPolja(), t.getStaraPolja())
                    && o.getPoeni() == t.getStariPoeni()) {
                return;
            }
            zapisi(o, t.getStaroTacno(), t.getStaraPolja(), t.getStariPoeni(), "Vraćena preocena iz serije " + ciljSerija, serija);
            izmenjeni.add(id);
        });
        odgovorRepository.flush();
        log.info("Preocena vraćena: runda={}, izmenjeno={}, ciljSerija={}, serija={}", rundaId, izmenjeni.size(), ciljSerija, serija);
        return new IshodPreocene(izmenjeni, brojTacnih(uRundi.values().stream().toList()), uRundi.size());
    }

    private PitanjeRunda runda(Long rundaId) {
        return rundaRepository.findById(rundaId)
                .orElseThrow(() -> new SystemException(RUNDA_NIJE_PRONADJENA, HttpStatus.NOT_FOUND));
    }

    /** Ključ izvođenja runde; dok traje je 409. */
    private Izvodjenje zavrseno(PitanjeRunda runda) {
        Izvodjenje iz = izvodjenjeRepository.findByIdForUpdate(runda.getIzvodjenje().getId())
                .orElseThrow(() -> new SystemException(RUNDA_NIJE_PRONADJENA, HttpStatus.NOT_FOUND));
        if (iz.getStatus() != StatusIzvodjenja.ZAVRSENO) {
            throw new SystemException(IZVODJENJE_TRAJE, HttpStatus.CONFLICT);
        }
        return iz;
    }

    private Map<Long, Odgovor> uRundi(Long rundaId) {
        Map<Long, Odgovor> odgovori = new LinkedHashMap<>();
        odgovorRepository.findAllByRundaId(rundaId).forEach(o -> odgovori.put(o.getId(), o));
        return odgovori;
    }

    private boolean primeni(Izvodjenje iz, PitanjeRunda runda, Odgovor o, boolean tacno, String polje, String razlog, String serija) {
        String polja = o.getPolja();
        boolean novo = tacno;
        if (polja != null) {
            Map<String, PoljeOdgovora> poPolju = jsonMapper.readValue(polja, POLJA);
            if (polje != null && !poPolju.containsKey(polje)) {
                throw new SystemException("Odgovor " + o.getId() + " nema polje '" + polje + "'.", HttpStatus.BAD_REQUEST);
            }
            poPolju.replaceAll((k, v) -> polje == null || polje.equals(k) ? new PoljeOdgovora(v.vrednost(), tacno) : v);
            novo = poPolju.values().stream().allMatch(PoljeOdgovora::tacno);
            polja = jsonMapper.writeValueAsString(poPolju);
        } else if (polje != null) {
            throw new SystemException("Polje važi samo za zadatak.", HttpStatus.BAD_REQUEST);
        }
        if (Objects.equals(o.getTacno(), novo) && Objects.equals(o.getPolja(), polja)) {
            return false;
        }
        zapisi(o, novo, polja, Ocenjivac.poeni(novo, iz.isTakmicenje(), runda.getTrajanjeMs(), o.getVremeMs()), razlog, serija);
        return true;
    }

    private void zapisi(Odgovor o, Boolean tacno, String polja, int poeni, String razlog, String serija) {
        PreocenaOdgovora trag = new PreocenaOdgovora();
        trag.setOdgovorId(o.getId());
        trag.setStaroTacno(o.getTacno());
        trag.setNovoTacno(Boolean.TRUE.equals(tacno));
        trag.setStaraPolja(o.getPolja());
        trag.setStariPoeni(o.getPoeni());
        trag.setRazlog(razlog);
        trag.setSerija(serija);
        trag.setKreirano(LocalDateTime.now(clock));
        preocenaRepository.save(trag);
        o.setTacno(tacno);
        o.setPolja(polja);
        o.setPoeni(poeni);
    }

    private static long brojTacnih(List<Odgovor> odgovori) {
        return odgovori.stream().filter(o -> Boolean.TRUE.equals(o.getTacno())).count();
    }
}
