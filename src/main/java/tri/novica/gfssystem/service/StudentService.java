package tri.novica.gfssystem.service;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tri.novica.gfssystem.dto.grupa.GrupaInfo;
import tri.novica.gfssystem.dto.predmet.PredmetInfo;
import tri.novica.gfssystem.dto.student.CreateStudentCmd;
import tri.novica.gfssystem.dto.student.StudentDetails;
import tri.novica.gfssystem.dto.student.StudentFilter;
import tri.novica.gfssystem.dto.student.StudentInfo;
import tri.novica.gfssystem.dto.student.StudentListItem;
import tri.novica.gfssystem.dto.student.UpdateStudentCmd;
import tri.novica.gfssystem.dto.student.pregled.*;
import tri.novica.gfssystem.dto.test.tip.TipTestaInfo;
import tri.novica.gfssystem.entity.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.*;
import tri.novica.gfssystem.repository.spec.StudentSpecs;
import tri.novica.gfssystem.utility.Brojaci;
import tri.novica.gfssystem.utility.IndeksUtil;
import tri.novica.gfssystem.utility.StudentMapper;
import tri.novica.gfssystem.utility.Utility;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudentService {

    /** Polja po kojima lista studenata sme da se sortira ({@code PageableUtil.proveri}). */
    public static final Set<String> SORT_POLJA = Set.of("prezime", "ime", "indeks", "godina");
    public static final Sort PODRAZUMEVANI_SORT = Sort.by(Sort.Order.asc("prezime"), Sort.Order.asc("ime"));

    public static final String DUPLIKAT = "Student sa ovim indeksom i godinom upisa već postoji u sistemu.";

    private final StudentRepository studentRepository;
    private final GrupaRepository grupaRepository;
    private final PredmetRepository predmetRepository;
    private final AktivnostRepository aktivnostRepository;
    private final DomaciRepository domaciRepository;
    private final PolaganjeRepository polaganjeRepository;
    private final ModelMapper mapper;

    public List<StudentInfo> findAll() {
        return studentRepository.findAll()
                .stream().map(
                        student -> mapper.map(student, StudentInfo.class)
                ).toList();
    }

    public StudentPregledDetails findById(Long id) {
        Student student = studentRepository.findByIdFetchDetails(id)
                .orElseThrow(() -> new SystemException("Student ne postoji! ID = " + id, HttpStatus.NOT_FOUND));

        StudentPregledDetails details = StudentMapper.INSTANCE.toPregledDetails(student);
        details.setAktivnosti(student.getAktivnosti().stream().map(StudentMapper.INSTANCE::toAktivnostDetails).toList());
//        details.setAktivnosti(StudentMapper.INSTANCE.toAktivnostDetails(student.getAktivnosti()));
        details.setUradjeniDomaci(student.getUradjeniDomaci().stream().map(StudentMapper.INSTANCE::toDomaciDetails).toList());
//        details.setUradjeniDomaci(StudentMapper.INSTANCE.toDomaciDetails(student.getUradjeniDomaci()));
        details.setPolaganja(student.getPolaganja().stream().map(StudentMapper.INSTANCE::toTestDetails).toList());
//        details.setPolaganja(StudentMapper.INSTANCE.toTestDetails(student.getPolaganja()));


        return details;
    }

    public StudentInfo create(CreateStudentCmd studentCmd) {
        Grupa grupa = findGrupaOrThrow(studentCmd.getGrupaId());
        String indeks = IndeksUtil.normalizuj(studentCmd.getIndeks());
        proveriDuplikat(indeks, studentCmd.getGodina(), null);

        Student newStudent = mapper.map(studentCmd, Student.class);
        newStudent.setIndeks(indeks);
        newStudent.setEmail(email(studentCmd.getEmail()));
        newStudent.setGrupa(grupa);

        return mapper.map(studentRepository.save(newStudent),
                StudentInfo.class
        );

    }

    /**
     * Izmena studenta (pun zamenski zapis): izostavljeno opciono polje se briše. Grupa je nova grupa studenta, pa
     * isto služi za premeštanje. Indeks se normalizuje i dedupe-uje kao pri dodavanju, ali bez samog studenta.
     */
    public StudentInfo update(Long id, UpdateStudentCmd cmd) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new SystemException("Student ne postoji! ID = " + id, HttpStatus.NOT_FOUND));
        Grupa grupa = findGrupaOrThrow(cmd.getGrupaId());
        String indeks = IndeksUtil.normalizuj(cmd.getIndeks());
        proveriDuplikat(indeks, cmd.getGodina(), id);

        // eksplicitno (ne ModelMapper): on preskače null, a PUT mora da obriše email, opštinu...
        student.setIme(cmd.getIme());
        student.setPrezime(cmd.getPrezime());
        student.setIndeks(indeks);
        student.setGodina(cmd.getGodina());
        student.setEmail(email(cmd.getEmail()));
        student.setBrojTelefona(cmd.getBrojTelefona());
        student.setDatumRodjenja(cmd.getDatumRodjenja());
        student.setOpstina(cmd.getOpstina());
        student.setGrupa(grupa);

        return mapper.map(studentRepository.save(student), StudentInfo.class);
    }

    /** Dedupe po (normalizovan indeks, godina upisa); {@code izuzetId} je student koji se menja (ili null pri dodavanju). */
    private void proveriDuplikat(String normalizovanIndeks, int godina, Long izuzetId) {
        boolean postoji = izuzetId == null
                ? studentRepository.postojiStudent(normalizovanIndeks, godina)
                : studentRepository.postojiDrugiStudent(normalizovanIndeks, godina, izuzetId);
        if (postoji) {
            throw new SystemException(DUPLIKAT, HttpStatus.BAD_REQUEST);
        }
    }

    /** Trim i mala slova; prazno -> null. */
    private static String email(String email) {
        if (email == null) return null;
        String e = email.trim().toLowerCase(Locale.ROOT);
        return e.isEmpty() ? null : e;
    }

    private Grupa findGrupaOrThrow(Long grupaId) {
        Grupa grupa = grupaRepository.findById(grupaId)
                .orElseThrow(() -> new SystemException("Grupa ne postoji! ID = " + grupaId, HttpStatus.NOT_FOUND));
        return grupa;
    }

    public List<StudentInfo> findAllByGroup(Long id) {
        Grupa grupa = findGrupaOrThrow(id);

        return studentRepository.findByGrupa(grupa)
                .stream()
                .sorted(Comparator.comparingInt(student -> Utility.index2int(student.getIndeks())))
                .map(
                        student -> mapper.map(student, StudentInfo.class)
                ).toList();
    }

    public StudentNaPredmetuDetails findByIdAndPredmet(Long studentId, Long predmetId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new SystemException("Student ne postoji! ID = " + studentId, HttpStatus.NOT_FOUND));
        Predmet predmet = predmetRepository.findById(predmetId)
                .orElseThrow(() -> new SystemException("Predmet ne postoji! ID = " + predmetId, HttpStatus.NOT_FOUND));

        StudentNaPredmetuDetails details = new StudentNaPredmetuDetails();
        details.setStudent(mapper.map(student, StudentInfo.class));
        details.setPredmet(mapper.map(predmet, PredmetInfo.class));
        details.setGrupaNaziv(student.getGrupa().getNaziv());

        // Aktivnosti za ovaj predmet
        List<Aktivnost> aktivnosti = aktivnostRepository.findAllByStudentAndPredavanjePredmetOrderByPredavanjeDatumAsc(student, predmet);
        details.setAktivnosti(aktivnosti.stream().map(StudentMapper.INSTANCE::toAktivnostDetails).toList());

        // Izračunaj ukupno poena aktivnosti
        double ukupnoAktivnost = 0;
        for (Aktivnost a : aktivnosti) {
            switch (a.getTip()) {
                case PRISUSTVO -> ukupnoAktivnost += 1;
                case ZADATAK -> ukupnoAktivnost += 2;
                case SA_ZVEZDICOM -> ukupnoAktivnost += 4;
            }
        }
        details.setUkupnoPoenaAktivnost(ukupnoAktivnost);

        // Domaći za ovaj predmet
        List<UradjenDomaci> uradjeniDomaci = domaciRepository.findUradjeniDomaciByStudentAndPredmet(student, predmet);
        details.setDomaci(uradjeniDomaci.stream().map(StudentMapper.INSTANCE::toDomaciDetails).toList());

        // Izračunaj ukupno poena domaći
        double ukupnoDomaci = 0;
        for (UradjenDomaci ud : uradjeniDomaci) {
            ukupnoDomaci += 4 + (6 * ud.getBodovi()) / 10.0;
        }
        details.setUkupnoPoenaDomaci(Math.round(ukupnoDomaci * 100.0) / 100.0);

        // Polaganja za ovaj predmet, grupisana po tipu testa
        List<Polaganje> polaganja = polaganjeRepository.findAllByStudentAndPredmet(studentId, predmetId);

        Map<Long, List<Polaganje>> poTipuTesta = polaganja.stream()
                .collect(Collectors.groupingBy(p -> p.getTest().getTipTesta().getId()));

        List<StudentTestoviPoTipuInfo> testoviPoTipu = new ArrayList<>();
        for (Map.Entry<Long, List<Polaganje>> entry : poTipuTesta.entrySet()) {
            List<Polaganje> polaganjaTipa = entry.getValue();
            if (polaganjaTipa.isEmpty()) continue;

            StudentTestoviPoTipuInfo tipInfo = new StudentTestoviPoTipuInfo();
            TipTesta tipTesta = polaganjaTipa.get(0).getTest().getTipTesta();
            tipInfo.setTipTesta(mapper.map(tipTesta, TipTestaInfo.class));

            List<StudentPregledTestInfo> polaganjaInfo = polaganjaTipa.stream()
                    .map(StudentMapper.INSTANCE::toTestDetails)
                    .sorted(Comparator.comparing(StudentPregledTestInfo::getDatum))
                    .toList();
            tipInfo.setPolaganja(polaganjaInfo);

            // Pronađi najbolje polaganje
            polaganjaInfo.stream()
                    .filter(p -> p.getOstvareniPoeni() != null)
                    .max(Comparator.comparing(StudentPregledTestInfo::getOstvareniPoeni))
                    .ifPresent(tipInfo::setNajboljePolaganje);

            testoviPoTipu.add(tipInfo);
        }

        // Sortiraj po nazivu tipa testa
        testoviPoTipu.sort(Comparator.comparing(t -> t.getTipTesta().getNaziv()));
        details.setTestoviPoTipu(testoviPoTipu);

        return details;
    }

    /**
     * Lista studenata sa filterima i stranicom. {@code pageable} je već prošao {@code PageableUtil.proveri}
     * (REST sloj). Grupa dolazi u istom upitu, broj studenata grupe jednim agregatnim upitom po stranici.
     */
    public PagedModel<StudentListItem> pretraga(StudentFilter f, Pageable pageable) {
        Integer godinaReference = null;
        if (f.starijiOdGrupe() != null) {
            godinaReference = grupaRepository.findById(f.starijiOdGrupe())
                    .orElseThrow(() -> new SystemException("Grupa ne postoji! ID = " + f.starijiOdGrupe(), HttpStatus.NOT_FOUND))
                    .getGodinaUpisa();
        }
        Specification<Student> spec = Specification.allOf(
                StudentSpecs.zaPrikaz(),
                StudentSpecs.grupa(f.grupaId()),
                f.starijiOdGrupe() == null ? Specification.<Student>unrestricted() : StudentSpecs.grupaStarijaOd(godinaReference),
                StudentSpecs.q(f.q()));
        Page<Student> strana = studentRepository.findAll(spec, pageable);

        Set<Long> grupaIds = strana.stream().map(Student::getGrupa).filter(Objects::nonNull)
                .map(Grupa::getId).collect(Collectors.toSet());
        Map<Long, Long> brojStudenata = Brojaci.poId(grupaIds, studentRepository::brojStudenataPoGrupi);

        return new PagedModel<>(strana.map(s -> uListItem(s, brojStudenata)));
    }

    /** Ručno mapiranje (ModelMapper je STRICT, a broj studenata grupe nije polje entiteta). */
    private static StudentListItem uListItem(Student s, Map<Long, Long> brojStudenata) {
        Grupa g = s.getGrupa();
        GrupaInfo grupa = g == null ? null
                : new GrupaInfo(g.getId(), g.getNaziv(), g.getGodinaUpisa(), brojStudenata.getOrDefault(g.getId(), 0L));
        return new StudentListItem(s.getId(), s.getIme(), s.getPrezime(), s.getIndeks(), s.getGodina(), s.getEmail(),
                s.getBrojTelefona(), grupa);
    }
}
