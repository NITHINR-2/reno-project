package com.renault.fleet.quotation.pdf;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.outputdevice.helper.ExternalResourceControlPriority;
import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.util.XRLog;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HexFormat;
import java.util.TimeZone;
import java.util.logging.Level;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Renders a {@link QuotationSnapshot} to PDF: Thymeleaf (XHTML template) → openhtmltopdf. Output is deterministic for
 * a given snapshot (fixed metadata dates, document ID and producer; embedded fonts; no external resources), so the
 * SHA-256 stored on issue can be checked on every later download.
 */
@Component
public class QuotationPdfRenderer {

    private static final String FONT = "DejaVu Sans";

    private final SpringTemplateEngine templates = new SpringTemplateEngine();

    static {
        // openhtmltopdf logs every render step at INFO through java.util.logging; keep only warnings.
        XRLog.listRegisteredLoggers().forEach(name -> XRLog.setLevel(name, Level.WARNING));
    }

    public QuotationPdfRenderer() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/pdf/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.XML); // openhtmltopdf needs well-formed XHTML
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCacheable(true);
        templates.setTemplateResolver(resolver);
    }

    public byte[] render(QuotationSnapshot snapshot) {
        String html = html(snapshot);
        try (PDDocument document = new PDDocument()) {
            PdfRendererBuilder builder = new PdfRendererBuilder()
                    .useFastMode()
                    .usePDDocument(document)
                    .withProducer("Renault Group Fleet Portal")
                    .withHtmlContent(html, null)
                    // Only inline data: URIs (the QR code); the template can never reach the network or disk.
                    .useExternalResourceAccessControl((uri, type) -> uri != null && uri.startsWith("data:"),
                            ExternalResourceControlPriority.RUN_BEFORE_RESOLVING_URI)
                    .useExternalResourceAccessControl((uri, type) -> uri != null && uri.startsWith("data:"),
                            ExternalResourceControlPriority.RUN_AFTER_RESOLVING_URI);
            builder.useFont(() -> font("fonts/DejaVuSans.ttf"), FONT, 400, FontStyle.NORMAL, true);
            builder.useFont(() -> font("fonts/DejaVuSans-Bold.ttf"), FONT, 700, FontStyle.NORMAL, true);
            try (PdfBoxRenderer renderer = builder.buildPdfRenderer()) {
                renderer.createPDFWithoutClosing();
                stampMetadata(document, snapshot);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                document.save(out);
                return out.toByteArray();
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not render quotation " + snapshot.quotationNumber(), ex);
        }
    }

    String html(QuotationSnapshot snapshot) {
        Context context = new Context();
        context.setVariable("q", snapshot);
        context.setVariable("f", new Formatter());
        context.setVariable("qr", snapshot.verificationUrl() == null ? null : QrCodes.pngDataUri(snapshot.verificationUrl(), 240));
        return templates.process("quotation", context);
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void stampMetadata(PDDocument document, QuotationSnapshot snapshot) {
        Calendar when = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        when.setTimeInMillis(snapshot.documentDate().toEpochMilli());
        PDDocumentInformation info = document.getDocumentInformation();
        info.setTitle("Quotation " + snapshot.quotationNumber() + (snapshot.draft() ? " (DRAFT)" : ""));
        info.setAuthor("Renault Group Fleet Portal");
        info.setCreator("Renault Group Fleet Portal");
        info.setProducer("Renault Group Fleet Portal");
        info.setCreationDate(when);
        info.setModificationDate(when);
        // PDFBox derives the trailer /ID from this seed; a fixed seed keeps the bytes reproducible.
        document.setDocumentId((long) (snapshot.quotationNumber() + snapshot.documentDate()).hashCode());
    }

    private static InputStream font(String path) {
        InputStream in = QuotationPdfRenderer.class.getClassLoader().getResourceAsStream(path);
        if (in == null) {
            throw new IllegalStateException("Missing PDF font " + path);
        }
        return in;
    }

    /** Formatting helpers exposed to the template as {@code f}. */
    public static final class Formatter {
        public String rupees(BigDecimal amount) { return IndianFormat.rupees(amount); }
        public String percent(BigDecimal rate) { return IndianFormat.percent(rate); }
        public String date(LocalDate date) { return IndianFormat.date(date); }
        public String words(BigDecimal amount) { return IndianFormat.inWords(amount); }
        public String number(int value) { return IndianFormat.grouped(BigDecimal.valueOf(value)).replace(".00", ""); }
    }
}
