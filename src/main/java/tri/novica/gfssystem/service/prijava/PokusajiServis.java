package tri.novica.gfssystem.service.prijava;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.entity.prijava.Pokusaj;
import tri.novica.gfssystem.entity.prijava.VrstaPokusaja;
import tri.novica.gfssystem.repository.prijava.PokusajRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Brojač pokušaja za ograničenja (spec 4.2, 5.2, 6): red u bazi po pokušaju, pa preživljava restart. Upis ide u
 * posebnoj transakciji ({@code REQUIRES_NEW}), da pokušaj ostane i kad pozivalac posle baci izuzetak (neuspela prijava
 * vraća 401, ali se mora upisati). Ključ je IP, id studenta, heš indeksa ili fiksna vrednost, zavisno od vrste.
 */
@Service
@RequiredArgsConstructor
public class PokusajiServis {

    private static final int MAX_KLJUC = 128;

    private final PokusajRepository repository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void zabelezi(VrstaPokusaja vrsta, String kljuc) {
        Pokusaj p = new Pokusaj();
        p.setVrsta(vrsta);
        p.setKljuc(kljuc(kljuc));
        p.setVreme(LocalDateTime.now(clock));
        repository.save(p);
    }

    /**
     * Upis u <b>pozivaočevoj</b> transakciji ({@code MANDATORY}): za brojač koji mora ići zajedno sa ostalim upisima pod
     * zaključanim redom (npr. kodovi studenta, {@code KodServis}). {@code REQUIRES_NEW} bi tamo uzeo drugu vezu iz pula
     * dok prva drži zaključan red, a pokušaj bi bio vidljiv pre nego što je kod upisan.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void zabeleziUTransakciji(VrstaPokusaja vrsta, String kljuc) {
        Pokusaj p = new Pokusaj();
        p.setVrsta(vrsta);
        p.setKljuc(kljuc(kljuc));
        p.setVreme(LocalDateTime.now(clock));
        repository.save(p);
    }

    @Transactional(readOnly = true)
    public long broj(VrstaPokusaja vrsta, String kljuc, Duration prozor) {
        return repository.countByVrstaAndKljucAndVremeAfter(vrsta, kljuc(kljuc), LocalDateTime.now(clock).minus(prozor));
    }

    public boolean dostignut(VrstaPokusaja vrsta, String kljuc, int limit, Duration prozor) {
        return broj(vrsta, kljuc, prozor) >= limit;
    }

    /** Kolona je {@code varchar(128)}; prazan ključ (npr. nepoznat IP) dobija fiksnu vrednost da brojač ipak radi. */
    private static String kljuc(String kljuc) {
        if (kljuc == null || kljuc.isEmpty()) {
            return "nepoznato";
        }
        return kljuc.length() > MAX_KLJUC ? kljuc.substring(0, MAX_KLJUC) : kljuc;
    }
}
