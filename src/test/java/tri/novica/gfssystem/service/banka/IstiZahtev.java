package tri.novica.gfssystem.service.banka;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.function.Supplier;

/**
 * Kao open-in-view u HTTP zahtevu: jedan EntityManager za sve transakcije posla (MCP fasada zove više metoda servisa u
 * jednom zahtevu, npr. {@code detalji} pa izmenu, ili {@code brojStavki} pa {@code kopiraj}).
 */
final class IstiZahtev {

    private IstiZahtev() {
    }

    static <T> T uIstomZahtevu(EntityManagerFactory emf, Supplier<T> posao) {
        EntityManager em = emf.createEntityManager();
        TransactionSynchronizationManager.bindResource(emf, new EntityManagerHolder(em));
        try {
            return posao.get();
        } finally {
            TransactionSynchronizationManager.unbindResource(emf);
            em.close();
        }
    }
}
