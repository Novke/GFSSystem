package tri.novica.gfssystem.dto.student;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tri.novica.gfssystem.dto.grupa.GrupaInfo;

/** Red liste studenata. {@code grupa}, {@code email} i {@code brojTelefona} mogu biti null (stari redovi). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentListItem {
    private Long id;
    private String ime;
    private String prezime;
    private String indeks;
    private Integer godina;
    private String email;
    private String brojTelefona;
    private GrupaInfo grupa;
}
