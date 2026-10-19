package tri.novica.gfssystem.service.mejl;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Lažni {@link MejlServis} za testove: pamti poslate poruke umesto da ide na SMTP. Uvozi se sa
 * {@code @Import(LazniMejlServis.class)} i zamenjuje {@code SmtpMejlServis} ({@code @Primary}). Jedna instanca po
 * kontekstu, pa svaki test na početku poziva {@link #ocisti()} (vraća i sva podešavanja).
 * <ul>
 *   <li>{@link #poslate()} / {@link #cekaj(int, Duration)}: šta je stiglo, odnosno čekanje na N poruka (do isteka roka);</li>
 *   <li>{@link #kasnjenje(Duration)}: veštačko trajanje slanja, da test dokaže da zahtev ne čeka mejl;</li>
 *   <li>{@link #neuspeh(int)}: prvih N poziva {@code posalji} baca izuzetak (ponavljanje); {@link #pokusaja()} broji pozive;</li>
 *   <li>{@link #dostupan(boolean)}: simulira nepodešen SMTP.</li>
 * </ul>
 */
@TestConfiguration(proxyBeanMethods = false)
@Primary
public class LazniMejlServis implements MejlServis {

    private final Object monitor = new Object();
    private final List<MejlPoruka> poslate = new ArrayList<>();
    private boolean dostupan = true;
    private Duration kasnjenje = Duration.ZERO;
    private int preostaliNeuspesi;
    private int pokusaja;

    @Override
    public boolean dostupan() {
        synchronized (monitor) {
            return dostupan;
        }
    }

    @Override
    public void posalji(MejlPoruka poruka) {
        Duration pauza;
        synchronized (monitor) {
            pokusaja++;
            if (preostaliNeuspesi > 0) {
                preostaliNeuspesi--;
                throw new IllegalStateException("Lažni neuspeh slanja");
            }
            pauza = kasnjenje;
        }
        if (!pauza.isZero()) {
            try {
                Thread.sleep(pauza.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        synchronized (monitor) {
            poslate.add(poruka);
            monitor.notifyAll();
        }
    }

    /** Kopija svega što je do sada poslato, redom. */
    public List<MejlPoruka> poslate() {
        synchronized (monitor) {
            return List.copyOf(poslate);
        }
    }

    /** Čeka da stigne bar {@code n} poruka; posle isteka roka baca {@link AssertionError}. Vraća kopiju poslatih. */
    public List<MejlPoruka> cekaj(int n, Duration rok) {
        long kraj = System.nanoTime() + rok.toNanos();
        synchronized (monitor) {
            while (poslate.size() < n) {
                long preostalo = kraj - System.nanoTime();
                if (preostalo <= 0) {
                    throw new AssertionError("Čekano " + n + " mejlova, stiglo " + poslate.size() + " za " + rok);
                }
                try {
                    monitor.wait(Math.max(1, preostalo / 1_000_000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("Prekinuto čekanje na mejl", e);
                }
            }
            return List.copyOf(poslate);
        }
    }

    /** Isto kao {@link #cekaj(int, Duration)} sa rokom od 5 s. */
    public List<MejlPoruka> cekaj(int n) {
        return cekaj(n, Duration.ofSeconds(5));
    }

    /** Veštačko trajanje svakog uspešnog slanja (poruka se upiše tek posle njega). */
    public void kasnjenje(Duration kasnjenje) {
        synchronized (monitor) {
            this.kasnjenje = kasnjenje;
        }
    }

    /** Prvih {@code n} sledećih poziva {@code posalji} baca izuzetak. */
    public void neuspeh(int n) {
        synchronized (monitor) {
            preostaliNeuspesi = n;
        }
    }

    public void dostupan(boolean dostupan) {
        synchronized (monitor) {
            this.dostupan = dostupan;
        }
    }

    /** Broj poziva {@code posalji} (i neuspelih) od poslednjeg {@link #ocisti()}. */
    public int pokusaja() {
        synchronized (monitor) {
            return pokusaja;
        }
    }

    /** Briše poruke, brojač i sva podešavanja (kasnjenje, neuspeh, dostupan). */
    public void ocisti() {
        synchronized (monitor) {
            poslate.clear();
            pokusaja = 0;
            preostaliNeuspesi = 0;
            kasnjenje = Duration.ZERO;
            dostupan = true;
        }
    }
}
