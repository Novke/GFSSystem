package tri.novica.gfssystem.dto.banka;

import tri.novica.gfssystem.dto.uzivo.MedijInfo;

/** Prilog varijante: ime u LaTeX-u i medij. PDF prilog se javno ne servira, vidi se samo kroz renderovani SVG. */
public record PrilogInfo(String ime, MedijInfo medij) {
}
