package tri.novica.gfssystem.service.mejl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * SMTP preko {@link JavaMailSender} (Spring Boot {@code spring.mail.*}). Dostupan je samo kad su podešeni i host i
 * pošiljalac ({@code gfs.mail.from}); inače {@link #posalji} baca {@link IllegalStateException}.
 */
@Service
public class SmtpMejlServis implements MejlServis {

    private final JavaMailSender sender;
    private final String host;
    private final String from;

    public SmtpMejlServis(JavaMailSender sender,
                          @Value("${spring.mail.host:}") String host,
                          @Value("${gfs.mail.from:}") String from) {
        this.sender = sender;
        this.host = host;
        this.from = from;
    }

    @Override
    public boolean dostupan() {
        return !host.isBlank() && !from.isBlank();
    }

    @Override
    public void posalji(MejlPoruka poruka) {
        if (!dostupan()) {
            throw new IllegalStateException("Mejl nije podešen.");
        }
        SimpleMailMessage m = new SimpleMailMessage();
        m.setFrom(from);
        m.setTo(poruka.za());
        m.setSubject(poruka.naslov());
        m.setText(poruka.telo());
        sender.send(m);
    }
}
