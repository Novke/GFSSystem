package tri.novica.gfssystem.service.uzivo;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Koji učesnici imaju otvorenu WebSocket vezu (samo u memoriji; posle restarta se telefoni sami ponovo povežu).
 * Učesnik može imati više veza (dva taba, ponovno povezivanje pre nego što stara veza istekne).
 */
@Component
public class PovezanostRegistar {

    private final Map<String, Long> ucesnikPoSesiji = new HashMap<>();
    private final Map<Long, Integer> brojVeza = new HashMap<>();

    public synchronized void povezan(Long ucesnikId, String sesijaId) {
        if (ucesnikId == null || sesijaId == null) return;
        Long prethodni = ucesnikPoSesiji.put(sesijaId, ucesnikId);
        if (ucesnikId.equals(prethodni)) return;
        if (prethodni != null) umanji(prethodni);
        brojVeza.merge(ucesnikId, 1, Integer::sum);
    }

    public synchronized void prekinut(String sesijaId) {
        if (sesijaId == null) return;
        Long ucesnikId = ucesnikPoSesiji.remove(sesijaId);
        if (ucesnikId != null) umanji(ucesnikId);
    }

    public synchronized boolean jePovezan(Long ucesnikId) {
        return brojVeza.containsKey(ucesnikId);
    }

    public synchronized int brojPovezanih(Collection<Long> ucesnikIds) {
        int n = 0;
        for (Long id : ucesnikIds) {
            if (brojVeza.containsKey(id)) n++;
        }
        return n;
    }

    private void umanji(Long ucesnikId) {
        brojVeza.computeIfPresent(ucesnikId, (id, n) -> n <= 1 ? null : n - 1);
    }
}
