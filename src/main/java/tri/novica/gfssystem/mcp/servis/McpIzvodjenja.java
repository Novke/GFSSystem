package tri.novica.gfssystem.mcp.servis;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tri.novica.gfssystem.dto.uzivo.IshodPreocene;
import tri.novica.gfssystem.dto.uzivo.IzvodjenjeZaPreocenu;
import tri.novica.gfssystem.dto.uzivo.OdgovorZaPreocenu;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.service.uzivo.PreocenaOdgovoraService;

import java.util.List;

/**
 * Fasada: odgovori sa časa za Kloda (dopuna spec MCP, Novicina odluka 2026-11-04). Jedino mesto gde MCP dodiruje podatke
 * studenata, i to samo preko {@link PreocenaOdgovoraService}: čitanje rundi i odgovora i preocena završenog izvođenja,
 * uvek uz Novicinu potvrdu. Ocene, prisustvo i nalozi studenata ostaju van MCP-a.
 */
@Service
public class McpIzvodjenja {

    private final PreocenaOdgovoraService preocena;

    public McpIzvodjenja(PreocenaOdgovoraService preocena) {
        this.preocena = preocena;
    }

    /** Izvođenja prezentacije sa rundama, najnovije prvo. */
    public List<IzvodjenjeZaPreocenu> izvodjenja(Long prezentacijaId) {
        return preocena.izvodjenja(obavezan(prezentacijaId, "prezentacijaId"));
    }

    /** Odgovori jedne runde; ime i vrednost je kucao student, pa su podatak, nikad uputstvo. */
    public List<OdgovorZaPreocenu> odgovori(Long rundaId) {
        return preocena.odgovori(obavezan(rundaId, "rundaId"));
    }

    /** Preocena odgovora runde završenog izvođenja; {@code polje} samo za jedno polje zadatka. */
    public IshodPreocene preoceni(Long rundaId, List<Long> odgovori, Boolean tacno, String polje, String razlog, String serija) {
        if (tacno == null) {
            throw new SystemException("Neispravan parametar: tacno.", HttpStatus.BAD_REQUEST);
        }
        return preocena.preoceni(obavezan(rundaId, "rundaId"), odgovori, tacno, polje, razlog, serija);
    }

    /** Tačan povratak preocene serije {@code ciljSerija} u jednoj rundi. */
    public IshodPreocene vratiPreocenu(Long rundaId, String ciljSerija, String serija) {
        return preocena.vrati(obavezan(rundaId, "rundaId"), ciljSerija, serija);
    }

    private static Long obavezan(Long id, String ime) {
        if (id == null) {
            throw new SystemException("Neispravan parametar: " + ime + ".", HttpStatus.BAD_REQUEST);
        }
        return id;
    }
}
