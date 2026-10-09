package tri.novica.gfssystem.service;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.ocenjivanje.*;
import tri.novica.gfssystem.dto.predmet.PredmetInfo;
import tri.novica.gfssystem.dto.pregled.StudentPredmetKarticaInfo;
import tri.novica.gfssystem.dto.student.StudentInfo;
import tri.novica.gfssystem.dto.test.tip.TipTestaInfo;
import tri.novica.gfssystem.entity.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.*;
import tri.novica.gfssystem.utility.Brojaci;
import tri.novica.gfssystem.utility.OcenaPragovi;

import java.util.*;

@Service
@RequiredArgsConstructor
public class OcenjivanjeService {

    private final KoeficijentiOcenjivanjaRepository koeficijentiRepo;
    private final PredmetRepository predmetRepository;
    private final TipTestaRepository tipTestaRepository;
    private final GrupaRepository grupaRepository;
    private final AktivnostRepository aktivnostRepository;
    private final DomaciRepository domaciRepository;
    private final PolaganjeRepository polaganjeRepository;
    private final StudentRepository studentRepository;
    private final PredavanjeRepository predavanjeRepository;
    private final ModelMapper mapper;

    public KoeficijentiInfo getKoeficijenti(Long predmetId) {
        Predmet predmet = predmetRepository.findById(predmetId)
                .orElseThrow(() -> new SystemException("Predmet ne postoji! ID = " + predmetId, 404));

        // Dobavi ili kreiraj default koeficijente
        KoeficijentiOcenjivanja koef = koeficijentiRepo.findByPredmetIdFetchTipovi(predmetId)
                .orElseGet(() -> createDefaultKoeficijenti(predmet));

        return mapToInfo(koef, predmet);
    }

    @Transactional
    public KoeficijentiInfo saveKoeficijenti(Long predmetId, SaveKoeficijentiCmd cmd) {
        Predmet predmet = predmetRepository.findById(predmetId)
                .orElseThrow(() -> new SystemException("Predmet ne postoji! ID = " + predmetId, 404));

        // Dobavi ili kreiraj koeficijente (fetch sa tipovima za update)
        KoeficijentiOcenjivanja koef = koeficijentiRepo.findByPredmetIdFetchTipovi(predmetId)
                .orElseGet(() -> new KoeficijentiOcenjivanja(predmet));

        // Ažuriraj vrednosti
        koef.setKoefPrisustvo(cmd.getKoefPrisustvo());
        koef.setKoefZadatak(cmd.getKoefZadatak());
        koef.setKoefZvezdica(cmd.getKoefZvezdica());
        koef.setDomaciFlat(cmd.getDomaciFlat());
        koef.setDomaciVarijansa(cmd.getDomaciVarijansa());
        koef.setKoristiMaxRezultat(cmd.getKoristiMaxRezultat());
        koef.setPrikaziZbirno(cmd.getPrikaziZbirno());
        koef.setMaxAktivnost(cmd.getMaxAktivnost());
        koef.setMaxDomaci(cmd.getMaxDomaci());

        koeficijentiRepo.save(koef);

        // Ažuriraj max poena po tipu testa - update existing, add new, remove missing
        Map<Long, KoeficijentTipTesta> postojeciPoTipu = new HashMap<>();
        for (KoeficijentTipTesta kt : koef.getKoeficijentiTipova()) {
            postojeciPoTipu.put(kt.getTipTesta().getId(), kt);
        }

        Set<Long> noviTipovi = new HashSet<>();
        if (cmd.getKoeficijentiTipova() != null) {
            for (KoeficijentTipTestaCmd tipCmd : cmd.getKoeficijentiTipova()) {
                noviTipovi.add(tipCmd.getTipTestaId());

                KoeficijentTipTesta existing = postojeciPoTipu.get(tipCmd.getTipTestaId());
                if (existing != null) {
                    // Ažuriraj postojeći
                    existing.setMaxPoena(tipCmd.getMaxPoena());
                } else {
                    // Kreiraj novi
                    TipTesta tipTesta = tipTestaRepository.findById(tipCmd.getTipTestaId())
                            .orElseThrow(() -> new SystemException("TipTesta ne postoji! ID = " + tipCmd.getTipTestaId(), 404));
                    KoeficijentTipTesta koefTip = new KoeficijentTipTesta(koef, tipTesta);
                    koefTip.setMaxPoena(tipCmd.getMaxPoena());
                    koef.getKoeficijentiTipova().add(koefTip);
                }
            }
        }

        // Ukloni tipove koji više nisu u cmd
        koef.getKoeficijentiTipova().removeIf(kt -> !noviTipovi.contains(kt.getTipTesta().getId()));

        koeficijentiRepo.save(koef);

        return mapToInfo(koef, predmet);
    }

