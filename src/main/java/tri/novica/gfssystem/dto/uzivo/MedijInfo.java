package tri.novica.gfssystem.dto.uzivo;

/** Otpremljena slika; javna adresa je {@code api/public/mediji/{id}}. */
public record MedijInfo(String id, String naziv, String mime, long velicina) {
}
