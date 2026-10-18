package tri.novica.gfssystem.dto.banka;

/** Fajl uz LaTeX izvor za render servis (slika u radnom direktorijumu); {@code sadrzaj} je base64. */
public record RenderPrilog(String ime, String ekstenzija, String sadrzaj) {
}
