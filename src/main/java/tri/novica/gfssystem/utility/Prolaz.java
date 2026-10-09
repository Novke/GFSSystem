package tri.novica.gfssystem.utility;

import tri.novica.gfssystem.entity.Polaganje;

/**
 * Pravilo prolaza na testu, jedino mesto u kodu (JPQL upit {@code PolaganjeRepository.statistikaPoTestu} ga ponavlja
 * istim redosledom uslova i mora da ostane usklađen). Prag prolaza ({@code Test.pragProlaza}, u poenima) bira
 * nastavnik za svaki test posebno i opcion je: kad je {@code null}, za test ne postoji pojam prolaza i sve
 * prolaznosti (procenat, broj položenih i palih) su {@code null}. Inače je polaganje položeno kad su
 * {@code ostvareniPoeni} upisani, {@code ostvareniPoeni >= pragProlaza} (prag je uključen) i {@code prepisivao}
 * nije {@code true}. Imenilac procenta su polaganja sa upisanim poenima (null kad ih nema). Sačuvano polje
 * {@code polozio} se ovde ignoriše (u stvarnoj bazi je uvek NULL, UI ga ne postavlja).
 */
public final class Prolaz {

    private Prolaz() {
    }

    /** Polaganje je položeno po pragu; bez praga ({@code null}) ili bez poena nikad nije. */
    public static boolean polozeno(Double poeni, Integer pragProlaza, Boolean prepisivao) {
        return pragProlaza != null
                && poeni != null
                && !Boolean.TRUE.equals(prepisivao)
                && poeni >= pragProlaza;
    }

    /** Isto, ali {@code null} kad test nema prag (za DTO pregleda: bez praga nema pojma prolaza, ni "pao"). */
    public static Boolean polozenoIliNull(Double poeni, Integer pragProlaza, Boolean prepisivao) {
        return pragProlaza == null ? null : polozeno(poeni, pragProlaza, prepisivao);
    }

    /** Isto za entitet, prag se uzima sa njegovog testa. */
    public static boolean polozeno(Polaganje p) {
        return polozeno(p.getOstvareniPoeni(), p.getTest() == null ? null : p.getTest().getPragProlaza(), p.getPrepisivao());
    }
}
