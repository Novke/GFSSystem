package tri.novica.gfssystem.service.fajlovi;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tri.novica.gfssystem.dto.fajlovi.MejlFajlaOdgovor;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.entity.fajlovi.FajlPrezentacije;
import tri.novica.gfssystem.entity.prijava.VrstaPokusaja;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.StudentRepository;
import tri.novica.gfssystem.repository.fajlovi.FajlPrezentacijeRepository;
import tri.novica.gfssystem.service.mejl.MejlPoruka;
import tri.novica.gfssystem.service.mejl.MejlPoruke;
import tri.novica.gfssystem.service.mejl.MejlPosleCommita;
import tri.novica.gfssystem.service.mejl.MejlServis;
import tri.novica.gfssystem.service.portal.KontekstStudenta;
import tri.novica.gfssystem.service.prijava.MaskaMejla;
import tri.novica.gfssystem.service.prijava.PokusajiServis;
import tri.novica.gfssystem.service.uzivo.MedijService;

import java.time.Duration;

/**
 * "Pošalji mi na mejl" (spec fajlova, odeljak 5): fajl ide kao prilog samo na {@code studenti.email} tog studenta.
 * <ol>
 * <li>Provere i upis pokušaja u jednoj kratkoj transakciji: 404 kad ne važi ni pravilo naloga ni važeći kod
 * ({@link PristupFajlovima}), 409 bez mejla, 503 bez SMTP-a, pa limiti ({@link VrstaPokusaja#MEJL_FAJLA}: 10 na sat i
 * 20 dnevno po studentu, ključ {@code s<id>}; 150 dnevno ukupno, ključ {@code *}), pa upis dva pokušaja. Pokušaj se
 * beleži pre slanja, pa neuspeh slanja ne vraća kvotu.</li>
 * <li>Posle commit-a (transakcija je gotova i zahtev ne drži DB vezu) zadatak ide na {@code mejlStudentimaIzvrsilac};
 * pun red je 503. Bajtovi se čitaju <b>tek u izvršiocu</b>: red od 500 poruka sa prilozima do 10 MB bi inače držao do
 * 5 GB u memoriji. Fajl obrisan u međuvremenu je samo zapis u logu.</li>
 * </ol>
 * Dva istovremena zahteva istog studenta mogu da prođu kroz limit zajedno (nema zaključavanja): višak je najviše
 * broj paralelnih zahteva, što je za ove limite prihvatljivo. Log nikad ne nosi adresu, ime ni naziv fajla.
 */
@Service
@Slf4j
public class MejlFajlova {

    static final int PO_STUDENTU_NA_SAT = 10;
    static final int PO_STUDENTU_DNEVNO = 20;
    static final int UKUPNO_DNEVNO = 150;
    static final String UKUPNI_KLJUC = "*";

    private final PristupFajlovima pristup;
    private final FajlPrezentacijeRepository fajlovi;
    private final StudentRepository studenti;
    private final PokusajiServis pokusaji;
    private final MaskaMejla maska;
    private final MejlServis mejl;
    private final MejlPoruke poruke;
    private final MejlPosleCommita slanje;
    private final MedijService medijService;
    private final TaskExecutor izvrsilac;
    private final TransactionTemplate tx;

    public MejlFajlova(PristupFajlovima pristup, FajlPrezentacijeRepository fajlovi, StudentRepository studenti,
                       PokusajiServis pokusaji, MaskaMejla maska, MejlServis mejl, MejlPoruke poruke,
                       MejlPosleCommita slanje, MedijService medijService,
                       @Qualifier("mejlStudentimaIzvrsilac") TaskExecutor izvrsilac, PlatformTransactionManager tm) {
        this.pristup = pristup;
        this.fajlovi = fajlovi;
        this.studenti = studenti;
        this.pokusaji = pokusaji;
        this.maska = maska;
        this.mejl = mejl;
        this.poruke = poruke;
        this.slanje = slanje;
        this.medijService = medijService;
        this.izvrsilac = izvrsilac;
        this.tx = new TransactionTemplate(tm);
    }

