package tri.novica.gfssystem.service.uzivo;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Izbačeni učesnici (samo u memoriji): njihove STOMP poruke se tiho odbacuju i pre servisa. Posle restarta je skup
 * prazan, ali izbačen učesnik se više ne može povezati (rukovanje proverava bazu), a servis odgovora ga odbija i sam.
 * Id-jevi učesnika su jedinstveni, pa skup ne treba čistiti po izvođenju (nekoliko id-jeva po času).
 */
@Component
public class IzbaceniRegistar {

    private final Set<Long> izbaceni = ConcurrentHashMap.newKeySet();

    /** Posle commit-a (izbacivanje koje se poništi ne odbacuje poruke). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onIzbacen(UcesnikIzbacen e) {
        if (e.ucesnikId() != null) izbaceni.add(e.ucesnikId());
    }

    public boolean jeIzbacen(Long ucesnikId) {
        return ucesnikId != null && izbaceni.contains(ucesnikId);
    }
}
