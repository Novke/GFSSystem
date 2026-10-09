package tri.novica.gfssystem.dto.uzivo;

/** Red rang-liste; {@code ucesnikId} se ne šalje u javnom stanju (javni builder ga postavlja na {@code null}). */
public record RangStavka(int mesto, Long ucesnikId, String ime, int poeni) {
}
