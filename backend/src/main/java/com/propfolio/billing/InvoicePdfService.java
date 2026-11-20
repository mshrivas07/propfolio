package com.propfolio.billing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.propfolio.billing.web.InvoiceDtos.Charge;
import com.propfolio.billing.web.InvoiceDtos.InvoiceDetail;
import com.propfolio.billing.web.InvoiceDtos.InvoiceSummary;
import com.propfolio.billing.web.InvoiceDtos.PaymentResponse;
import com.propfolio.billing.web.InvoiceDtos.UnpaidInvoice;
import com.propfolio.rentals.PropertyRepository;
import com.propfolio.rentals.ShareType;

/**
 * The invoice as a one-or-more page PDF (US Letter): landlord, tenant, unit and property, invoice number
 * and dates, one line per charge, total, payments received, other unpaid invoices of the lease with the
 * total owing, and the due date. Built with Apache PDFBox and its built-in Helvetica.
 */
@Service
@Transactional(readOnly = true)
public class InvoicePdfService {

    public record Pdf(String filename, byte[] bytes) {
    }

    private final InvoiceQueryService invoiceQueries;
    private final PropertyRepository properties;
    private final Clock clock;

    public InvoicePdfService(InvoiceQueryService invoiceQueries, PropertyRepository properties, Clock clock) {
        this.invoiceQueries = invoiceQueries;
        this.properties = properties;
        this.clock = clock;
    }

    public Pdf render(UUID invoiceId) {
        InvoiceDetail detail = invoiceQueries.detail(invoiceId);
        String address = detail.invoice().propertyId() == null ? null
                : properties.findById(detail.invoice().propertyId()).map(p -> Stream.of(p.getAddressLine1(),
                        p.getAddressLine2(), p.getCity(), p.getProvince(), p.getPostalCode())
                        .filter(Objects::nonNull).filter(s -> !s.isBlank()).collect(Collectors.joining(", ")))
                        .orElse(null);
        return new Pdf(detail.invoice().invoiceNumber() + ".pdf",
                new Layout(detail, address, LocalDate.now(clock), clock.getZone()).draw());
    }

    /** Draws one invoice. Coordinates are PDF points from the bottom-left corner. */
    static final class Layout {

        private static final float MARGIN = 54;
        private static final float WIDTH = PDRectangle.LETTER.getWidth();
        private static final float HEIGHT = PDRectangle.LETTER.getHeight();
        private static final float RIGHT = WIDTH - MARGIN;
        private static final float BOTTOM = 72;
        // Charge table columns: left edges, plus right edges for the right-aligned ones.
        private static final float COL_UTILITY = MARGIN;
        private static final float COL_PERIOD = MARGIN + 120;
        private static final float COL_DAYS = MARGIN + 270;
        private static final float COL_SHARE = MARGIN + 370;
        private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
        private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("MMM d", Locale.US);

        private final InvoiceDetail detail;
        private final InvoiceSummary invoice;
        private final String address;
        private final LocalDate today;
        private final ZoneId zone;
        private final NumberFormat money = NumberFormat.getCurrencyInstance(Locale.CANADA);
        private final PDFont regular = font(false);
        private final PDFont bold = font(true);

        private PDDocument document;
        private PDPageContentStream out;
        private float y;

        Layout(InvoiceDetail detail, String address, LocalDate today, ZoneId zone) {
            this.detail = detail;
            this.invoice = detail.invoice();
            this.address = address;
            this.today = today;
            this.zone = zone;
        }

        /** PDFBox 3 built-in fonts. (The only version-specific call in this class.) */
        static PDFont font(boolean boldFace) {
            return new PDType1Font(boldFace ? Standard14Fonts.FontName.HELVETICA_BOLD : Standard14Fonts.FontName.HELVETICA);
        }

        byte[] draw() {
            try (PDDocument doc = new PDDocument()) {
                document = doc;
                newPage();
                header();
                charges();
                totals();
                otherUnpaid();
                footer();
                out.close();
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                doc.save(bytes);
                return bytes.toByteArray();
            } catch (IOException e) {
                throw new UncheckedIOException("Couldn't build the invoice PDF", e);
            }
        }

