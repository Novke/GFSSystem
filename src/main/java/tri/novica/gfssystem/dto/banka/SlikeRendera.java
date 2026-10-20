package tri.novica.gfssystem.dto.banka;

/**
 * PNG render varijante za Kloda (spec MCP 6.4): {@code zadatak} i {@code resenje} (null kad rešenja nema ili nije
 * traženo) iz istog prevođenja kao SVG; ne čuva se. Čist podatak koji servis vraća fasadi, pa je u {@code dto}, ne u
 * {@code service}.
 */
public record SlikeRendera(byte[] zadatak, byte[] resenje) {
}
