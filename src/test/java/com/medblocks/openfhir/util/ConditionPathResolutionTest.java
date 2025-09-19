package com.medblocks.openfhir.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medblocks.openfhir.fc.OpenFhirFhirConnectModelMapper;
import com.medblocks.openfhir.fc.schema.model.FhirConnectModel;
import com.medblocks.openfhir.fc.schema.model.Mapping;
import java.io.InputStream;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/**
 * Verifies behavior discussed in issue #73:
 * - A condition's targetRoot is placed on the WITH path unless anchored at $resource
 * - Conditions do not auto-generate manual mappings
 */
public class ConditionPathResolutionTest {

    @Test
    public void conditionTargetRootPrefixedByParentWithPath() throws Exception {
        // Arrange: reuse the existing manualMappingsTest.yml which includes nested followedBy and conditions
        final ObjectMapper yaml = OpenFhirTestUtility.getYaml();
        try (InputStream is = getClass().getResourceAsStream("/com/medblocks/openfhir/fc/manualMappingsTest.yml")) {
            final FhirConnectModel model = yaml.readValue(is, FhirConnectModel.class);

            // Act
            final OpenFhirFhirConnectModelMapper handled = new OpenFhirFhirConnectModelMapper().fromFhirConnectModelMapper(model);

            // Find the mapping "innerTest" -> followedBy -> "secondLevel" -> followedBy -> first child "name"
            final List<Mapping> root = handled.getMappings();
            final Mapping inner = root.stream().filter(m -> "innerTest".equals(m.getName())).findFirst().orElseThrow();
            final Mapping second = inner.getFollowedBy().getMappings().stream()
                    .filter(m -> "secondLevel".equals(m.getName()))
                    .findFirst().orElseThrow();
            final Mapping name = second.getFollowedBy().getMappings().get(0);

            // That mapping has a followedBy mapping with fhirCondition on "coding.system"; ensure the resolved path on child
            // includes the parent's fhir path ($resource.code) when building child fhir paths.
            Assert.assertEquals("$resource.code", name.getWith().getFhir());
            Assert.assertEquals("coding", name.getFollowedBy().getMappings().get(0).getWith().getFhir());
            // Parent condition propagation check: the fhirCondition lives on the child mapping and should remain under the child's path
            Assert.assertEquals("coding", name.getFollowedBy().getMappings().get(0).getFhirCondition().getTargetRoot());
        }
    }

    @Test
    public void conditionDoesNotCreateManualMappings() throws Exception {
        final ObjectMapper yaml = OpenFhirTestUtility.getYaml();
        try (InputStream is = getClass().getResourceAsStream("/com/medblocks/openfhir/fc/manualMappingsTest.yml")) {
            final FhirConnectModel model = yaml.readValue(is, FhirConnectModel.class);
            final OpenFhirFhirConnectModelMapper handled = new OpenFhirFhirConnectModelMapper().fromFhirConnectModelMapper(model);

            // Locate a mapping that has a condition but no manual definitions (e.g., innerTest.secondLevel.name)
            final Mapping inner = handled.getMappings().stream().filter(m -> "innerTest".equals(m.getName())).findFirst().orElseThrow();
            final Mapping second = inner.getFollowedBy().getMappings().stream()
                    .filter(m -> "secondLevel".equals(m.getName()))
                    .findFirst().orElseThrow();
            final Mapping name = second.getFollowedBy().getMappings().get(0); // name

            // Assert that this mapping did not gain any manual definitions due to conditions alone
            Assert.assertNull(name.getManual());
        }
    }
}
