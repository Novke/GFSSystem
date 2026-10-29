package tri.novica.gfssystem.service.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tri.novica.gfssystem.dto.push.PushPodesavanjaInfo;
import tri.novica.gfssystem.dto.push.PushVrstaCmd;
import tri.novica.gfssystem.dto.push.PushVrstaInfo;
import tri.novica.gfssystem.entity.prijava.VrstaObavestenja;
import tri.novica.gfssystem.entity.push.PodesavanjeNastavnikaPush;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.push.PodesavanjeNastavnikaPushRepository;
import tri.novica.gfssystem.service.prijava.NovoObavestenje;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Push nastavniku (spec aplikacije 6.6): svako novo nastavničko obaveštenje ({@link NovoObavestenje}, posle commit-a) ide
 * na sve važeće pretplate nastavnika, ako vrsta nije isključena u {@code podesavanja_nastavnika_push} (bez reda =
 * uključeno). {@code Urgency: high}, TTL 24 h. Tekst bez imena studenta i bez razloga ({@link PushTekstovi#nastavniku}).
 * Zvonce i mejl rade kao do sada.
 */
@Service
@Slf4j
public class PushNastavniku {

    static final String NEPOZNATA_VRSTA = "Nepoznata vrsta obaveštenja.";
    static final String BEZ_IZBORA = "Izaberi da li je obaveštenje uključeno.";
    static final Duration TTL = Duration.ofHours(24);

    private final PodesavanjeNastavnikaPushRepository repository;
    private final PushPosiljalac posiljalac;
    private final Clock clock;

    public PushNastavniku(PodesavanjeNastavnikaPushRepository repository, PushPosiljalac posiljalac, Clock clock) {
        this.repository = repository;
        this.posiljalac = posiljalac;
        this.clock = clock;
    }

    /** Sve vrste redom kao u enumu, sa nazivom i prekidačem. */
    @Transactional(readOnly = true)
    public PushPodesavanjaInfo podesavanja() {
        Map<String, Boolean> sacuvano = repository.findAll().stream()
                .collect(Collectors.toMap(PodesavanjeNastavnikaPush::getVrsta, PodesavanjeNastavnikaPush::isUkljuceno));
        return new PushPodesavanjaInfo(Arrays.stream(VrstaObavestenja.values())
                .map(v -> new PushVrstaInfo(v.name(), v.naziv(), sacuvano.getOrDefault(v.name(), true)))
                .toList());
    }

    /** Menja prekidač jedne vrste; nepoznata vrsta ili bez {@code ukljuceno} je 400. Vraća celu listu posle izmene. */
    @Transactional
    public PushPodesavanjaInfo izmeni(PushVrstaCmd cmd) {
        VrstaObavestenja v = vrsta(cmd == null ? null : cmd.vrsta());
        if (cmd.ukljuceno() == null) {
            throw new SystemException(BEZ_IZBORA, HttpStatus.BAD_REQUEST);
        }
        repository.upisi(v.name(), cmd.ukljuceno(), LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS));
        log.info("Push nastavniku za vrstu {}: {}", v, cmd.ukljuceno() ? "uključen" : "isključen");
        return podesavanja();
    }

    @Transactional(readOnly = true)
    public boolean ukljuceno(VrstaObavestenja v) {
        return repository.findById(v.name()).map(PodesavanjeNastavnikaPush::isUkljuceno).orElse(true);
    }

    /**
     * Novo obaveštenje je potvrđeno: push na nastavnikove uređaje. Greška se samo loguje (nikad ne obara zahtev koji je
     * obaveštenje napravio; njegova transakcija je već potvrđena).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void naNovoObavestenje(NovoObavestenje d) {
        // nit zahteva još drži svoju vezu: čitanje ide na nit push izvršioca (PushPosiljalac#uPozadiniPosleCommita)
        posiljalac.uPozadini(() -> {
            try {
                if (!ukljucenoNovaTransakcija(d.vrsta())) {
                    return;
                }
                int n = posiljalac.nastavniku(PushTekstovi.nastavniku(d.vrsta(), d.termin(), d.broj(), d.prag()), TTL, true);
                if (n > 0) {
                    log.info("Push nastavniku stavljen u red, vrsta={}, poruka={}", d.vrsta(), n);
                }
            } catch (RuntimeException e) {
                log.error("Push nastavniku nije stavljen u red, vrsta={}: {}", d.vrsta(), e.getClass().getSimpleName());
            }
        });
    }

    private boolean ukljucenoNovaTransakcija(VrstaObavestenja v) {
        return posiljalac.uNovojTransakciji(() -> ukljuceno(v));
    }

    static VrstaObavestenja vrsta(String naziv) {
        if (naziv == null) {
            throw new SystemException(NEPOZNATA_VRSTA, HttpStatus.BAD_REQUEST);
        }
        return Arrays.stream(VrstaObavestenja.values()).filter(v -> v.name().equals(naziv)).findFirst()
                .orElseThrow(() -> new SystemException(NEPOZNATA_VRSTA, HttpStatus.BAD_REQUEST));
    }
}
