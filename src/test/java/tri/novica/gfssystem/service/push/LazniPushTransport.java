package tri.novica.gfssystem.service.push;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Lažni {@link PushTransport} za testove: pamti svaki zahtev umesto da ide na push servis i vraća status koji test zada
 * (podrazumevano 201). Uvozi se sa {@code @Import(LazniPushTransport.class)} i zamenjuje transport bez slanja iz test
 * resursa ({@code gfs.push.http=false}). Šifrovanje, VAPID i provera adrese u {@link PushPosiljalac} rade kao u produkciji;
 * sadržaj poruke test čita sa {@link LazniUredjaj#desifruj}. Jedna instanca po kontekstu: svaki test počinje sa
 * {@link #ocisti()}.
 */
@TestConfiguration(proxyBeanMethods = false)
@Primary
public class LazniPushTransport implements PushTransport {

    private final Object monitor = new Object();
    private final List<Zahtev> primljeni = new ArrayList<>();
    private ToIntFunction<Zahtev> status = z -> 201;

    @Override
    public int posalji(Zahtev zahtev) {
        int s;
        synchronized (monitor) {
            primljeni.add(zahtev);
            s = status.applyAsInt(zahtev);
            monitor.notifyAll();
        }
        return s;
    }

    /** Briše primljene i vraća status 201. */
    public void ocisti() {
        synchronized (monitor) {
            primljeni.clear();
            status = z -> 201;
        }
    }

    public void status(ToIntFunction<Zahtev> s) {
        synchronized (monitor) {
            status = s;
        }
    }

    public List<Zahtev> primljeni() {
        synchronized (monitor) {
            return List.copyOf(primljeni);
        }
    }

    /** Čeka da stigne bar {@code n} zahteva (do isteka roka) i vraća sve primljene. */
    public List<Zahtev> cekaj(int n, Duration rok) throws InterruptedException {
        long kraj = System.nanoTime() + rok.toNanos();
        synchronized (monitor) {
            while (primljeni.size() < n) {
                long ostalo = kraj - System.nanoTime();
                if (ostalo <= 0) break;
                monitor.wait(Math.max(1, ostalo / 1_000_000));
            }
            return List.copyOf(primljeni);
        }
    }
}
