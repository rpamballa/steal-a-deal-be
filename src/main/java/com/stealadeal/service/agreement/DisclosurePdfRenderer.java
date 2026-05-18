package com.stealadeal.service.agreement;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfWriter;
import com.stealadeal.domain.Deal;
import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/**
 * Renders the two regulatory disclosures required on every deal
 * (Dev-Instruction §8.3): the federal/state Odometer Disclosure and the
 * "AS-IS — No Warranty" buyers-guide acknowledgement. Each is a
 * deal-specific PDF (parties + VIN + odometer) so the signed artifact is
 * a real disclosure, not a blank form.
 *
 * NOTE: the legal language below is a structured placeholder and MUST be
 * reviewed and finalized by counsel before production sign-off.
 */
@Component
public class DisclosurePdfRenderer {

    private static final Font H1 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
    private static final Font H2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
    private static final Font BODY = FontFactory.getFont(FontFactory.HELVETICA, 10);
    private static final Font MUTED = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.ITALIC);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'");

    public byte[] renderOdometer(Deal deal) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.LETTER, 54, 54, 54, 54);
        PdfWriter.getInstance(doc, out);
        doc.open();

        doc.add(new Paragraph("Federal/State Odometer Disclosure Statement", H1));
        doc.add(ref(deal));

        doc.add(section("Vehicle"));
        doc.add(kv("Vehicle", vehicleLabel(deal)));
        doc.add(kv("VIN", deal.getVehicle().getVin()));
        doc.add(kv("Odometer reading", deal.getVehicle().getMileage() + " miles"));

        doc.add(section("Parties"));
        doc.add(kv("Transferor (dealer)", deal.getVehicle().getDealer().getName()));
        doc.add(kv("Transferee (buyer)", deal.getBuyerName() + "  <" + deal.getBuyerEmail() + ">"));

        Paragraph terms = new Paragraph(
                "Federal law (and State law, if applicable) requires that the seller state the "
                        + "mileage upon transfer of ownership. Providing a false statement may result in "
                        + "fines and/or imprisonment. The transferor certifies that, to the best of their "
                        + "knowledge, the odometer reading stated above reflects the actual mileage of the "
                        + "vehicle, unless one of the following statements is checked: (1) the mileage "
                        + "exceeds the odometer's mechanical limits; (2) the odometer reading is NOT the "
                        + "actual mileage — WARNING: ODOMETER DISCREPANCY.",
                BODY);
        terms.setSpacingBefore(16f);
        terms.setSpacingAfter(28f);
        doc.add(terms);

        doc.add(new Paragraph("Buyer signature: ______________________________   Date: ______________", BODY));
        doc.close();
        return out.toByteArray();
    }

    public byte[] renderAsIs(Deal deal) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.LETTER, 54, 54, 54, 54);
        PdfWriter.getInstance(doc, out);
        doc.open();

        doc.add(new Paragraph("Buyers Guide — AS-IS, No Warranty", H1));
        doc.add(ref(deal));

        doc.add(section("Vehicle"));
        doc.add(kv("Vehicle", vehicleLabel(deal)));
        doc.add(kv("VIN", deal.getVehicle().getVin()));

        doc.add(section("Parties"));
        doc.add(kv("Selling dealer", deal.getVehicle().getDealer().getName()));
        doc.add(kv("Buyer", deal.getBuyerName() + "  <" + deal.getBuyerEmail() + ">"));

        Paragraph terms = new Paragraph(
                "AS-IS — NO DEALER WARRANTY. The dealer will not pay for any repairs. The vehicle is "
                        + "sold AS-IS and the buyer will bear the entire expense of repairing or "
                        + "correcting any defects that are present now or that may occur in the vehicle. "
                        + "This disclosure does not waive any rights the buyer may have under applicable "
                        + "law or any separate written warranty or service contract. The buyer "
                        + "acknowledges receiving and reviewing this AS-IS disclosure prior to purchase.",
                BODY);
        terms.setSpacingBefore(16f);
        terms.setSpacingAfter(28f);
        doc.add(terms);

        doc.add(new Paragraph("Buyer signature: ______________________________   Date: ______________", BODY));
        doc.close();
        return out.toByteArray();
    }

    private Paragraph ref(Deal deal) {
        Paragraph ref = new Paragraph(
                "Deal #" + deal.getId() + "  ·  Generated " + OffsetDateTime.now().format(DATE),
                MUTED);
        ref.setSpacingAfter(14f);
        return ref;
    }

    private String vehicleLabel(Deal deal) {
        return deal.getVehicle().getModelYear() + " " + deal.getVehicle().getMake()
                + " " + deal.getVehicle().getModel() + " " + deal.getVehicle().getTrim();
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
}
