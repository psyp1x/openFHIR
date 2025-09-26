package com.medblocks.openfhir.kds;

import ca.uhn.fhir.context.FhirContext;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nedap.archie.rm.composition.Composition;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.xmlbeans.XmlException;
import org.bson.types.Code;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.*;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.TimeZone;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.stream.Collectors;
import org.openehr.schemas.v1.TemplateDocument;

public class MedikationseintragTest extends KdsBidirectionalTest {


    final String MODEL_MAPPINGS = "/kds_new/";
    final String CONTEXT = "/kds_new/projects/org.highmed/KDS/medikationseintrag/KDS_medikationseintrag.context.yaml";
    final String HELPER_LOCATION = "/kds/medikationseintrag/";
    final String OPT = "KDS_Medikationseintrag.opt";
    final String FLAT = "KDS_Medikationseintrag.flat.json";

    final String BUNDLE = "KDS_Medikationseintrag_v1-Fhir-Bundle-input.json";

    @SneakyThrows
    @Override
    protected void prepareState() {
        context = getContext(CONTEXT);
        operationaltemplateSerialized = IOUtils.toString(this.getClass().getResourceAsStream(HELPER_LOCATION + OPT));
        operationaltemplate = getOperationalTemplate();
        repo.initRepository(context, operationaltemplate, getClass().getResource(MODEL_MAPPINGS).getFile());
        webTemplate = new OPTParser(operationaltemplate).parse();
    }

    @Test
    public void kdsMedicationList_toFhir() throws IOException {
        // openEHR to FHIR
        final Composition compositionFromFlat = new FlatJsonUnmarshaller().unmarshal(getFlat(HELPER_LOCATION + FLAT), webTemplate);
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, compositionFromFlat, operationaltemplate);

        final List<MedicationStatement> requests = bundle.getEntry().stream()
                .filter(en -> en.getResource() instanceof MedicationStatement)
                .map(en -> (MedicationStatement) en.getResource())
                .collect(Collectors.toList());

        Assert.assertEquals(2, requests.size());

        final MedicationStatement req1 = requests.get(0);
        final MedicationStatement req2 = requests.get(1);

        Assert.assertEquals("2022-02-03T04:05:06+01:00", req2.getDateAssertedElement().getValueAsString());


        Assert.assertEquals("behandlungsgrund1", req1.getReasonCodeFirstRep().getText());
        Assert.assertEquals("behandlungsgrund", req2.getReasonCodeFirstRep().getText());

        Assert.assertEquals("hinweis1", req1.getNoteFirstRep().getText());
        Assert.assertEquals("hinweis", req2.getNoteFirstRep().getText());

        final List<Dosage> req2Dosages = req2.getDosage();

        Assert.assertEquals(2, req2Dosages.size());
        Assert.assertEquals("structured dosage text", req2Dosages.get(0).getText());
        Assert.assertEquals("mm", req2Dosages.get(0).getDoseAndRateFirstRep().getDoseQuantity().getUnit());
        Assert.assertEquals("22.0", req2Dosages.get(0).getDoseAndRateFirstRep().getDoseQuantity().getValue().toPlainString());
        Assert.assertEquals(22, req2Dosages.get(0).getSequence());

        Assert.assertEquals("structured dosage 2 text", req2Dosages.get(1).getText());
        Assert.assertEquals("mm1", req2Dosages.get(1).getDoseAndRateFirstRep().getDoseQuantity().getUnit());
        Assert.assertEquals("23.0", req2Dosages.get(1).getDoseAndRateFirstRep().getDoseQuantity().getValue().toPlainString());
        Assert.assertEquals(23, req2Dosages.get(1).getSequence());

//        Assert.assertEquals(true, req2.getDosageFirstRep().getAsNeededBooleanType().getValue());
        Assert.assertNull(req1.getDosageFirstRep().getAsNeededBooleanType().getValue());

        final Medication med1 = (Medication) req1.getMedicationReference().getResource();
        final Medication med2 = (Medication) req2.getMedicationReference().getResource();
//        Assert.assertEquals("req0, medication code text", med2.getCode().getCodingFirstRep().getDisplay());
        Assert.assertEquals("42", med2.getForm().getCodingFirstRep().getCode());
        Assert.assertEquals("52", med1.getForm().getCodingFirstRep().getCode());
//        Assert.assertEquals("req1, medication code text", med1.getCode().getCodingFirstRep().getDisplay());
        Assert.assertEquals("25.0", med1.getAmount().getNumerator().getValue().toPlainString());
        Assert.assertEquals("mm", med1.getAmount().getNumerator().getUnit());
        Assert.assertEquals("20.0", med2.getAmount().getNumerator().getValue().toPlainString());
        Assert.assertEquals("mm", med2.getAmount().getNumerator().getUnit());

