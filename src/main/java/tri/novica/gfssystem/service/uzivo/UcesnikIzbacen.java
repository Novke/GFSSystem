package tri.novica.gfssystem.service.uzivo;

/** Nastavnik je izbacio učesnika; slušalac (posle commit-a) mu javlja i odbacuje njegove poruke. */
public record UcesnikIzbacen(Long izvodjenjeId, Long ucesnikId) {
}
