package com.medblocks.openfhir.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medblocks.openfhir.fc.schema.model.FhirConnectModel;
import com.medblocks.openfhir.fc.schema.model.Mapping;
import com.medblocks.openfhir.fc.schema.model.With;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

class FhirConnectValidatorTest {

    @Test
    void validateSkipsOpenEhrContextMappings() {
        final FhirConnectValidator validator = new FhirConnectValidator();
        final FhirConnectModel model = new FhirConnectModel();

        final List<Mapping> mappings = new ArrayList<>();
        final Mapping mapping = new Mapping();
        final With with = new With();
        with.setFhir("$resource.subject.reference");
        with.setOpenehr("$openEhrContext.$ehr");
        mapping.setWith(with);
        mapping.setName("patient");
        mappings.add(mapping);
        model.setMappings(mappings);

        final List<String> errors = validator.validateFhirConnectModel(model);
        Assert.assertNotNull(errors);
        Assert.assertTrue("Expected no AQL validation errors for $openEhrContext mappings", errors.isEmpty());
    }
    @Test
    void validateAgainstModelSchema() throws IOException {
        final ObjectMapper yaml = OpenFhirTestUtility.getYaml();
        final FhirConnectModel fhirConnectModel = yaml.readValue(
                getClass().getResourceAsStream("/kds_new/projects/org.highmed/KDS/diagnose/KDS_problem_diagnose.yml"),
                FhirConnectModel.class);
        final List<String> strings = new FhirConnectValidator().validateAgainstModelSchema(fhirConnectModel);
        Assert.assertTrue(strings.isEmpty());
    }

    @Test
    void validateAgainstModelCondition() throws IOException {
        final ObjectMapper yaml = OpenFhirTestUtility.getYaml();
        final FhirConnectModel fhirConnectModel = yaml.readValue(
                getClass().getResourceAsStream("/growth_chart/body-height.model.yml"),
                FhirConnectModel.class);
        final List<String> strings = new FhirConnectValidator().validateAgainstModelSchema(fhirConnectModel);
        Assert.assertNull(strings);
    }
}