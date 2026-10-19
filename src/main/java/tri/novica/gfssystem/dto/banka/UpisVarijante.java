package tri.novica.gfssystem.dto.banka;

/**
 * Ishod Klodovog upisa varijante koji {@code VarijantaService} vraća MCP fasadi (ona ga prevodi u {@code McpUpisVarijante};
 * alat ga nikad ne vidi): sačuvana varijanta, šta je urađeno i PNG iz istog rendera ({@code null} kad PNG nije tražen, kad
 * upis nije renderovao (keš) ili kad se tekst nije preveo; kad je palo samo rešenje, nosi PNG zadatka). Čist podatak, pa
 * je u {@code dto}, ne u {@code service}.
 */
public record UpisVarijante(VarijantaDetails varijanta, Radnja radnja, SlikeRendera slike) {

    /** {@code NOVA}: nova varijanta; {@code IZMENJENA}: sopstveni nacrt izmenjen na mestu; {@code KOPIJA}: nov nacrt koji zamenjuje original. */
    public enum Radnja { NOVA, IZMENJENA, KOPIJA }
}
