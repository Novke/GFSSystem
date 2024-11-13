package tri.novica.gfssystem.dto.test.tip;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateTipTestaCmd {

    @NotNull
    @Length(min = 2)
    private String naziv;
    @NotNull
    private Long predmetId;

}