    public List<RezultatiStudentaInfo> getRezultati(Long predmetId, GetOceneCmd cmd) {
        Predmet predmet = predmetRepository.findById(predmetId)
                .orElseThrow(() -> new SystemException("Predmet ne postoji! ID = " + predmetId, 404));

        KoeficijentiOcenjivanja koef = koeficijentiRepo.findByPredmetIdFetchTipovi(predmetId)
                .orElseGet(() -> createDefaultKoeficijenti(predmet));

        List<TipTesta> tipovi = tipTestaRepository.findAllByPredmetAndAktivanTrue(predmet);

        Grupa grupa = grupaRepository.findByIdFetchStudents(cmd.getGrupaId())
                .orElseThrow(() -> new SystemException("Grupa ne postoji! ID = " + cmd.getGrupaId(), 404));

        // Prikupi sve aktivnosti za grupu i predmet
        List<Aktivnost> aktivnosti = aktivnostRepository.findAllByStudentGrupaAndPredavanjePredmet(grupa, predmet);

        // Prikupi sve domaće
        List<Domaci> domaciList = domaciRepository.findAllByGrupaAndPredmetOrderByDatumAsc(grupa, predmet);
        List<UradjenDomaci> uradjeniDomaci = new ArrayList<>();
        domaciList.forEach(d -> uradjeniDomaci.addAll(d.getUradjeniDomaci()));

        // Prebroj predavanja i domaće za normalizaciju
        long brojPredavanja = aktivnosti.stream()
                .map(a -> a.getPredavanje().getId())
                .distinct()
                .count();
        int brojDomacih = domaciList.size();

        Map<Long, KoeficijentTipTesta> koefPoTipu = koefPoTipu(koef);

        List<RezultatiStudentaInfo> rezultati = new ArrayList<>();
        List<Student> students = new ArrayList<>(grupa.getStudenti());

        for (Student student : students) {
            List<Aktivnost> studentoveAktivnosti = aktivnosti.stream()
                    .filter(a -> Objects.equals(a.getStudent().getId(), student.getId()))
                    .toList();
            List<UradjenDomaci> studentoviDomaci = uradjeniDomaci.stream()
                    .filter(d -> Objects.equals(d.getStudent().getId(), student.getId()))
                    .toList();
            RezultatiStudentaInfo rez = izracunajPoeneStudenta(
                    student, koef, tipovi, studentoveAktivnosti, studentoviDomaci,
                    koefPoTipu, brojPredavanja, brojDomacih
            );
            rezultati.add(rez);
        }

        return rezultati;
    }

    /**
     * S2: kartice studenta po predmetu (predmeti na kojima grupa studenta ima predavanje, test ili domaći, ili student
     * ima aktivnost ili polaganje; po nazivu). Poeni su isti kao red studenta u {@link #getRezultati} za njegovu grupu,
     * ali se računaju samo za njega. Ništa ne upisuje: bez sačuvanih koeficijenata računa sa podrazumevanim (iste
     * vrednosti koje bi {@code getRezultati} upisao).
     */
    @Transactional(readOnly = true)
    public List<StudentPredmetKarticaInfo> karticeStudenta(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new SystemException("Student ne postoji! ID = " + studentId, 404));
        Long grupaId = student.getGrupa() == null ? null : student.getGrupa().getId();
        Map<Long, Long> predavanjaPoPredmetu =
                Brojaci.mapa(predavanjeRepository.brojPredavanjaZaStudentaPoPredmetu(grupaId, studentId));

