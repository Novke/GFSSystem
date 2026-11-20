package tri.novica.gfssystem.service.mejl;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmtpMejlServisTest {

    final JavaMailSender sender = mock(JavaMailSender.class);
    final Clock sat = Clock.fixed(Instant.parse("2026-10-22T10:15:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void poruka() {
        when(sender.createMimeMessage()).thenAnswer(i -> new MimeMessage(Session.getInstance(new Properties())));
    }

    private SmtpMejlServis servis(String front) {
        return new SmtpMejlServis(sender, "smtp.example.test", "gfs@example.test", SmtpMejlServis.PODRAZUMEVANO_IME, front, sat);
    }

    private MimeMessage poslata() {
        ArgumentCaptor<MimeMessage> m = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(m.capture());
        return m.getValue();
    }

    @Test
    void cistTekstSaImenomPosiljaocaIDatumom() throws Exception {
        servis("").posalji(new MejlPoruka("student@example.test", "Šifra za prijavu", "Tvoj kod je 123456."));

        MimeMessage m = poslata();
        InternetAddress od = (InternetAddress) m.getFrom()[0];
        assertEquals("gfs@example.test", od.getAddress());
        assertEquals("GFS · Građevinski fakultet Subotica", od.getPersonal());
        assertEquals("student@example.test", ((InternetAddress) m.getRecipients(MimeMessage.RecipientType.TO)[0]).getAddress());
        assertEquals("Šifra za prijavu", m.getSubject());
        assertEquals(Date.from(sat.instant()), m.getSentDate());
        assertEquals("Tvoj kod je 123456.", m.getContent());
        assertNull(m.getHeader("Reply-To"));
    }

    @Test
    void htmlJeMultipartAlternativeSaTekstomPrvo() throws Exception {
        servis("http://localhost:4200, https://gfs.example.test").posalji(
                new MejlPoruka("student@example.test", "Naslov", "tekst 123456", "<p>html 123456</p>"));

        MimeMessage m = poslata();
        m.saveChanges();
        assertTrue(m.getContentType().startsWith("multipart/alternative"), m.getContentType());
        MimeMultipart mp = (MimeMultipart) m.getContent();
        assertEquals(2, mp.getCount());
        assertTrue(mp.getBodyPart(0).isMimeType("text/plain"));
        assertTrue(mp.getBodyPart(1).isMimeType("text/html"));
        assertEquals("tekst 123456", mp.getBodyPart(0).getContent());
        assertEquals("<p>html 123456</p>", mp.getBodyPart(1).getContent());
        assertTrue(m.getMessageID().matches("^<[0-9a-f-]{36}@gfs\\.example\\.test>$"), m.getMessageID());
        ByteArrayOutputStream sirovo = new ByteArrayOutputStream();
        m.writeTo(sirovo);
        String s = sirovo.toString(StandardCharsets.US_ASCII);
        assertTrue(s.contains("charset=UTF-8") || s.contains("charset=utf-8"), s);
    }

    @Test
    void domenFrontaSamoHttps() {
        assertEquals("gfs.trif.rs", SmtpMejlServis.domenFronta("https://gfs.trif.rs"));
        assertEquals("gfs.dev.trif.rs", SmtpMejlServis.domenFronta("http://localhost:4200, https://gfs.dev.trif.rs/"));
        assertEquals("", SmtpMejlServis.domenFronta("http://localhost:4200"));
        assertEquals("", SmtpMejlServis.domenFronta(null));
    }

    @Test
    void imeIzAdreseSeNeMenja() throws Exception {
        new SmtpMejlServis(sender, "smtp.example.test", "Neko Ime <gfs@example.test>", "Drugo", "", sat)
                .posalji(new MejlPoruka("student@example.test", "n", "t"));

        assertEquals("Neko Ime", ((InternetAddress) poslata().getFrom()[0]).getPersonal());
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
        doThrow(new MailSendException("pao")).when(sender).send(any(MimeMessage.class));

        assertThrows(MailSendException.class,
                () -> servis.posalji(new MejlPoruka("student@example.test", "n", "t")));
    }

    @Test
    void toStringBezAdreseITela() {
        String s = new MejlPoruka("student@example.test", "Naslov", "Tvoj kod je 123456.", "<b>Ana 123456</b>").toString();

        assertFalse(s.contains("student@example.test"));
        assertFalse(s.contains("123456"));
        assertFalse(s.contains("Ana"));
        assertTrue(s.contains("Naslov"));
    }

    @Test
    void saPrilogomJeMultipartMixedSaNepromenjenimTelomIImenomPriloga() throws Exception {
        byte[] sadrzaj = {0x50, 0x4B, 3, 4, 1, 2, 3};
        MejlPoruka.Prilog prilog = new MejlPoruka.Prilog("Vežba č.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", sadrzaj);
        servis("").posalji(new MejlPoruka("student@example.test", "Fajl sa časa", "tekst 1", "<p>html 1</p>", java.util.List.of(prilog)));

        MimeMessage m = poslata();
        ByteArrayOutputStream izlaz = new ByteArrayOutputStream();
        m.writeTo(izlaz);   // saveChanges + serijalizacija kao pri slanju
        MimeMessage procitana = new MimeMessage(Session.getInstance(new Properties()),
                new java.io.ByteArrayInputStream(izlaz.toByteArray()));
        assertTrue(procitana.getContentType().startsWith("multipart/mixed"), procitana.getContentType());
        MimeMultipart mixed = (MimeMultipart) procitana.getContent();
        assertEquals(2, mixed.getCount());

        MimeMultipart telo = (MimeMultipart) mixed.getBodyPart(0).getContent();
        assertTrue(telo.getContentType().startsWith("multipart/alternative"), telo.getContentType());
        assertEquals("tekst 1", ((String) telo.getBodyPart(0).getContent()).strip());
        assertEquals("<p>html 1</p>", ((String) telo.getBodyPart(1).getContent()).strip());

        jakarta.mail.BodyPart deo = mixed.getBodyPart(1);
        assertEquals(jakarta.mail.Part.ATTACHMENT, deo.getDisposition());
        assertEquals("Vežba č.docx", deo.getFileName());
        assertTrue(new String(izlaz.toByteArray(), StandardCharsets.ISO_8859_1).contains("filename*="), "RFC 2231 ime");
        assertArrayEquals(sadrzaj, deo.getInputStream().readAllBytes());
    }

    @Test
    void saPrilogomIBezHtmlaTeloJeCistTekst() throws Exception {
        servis("").posalji(new MejlPoruka("student@example.test", "N", "samo tekst", null,
                java.util.List.of(new MejlPoruka.Prilog("a.pdf", "application/pdf", new byte[]{'%', 'P'}))));
        MimeMessage m = poslata();
        m.saveChanges();
        MimeMultipart mixed = (MimeMultipart) m.getContent();
        assertTrue(mixed.getBodyPart(0).isMimeType("text/plain"));
        assertEquals("samo tekst", mixed.getBodyPart(0).getContent());
        assertEquals("a.pdf", mixed.getBodyPart(1).getFileName());
    }

    @Test
    void toStringNeIspisujeSadrzajNiImePriloga() {
        MejlPoruka.Prilog prilog = new MejlPoruka.Prilog("tajno-ime.docx", "application/pdf", "TAJNO".getBytes(StandardCharsets.UTF_8));
        String s = new MejlPoruka("a@example.test", "Naslov", "telo", null, java.util.List.of(prilog)).toString();
        assertFalse(s.contains("a@example.test") || s.contains("telo") || s.contains("tajno") || s.contains("TAJNO"), s);
        assertFalse(prilog.toString().contains("tajno") || prilog.toString().contains("TAJNO"), prilog.toString());
    }
}