        private void header() throws IOException {
            text(bold, 22, MARGIN, y, "INVOICE");
            right(bold, 12, RIGHT, y + 6, invoice.invoiceNumber());
            String state = switch (invoice.status()) {
                case OPEN -> "Preview - still collecting charges";
                case CANCELLED -> "CANCELLED";
                case PAID, ARCHIVED -> "PAID";
                default -> null;
            };
            if (state != null) {
                right(regular, 9, RIGHT, y - 8, state);
            }
            y -= 40;

            float top = y;
            label(MARGIN, y, "FROM");
            y -= 14;
            y = lines(MARGIN, y, nonBlank(detail.landlordName(), detail.landlordEmail()),
                    detail.landlordName() != null ? detail.landlordEmail() : null);

            float left = y;
            y = top;
            float col = MARGIN + 260;
            label(col, y, "BILL TO");
            y -= 14;
            List<String> billTo = new ArrayList<>();
            billTo.add(invoice.tenantName());
            billTo.addAll(detail.tenantEmails());
            billTo.add(joinNonBlank(", ", invoice.unitName(), invoice.propertyName()));
            if (address != null && !address.isBlank() && (invoice.propertyName() == null
                    || !address.startsWith(invoice.propertyName()))) {
                billTo.add(address);
            }
            y = lines(col, y, billTo.toArray(String[]::new));
            y = Math.min(y, left) - 18;

            float rowY = y;
            fact(MARGIN, rowY, "INVOICE DATE", date(invoiceDate()));
            fact(MARGIN + 130, rowY, "PERIOD", period(invoice.periodStart(), invoice.periodEnd()));
            fact(MARGIN + 300, rowY, "DUE DATE", date(invoice.dueDate()));
            y -= 44;
        }

        private void charges() throws IOException {
            tableHeader();
            List<Charge> rows = detail.charges();
            if (rows.isEmpty()) {
                text(regular, 10, COL_UTILITY, y, "No charges yet.");
                y -= 18;
                return;
            }
            for (Charge c : rows) {
                if (y < BOTTOM + 40) {
                    newPage();
                    tableHeader();
                }
                text(bold, 10, COL_UTILITY, y, nonBlank(c.providerName(), "Utility"));
                text(regular, 10, COL_PERIOD, y, period(c.billPeriodStart(), c.billPeriodEnd()));
                text(regular, 10, COL_DAYS, y, c.daysCovered() + " of " + c.billPeriodDays());
                text(regular, 10, COL_SHARE, y, share(c));
                right(regular, 10, RIGHT, y, money(c.amount()));
                float detailY = y - 12;
                if (c.utilityType() != null) {
                    text(regular, 8, COL_UTILITY, detailY, capitalize(c.utilityType().name()));
                }
                if (c.daysCovered() < c.billPeriodDays()) {
                    text(regular, 8, COL_DAYS, detailY, SHORT_DATE.format(c.coveredStart()) + " - "
                            + SHORT_DATE.format(c.coveredEnd()));
                }
                if (c.shareType() == ShareType.PERCENT && c.splitAmount() != null) {
                    text(regular, 8, COL_SHARE, detailY, "of " + money(c.splitAmount()));
                }
                y -= 28;
                rule(y + 10, 0.85f);
            }
        }

        private void totals() throws IOException {
            if (y < BOTTOM + 80) {
                newPage();
            }
            y -= 6;
            right(bold, 12, COL_SHARE + 60, y, "Total");
            right(bold, 12, RIGHT, y, money(invoice.totalAmount()));
            y -= 18;
            List<PaymentResponse> payments = detail.payments();
            for (PaymentResponse p : payments) {
                right(regular, 10, COL_SHARE + 60, y, "Received " + date(p.paidOn()));
                right(regular, 10, RIGHT, y, "-" + money(p.amount()));
                y -= 14;
            }
            if (!payments.isEmpty()) {
                right(bold, 11, COL_SHARE + 60, y, "Amount due");
                right(bold, 11, RIGHT, y, money(invoice.remaining()));
                y -= 18;
            }
            y -= 10;
        }

        private void otherUnpaid() throws IOException {
            List<UnpaidInvoice> others = detail.otherUnpaid();
            if (others.isEmpty()) {
                return;
            }
            if (y < BOTTOM + 30 + 14 * others.size()) {
                newPage();
            }
            label(MARGIN, y, "STILL UNPAID (NOT PART OF THIS INVOICE'S TOTAL)");
            y -= 14;
            for (UnpaidInvoice other : others) {
                text(regular, 10, MARGIN, y, other.invoiceNumber());
                text(regular, 10, MARGIN + 110, y, other.dueDate() == null ? "" : "due " + date(other.dueDate()));
                right(regular, 10, RIGHT, y, money(other.remaining()));
                y -= 14;
            }
            rule(y + 8, 0.6f);
            y -= 6;
            right(bold, 11, COL_SHARE + 60, y, "Total owing");
            right(bold, 11, RIGHT, y, money(detail.totalOwing()));
            y -= 24;
        }

