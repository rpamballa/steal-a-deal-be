package com.stealadeal.service.agreement;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.stealadeal.domain.Deal;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/**
 * Renders a Buyer Agreement PDF from a {@link Deal}. The generated PDF
 * is what gets stored as the BUYER_AGREEMENT document and sent to the
 * e-sign provider, so the signed artifact contains the actual deal
 * terms (parties, vehicle, itemized price) rather than a blank form.
 */
@Component
public class BuyerAgreementPdfRenderer {

    private static final Font H1 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
    private static final Font H2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
    private static final Font BODY = FontFactory.getFont(FontFactory.HELVETICA, 10);
    private static final Font MUTED = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.ITALIC);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'");

    public byte[] render(Deal deal) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.LETTER, 54, 54, 54, 54);
        PdfWriter.getInstance(doc, out);
        doc.open();

        doc.add(new Paragraph("Vehicle Buyer Agreement", H1));
        Paragraph ref = new Paragraph(
                "Deal #" + deal.getId() + "  ·  Generated " + java.time.OffsetDateTime.now().format(DATE),
                MUTED);
        ref.setSpacingAfter(14f);
        doc.add(ref);

        doc.add(section("Parties"));
        doc.add(kv("Buyer", deal.getBuyerName() + "  <" + deal.getBuyerEmail() + ">  " + deal.getBuyerPhone()));
        String addr = deal.getBuyerAddressLine1()
                + (deal.getBuyerAddressLine2() == null || deal.getBuyerAddressLine2().isBlank()
                        ? "" : ", " + deal.getBuyerAddressLine2())
                + ", " + deal.getBuyerCity() + ", " + deal.getBuyerState() + " " + deal.getBuyerPostalCode();
        doc.add(kv("Buyer address", addr));
        doc.add(kv("Selling dealer", deal.getVehicle().getDealer().getName()
                + " (" + deal.getVehicle().getDealer().getCity() + ", "
                + deal.getVehicle().getDealer().getState() + ")"));

        doc.add(section("Vehicle"));
        doc.add(kv("Vehicle", deal.getVehicle().getModelYear() + " " + deal.getVehicle().getMake()
                + " " + deal.getVehicle().getModel() + " " + deal.getVehicle().getTrim()));
        doc.add(kv("VIN", deal.getVehicle().getVin()));
        doc.add(kv("Fulfillment", String.valueOf(deal.getFulfillmentType())));
        if (deal.isTradeIn()) {
            doc.add(kv("Trade-in", "VIN " + deal.getTradeInVin()
                    + ", " + deal.getTradeInMileage() + " mi, offer " + money(deal.getTradeInOffer())));
        }

        doc.add(section("Itemized Price"));
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        t.setSpacingBefore(4f);
        priceRow(t, "Vehicle price", deal.getVehiclePrice());
        priceRow(t, "Tax", deal.getTaxAmount());
        priceRow(t, "Registration fee", deal.getRegistrationFee());
        priceRow(t, "Documentation fee", deal.getDocumentationFee());
        priceRow(t, "Delivery fee", deal.getDeliveryFee());
        priceRow(t, "Trade-in credit", deal.getTradeInOffer().negate());
        priceRow(t, "Discount", deal.getDiscountAmount().negate());
        priceRow(t, "Deposit", deal.getDepositAmount());
        totalRow(t, "Total due", deal.getTotalAmount());
        doc.add(t);

        Paragraph terms = new Paragraph(
                "By signing below, the buyer agrees to purchase the vehicle described above on the "
                        + "itemized terms. The deposit is applied to the total. Electronic signature is "
                        + "intended to be legally binding under the U.S. ESIGN Act and applicable state UETA.",
                BODY);
        terms.setSpacingBefore(16f);
        terms.setSpacingAfter(28f);
        doc.add(terms);

        doc.add(new Paragraph("Buyer signature: ______________________________   Date: ______________", BODY));

        doc.close();
        return out.toByteArray();
    }

    private Paragraph section(String title) {
        Paragraph p = new Paragraph(title, H2);
        p.setSpacingBefore(14f);
        p.setSpacingAfter(4f);
        return p;
    }

    private Paragraph kv(String key, String value) {
        Paragraph p = new Paragraph();
        p.add(new Phrase(key + ": ", H2));
        p.add(new Phrase(value == null ? "" : value, BODY));
        p.setSpacingAfter(2f);
        return p;
    }

    private void priceRow(PdfPTable t, String label, BigDecimal amount) {
        t.addCell(cell(label, false, Element.ALIGN_LEFT));
        t.addCell(cell(money(amount), false, Element.ALIGN_RIGHT));
    }

    private void totalRow(PdfPTable t, String label, BigDecimal amount) {
        t.addCell(cell(label, true, Element.ALIGN_LEFT));
        t.addCell(cell(money(amount), true, Element.ALIGN_RIGHT));
    }

    private PdfPCell cell(String text, boolean bold, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, bold ? H2 : BODY));
        c.setHorizontalAlignment(align);
        c.setPadding(5f);
        return c;
    }

    private String money(BigDecimal v) {
        BigDecimal s = (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
        return (s.signum() < 0 ? "-$" + s.negate() : "$" + s);
    }
}
