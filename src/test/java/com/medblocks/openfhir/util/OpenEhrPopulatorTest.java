package com.medblocks.openfhir.util;

import com.google.gson.JsonObject;
import com.medblocks.openfhir.fc.FhirConnectConst;
import org.hl7.fhir.r4.model.Coding;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class OpenEhrPopulatorTest {

    private OpenEhrPopulator populator;
    private JsonObject flat;

    @Before
    public void setUp() {
        OpenFhirMapperUtils mapperUtils = new OpenFhirMapperUtils();
        populator = new OpenEhrPopulator(mapperUtils);
        flat = new JsonObject();
    }

    @Test
    public void setNullFlavourForDataAbsentReasonCoding() {
        Coding coding = new Coding(
                "http://terminology.hl7.org/CodeSystem/data-absent-reason",
                "unknown",
                "Unknown");

        boolean handled = populator.setNullFlavourForDataAbsentReason("test/value/null_flavour", coding, flat);

        Assert.assertTrue(handled);
        Assert.assertEquals("unknown", flat.get("test/value/null_flavour|value").getAsString());
        Assert.assertEquals("253", flat.get("test/value/null_flavour|code").getAsString());
        Assert.assertEquals("openehr", flat.get("test/value/null_flavour|terminology").getAsString());
    }

    @Test
    public void setFhirPathValueHandlesNullFlavourPath() {
        Coding coding = new Coding(
                "http://terminology.hl7.org/CodeSystem/data-absent-reason",
                "asked-declined",
                "Asked Declined");

        populator.setFhirPathValue("test/value/null_flavour", coding, FhirConnectConst.CODE_PHRASE, flat);

        Assert.assertEquals("masked", flat.get("test/value/null_flavour|value").getAsString());
        Assert.assertEquals("272", flat.get("test/value/null_flavour|code").getAsString());
        Assert.assertEquals("openehr", flat.get("test/value/null_flavour|terminology").getAsString());
    }
}
