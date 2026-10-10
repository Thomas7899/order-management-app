package com.thomas.order_management.service.sales;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.thomas.order_management.model.Invoice;
import com.thomas.order_management.model.InvoiceItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Rechnungsformular als PDF (vereinfachte Nachrichtenausgabe, angelehnt an die SAP-Ausgabeart RD00).
 */
@Service
public class InvoicePdfService {

    private static final Locale DE = Locale.GERMANY;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Color HEADER_BG = new Color(0xEF, 0xF4, 0xF9);
    private static final Color LINE = new Color(0xD9, 0xD9, 0xD9);

    static final String COMPANY_NAME = "OM Handels GmbH";
    static final String COMPANY_ADDRESS = "Industriestraße 15 · 80339 München";
    static final String COMPANY_FOOTER = "OM Handels GmbH · Amtsgericht München HRB 123456 · USt-IdNr. DE123456789 · "
            + "IBAN DE12 7001 0080 0123 4567 89 · BIC PBNKDEFF";

    private final BillingService billingService;

    public InvoicePdfService(BillingService billingService) {
        this.billingService = billingService;
    }

    public record RenderedPdf(String fileName, byte[] content) {
    }

    @Transactional(readOnly = true)
    public RenderedPdf render(Long invoiceId) {
        Invoice invoice = billingService.getEntity(invoiceId);
        return new RenderedPdf("Rechnung_" + invoice.getInvoiceNumber() + ".pdf", render(invoice));
    }

