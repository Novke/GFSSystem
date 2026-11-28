package tri.novica.gfssystem.dto.test.online;

/** Produženje: 1-120 minuta, jednom primerku ili ({@code primerakId == null}) svima koji rade. */
public record ProduziCmd(Integer minuti, Long primerakId) {
}
