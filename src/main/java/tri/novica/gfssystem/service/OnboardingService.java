package tri.novica.gfssystem.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.grupa.GrupaInfo;
import tri.novica.gfssystem.dto.onboarding.*;
import tri.novica.gfssystem.entity.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.*;
import tri.novica.gfssystem.utility.TokenGenerator;
import tri.novica.gfssystem.validation.PrijavaPP;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Onboarding studenata: nastavnik otvori sesiju za grupu (link/QR), studenti šalju prijave, nastavnik ih prihvata
 * ili odbija. Samo novi studenti: dedupe po (normalizovan indeks, godina upisa). Svaka izmena nad sesijom prvo
 * zaključa red sesije, pa prijave i obrade jedne sesije idu redom.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OnboardingService {

    public static final String STUDENT_POSTOJI = "Student sa ovim indeksom već postoji u sistemu, javi se asistentu.";
    public static final String PRIJAVA_CEKA = "Prijava sa ovim indeksom već čeka odobrenje.";
    public static final String ZATVORENA = "Prijave za ovu grupu su zatvorene.";
    public static final String NEPOZNAT_TOKEN = "Link za prijavu nije ispravan.";
    static final String VEC_OBRADJENA = "Prijava je već obrađena.";
    static final String PRESKOCENO = "Preskočeno pri prihvatanju svih: student sa ovim indeksom i godinom upisa već postoji.";
    static final int PODRAZUMEVANO_DANA = 7;
    static final int PODRAZUMEVANO_MAX = 200;

    private final OnboardingSesijaRepository sesijaRepository;
    private final PrijavaRepository prijavaRepository;
    private final StudentRepository studentRepository;
    private final GrupaRepository grupaRepository;
    private final TokenGenerator tokenGenerator;
    private final ModelMapper mapper;
    private final Clock clock;

    // ---------------------------------------------------------------- nastavnik

    @Transactional
    public OnboardingSesijaInfo kreiraj(Long grupaId, CreateOnboardingCmd cmd) {
        Grupa grupa = grupaRepository.findById(grupaId)
                .orElseThrow(() -> new SystemException("Grupa ne postoji! ID = " + grupaId, HttpStatus.NOT_FOUND));
        CreateOnboardingCmd c = cmd == null ? new CreateOnboardingCmd() : cmd;
        int dana = rokDana(c.getIsticeZaDana());
        int max = c.getMaxPrijava() == null ? PODRAZUMEVANO_MAX : c.getMaxPrijava();
        if (max < 1 || max > 1000) {
            throw new SystemException("Maksimalan broj prijava mora biti od 1 do 1000.", HttpStatus.BAD_REQUEST);
        }
        String napomena = napomena(c.getNapomena());

        LocalDateTime sada = sada();
        OnboardingSesija sesija = new OnboardingSesija();
        sesija.setGrupa(grupa);
        sesija.setToken(noviToken());
        sesija.setAktivna(true);
        sesija.setKreirano(sada);
        sesija.setIstice(sada.plusDays(dana));
        sesija.setMaxPrijava(max);
        sesija.setNapomena(napomena);
        return info(sesijaRepository.save(sesija));
    }

    @Transactional(readOnly = true)
    public List<OnboardingSesijaInfo> sesijeGrupe(Long grupaId) {
        return sesijaRepository.findAllByGrupaIdOrderByKreiranoDesc(grupaId).stream().map(this::info).toList();
    }

    @Transactional(readOnly = true)
    public OnboardingSesijaDetails details(Long sesijaId) {
        OnboardingSesija sesija = sesijaRepository.findById(sesijaId).orElseThrow(() -> nemaSesije(sesijaId));
        return details(sesija, null);
    }

    @Transactional
    public OnboardingSesijaInfo promeniAktivnost(Long sesijaId, UpdateOnboardingCmd cmd) {
        OnboardingSesija sesija = zakljucana(sesijaId);
        if (cmd == null || cmd.getAktivna() == null) {
            throw new SystemException("Polje aktivna je obavezno.", HttpStatus.BAD_REQUEST);
        }
        int dana = rokDana(cmd.getIsticeZaDana());
        LocalDateTime sada = sada();
        sesija.setAktivna(cmd.getAktivna());
        if (cmd.getAktivna() && !sada.isBefore(sesija.getIstice())) {
            // ponovno otvaranje istekle sesije bez novog roka ne bi imalo efekta
            sesija.setIstice(sada.plusDays(dana));
        }
        return info(sesijaRepository.save(sesija));
    }

    @Transactional
    public PrijavaInfo izmeniPrijavu(Long sesijaId, Long prijavaId, UpdatePrijavaCmd cmd) {
        OnboardingSesija sesija = zakljucana(sesijaId);
        Prijava prijava = prijavaUSesiji(sesija, prijavaId);
        if (prijava.getStatus() != StatusPrijave.NA_CEKANJU) {
            throw new SystemException("Može se menjati samo prijava na čekanju.", HttpStatus.CONFLICT);
        }
        // Menja upravljani entitet; ako dedupe ispod baci izuzetak, transakcija se poništava.
        PrijavaPP.primeni(cmd, prijava, clock);
        if (studentRepository.postojiStudent(prijava.getIndeks(), prijava.getGodina())) {
            throw new SystemException(StudentService.DUPLIKAT, HttpStatus.BAD_REQUEST);
        }
        if (prijavaRepository.existsBySesijaIdAndIndeksAndGodinaAndStatusAndIdNot(sesija.getId(), prijava.getIndeks(),
                prijava.getGodina(), StatusPrijave.NA_CEKANJU, prijava.getId())) {
            throw new SystemException(PRIJAVA_CEKA, HttpStatus.BAD_REQUEST);
        }
        return prijavaInfo(prijavaRepository.save(prijava));
    }

    @Transactional
    public OnboardingSesijaDetails prihvati(Long sesijaId, Long prijavaId) {
        OnboardingSesija sesija = zakljucana(sesijaId);
        Prijava prijava = naCekanju(sesija, prijavaId);
        if (studentRepository.postojiStudent(prijava.getIndeks(), prijava.getGodina())) {
            throw new SystemException("Student sa indeksom " + prijava.getIndeks() + " (godina upisa "
                    + prijava.getGodina() + ") već postoji u sistemu.", HttpStatus.CONFLICT);
        }
        upisi(sesija, prijava);
        return details(sesija, null);
    }

    @Transactional
    public OnboardingSesijaDetails odbij(Long sesijaId, Long prijavaId, OdbijPrijavuCmd cmd) {
        OnboardingSesija sesija = zakljucana(sesijaId);
        Prijava prijava = naCekanju(sesija, prijavaId);
        prijava.setNapomena(napomena(cmd == null ? null : cmd.getNapomena()));
        prijava.setStatus(StatusPrijave.ODBIJENA);
        prijava.setObradjeno(sada());
        prijavaRepository.save(prijava);
        return details(sesija, null);
    }

    /** Prihvata sve prijave na čekanju redom; konflikt ne ruši operaciju, prijava ostaje na čekanju sa napomenom. */
    @Transactional
    public OnboardingSesijaDetails prihvatiSve(Long sesijaId) {
        OnboardingSesija sesija = zakljucana(sesijaId);
        int prihvaceno = 0;
        List<String> preskoceni = new ArrayList<>();
        for (Prijava prijava : prijavaRepository.findAllBySesijaIdAndStatusOrderByPodnetoAsc(sesija.getId(),
                StatusPrijave.NA_CEKANJU)) {
            if (studentRepository.postojiStudent(prijava.getIndeks(), prijava.getGodina())) {
                prijava.setNapomena(PRESKOCENO);
                prijavaRepository.save(prijava);
                preskoceni.add(prijava.getIndeks());
            } else {
                upisi(sesija, prijava);
                prihvaceno++;
            }
        }
        String poruka = "Prihvaćeno: " + prihvaceno + ".";
        if (!preskoceni.isEmpty()) {
            poruka += " Preskočeno (indeks već postoji): " + String.join(", ", preskoceni) + ".";
        }
        return details(sesija, poruka);
    }

    // ---------------------------------------------------------------- javno (bez autentifikacije)

    @Transactional(readOnly = true)
    public JavniUpisInfo javniInfo(String token) {
        proveriFormat(token);
        OnboardingSesija sesija = sesijaRepository.findByToken(token)
                .orElseThrow(() -> new SystemException(NEPOZNAT_TOKEN, HttpStatus.NOT_FOUND));
        Grupa grupa = sesija.getGrupa();
        return new JavniUpisInfo(grupa.getNaziv(), grupa.getGodinaUpisa(),
                otvorena(sesija, prijavaRepository.countBySesijaId(sesija.getId())), sesija.getIstice());
    }

    @Transactional
    public PodnetaPrijavaInfo podnesi(String token, PodnesiPrijavuCmd cmd, String ip) {
        proveriFormat(token);
        OnboardingSesija sesija = sesijaRepository.findByTokenForUpdate(token)
                .orElseThrow(() -> new SystemException(NEPOZNAT_TOKEN, HttpStatus.NOT_FOUND));
        if (!otvorena(sesija, prijavaRepository.countBySesijaId(sesija.getId()))) {
            throw new SystemException(ZATVORENA, HttpStatus.GONE);
        }
        Prijava prijava = new Prijava();
        PrijavaPP.primeni(cmd, prijava, clock);
        if (studentRepository.postojiStudent(prijava.getIndeks(), prijava.getGodina())) {
            throw new SystemException(STUDENT_POSTOJI, HttpStatus.BAD_REQUEST);
        }
        if (prijavaRepository.existsBySesijaIdAndIndeksAndGodinaAndStatus(sesija.getId(), prijava.getIndeks(),
                prijava.getGodina(), StatusPrijave.NA_CEKANJU)) {
            throw new SystemException(PRIJAVA_CEKA, HttpStatus.BAD_REQUEST);
        }
        prijava.setSesija(sesija);
        prijava.setStatus(StatusPrijave.NA_CEKANJU);
        prijava.setPodneto(sada());
        Prijava sacuvana = prijavaRepository.save(prijava);
        log.info("Onboarding prijava: sesija={}, indeks={}, ip={}", sesija.getId(), sacuvana.getIndeks(), ip);
        return new PodnetaPrijavaInfo(sacuvana.getId());
    }

    // ---------------------------------------------------------------- pomoćne

    private LocalDateTime sada() {
        return LocalDateTime.now(clock);
    }

    /** Isto pravilo za nastavnički ekran i javnu formu: aktivna, nije istekla, nije popunjena. */
    private boolean otvorena(OnboardingSesija s, long brojPrijava) {
        return s.isAktivna() && sada().isBefore(s.getIstice()) && brojPrijava < s.getMaxPrijava();
    }

    /** Token van formata se odbija bez upita u bazu. */
    private static void proveriFormat(String token) {
        if (token == null || !TokenGenerator.FORMAT.matcher(token).matches()) {
            throw new SystemException(NEPOZNAT_TOKEN, HttpStatus.NOT_FOUND);
        }
    }

    private OnboardingSesija zakljucana(Long sesijaId) {
        return sesijaRepository.findByIdForUpdate(sesijaId).orElseThrow(() -> nemaSesije(sesijaId));
    }

    private static SystemException nemaSesije(Long sesijaId) {
        return new SystemException("Onboarding sesija ne postoji! ID = " + sesijaId, HttpStatus.NOT_FOUND);
    }

    private Prijava prijavaUSesiji(OnboardingSesija sesija, Long prijavaId) {
        return prijavaRepository.findByIdAndSesijaId(prijavaId, sesija.getId())
                .orElseThrow(() -> new SystemException("Prijava ne postoji! ID = " + prijavaId, HttpStatus.NOT_FOUND));
    }

    private Prijava naCekanju(OnboardingSesija sesija, Long prijavaId) {
        Prijava prijava = prijavaUSesiji(sesija, prijavaId);
        if (prijava.getStatus() != StatusPrijave.NA_CEKANJU) {
            throw new SystemException(VEC_OBRADJENA, HttpStatus.CONFLICT);
        }
        return prijava;
    }

    /** Pravi studenta u grupi sesije iz prijave i označava prijavu prihvaćenom. */
    private void upisi(OnboardingSesija sesija, Prijava prijava) {
        Student student = new Student();
        student.setIme(prijava.getIme());
        student.setPrezime(prijava.getPrezime());
        student.setGodina(prijava.getGodina());
        student.setIndeks(prijava.getIndeks());
        student.setBrojTelefona(prijava.getBrojTelefona());
        student.setEmail(prijava.getEmail());
        student.setDatumRodjenja(prijava.getDatumRodjenja());
        student.setOpstina(prijava.getOpstina());
        student.setGrupa(sesija.getGrupa());
        Student sacuvan = studentRepository.save(student);

        prijava.setStatus(StatusPrijave.PRIHVACENA);
        prijava.setStudent(sacuvan);
        prijava.setObradjeno(sada());
        prijava.setNapomena(null);   // eventualna napomena "Preskočeno ..." iz ranijeg prihvatanja svih više ne važi
        prijavaRepository.save(prijava);
    }

    private String noviToken() {
        String token;
        do {
            token = tokenGenerator.novi();
        } while (sesijaRepository.existsByToken(token));
        return token;
    }

    private static int rokDana(Integer isticeZaDana) {
        int dana = isticeZaDana == null ? PODRAZUMEVANO_DANA : isticeZaDana;
        if (dana < 1 || dana > 60) {
            throw new SystemException("Rok važenja mora biti od 1 do 60 dana.", HttpStatus.BAD_REQUEST);
        }
        return dana;
    }

    /** Trim; prazno -> null; najviše 255 znakova (kolona). */
    private static String napomena(String napomena) {
        if (napomena == null) return null;
        String s = napomena.trim();
        if (s.isEmpty()) return null;
        if (s.length() > 255) {
            throw new SystemException("Napomena može imati najviše 255 znakova.", HttpStatus.BAD_REQUEST);
        }
        return s;
    }

    private OnboardingSesijaInfo info(OnboardingSesija s) {
        long brojPrijava = prijavaRepository.countBySesijaId(s.getId());
        long brojNaCekanju = prijavaRepository.countBySesijaIdAndStatus(s.getId(), StatusPrijave.NA_CEKANJU);
        return new OnboardingSesijaInfo(s.getId(), s.getToken(), mapper.map(s.getGrupa(), GrupaInfo.class),
                s.isAktivna(), otvorena(s, brojPrijava), s.getKreirano(), s.getIstice(), s.getMaxPrijava(),
                brojPrijava, brojNaCekanju, s.getNapomena());
    }

    private static PrijavaInfo prijavaInfo(Prijava p) {
        return new PrijavaInfo(p.getId(), p.getIme(), p.getPrezime(), p.getIndeks(), p.getGodina(), p.getEmail(),
                p.getBrojTelefona(), p.getDatumRodjenja(), p.getOpstina(), p.getStatus(), p.getPodneto(),
                p.getObradjeno(), p.getStudent() == null ? null : p.getStudent().getId(), p.getNapomena());
    }

    private OnboardingSesijaDetails details(OnboardingSesija sesija, String poruka) {
        List<PrijavaInfo> prijave = prijavaRepository.findAllBySesijaIdOrderByPodnetoAsc(sesija.getId()).stream()
                .map(OnboardingService::prijavaInfo).toList();
        return new OnboardingSesijaDetails(info(sesija), prijave, poruka);
    }
}