    byte[] render(Invoice invoice) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 56, 56, 56, 64);
        PdfWriter writer = PdfWriter.getInstance(document, out);
        writer.setPageEvent(new com.lowagie.text.pdf.PdfPageEventHelper() {
            @Override
            public void onEndPage(PdfWriter w, Document d) {
                var cb = w.getDirectContent();
                com.lowagie.text.pdf.ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                        new Phrase(COMPANY_FOOTER, font(7, Font.NORMAL, Color.GRAY)),
                        (d.right() + d.left()) / 2, d.bottom() - 30, 0);
            }
        });

        document.open();
        boolean cancellation = invoice.getBillingType() == Invoice.BillingType.S1;

        Paragraph sender = new Paragraph(COMPANY_NAME + " · " + COMPANY_ADDRESS, font(7, Font.UNDERLINE, Color.DARK_GRAY));
        document.add(sender);

        PdfPTable head = new PdfPTable(new float[]{3, 2});
        head.setWidthPercentage(100);
        head.setSpacingBefore(6);
        head.addCell(borderless(addressBlock(invoice)));
        head.addCell(borderless(infoBlock(invoice)));
        document.add(head);

        Paragraph title = new Paragraph((cancellation ? "Stornorechnung " : "Rechnung ") + invoice.getInvoiceNumber(),
                font(16, Font.BOLD, Color.BLACK));
        title.setSpacingBefore(28);
        title.setSpacingAfter(4);
        document.add(title);
        if (cancellation && invoice.getReferenceInvoice() != null) {
            document.add(new Paragraph("Storno zu Rechnung " + invoice.getReferenceInvoice().getInvoiceNumber(), font(9)));
        }
        document.add(new Paragraph("Wir berechnen Ihnen für die Lieferung "
                + (invoice.getDelivery() != null ? invoice.getDelivery().getDeliveryNumber() : "")
                + " zu Ihrem Auftrag " + invoice.getOrder().getOrderNumber()
                + (invoice.getOrder().getCustomerReference() != null
                        ? " (Ihre Bestellung " + invoice.getOrder().getCustomerReference() + ")" : "") + ":", font(9)));

        PdfPTable items = new PdfPTable(new float[]{0.7f, 1.4f, 4.2f, 1.1f, 1.6f, 1.0f, 1.8f});
        items.setWidthPercentage(100);
        items.setSpacingBefore(12);
        items.setHeaderRows(1);
        for (String h : new String[]{"Pos.", "Material", "Bezeichnung", "Menge", "Preis/ME", "USt.", "Betrag"}) {
            PdfPCell cell = new PdfPCell(new Phrase(h, font(8, Font.BOLD, Color.DARK_GRAY)));
            cell.setBackgroundColor(HEADER_BG);
            cell.setBorderColor(LINE);
            cell.setPadding(5);
            cell.setHorizontalAlignment(isNumericColumn(h) ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
            items.addCell(cell);
        }
        for (InvoiceItem item : invoice.getItems()) {
            items.addCell(cell(String.valueOf(item.getItemNumber()), false));
            items.addCell(cell(item.getProduct().getMaterialNumber(), false));
            items.addCell(cell(item.getProduct().getName(), false));
            items.addCell(cell(item.getQuantity() + " " + item.getProduct().getUnitOfMeasure(), true));
            items.addCell(cell(money(item.getUnitNetPrice()), true));
            items.addCell(cell(percent(item.getTaxRate()), true));
            items.addCell(cell(money(item.getNetAmount()), true));
        }
        document.add(items);

        PdfPTable totals = new PdfPTable(new float[]{4, 1.8f});
        totals.setWidthPercentage(45);
        totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totals.setSpacingBefore(10);
        totals.addCell(borderless(new Phrase("Nettobetrag", font(9))));
        totals.addCell(right(money(invoice.getNetAmount()), font(9)));
        totals.addCell(borderless(new Phrase("Umsatzsteuer", font(9))));
        totals.addCell(right(money(invoice.getTaxAmount()), font(9)));
        totals.addCell(borderless(new Phrase("Rechnungsbetrag", font(10, Font.BOLD, Color.BLACK))));
        totals.addCell(right(money(invoice.getGrossAmount()), font(10, Font.BOLD, Color.BLACK)));
        document.add(totals);

        if (!cancellation && invoice.getPaymentTerms() != null) {
            StringBuilder terms = new StringBuilder("Zahlungsbedingung: ").append(invoice.getPaymentTerms().getDescription()).append(". ");
            if (invoice.getCashDiscountDate() != null && invoice.getCashDiscountPercent().signum() > 0) {
                BigDecimal discount = invoice.getGrossAmount().multiply(invoice.getCashDiscountPercent())
                        .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
                terms.append("Bei Zahlung bis ").append(invoice.getCashDiscountDate().format(DATE))
                        .append(" ziehen Sie ").append(percent(invoice.getCashDiscountPercent()))
                        .append(" Skonto (").append(money(discount)).append(") ab. ");
            }
            terms.append("Zahlbar netto bis ").append(invoice.getDueDate().format(DATE)).append(".");
            Paragraph p = new Paragraph(terms.toString(), font(9));
            p.setSpacingBefore(24);
            document.add(p);
        }

        document.close();
        return out.toByteArray();
    }

    private Phrase addressBlock(Invoice invoice) {
        Phrase phrase = new Phrase();
        String address = invoice.getOrder().getBillingAddress() != null
                ? invoice.getOrder().getBillingAddress() : invoice.getCustomer().getFormattedAddress();
        for (String line : address.split(",\\s*")) {
            phrase.add(new Chunk(line + "\n", font(10)));
        }
        return phrase;
    }

    private Phrase infoBlock(Invoice invoice) {
        Phrase phrase = new Phrase();
        addInfo(phrase, "Rechnungsdatum", invoice.getBillingDate().format(DATE));
        addInfo(phrase, "Kundennummer", invoice.getCustomer().getCustomerNumber());
        addInfo(phrase, "Auftrag", invoice.getOrder().getOrderNumber());
        if (invoice.getDelivery() != null) {
            addInfo(phrase, "Lieferung", invoice.getDelivery().getDeliveryNumber());
            if (invoice.getDelivery().getGoodsIssueDate() != null) {
                addInfo(phrase, "Lieferdatum", invoice.getDelivery().getGoodsIssueDate().toLocalDate().format(DATE));
            }
        }
        return phrase;
    }

    private void addInfo(Phrase phrase, String label, String value) {
        phrase.add(new Chunk(label + ": ", font(8, Font.NORMAL, Color.GRAY)));
        phrase.add(new Chunk((value != null ? value : "–") + "\n", font(9)));
    }

    private PdfPCell borderless(Phrase phrase) {
        PdfPCell cell = new PdfPCell(phrase);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(2);
        return cell;
    }

    private PdfPCell right(String text, Font font) {
        PdfPCell cell = borderless(new Phrase(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return cell;
    }

    private PdfPCell cell(String text, boolean numeric) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font(8)));
        cell.setBorderColor(LINE);
        cell.setPadding(5);
        cell.setHorizontalAlignment(numeric ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        return cell;
    }

    private static boolean isNumericColumn(String header) {
        return switch (header) {
            case "Menge", "Preis/ME", "USt.", "Betrag" -> true;
            default -> false;
        };
    }

    private static Font font(float size) {
        return font(size, Font.NORMAL, Color.BLACK);
    }

    private static Font font(float size, int style, Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA, size, style, color);
    }

    private static String money(BigDecimal value) {
        NumberFormat format = NumberFormat.getCurrencyInstance(DE);
        return format.format(value != null ? value : BigDecimal.ZERO);
    }

    private static String percent(BigDecimal value) {
        NumberFormat format = NumberFormat.getNumberInstance(DE);
        format.setMaximumFractionDigits(2);
        return format.format(value != null ? value : BigDecimal.ZERO) + " %";
    }
}