        private void footer() throws IOException {
            if (y < BOTTOM + 30) {
                newPage();
            }
            text(regular, 8, MARGIN, BOTTOM - 14,
                    "Each charge = bill amount x share x days in the unit / days in the bill period, rounded to the cent.");
            text(regular, 8, MARGIN, BOTTOM - 25, "Questions? Reply to the email this invoice came with.");
        }

        // ---- drawing helpers --------------------------------------------------------------------

        private void newPage() throws IOException {
            if (out != null) {
                out.close();
            }
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            out = new PDPageContentStream(document, page);
            y = HEIGHT - MARGIN - 16;
        }

        private void tableHeader() throws IOException {
            label(COL_UTILITY, y, "UTILITY");
            label(COL_PERIOD, y, "BILL PERIOD");
            label(COL_DAYS, y, "DAYS");
            label(COL_SHARE, y, "SHARE");
            out.beginText();
            out.setFont(bold, 8);
            out.newLineAtOffset(RIGHT - width(bold, 8, "AMOUNT"), y);
            out.showText("AMOUNT");
            out.endText();
            rule(y - 6, 0.6f);
            y -= 22;
        }

        private void fact(float x, float atY, String name, String value) throws IOException {
            label(x, atY, name);
            text(regular, 10, x, atY - 14, value);
        }

        private float lines(float x, float startY, String... values) throws IOException {
            float at = startY;
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    text(regular, 10, x, at, value);
                    at -= 13;
                }
            }
            return at;
        }

        private void label(float x, float atY, String value) throws IOException {
            text(bold, 8, x, atY, value);
        }

        private void text(PDFont font, float size, float x, float atY, String value) throws IOException {
            if (value == null || value.isEmpty()) {
                return;
            }
            out.beginText();
            out.setFont(font, size);
            out.newLineAtOffset(x, atY);
            out.showText(printable(font, value));
            out.endText();
        }

        private void right(PDFont font, float size, float rightEdge, float atY, String value) throws IOException {
            text(font, size, rightEdge - width(font, size, value), atY, value);
        }

        private void rule(float atY, float gray) throws IOException {
            out.setStrokingColor(gray, gray, gray);
            out.setLineWidth(0.5f);
            out.moveTo(MARGIN, atY);
            out.lineTo(RIGHT, atY);
            out.stroke();
            out.setStrokingColor(0f, 0f, 0f);
        }

        private float width(PDFont font, float size, String value) throws IOException {
            return font.getStringWidth(printable(font, value)) / 1000 * size;
        }

        /** Helvetica only covers Latin-1 style characters: anything else prints as "?" instead of failing. */
        static String printable(PDFont font, String value) {
            StringBuilder result = new StringBuilder(value.length());
            value.codePoints().forEach(cp -> {
                String ch = new String(Character.toChars(cp));
                try {
                    font.encode(ch);
                    result.append(ch);
                } catch (IOException | IllegalArgumentException notInFont) {
                    result.append('?');
                }
            });
            return result.toString();
        }

        // ---- formatting -------------------------------------------------------------------------

        private LocalDate invoiceDate() {
            return invoice.invoiceCreatedAt() == null ? today : invoice.invoiceCreatedAt().atZone(zone).toLocalDate();
        }

        private String money(BigDecimal amount) {
            return amount == null ? "" : money.format(amount);
        }

        private static String date(LocalDate date) {
            return date == null ? "-" : DATE.format(date);
        }

        private static String period(LocalDate start, LocalDate end) {
            return start == null || end == null ? "-" : date(start) + " - " + date(end);
        }

        private String share(Charge c) {
            return c.shareType() == ShareType.PERCENT
                    ? c.shareValue().stripTrailingZeros().toPlainString() + "%"
                    : money(c.shareValue()) + " flat";
        }

        private static String capitalize(String name) {
            return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
        }

        private static String nonBlank(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value;
        }

        private static String joinNonBlank(String separator, String... values) {
            return Stream.of(values).filter(Objects::nonNull).filter(v -> !v.isBlank())
                    .collect(Collectors.joining(separator));
        }
    }
}