        Assert.assertEquals(3, med2.getIngredient().size());
        Assert.assertEquals(2, med1.getIngredient().size());
        Assert.assertEquals("ingridient item 0, 0",
                            ((Medication) med2.getIngredient().get(0).getItemReference().getResource()).getCode().getCodingFirstRep().getCode());
        Assert.assertEquals("ingridient item 0, 1", ((Medication) med2.getIngredient().get(1).getItemReference().getResource()).getCode().getCodingFirstRep().getCode());

        Assert.assertEquals("ingridient item 1, 0", ((Medication) med1.getIngredient().get(0).getItemReference().getResource()).getCode().getCodingFirstRep().getCode());
        Assert.assertEquals("ingridient item 1, 1", ((Medication) med1.getIngredient().get(1).getItemReference().getResource()).getCode().getCodingFirstRep().getCode());


        final Medication.MedicationIngredientComponent ingridient00 = med2.getIngredient().get(0);

        final Medication.MedicationIngredientComponent ingridient01 = med2.getIngredient().get(1);

        final Medication.MedicationIngredientComponent ingridient0Empty = med2.getIngredient().get(2);

        final Medication.MedicationIngredientComponent ingridient10 = med1.getIngredient().get(0);

        final Ratio zerothIngridientStrenght = ingridient00.getStrength();
        final Ratio firstIngridientStrenght = ingridient01.getStrength();

        final Ratio secondIngridientStrenght = ingridient0Empty.getStrength();

        Assert.assertEquals("10.0", zerothIngridientStrenght.getNumerator().getValue().toPlainString());
        Assert.assertEquals("11.0", zerothIngridientStrenght.getDenominator().getValue().toPlainString());
        Assert.assertEquals("1mm", zerothIngridientStrenght.getNumerator().getUnit());
        Assert.assertEquals("2mm", zerothIngridientStrenght.getDenominator().getUnit());

        Assert.assertEquals("20.0", firstIngridientStrenght.getNumerator().getValue().toPlainString());
        Assert.assertEquals("21.0", firstIngridientStrenght.getDenominator().getValue().toPlainString());
        Assert.assertEquals("3mm", firstIngridientStrenght.getNumerator().getUnit());
        Assert.assertEquals("4mm", firstIngridientStrenght.getDenominator().getUnit());

        Assert.assertEquals("30.0", secondIngridientStrenght.getNumerator().getValue().toPlainString());
        Assert.assertEquals("31.0", secondIngridientStrenght.getDenominator().getValue().toPlainString());
        Assert.assertEquals("5mm", secondIngridientStrenght.getNumerator().getUnit());
        Assert.assertEquals("6mm", secondIngridientStrenght.getDenominator().getUnit());

        final Ratio firstZerothIngridientStrenght = ingridient10.getStrength();

        final Ratio firstFirstIngridientStrenght = med1.getIngredient().get(1).getStrength();

        Assert.assertEquals("40.0", firstZerothIngridientStrenght.getDenominator().getValue().toPlainString());
        Assert.assertEquals("mm", firstZerothIngridientStrenght.getDenominator().getUnit());

