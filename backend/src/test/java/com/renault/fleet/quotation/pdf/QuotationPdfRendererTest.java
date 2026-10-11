package com.renault.fleet.quotation.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/** Rendering of the FLT-2026-0891 figures, watermark, QR code and byte-for-byte reproducibility. */
class QuotationPdfRendererTest {

    private final QuotationPdfRenderer renderer = new QuotationPdfRenderer();

    static QuotationSnapshot snapshot(boolean draft) {
        return new QuotationSnapshot(QuotationSnapshot.SCHEMA_VERSION, "QT-2026-00042", "FLT-2026-0891", draft,
                draft ? null : LocalDate.of(2026, 10, 11), draft ? null : LocalDate.of(2026, 11, 10), 30,
                draft ? null : "Priya Finance", Instant.parse("2026-10-11T05:30:00Z"),
                new QuotationSnapshot.DealerDetails("Chennai North Dealership", "DLR-CHN-N", "33ABCDE1234F1Z5",
                        "12 Anna Salai", "Chennai", "Tamil Nadu", "600002", "Chennai Region", "R. Kumar",
                        "+91 44 2345 6789", "fleet@chennainorth.example"),
                List.of(new QuotationSnapshot.Line(1, "Duster RXZ Petrol", 50, new BigDecimal("1200000.00"),
                        new BigDecimal("60000000.00"), new BigDecimal("0.1800"), new BigDecimal("10800000.00"),
                        new BigDecimal("70800000.00"))),
                new BigDecimal("0.1800"), new BigDecimal("60000000.00"), new BigDecimal("10800000.00"),
                new BigDecimal("70800000.00"), new BigDecimal("5000000.00"), new BigDecimal("1000000.00"),
                new BigDecimal("4000000.00"), "INR", "Prices include GST.\nIssuing is not payment.",
                draft ? null : "http://localhost:5173/verify/quotation/AbC123_-tokentokentokentokentoken0000000");
    }

    @Test
    void issuedPdfShowsTheFiguresInIndianFormatWithAQrCode() throws IOException {
        byte[] pdf = renderer.render(snapshot(false));
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertThat(text).contains("Renault Group Fleet Portal", "QT-2026-00042", "FLT-2026-0891", "11 Oct 2026",
                    "10 Nov 2026", "Chennai North Dealership", "33ABCDE1234F1Z5", "Duster RXZ Petrol",
                    "₹12,00,000.00", "₹6,00,00,000.00", "GST @ 18%", "₹1,08,00,000.00", "₹7,08,00,000.00",
                    "₹50,00,000.00", "₹10,00,000.00", "₹40,00,000.00",
                    "Indian Rupees Seven Crore Eight Lakh Only", "Issuing is not payment.", "TAX QUOTATION",
                    "/verify/quotation/AbC123_-");
            assertThat(text).doesNotContain("DRAFT");
            assertThat(images(doc.getPage(0))).isEqualTo(1);
            assertThat(doc.getDocumentInformation().getTitle()).isEqualTo("Quotation QT-2026-00042");
        }
    }

    @Test
    void draftPdfIsWatermarkedAndHasNoVerificationCode() throws IOException {
        byte[] pdf = renderer.render(snapshot(true));
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertThat(text).contains("DRAFT QUOTATION", "Not issued (draft)", "30 days from issue", "₹7,08,00,000.00");
            assertThat(text.replace("DRAFT QUOTATION", "")).contains("DRAFT");
            assertThat(text).doesNotContain("/verify/quotation/");
            assertThat(images(doc.getPage(0))).isZero();
        }
    }

    @Test
    void sameSnapshotRendersIdenticalBytes() {
        byte[] first = renderer.render(snapshot(false));
        byte[] second = new QuotationPdfRenderer().render(snapshot(false));
        assertThat(QuotationPdfRenderer.sha256(second)).isEqualTo(QuotationPdfRenderer.sha256(first)).hasSize(64);
    }

    @Test
    void embeddedFontHasTheRupeeGlyph() throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("fonts/DejaVuSans.ttf");
                TrueTypeFont font = new TTFParser().parse(new RandomAccessReadBuffer(in))) {
            assertThat(font.getUnicodeCmapLookup().getGlyphId('₹')).isPositive();
        }
    }

    private static int images(PDPage page) throws IOException {
        int count = 0;
        for (COSName name : page.getResources().getXObjectNames()) {
            if (page.getResources().getXObject(name) instanceof PDImageXObject) {
                count++;
            }
        }
        return count;
    }
}
