package tri.novica.gfssystem.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum TipAktivnosti {

    PRISUSTVO(1),
    ZADATAK(2),
    SA_ZVEZDICOM(4);

    private final int vrednost;
}