        Assert.assertEquals("41.0", firstFirstIngridientStrenght.getDenominator().getValue().toPlainString());
        Assert.assertEquals("mm", firstFirstIngridientStrenght.getDenominator().getUnit());

//        Assert.assertEquals("at0143", ingridient00.getItemCodeableConcept().getCodingFirstRep().getCode());
//        Assert.assertEquals("Ad-hoc Mixtur", ingridient00.getItemCodeableConcept().getCodingFirstRep().getDisplay());
//
//        Assert.assertEquals("at3143", ingridient0Empty.getItemCodeableConcept().getCodingFirstRep().getCode());
//        Assert.assertEquals("3Ad-hoc Mixtur", ingridient0Empty.getItemCodeableConcept().getCodingFirstRep().getDisplay());
//
//        Assert.assertEquals("at0243", ingridient10.getItemCodeableConcept().getCodingFirstRep().getCode());
//        Assert.assertEquals("2Ad-hoc Mixtur", ingridient10.getItemCodeableConcept().getCodingFirstRep().getDisplay());
    }

    @Test
    public void kdsMedicationList_toFhir_testOpenEhrCondition() throws IOException {
        // openEHR to FHIR
        final String flat = getFlat(HELPER_LOCATION + FLAT);
        final Gson gson = new Gson();
        final JsonObject flatJsonObject = gson.fromJson(flat, JsonObject.class);

//        flatJsonObject.remove("medikamentenliste/aussage_zur_medikamenteneinnahme:0/arzneimittel/darreichungsform|value");
//        flatJsonObject.remove("medikamentenliste/aussage_zur_medikamenteneinnahme:0/arzneimittel/darreichungsform|code");
//        flatJsonObject.remove("medikamentenliste/aussage_zur_medikamenteneinnahme:0/arzneimittel/darreichungsform|terminology");
        flatJsonObject.remove("medikamentenliste/aussage_zur_medikamenteneinnahme:1/arzneimittel/wirkstärke_konzentration|magnitude");
        flatJsonObject.remove("medikamentenliste/aussage_zur_medikamenteneinnahme:1/arzneimittel/wirkstärke_konzentration|unit");

        final Composition compositionFromFlat = new FlatJsonUnmarshaller().unmarshal(gson.toJson(flatJsonObject), webTemplate);
        final Bundle bundle = openEhrToFhir.compositionToFhir(context, compositionFromFlat, operationaltemplate);

        final List<MedicationStatement> requests = bundle.getEntry().stream()
                .filter(en -> en.getResource() instanceof MedicationStatement)
                .map(en -> (MedicationStatement) en.getResource())
                .collect(Collectors.toList());

        Assert.assertEquals(2, requests.size());

        final MedicationStatement theOneWithMedicationReference = requests.stream()
                .filter(req -> req.getReasonCodeFirstRep().getText().equals("behandlungsgrund"))
                .findFirst().orElse(null);

        final MedicationStatement theOneWithMedicationCodeableConcept = requests.stream()
                .filter(req -> req.getReasonCodeFirstRep().getText().equals("behandlungsgrund1"))
                .findFirst().orElse(null);

        final Medication med1 = (Medication) theOneWithMedicationReference.getMedicationReference().getResource();
//        final CodeableConcept med2 = theOneWithMedicationCodeableConcept.getMedicationCodeableConcept();
//
//        Assert.assertEquals("req1, medication code text", med2.getText());
//
//        Assert.assertEquals("req0, medication code text", med1.getCode().getCodingFirstRep().getDisplay());
        Assert.assertEquals("20.0", med1.getAmount().getNumerator().getValue().toPlainString());
        Assert.assertEquals("mm", med1.getAmount().getNumerator().getUnit());

    }


    public JsonObject toOpenEhr() {
        final Bundle testBundle = FhirContext.forR4().newJsonParser().parseResource(Bundle.class, getClass().getResourceAsStream(HELPER_LOCATION + BUNDLE));

        final JsonObject jsonObject = fhirToOpenEhr.fhirToFlatJsonObject(context, testBundle, operationaltemplate);

        Assert.assertEquals("Take 1 tablet every 6 hours as needed for pain", jsonObject.getAsJsonPrimitive("medikamentenliste/aussage_zur_medikamenteneinnahme:0/dosierung:0/dosierung_freitext").getAsString());
        Assert.assertEquals("500.0", jsonObject.getAsJsonPrimitive("medikamentenliste/aussage_zur_medikamenteneinnahme:0/dosierung:0/dosis/quantity_value|magnitude").getAsString());
        Assert.assertEquals("mg", jsonObject.getAsJsonPrimitive("medikamentenliste/aussage_zur_medikamenteneinnahme:0/dosierung:0/dosis/quantity_value|unit").getAsString());
        Assert.assertEquals("at0143", jsonObject.getAsJsonPrimitive("medikamentenliste/aussage_zur_medikamenteneinnahme:0/arzneimittel/arzneimittel-name|code").getAsString());
        Assert.assertEquals("local", jsonObject.getAsJsonPrimitive("medikamentenliste/aussage_zur_medikamenteneinnahme:0/arzneimittel/arzneimittel-name|terminology").getAsString());
        Assert.assertEquals("Paracetamol 500mg tablet", jsonObject.getAsJsonPrimitive("medikamentenliste/aussage_zur_medikamenteneinnahme:0/arzneimittel/arzneimittel-name|value").getAsString());
        Assert.assertEquals("385055001", jsonObject.getAsJsonPrimitive("medikamentenliste/aussage_zur_medikamenteneinnahme:0/arzneimittel/darreichungsform|code").getAsString());
        Assert.assertEquals("http://snomed.info/sct", jsonObject.getAsJsonPrimitive("medikamentenliste/aussage_zur_medikamenteneinnahme:0/arzneimittel/darreichungsform|terminology").getAsString());
        Assert.assertEquals("Paracetamol", jsonObject.getAsJsonPrimitive("medikamentenliste/aussage_zur_medikamenteneinnahme:0/arzneimittel/bestandteil:0/bestandteil").getAsString());
        Assert.assertEquals("at0143", jsonObject.getAsJsonPrimitive("medikamentenliste/aussage_zur_medikamenteneinnahme:0/arzneimittel/bestandteil:0/wirkstofftyp|code").getAsString());
        Assert.assertEquals("local", jsonObject.getAsJsonPrimitive("medikamentenliste/aussage_zur_medikamenteneinnahme:0/arzneimittel/bestandteil:0/wirkstofftyp|terminology").getAsString());
        Assert.assertEquals("500.0", jsonObject.getAsJsonPrimitive("medikamentenliste/aussage_zur_medikamenteneinnahme:0/arzneimittel/bestandteil:0/bestandteil-menge/zähler|magnitude").getAsString());
        Assert.assertEquals("mg", jsonObject.getAsJsonPrimitive("medikamentenliste/aussage_zur_medikamenteneinnahme:0/arzneimittel/bestandteil:0/bestandteil-menge/zähler|unit").getAsString());
        Assert.assertEquals("1.0", jsonObject.getAsJsonPrimitive("medikamentenliste/aussage_zur_medikamenteneinnahme:0/arzneimittel/bestandteil:0/bestandteil-menge/nenner|magnitude").getAsString());
        Assert.assertEquals("tablet", jsonObject.getAsJsonPrimitive("medikamentenliste/aussage_zur_medikamenteneinnahme:0/arzneimittel/bestandteil:0/bestandteil-menge/nenner|unit").getAsString());
        Assert.assertEquals("11Paracetamol", jsonObject.getAsJsonPrimitive("medikamentenliste/aussage_zur_medikamenteneinnahme:0/arzneimittel/bestandteil:1/bestandteil").getAsString());
        Assert.assertEquals("at0143", jsonObject.getAsJsonPrimitive("medikamentenliste/aussage_zur_medikamenteneinnahme:0/arzneimittel/bestandteil:1/wirkstofftyp|code").getAsString());
        Assert.assertEquals("local", jsonObject.getAsJsonPrimitive("medikamentenliste/aussage_zur_medikamenteneinnahme:0/arzneimittel/bestandteil:1/wirkstofftyp|terminology").getAsString());
        Assert.assertEquals("1500.0", jsonObject.getAsJsonPrimitive("medikamentenliste/aussage_zur_medikamenteneinnahme:0/arzneimittel/bestandteil:1/bestandteil-menge/zähler|magnitude").getAsString());
        Assert.assertEquals("mg", jsonObject.getAsJsonPrimitive("medikamentenliste/aussage_zur_medikamenteneinnahme:0/arzneimittel/bestandteil:1/bestandteil-menge/zähler|unit").getAsString());
        Assert.assertEquals("11.0", jsonObject.getAsJsonPrimitive("medikamentenliste/aussage_zur_medikamenteneinnahme:0/arzneimittel/bestandteil:1/bestandteil-menge/nenner|magnitude").getAsString());
        Assert.assertEquals("tablet", jsonObject.getAsJsonPrimitive("medikamentenliste/aussage_zur_medikamenteneinnahme:0/arzneimittel/bestandteil:1/bestandteil-menge/nenner|unit").getAsString());
        Assert.assertEquals("at0143", jsonObject.getAsJsonPrimitive("medikamentenliste/aussage_zur_medikamenteneinnahme:0/arzneimittel/bestandteil:2/wirkstofftyp|code").getAsString());
        Assert.assertEquals("local", jsonObject.getAsJsonPrimitive("medikamentenliste/aussage_zur_medikamenteneinnahme:0/arzneimittel/bestandteil:2/wirkstofftyp|terminology").getAsString());
        Assert.assertEquals("Take 1 capsule daily", jsonObject.getAsJsonPrimitive("medikamentenliste/aussage_zur_medikamenteneinnahme:1/dosierung:0/dosierung_freitext").getAsString());
        Assert.assertEquals("5.0", jsonObject.getAsJsonPrimitive("medikamentenliste/aussage_zur_medikamenteneinnahme:1/dosierung:0/dosis/quantity_value|magnitude").getAsString());
        Assert.assertEquals("mg", jsonObject.getAsJsonPrimitive("medikamentenliste/aussage_zur_medikamenteneinnahme:1/dosierung:0/dosis/quantity_value|unit").getAsString());
        Assert.assertEquals("local", jsonObject.getAsJsonPrimitive("medikamentenliste/aussage_zur_medikamenteneinnahme:1/arzneimittel/arzneimittel-name|terminology").getAsString());
        Assert.assertEquals("C09AA05", jsonObject.getAsJsonPrimitive("medikamentenliste/aussage_zur_medikamenteneinnahme:1/arzneimittel/arzneimittel-name|code").getAsString());
        Assert.assertEquals("Ramipril 5mg capsule", jsonObject.getAsJsonPrimitive("medikamentenliste/aussage_zur_medikamenteneinnahme:1/arzneimittel/arzneimittel-name|value").getAsString());

        Assert.assertEquals("High cholesterol", jsonObject.getAsJsonPrimitive("medikamentenliste/aussage_zur_medikamenteneinnahme:0/behandlungsgrund:0").getAsString());

        return jsonObject;
    }
}