    /** Podaci za mejl izvučeni u transakciji (entiteti ne izlaze iz nje). */
    private record Priprema(String adresa, String ime, String nazivFajla, String predmet, String prezentacija,
                            String medijId, String mime) {
    }

    public MejlFajlaOdgovor posalji(long studentId, Long fajlId) {
        Priprema p = tx.execute(status -> pripremi(studentId, fajlId));
        try {
            izvrsilac.execute(() -> {
                MejlPoruka.Prilog prilog;
                try {
                    prilog = new MejlPoruka.Prilog(p.nazivFajla(), p.mime(), medijService.bajtovi(p.medijId()));
                } catch (RuntimeException e) {
                    // fajl obrisan posle pripreme: nema šta da se pošalje (samo klasa, poruka može da nosi putanju)
                    log.warn("Mejl sa fajlom nije poslat: sadržaj nije dostupan ({})", e.getClass().getSimpleName());
                    return;
                }
                slanje.posaljiSaPokusajima(poruke.fajl(p.adresa(), p.ime(), p.nazivFajla(), p.predmet(),
                        p.prezentacija(), prilog));
            });
        } catch (TaskRejectedException e) {
            log.warn("Mejl sa fajlom nije u redu: red za slanje studentima je pun");
            throw new SystemException("Slanje mejla trenutno ne radi.", HttpStatus.SERVICE_UNAVAILABLE);
        }
        return new MejlFajlaOdgovor(maska.maska(p.adresa()));
    }

    private Priprema pripremi(long studentId, Long fajlId) {
        FajlPrezentacije f = fajlId == null ? null : fajlovi.findById(fajlId).orElse(null);
        if (f == null || !(pristup.smeNalog(studentId, f) || pristup.naVazecemKoduZaStudenta(studentId, f))) {
            throw KontekstStudenta.nema();
        }
        Student s = studenti.findById(studentId).orElseThrow(KontekstStudenta::nema);
        String adresa = s.getEmail() == null ? "" : s.getEmail().strip();
        if (adresa.isEmpty()) {
            throw new SystemException("U GFS-u nemaš upisan mejl. Javi se nastavniku.", HttpStatus.CONFLICT);
        }
        if (!mejl.dostupan()) {
            throw new SystemException("Slanje mejla trenutno ne radi.", HttpStatus.SERVICE_UNAVAILABLE);
        }
        String kljuc = "s" + studentId;
        if (pokusaji.dostignut(VrstaPokusaja.MEJL_FAJLA, kljuc, PO_STUDENTU_NA_SAT, Duration.ofHours(1))
                || pokusaji.dostignut(VrstaPokusaja.MEJL_FAJLA, kljuc, PO_STUDENTU_DNEVNO, Duration.ofDays(1))) {
            throw new SystemException("Već si poslao dosta fajlova. Pokušaj ponovo za sat.", HttpStatus.TOO_MANY_REQUESTS);
        }
        if (pokusaji.dostignut(VrstaPokusaja.MEJL_FAJLA, UKUPNI_KLJUC, UKUPNO_DNEVNO, Duration.ofDays(1))) {
            throw new SystemException("Danas je poslato previše fajlova mejlom. Preuzmi fajl na računaru.",
                    HttpStatus.TOO_MANY_REQUESTS);
        }
        pokusaji.zabeleziUTransakciji(VrstaPokusaja.MEJL_FAJLA, kljuc);
        pokusaji.zabeleziUTransakciji(VrstaPokusaja.MEJL_FAJLA, UKUPNI_KLJUC);
        return new Priprema(adresa, s.getIme(), f.getNaziv(), f.getPrezentacija().getPredmet().getNaziv(),
                f.getPrezentacija().getNaziv(), f.getMedij().getId(), f.getMedij().getMime());
    }
}
