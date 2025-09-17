package com.medblocks.openfhir.util;

import com.google.gson.JsonObject;
import org.junit.Assert;
import org.junit.Test;

public class OpenEhrPopulatorTest {

    private final OpenEhrPopulator populator = new OpenEhrPopulator(new OpenFhirMapperUtils());

    @Test
    public void contextStartTimeRetainsEarliestValue() {
        JsonObject flat = new JsonObject();
        String path = "test_template/context/start_time";

        populator.addToConstructingFlat(path, "2024-05-05T10:00:00", flat);
        populator.addToConstructingFlat(path, "2024-05-05T12:00:00", flat);
        populator.addToConstructingFlat(path, "2024-05-05T09:00:00", flat);

        Assert.assertEquals("2024-05-05T09:00:00", flat.get(path).getAsString());
    }

    @Test
    public void contextEndTimeRetainsLatestValue() {
        JsonObject flat = new JsonObject();
        String path = "test_template/context/_end_time";

        populator.addToConstructingFlat(path, "2024-05-05T10:00:00", flat);
        populator.addToConstructingFlat(path, "2024-05-05T09:00:00", flat);
        populator.addToConstructingFlat(path, "2024-05-05T11:00:00", flat);

        Assert.assertEquals("2024-05-05T11:00:00", flat.get(path).getAsString());
    }
}
