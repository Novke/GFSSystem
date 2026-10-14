package tri.novica.gfssystem.service;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.grupa.GrupaInfo;
import tri.novica.gfssystem.dto.onboarding.OnboardingSesijaInfo;
import tri.novica.gfssystem.dto.pregled.GrupaPregledInfo;
import tri.novica.gfssystem.dto.pregled.GrupaStudentStatInfo;
import tri.novica.gfssystem.dto.pregled.PrisustvoMatricaInfo;
import tri.novica.gfssystem.dto.student.StudentInfo;
import tri.novica.gfssystem.entity.Grupa;
import tri.novica.gfssystem.entity.Predavanje;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.entity.TipAktivnosti;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.*;
import tri.novica.gfssystem.repository.spec.PredavanjeSpecs;
import tri.novica.gfssystem.utility.Brojaci;
import tri.novica.gfssystem.utility.SkolskaGodina;
import tri.novica.gfssystem.utility.Utility;

import java.time.LocalDate;
import java.util.*;

/**
 * Pregled jedne grupe: brojke i statistika po studentu (G1, G2) i matrica prisustva (G3). Brojevi dolaze iz agregatnih
 * upita po grupi (konstantan broj upita bez obzira na broj studenata), ne iz petlje po studentu. Kartice studenta po
 * predmetu (S2) računa {@link OcenjivanjeService#karticeStudenta}, jer su poeni iz ocenjivanja.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PregledGrupeService {

    private static final Sort PO_DATUMU = Sort.by(Sort.Order.asc("datum"), Sort.Order.asc("rb"), Sort.Order.asc("id"));
    /** Kao na stranici grupe: po broju iz indeksa, pa po id-ju (indeks bez broja i null idu na kraj). */
    private static final Comparator<Student> PO_INDEKSU = Comparator
            .comparingInt((Student s) -> s.getIndeks() == null ? Integer.MAX_VALUE : Utility.index2int(s.getIndeks()))
            .thenComparing(Student::getId);

    private final GrupaRepository grupaRepository;
    private final PredmetRepository predmetRepository;
    private final StudentRepository studentRepository;
    private final PredavanjeRepository predavanjeRepository;
    private final AktivnostRepository aktivnostRepository;
    private final DomaciRepository domaciRepository;
    private final PolaganjeRepository polaganjeRepository;
    private final OnboardingService onboardingService;
    private final ModelMapper mapper;

    /** G1 + G2; {@code predmetId} null = brojevi preko svih predmeta. */
    public GrupaPregledInfo pregled(Long grupaId, Long predmetId) {
        Grupa grupa = grupa(grupaId);
        proveriPredmet(predmetId);
        List<Student> studenti = studentiGrupe(grupa);

        long brojPredavanja = predavanjeRepository.brojPredavanjaGrupe(grupaId, predmetId);
        long domaciUkupno = domaciRepository.brojDomacihGrupe(grupaId, predmetId);
        Map<Long, Long> prisutan = Brojaci.mapa(aktivnostRepository.prisustvoPoStudentu(grupaId, predmetId));
        Map<Long, Long> vanGrupe = Brojaci.mapa(aktivnostRepository.prisustvoVanGrupePoStudentu(grupaId, predmetId));
        Map<Long, Long> uradjeno = Brojaci.mapa(domaciRepository.brojUradjenihPoStudentu(grupaId, predmetId));
        Map<Long, GrupaStudentStatInfo.TestRef> poslednji = poslednjaPolaganja(grupaId, predmetId);

        long zbirPrisutan = 0;
        long zbirPredavanja = 0;
        List<GrupaStudentStatInfo> redovi = new ArrayList<>();
        for (Student s : studenti) {
            long p = prisutan.getOrDefault(s.getId(), 0L);
            long pred = brojPredavanja + vanGrupe.getOrDefault(s.getId(), 0L);
            zbirPrisutan += p;
            zbirPredavanja += pred;
            redovi.add(new GrupaStudentStatInfo(mapper.map(s, StudentInfo.class), p, pred,
                    uradjeno.getOrDefault(s.getId(), 0L), domaciUkupno, poslednji.get(s.getId())));
        }
        Double prosecnaPrisutnost = zbirPredavanja == 0 ? null : (double) zbirPrisutan / zbirPredavanja;

        GrupaInfo info = new GrupaInfo(grupa.getId(), grupa.getNaziv(), grupa.getGodinaUpisa(), (long) studenti.size());
        return new GrupaPregledInfo(info, studenti.size(), brojPredavanja, prosecnaPrisutnost,
                otvorenOnboarding(grupaId), redovi);
    }

    /**
     * G3: predavanja grupe na predmetu (opciono školske godine {@code godina}; null = sva) po datumu, i studenti grupe
     * sa tipom aktivnosti po predavanju. {@code predmetId} je obavezan.
     */
    public PrisustvoMatricaInfo prisustvo(Long grupaId, Long predmetId, Integer godina) {
        if (predmetId == null) {
            throw new SystemException("Neispravan parametar: predmetId.", HttpStatus.BAD_REQUEST);
        }
        SkolskaGodina.proveri(godina);
        Grupa grupa = grupa(grupaId);
        proveriPredmet(predmetId);

        List<Predavanje> predavanja = predavanjeRepository.findAll(Specification.allOf(
                PredavanjeSpecs.grupa(grupaId), PredavanjeSpecs.predmet(predmetId), PredavanjeSpecs.godina(godina)),
                PO_DATUMU);
        Map<Long, Map<Long, TipAktivnosti>> celije = new HashMap<>();
        if (!predavanja.isEmpty()) {
            List<Long> ids = predavanja.stream().map(Predavanje::getId).toList();
            for (Object[] red : aktivnostRepository.celijePrisustva(ids, grupaId)) {
                // aktivnost bez tipa (stari red) je prisustvo; dve aktivnosti na istom predavanju: jača pobeđuje
                TipAktivnosti tip = red[2] == null ? TipAktivnosti.PRISUSTVO : (TipAktivnosti) red[2];
                celije.computeIfAbsent((Long) red[0], k -> new HashMap<>())
                        .merge((Long) red[1], tip, (x, y) -> x.getVrednost() >= y.getVrednost() ? x : y);
            }
        }

        List<PrisustvoMatricaInfo.Red> redovi = studentiGrupe(grupa).stream().map(s -> {
            Map<Long, String> tip = new LinkedHashMap<>();
            Map<Long, TipAktivnosti> studentove = celije.getOrDefault(s.getId(), Map.of());
            for (Predavanje p : predavanja) {
                TipAktivnosti t = studentove.get(p.getId());
                if (t != null) tip.put(p.getId(), t.name());
            }
            return new PrisustvoMatricaInfo.Red(mapper.map(s, StudentInfo.class), tip);
        }).toList();
        List<PrisustvoMatricaInfo.PredavanjeRef> kolone = predavanja.stream()
                .map(p -> new PrisustvoMatricaInfo.PredavanjeRef(p.getId(), p.getRb(), p.getDatum(), p.getTema()))
                .toList();
        return new PrisustvoMatricaInfo(kolone, redovi);
    }

    private Grupa grupa(Long grupaId) {
        return grupaRepository.findById(grupaId)
                .orElseThrow(() -> new SystemException("Grupa ne postoji! ID = " + grupaId, HttpStatus.NOT_FOUND));
    }

    private void proveriPredmet(Long predmetId) {
        if (predmetId != null && !predmetRepository.existsById(predmetId)) {
            throw new SystemException("Predmet ne postoji! ID = " + predmetId, HttpStatus.NOT_FOUND);
        }
    }

    private List<Student> studentiGrupe(Grupa grupa) {
        List<Student> studenti = new ArrayList<>(studentRepository.findByGrupa(grupa));
        studenti.sort(PO_INDEKSU);
        return studenti;
    }

    /** Prvi red po studentu je poslednje polaganje (upit je sortiran). */
    private Map<Long, GrupaStudentStatInfo.TestRef> poslednjaPolaganja(Long grupaId, Long predmetId) {
        Map<Long, GrupaStudentStatInfo.TestRef> poslednji = new HashMap<>();
        for (Object[] red : polaganjeRepository.poslednjaPolaganjaGrupe(grupaId, predmetId)) {
            poslednji.putIfAbsent((Long) red[0], new GrupaStudentStatInfo.TestRef((Long) red[1], (String) red[2],
                    (LocalDate) red[3], (Double) red[4], (Integer) red[5]));
        }
        return poslednji;
    }

    /** Najnovija otvorena sesija (aktivna, nije istekla, nije popunjena) ili null. */
    private OnboardingSesijaInfo otvorenOnboarding(Long grupaId) {
        return onboardingService.sesijeGrupe(grupaId).stream()
                .filter(OnboardingSesijaInfo::isOtvorena)
                .findFirst()
                .orElse(null);
    }
}
