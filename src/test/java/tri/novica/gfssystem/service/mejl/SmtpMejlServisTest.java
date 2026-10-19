package tri.novica.gfssystem.service.mejl;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmtpMejlServisTest {

    final JavaMailSender sender = mock(JavaMailSender.class);

    @Test
    void saljePorukuSaPosiljaocemPrimaocemNaslovomITelom() {
        SmtpMejlServis servis = new SmtpMejlServis(sender, "smtp.example.test", "gfs@example.test");

        servis.posalji(new MejlPoruka("student@example.test", "Šifra za prijavu", "Tvoj kod je 123456."));

        ArgumentCaptor<SimpleMailMessage> m = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(m.capture());
        assertEquals("gfs@example.test", m.getValue().getFrom());
        assertArrayEquals(new String[]{"student@example.test"}, m.getValue().getTo());
        assertEquals("Šifra za prijavu", m.getValue().getSubject());
        assertEquals("Tvoj kod je 123456.", m.getValue().getText());
    }

    @Test
    void dostupanSamoKadSuHostIPosiljalacPodeseni() {
        assertTrue(new SmtpMejlServis(sender, "smtp.example.test", "gfs@example.test").dostupan());
        assertFalse(new SmtpMejlServis(sender, "", "gfs@example.test").dostupan());
        assertFalse(new SmtpMejlServis(sender, "  ", "gfs@example.test").dostupan());
        assertFalse(new SmtpMejlServis(sender, "smtp.example.test", "").dostupan());
    }

    @Test
    void nepodesenoNistaNeSaljeIBacaIzuzetak() {
        SmtpMejlServis servis = new SmtpMejlServis(sender, "", "gfs@example.test");

        assertThrows(IllegalStateException.class,
                () -> servis.posalji(new MejlPoruka("student@example.test", "n", "t")));
        verifyNoInteractions(sender);
    }

    @Test
    void neuspehSmtpaProlaziPozivaocu() {
        SmtpMejlServis servis = new SmtpMejlServis(sender, "smtp.example.test", "gfs@example.test");
        doThrow(new MailSendException("pao")).when(sender).send(any(SimpleMailMessage.class));

        assertThrows(MailSendException.class,
                () -> servis.posalji(new MejlPoruka("student@example.test", "n", "t")));
    }

    @Test
    void toStringBezAdreseITela() {
        String s = new MejlPoruka("student@example.test", "Naslov", "Tvoj kod je 123456.").toString();

        assertFalse(s.contains("student@example.test"));
        assertFalse(s.contains("123456"));
        assertTrue(s.contains("Naslov"));
    }
}
