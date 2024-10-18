package tri.novica.gfssystem.dto.domaci;

import lombok.Data;
import lombok.NoArgsConstructor;
import tri.novica.gfssystem.entity.TipAktivnosti;

@Data
@NoArgsConstructor
public class DomaciStudentiInfo {
    private Long studentId;
    private Long domaciId;
    private String ime;
    private String prezime;
    private String indeks;
    private Integer godina;
    private TipAktivnosti tip;
    private String predavanjaNapomene;
    private Long uradjenDomaciId;
    private Integer bodovi;
    private String uradjenDomaciNapomene;
    private Boolean prepisivanje;
}
