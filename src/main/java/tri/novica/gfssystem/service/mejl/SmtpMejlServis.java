package tri.novica.gfssystem.service.mejl;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Date;
import java.util.UUID;

/**
 * SMTP preko {@link JavaMailSender} (Spring Boot {@code spring.mail.*}). Dostupan je samo kad su podešeni i host i
 * pošiljalac ({@code gfs.mail.from}); inače {@link #posalji} baca {@link IllegalStateException}.
 * <p>
 * Poruka: {@code From} sa imenom za prikaz ({@code gfs.mail.ime}, podrazumevano "GFS · Građevinski fakultet Subotica";
 * Google traži da ime samo identifikuje pošiljaoca, bez imena primaoca i bez naslova) i adresom iz {@code gfs.mail.from};
 * {@code Date} iz {@code Clock}-a; {@code Message-ID} sa domenom fronta (prvi https URL iz {@code gfs.front.url}, npr.
 * {@code <uuid@gfs.trif.rs>}), inače onaj koji pravi JavaMail. Sa HTML-om je telo {@code multipart/alternative} (tekst
 * pa HTML, UTF-8), bez njega {@code text/plain}. {@code Reply-To} se namerno ne postavlja: drugačija besplatna adresa
 * (npr. nastavnikov Gmail) u {@code Reply-To} je poznat znak za spam filtere (SpamAssassin {@code FREEMAIL_REPLYTO}),
 * a odgovor na isti nalog je u redu.
 */
@Service
public class SmtpMejlServis implements MejlServis {

    static final String PODRAZUMEVANO_IME = "GFS · Građevinski fakultet Subotica";

    private final JavaMailSender sender;
    private final String host;
    private final String from;
    private final String ime;
    private final String domenPoruke;
    private final Clock clock;

    @Autowired
    public SmtpMejlServis(JavaMailSender sender,
                          @Value("${spring.mail.host:}") String host,
                          @Value("${gfs.mail.from:}") String from,
                          @Value("${gfs.mail.ime:" + PODRAZUMEVANO_IME + "}") String ime,
                          @Value("${gfs.front.url:}") String frontUrl,
                          Clock clock) {
        this.sender = sender;
        this.host = host;
        this.from = from == null ? "" : from.strip();
        this.ime = ime == null ? "" : ime.strip();
        this.domenPoruke = domenFronta(frontUrl);
        this.clock = clock;
    }

    /** Za testove: bez imena i domena, sistemski sat. */
    SmtpMejlServis(JavaMailSender sender, String host, String from) {
        this(sender, host, from, PODRAZUMEVANO_IME, "", Clock.systemUTC());
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
        MimeMessage m = new PorukaSaId(sender.createMimeMessage(), domenPoruke);
        try {
            m.setFrom(posiljalac());
            m.setRecipient(MimeMessage.RecipientType.TO, new InternetAddress(poruka.za(), true));
            m.setSubject(poruka.naslov(), StandardCharsets.UTF_8.name());
            m.setSentDate(Date.from(clock.instant()));
            if (poruka.html() == null) {
                m.setText(poruka.telo(), StandardCharsets.UTF_8.name());
            } else {
                MimeMultipart alternativa = new MimeMultipart("alternative");
                MimeBodyPart tekst = new MimeBodyPart();
                tekst.setText(poruka.telo(), StandardCharsets.UTF_8.name());
                MimeBodyPart html = new MimeBodyPart();
                html.setText(poruka.html(), StandardCharsets.UTF_8.name(), "html");
                alternativa.addBodyPart(tekst);
                alternativa.addBodyPart(html);
                m.setContent(alternativa);
            }
        } catch (MessagingException e) {
            // bez poruke izuzetka: može da nosi adresu
            throw new MailPreparationException("Mejl nije sastavljen: " + e.getClass().getSimpleName());
        }
        sender.send(m);
    }

    private InternetAddress posiljalac() throws AddressException {
        InternetAddress a = new InternetAddress(from, true);
        if ((a.getPersonal() == null || a.getPersonal().isBlank()) && !ime.isEmpty()) {
            try {
                a.setPersonal(ime, StandardCharsets.UTF_8.name());
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException(e);
            }
        }
        return a;
    }

    /** Host prvog https URL-a iz liste (zarez); prazno kad ga nema (lokalni razvoj na http-u). */
    static String domenFronta(String frontUrl) {
        if (frontUrl == null) {
            return "";
        }
        for (String u : frontUrl.split(",")) {
            String s = u.strip();
            if (s.startsWith("https://")) {
                String h = URI.create(s).getHost();
                return h == null ? "" : h;
            }
        }
        return "";
    }

    /** {@code Message-ID} sa domenom aplikacije umesto imena kontejnera (JavaMail uzima lokalni host). */
    static final class PorukaSaId extends MimeMessage {
        private final String domen;

        PorukaSaId(MimeMessage izvor, String domen) {
            super((Session) izvor.getSession());
            this.domen = domen;
        }

        @Override
        protected void updateMessageID() throws MessagingException {
            if (domen.isEmpty()) {
                super.updateMessageID();
            } else {
                setHeader("Message-ID", "<" + UUID.randomUUID() + "@" + domen + ">");
            }
        }
    }
}
