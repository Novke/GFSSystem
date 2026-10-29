package tri.novica.gfssystem.dto.portal;

import java.time.LocalDateTime;

/** Ocena na nalogu: {@code objavljeno=false} i ništa drugo dok Novica ne objavi; posle objave sopstveni snimak. */
public record OcenaStudenta(boolean objavljeno, LocalDateTime objavljenoU, SnimakOcene ocena) {
    public static final OcenaStudenta NIJE_OBJAVLJENO = new OcenaStudenta(false, null, null);
}