        List<StudentPredmetKarticaInfo> kartice = new ArrayList<>();
        for (Predmet predmet : predmetRepository.predmetiStudenta(studentId, grupaId)) {
            UlazStudenta ulaz = ulazStudenta(student, predmet);
            RezultatiStudentaInfo rez = izracunajPoeneStudenta(student, ulaz.koef(), ulaz.tipovi(), ulaz.aktivnosti(),
                    ulaz.domaci(), koefPoTipu(ulaz.koef()), ulaz.brojPredavanja(), ulaz.brojDomacih());
            List<Aktivnost> akt = ulaz.aktivnosti();
            long prisutan = akt.stream().map(a -> a.getPredavanje().getId()).distinct().count();
            long zadaci = akt.stream().filter(a -> a.getTip() == TipAktivnosti.ZADATAK).count();
            long zvezdice = akt.stream().filter(a -> a.getTip() == TipAktivnosti.SA_ZVEZDICOM).count();
            Double domaciProsek = ulaz.domaci().isEmpty() ? null
                    : ulaz.domaci().stream().mapToInt(UradjenDomaci::getBodovi).average().orElseThrow();
            kartice.add(new StudentPredmetKarticaInfo(new PredmetInfo(predmet.getId(), predmet.getNaziv()), prisutan,
                    predavanjaPoPredmetu.getOrDefault(predmet.getId(), 0L), zadaci, zvezdice, ulaz.domaci().size(),
                    ulaz.brojDomacih(), domaciProsek, rez.getRezultati(), rez.getUkupno(), rez.getPredlogOcene(),
                    OcenaPragovi.doSledece(rez.getUkupno())));
        }
        return kartice;
    }

    /**
     * Ulaz za jednog studenta na predmetu, isti kao njegov deo ulaza u {@link #getRezultati} za grupu studenta:
     * sve njegove aktivnosti na predmetu, njegovi urađeni domaći među domaćima grupe, imenilac predavanja (predavanja
     * sa aktivnošću bilo kog studenta grupe) i broj domaćih grupe. Student bez grupe: grupa od jednog, bez domaćih.
     */
    private record UlazStudenta(KoeficijentiOcenjivanja koef, List<TipTesta> tipovi, List<Aktivnost> aktivnosti,
                                List<UradjenDomaci> domaci, long brojPredavanja, int brojDomacih) {
    }

    private UlazStudenta ulazStudenta(Student student, Predmet predmet) {
        KoeficijentiOcenjivanja koef = koeficijentiRepo.findByPredmetIdFetchTipovi(predmet.getId())
                .orElseGet(() -> new KoeficijentiOcenjivanja(predmet));
        List<TipTesta> tipovi = tipTestaRepository.findAllByPredmetAndAktivanTrue(predmet);
        List<Aktivnost> aktivnosti = aktivnostRepository.aktivnostiStudentaNaPredmetu(student.getId(), predmet.getId());
        Grupa grupa = student.getGrupa();
        if (grupa == null) {
            long brojPredavanja = aktivnosti.stream().map(a -> a.getPredavanje().getId()).distinct().count();
            return new UlazStudenta(koef, tipovi, aktivnosti, List.of(), brojPredavanja, 0);
        }
        return new UlazStudenta(koef, tipovi, aktivnosti,
                domaciRepository.uradjeniNaDomacimaGrupe(student.getId(), grupa.getId(), predmet.getId()),
                aktivnostRepository.brojPredavanjaSaAktivnoscuGrupe(grupa.getId(), predmet.getId()),
                Math.toIntExact(domaciRepository.countByGrupaIdAndPredmetId(grupa.getId(), predmet.getId())));
    }

    /** maxPoena po tipu testa za brz pristup. */
    private static Map<Long, KoeficijentTipTesta> koefPoTipu(KoeficijentiOcenjivanja koef) {
        Map<Long, KoeficijentTipTesta> koefPoTipu = new HashMap<>();
        for (KoeficijentTipTesta kt : koef.getKoeficijentiTipova()) {
            koefPoTipu.put(kt.getTipTesta().getId(), kt);
        }
        return koefPoTipu;
    }

    /** Poeni jednog studenta; {@code studentoveAktivnosti} i {@code studentoviDomaci} su već samo njegovi. */
    private RezultatiStudentaInfo izracunajPoeneStudenta(
            Student student,
            KoeficijentiOcenjivanja koef,
            List<TipTesta> tipovi,
            List<Aktivnost> studentoveAktivnosti,
            List<UradjenDomaci> studentoviDomaci,
            Map<Long, KoeficijentTipTesta> koefPoTipu,
            long brojPredavanja,
            int brojDomacih
    ) {
        RezultatiStudentaInfo rez = new RezultatiStudentaInfo(mapper.map(student, StudentInfo.class));

        // 1. AKTIVNOSTI - uvek računaj sa koeficijentima
        double aktivnostPoeni = 0;
        for (Aktivnost a : studentoveAktivnosti) {
            switch (a.getTip()) {
                case PRISUSTVO -> aktivnostPoeni += koef.getKoefPrisustvo();
                case ZADATAK -> aktivnostPoeni += koef.getKoefZadatak();
                case SA_ZVEZDICOM -> aktivnostPoeni += koef.getKoefZvezdica();
            }
        }

        // Normalizacija aktivnosti - ako je maxAktivnost setovan, normalizuj na taj broj
        if (koef.getMaxAktivnost() != null && brojPredavanja > 0) {
            double maxMoguceAktivnost = brojPredavanja * (koef.getKoefPrisustvo() + koef.getKoefZadatak() + koef.getKoefZvezdica());
            if (maxMoguceAktivnost > 0) {
                aktivnostPoeni = (aktivnostPoeni / maxMoguceAktivnost) * koef.getMaxAktivnost();
            }
        }
        rez.setPoeniAktivnost(aktivnostPoeni);

        // 2. DOMAĆI - uvek računaj sa koeficijentima (flat + varijansa)
        double domaciPoeni = 0;
        for (UradjenDomaci ud : studentoviDomaci) {
            domaciPoeni += koef.getDomaciFlat();
            domaciPoeni += (koef.getDomaciVarijansa() * ud.getBodovi()) / 10.0; // bodovi su 0-10
        }

        // Normalizacija domaćih - ako je maxDomaci setovan, normalizuj na taj broj
        if (koef.getMaxDomaci() != null && brojDomacih > 0) {
            double maxMoguceDomaci = brojDomacih * (koef.getDomaciFlat() + koef.getDomaciVarijansa());
            if (maxMoguceDomaci > 0) {
                domaciPoeni = (domaciPoeni / maxMoguceDomaci) * koef.getMaxDomaci();
            }
        }
        rez.setPoeniDomaci(domaciPoeni);

        // 3. TESTOVI
        for (TipTesta tip : tipovi) {
            Double poeni;

            // MAX ili POSLEDNJI rezultat
            if (Boolean.TRUE.equals(koef.getKoristiMaxRezultat())) {
                poeni = polaganjeRepository.findMaxPoeniByStudentAndTipTesta(student.getId(), tip.getId());
            } else {
                poeni = polaganjeRepository.findPoslednjiPoeniByStudentAndTipTesta(student.getId(), tip.getId());
            }

            if (poeni == null) {
                poeni = 0.0;
            }

            // Normalizacija testa - ako je maxPoena setovan za ovaj tip, normalizuj
            KoeficijentTipTesta koefTip = koefPoTipu.get(tip.getId());
            if (koefTip != null && koefTip.getMaxPoena() != null) {
                // Pronađi max poena testa (koliko je test nosio)
                Double testMaxPoena = polaganjeRepository.findMaxPoenaTestByTipTesta(tip.getId());
                if (testMaxPoena != null && testMaxPoena > 0) {
                    poeni = (poeni / testMaxPoena) * koefTip.getMaxPoena();
                }
            }

            MaxPoeniStudentaNaTestuInfo testRez = new MaxPoeniStudentaNaTestuInfo(
                    mapper.map(tip, TipTestaInfo.class),
                    poeni
            );
            rez.getRezultati().add(testRez);
        }

        rez.izracunajUkupno();
        return rez;
    }

    private KoeficijentiOcenjivanja createDefaultKoeficijenti(Predmet predmet) {
        KoeficijentiOcenjivanja koef = new KoeficijentiOcenjivanja(predmet);
        // Default vrednosti su već postavljene u entitetu
        return koeficijentiRepo.save(koef);
    }

    private KoeficijentiInfo mapToInfo(KoeficijentiOcenjivanja koef, Predmet predmet) {
        KoeficijentiInfo info = new KoeficijentiInfo();
        info.setId(koef.getId());
        info.setPredmetId(predmet.getId());
        info.setKoefPrisustvo(koef.getKoefPrisustvo());
        info.setKoefZadatak(koef.getKoefZadatak());
        info.setKoefZvezdica(koef.getKoefZvezdica());
        info.setDomaciFlat(koef.getDomaciFlat());
        info.setDomaciVarijansa(koef.getDomaciVarijansa());
        info.setKoristiMaxRezultat(koef.getKoristiMaxRezultat());
        info.setPrikaziZbirno(koef.getPrikaziZbirno());
        info.setMaxAktivnost(koef.getMaxAktivnost());
        info.setMaxDomaci(koef.getMaxDomaci());

        List<KoeficijentTipTestaInfo> tipovi = new ArrayList<>();
        for (KoeficijentTipTesta kt : koef.getKoeficijentiTipova()) {
            KoeficijentTipTestaInfo tipInfo = new KoeficijentTipTestaInfo();
            tipInfo.setTipTestaId(kt.getTipTesta().getId());
            tipInfo.setTipTestaNaziv(kt.getTipTesta().getNaziv());
            tipInfo.setMaxPoena(kt.getMaxPoena());
            tipovi.add(tipInfo);
        }

        // Dodaj i tipove koji još nisu u koeficijentima (bez maxPoena)
        List<TipTesta> sviTipovi = tipTestaRepository.findAllByPredmetAndAktivanTrue(predmet);
        Set<Long> postojeciTipovi = new HashSet<>();
        for (KoeficijentTipTestaInfo ti : tipovi) {
            postojeciTipovi.add(ti.getTipTestaId());
        }
        for (TipTesta tip : sviTipovi) {
            if (!postojeciTipovi.contains(tip.getId())) {
                KoeficijentTipTestaInfo tipInfo = new KoeficijentTipTestaInfo();
                tipInfo.setTipTestaId(tip.getId());
                tipInfo.setTipTestaNaziv(tip.getNaziv());
                tipInfo.setMaxPoena(null);
                tipovi.add(tipInfo);
            }
        }

        info.setKoeficijentiTipova(tipovi);
        return info;
    }
}
