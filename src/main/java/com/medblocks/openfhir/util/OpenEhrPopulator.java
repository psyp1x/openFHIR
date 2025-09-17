package com.medblocks.openfhir.util;

import static com.medblocks.openfhir.fc.FhirConnectConst.OPENEHR_TYPE_CLUSTER;
import static com.medblocks.openfhir.fc.FhirConnectConst.OPENEHR_TYPE_NONE;
import static com.medblocks.openfhir.util.OpenFhirStringUtils.RECURRING_SYNTAX;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.medblocks.openfhir.fc.FhirConnectConst;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.Annotation;
import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Enumeration;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Ratio;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.TimeType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Class used for populating openEHR flat path Composition
 */
@Slf4j
@Component
public class OpenEhrPopulator {

    private final OpenFhirMapperUtils openFhirMapperUtils;

    public static final String DATA_ABSENT_REASON_URL = "http://hl7.org/fhir/StructureDefinition/data-absent-reason";
    private static final Set<String> DATA_ABSENT_REASON_SYSTEMS = Set.of(
            "http://terminology.hl7.org/CodeSystem/data-absent-reason",
            "http://hl7.org/fhir/data-absent-reason",
            "http://terminology.hl7.org/CodeSystem/dataabsentreason"
    );
    private static final String NULL_FLAVOUR_TERMINOLOGY = "openehr";

    private enum NullFlavourAttributes {
        UNKNOWN("unknown", "253"),
        NO_INFORMATION("no information", "271"),
        MASKED("masked", "272"),
        NOT_APPLICABLE("not applicable", "273");

        private final String value;
        private final String code;

        NullFlavourAttributes(String value, String code) {
            this.value = value;
            this.code = code;
        }

        public String getValue() {
            return value;
        }

        public String getCode() {
            return code;
        }
    }

    @Autowired
    public OpenEhrPopulator(OpenFhirMapperUtils openFhirMapperUtils) {
        this.openFhirMapperUtils = openFhirMapperUtils;
    }


    /**
     * Adds extracted value to the openEHR flat path composition represented with the 'constructingFlat' variable
     *
     * @param openEhrPath path that should be used in the flat path composition
     * @param extractedValue value as extracted from a FHIR object
     * @param openEhrType openEHR type as defined in the fhir connect model mapping
     * @param constructingFlat composition in a flat path format that's being constructed
     */
    public void setFhirPathValue(String openEhrPath, final Base extractedValue, final String openEhrType,
                                 final JsonObject constructingFlat) {
        if (openEhrType == null) {
            addValuePerFhirType(extractedValue, openEhrPath, constructingFlat, openEhrType);
            return;
        }
        if (OPENEHR_TYPE_NONE.equals(openEhrType) || OPENEHR_TYPE_CLUSTER.equals(openEhrType)) {
            log.warn("Adding nothing on path {} as type is marked as NONE / CLUSTER", openEhrPath);
            return;
        }
        if (extractedValue == null) {
            log.warn("Extracted value is null");
            return;
        }
        if (openEhrPath.contains(RECURRING_SYNTAX)) {
            // still has recurring syntax due to the fact some recurring elements were not aligned or simply couldn't have been
            // in this case just set all to 0th
            openEhrPath = openEhrPath.replace(RECURRING_SYNTAX, ":0");
        }

        if (openEhrPath.contains("null_flavour")) {
            final boolean handledNullFlavour = setNullFlavourForDataAbsentReason(openEhrPath, extractedValue,
                                                                                 constructingFlat);
            if (handledNullFlavour) {
                return;
            }
        }

        if (openEhrPath.contains("|")) {
            // can only be a string, ignore the actual type
            addPrimitive(extractedValue, openEhrPath, constructingFlat);
            return;
        }

        switch (openEhrType) {
            case FhirConnectConst.DV_MULTIMEDIA:
                handleDvMultimedia(openEhrPath, extractedValue, constructingFlat);
            case FhirConnectConst.DV_QUANTITY:
                final boolean addedQuantity = handleDvQuantity(openEhrPath, extractedValue, constructingFlat);
                if (addedQuantity) {
                    return;
                }
            case FhirConnectConst.DV_ORDINAL:
                boolean addedOrdinal = handleDvOrdinal(openEhrPath, extractedValue, constructingFlat);
                if (addedOrdinal) {
                    return;
                }
            case FhirConnectConst.DV_PROPORTION:
                boolean addedProportion = handleDvProportion(openEhrPath, extractedValue, constructingFlat);
                if (addedProportion) {
                    return;
                }
            case FhirConnectConst.DV_COUNT:
                final boolean addedCount = handleDvCount(openEhrPath, extractedValue, constructingFlat);
                if (addedCount) {
                    return;
                }
            case FhirConnectConst.DV_DATE_TIME:
                final boolean addedDateTime = handleDvDateTime(openEhrPath, extractedValue, constructingFlat);
                if (addedDateTime) {
                    return;
                }
            case FhirConnectConst.DV_DATE:
                final boolean addedDate = handleDvDate(openEhrPath, extractedValue, constructingFlat);
                if (addedDate) {
                    return;
                }
            case FhirConnectConst.DV_TIME:
                final boolean addedTime = handleDvTime(openEhrPath, extractedValue, constructingFlat);
                if (addedTime) {
                    return;
                }
            case FhirConnectConst.DV_CODED_TEXT:
                final boolean addedCodeText = handleDvCodedText(openEhrPath, extractedValue, constructingFlat);
                if (addedCodeText) {
                    return;
                }
            case FhirConnectConst.DV_IDENTIFIER:
                final boolean addedIdentifier = handleIdentifier(openEhrPath, extractedValue, constructingFlat);
                if (addedIdentifier) {
                    return;
                }
            case FhirConnectConst.CODE_PHRASE:
                final boolean addedCode = handleCodePhrase(openEhrPath, extractedValue, constructingFlat, openEhrType);
                if (addedCode) {
                    return;
                }
            case FhirConnectConst.DV_TEXT:
                addValuePerFhirType(extractedValue, openEhrPath, constructingFlat, FhirConnectConst.DV_TEXT);
                return;
            case FhirConnectConst.DV_BOOL:
                final boolean addedBool = handleDvBool(openEhrPath, extractedValue, constructingFlat);
                if (addedBool) {
                    return;
                }
            case FhirConnectConst.DV_PARTY_IDENTIFIED:
                final boolean addedPartyIdentified = handlePartyIdentifier(openEhrPath, extractedValue,
                                                                           constructingFlat);
                if (addedPartyIdentified) {
                    return;
                }
            case FhirConnectConst.DV_PARTY_PROXY:
                final boolean addedPartyProxy = handlePartyProxy(openEhrPath, extractedValue, constructingFlat);
                if (addedPartyProxy) {
                    return;
                }
            default:
                addValuePerFhirType(extractedValue, openEhrPath, constructingFlat, openEhrType);

        }
    }

    private void addPrimitive(final Base fhirValue, final String openEhrPath,
                              final JsonObject constructingFlat) {
        final String primitiveValue = fhirValue.primitiveValue();

        addToConstructingFlat(openEhrPath, primitiveValue, constructingFlat);
    }

    private void handleDvMultimedia(final String path, final Base value, final JsonObject flat) {
        if (value instanceof Attachment attachment) {
            int size = (attachment.getSize() == 0 && attachment.getData() != null) ? attachment.getData().length
                    : attachment.getSize();
            addToConstructingFlat(path + "|size", String.valueOf(size), flat);
            addToConstructingFlat(path + "|mediatype", attachment.getContentType(), flat);
            if (StringUtils.isNotEmpty(attachment.getUrl())) {
                addToConstructingFlat(path + "|url", attachment.getUrl(), flat);
            } else {
                addToConstructingFlat(path + "|data", Base64.getEncoder().encodeToString(attachment.getData()), flat);
            }
        } else {
            log.warn("openEhrType is MULTIMEDIA but extracted value is not Attachment; is {}", value.getClass());
        }
    }

    private boolean handleDvQuantity(final String path, final Base value, final JsonObject flat) {
        if (value instanceof Quantity quantity) {
            if (quantity.getValue() != null) {
                addToConstructingFlatDouble(path + "|magnitude", quantity.getValue().doubleValue(), flat);
            }
            addToConstructingFlat(path + "|unit", quantity.getUnit(), flat);
            return true;
        } else if (value instanceof Ratio ratio) {
            setFhirPathValue(path, ratio.getNumerator(), FhirConnectConst.DV_QUANTITY, flat);
            return true;
        } else if (value instanceof StringType stringType) {
            addToConstructingFlatDouble(path + "|magnitude", Double.valueOf(stringType.getValue()), flat);
            return true;
        } else {
            log.warn("openEhrType is DV_QUANTITY but extracted value is not Quantity and not Ratio; is {}",
                     value.getClass());
        }
        return false;
    }

    private boolean handleDvOrdinal(final String path, final Base value, final JsonObject flat) {
        if (value instanceof Quantity quantity) {
            if (quantity.getValue() != null) {
                addToConstructingFlat(path + "|ordinal", quantity.getValue().toPlainString(), flat);
            }
            addToConstructingFlat(path + "|value", quantity.getUnit(), flat);
            addToConstructingFlat(path + "|code", quantity.getCode(), flat);
            return true;
        } else {
            log.warn("openEhrType is DV_ORDINAL but extracted value is not Quantity; is {}", value.getClass());
        }
        return false;
    }

    private boolean handleDvProportion(final String path, final Base value, final JsonObject flat) {
        if (value instanceof Quantity quantity) {
            if ("%".equals(quantity.getCode())) {
                addToConstructingFlatDouble(path + "|denominator", 100.0, flat);
            }
            if (quantity.getValue() != null) {
                addToConstructingFlatDouble(path + "|numerator", quantity.getValue().doubleValue(), flat);
            }
            addToConstructingFlat(path + "|type", "2", flat); // hardcoded?
            return true;
        } else {
            log.warn("openEhrType is DV_PROPORTION but extracted value is not Quantity; is {}", value.getClass());
        }
        return false;
    }

    private boolean handleDvCount(final String path, final Base value, final JsonObject flat) {
        if (value instanceof Quantity quantity) {
            if (quantity.getValue() != null) {
                addToConstructingFlatInteger(path, quantity.getValue().intValueExact(), flat);
            }
            return true;
        } else if (value instanceof IntegerType integerType) {
            if (integerType.getValue() != null) {
                addToConstructingFlatInteger(path, integerType.getValue(), flat);
            }
            return true;
        } else {
            log.warn("openEhrType is DV_COUNT but extracted value is not Quantity and not IntegerType; is {}",
                     value.getClass());
        }
        return false;
    }

    private boolean handleDvDateTime(final String path, final Base value, final JsonObject flat) {
        if (value instanceof DateTimeType dateTime) {
            if (dateTime.getValue() != null) {
                final String formattedDate = openFhirMapperUtils.dateTimeToString(dateTime.getValue());
                addToConstructingFlat(path, formattedDate, flat);
            }
            return true;
        } else if (value instanceof DateType date) {
            if (date.getValue() != null) {
                final String formattedDate = openFhirMapperUtils.dateToString(date.getValue());
                addToConstructingFlat(path, formattedDate, flat);
            }
            return true;
        } else if (value instanceof TimeType time) {
            if (time.getValue() != null) {
                addToConstructingFlat(path, time.getValue(), flat);
            }
            return true;
        } else if (value instanceof InstantType instant) {
            if (instant.getValue() != null) {
                addToConstructingFlat(path, openFhirMapperUtils.dateTimeToString(instant.getValue()), flat);
            }
            return true;
        }
        return false;
    }

    private boolean handleDvDate(final String path, final Base value, final JsonObject flat) {
        if (value instanceof DateTimeType dateTime) {
            if (dateTime.getValue() != null) {
                final String formattedDate = openFhirMapperUtils.dateToString(dateTime.getValue());
                addToConstructingFlat(path, formattedDate, flat);
            }
            return true;
        } else if (value instanceof DateType date) {
            if (date.getValue() != null) {
                final String formattedDate = openFhirMapperUtils.dateToString(date.getValue());
                addToConstructingFlat(path, formattedDate, flat);
            }
            return true;
        }
        return false;
    }

    private boolean handleDvTime(final String path, final Base value, final JsonObject flat) {
        if (value instanceof DateTimeType dateTime) {
            if (dateTime.getValue() != null) {
                final String formattedDate = openFhirMapperUtils.timeToString(dateTime.getValue());
                addToConstructingFlat(path, formattedDate, flat);
            }
            return true;
        } else if (value instanceof DateType date) {
            if (date.getValue() != null) {
                final String formattedDate = openFhirMapperUtils.timeToString(date.getValue());
                addToConstructingFlat(path, formattedDate, flat);
            }
            return true;
        } else if (value instanceof TimeType time) {
            if (time.getValue() != null) {
                addToConstructingFlat(path, time.getValue(), flat);
            }
            return true;
        }
        return false;
    }

    private boolean handleDvCodedText(final String path, final Base value, final JsonObject flat) {
        if (value instanceof CodeableConcept codeableConcept) {
            List<Coding> codings = codeableConcept.getCoding();
            if (!codings.isEmpty()) {
                // Handle the first coding as the primary coded text
                Coding primaryCoding = codings.get(0);
                addToConstructingFlat(path + "|code", primaryCoding.getCode(), flat);
                addToConstructingFlat(path + "|terminology", primaryCoding.getSystem(), flat);
                addToConstructingFlat(path + "|value", primaryCoding.getDisplay(), flat);
                
                // Handle additional codings as mappings
                addAdditionalCodingsAsMappings(path, codings, flat);
            }
            addToConstructingFlat(path + "|value", codeableConcept.getText(), flat);
            return true;
        } else if (value instanceof Coding coding) {
            addToConstructingFlat(path + "|code", coding.getCode(), flat);
            addToConstructingFlat(path + "|terminology", coding.getSystem(), flat);
            addToConstructingFlat(path + "|value", coding.getDisplay(), flat);
            return true;
        } else if (value instanceof StringType extractedString && path.contains("|")) {
            addToConstructingFlat(path, extractedString.getValue(), flat);
            return true;
        } else {
            log.warn("openEhrType is DV_CODED_TEXT but extracted value is not CodeableConcept; is {}",
                     value.getClass());
        }
        return false;
    }

    /**
     * Adds additional codings from a CodeableConcept as mappings in the openEHR flat format
     * 
     * @param path The base path for the mappings
     * @param codings The list of codings (first one is skipped as it's the primary coding)
     * @param flat The JSON object to add the mappings to
     */
    private void addAdditionalCodingsAsMappings(String path, List<Coding> codings, JsonObject flat) {
        for (int i = 1; i < codings.size(); i++) {
            Coding coding = codings.get(i);
            String mappingPath = path + "/_mapping:" + (i-1);
            
            addToConstructingFlat(mappingPath + "/match", "=", flat);
            addToConstructingFlat(mappingPath + "/target|preferred_term", coding.getDisplay(), flat);
            addToConstructingFlat(mappingPath + "/target|code", coding.getCode(), flat);
            addToConstructingFlat(mappingPath + "/target|terminology", coding.getSystem(), flat);
        }
    }

    public boolean setNullFlavourForDataAbsentReason(final String openEhrPath,
                                                     final Base dataAbsentReasonValue,
                                                     final JsonObject constructingFlat) {
        if (constructingFlat == null || StringUtils.isBlank(openEhrPath) || dataAbsentReasonValue == null) {
            return false;
        }
        final String basePath = extractNullFlavourBasePath(openEhrPath);
        if (StringUtils.isBlank(basePath)) {
            return false;
        }
        final NullFlavourAttributes attributes = resolveNullFlavour(dataAbsentReasonValue);
        if (attributes == null) {
            return false;
        }

        addToConstructingFlat(basePath + "|value", attributes.getValue(), constructingFlat);
        addToConstructingFlat(basePath + "|code", attributes.getCode(), constructingFlat);
        addToConstructingFlat(basePath + "|terminology", NULL_FLAVOUR_TERMINOLOGY, constructingFlat);
        return true;
    }

    private String extractNullFlavourBasePath(final String openEhrPath) {
        final int pipeIndex = openEhrPath.indexOf('|');
        if (pipeIndex >= 0) {
            return openEhrPath.substring(0, pipeIndex);
        }
        return openEhrPath;
    }

    private NullFlavourAttributes resolveNullFlavour(final Base value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Extension extension) {
            if (!DATA_ABSENT_REASON_URL.equals(extension.getUrl())) {
                return null;
            }
            return resolveNullFlavour(extension.getValue());
        }
        if (value instanceof CodeableConcept concept) {
            for (Coding coding : concept.getCoding()) {
                final NullFlavourAttributes mapped = resolveNullFlavourFromCoding(coding);
                if (mapped != null) {
                    return mapped;
                }
            }
            return mapDataAbsentReasonCode(concept.getText());
        }
        if (value instanceof Coding coding) {
            return resolveNullFlavourFromCoding(coding);
        }
        if (value instanceof Enumeration<?> enumeration) {
            return mapDataAbsentReasonCode(enumeration.getValueAsString());
        }
        if (value instanceof CodeType codeType) {
            return mapDataAbsentReasonCode(codeType.getCode());
        }
        if (value instanceof StringType stringType) {
            return mapDataAbsentReasonCode(stringType.getValue());
        }
        if (value.hasPrimitiveValue()) {
            return mapDataAbsentReasonCode(value.primitiveValue());
        }
        return null;
    }

    private NullFlavourAttributes resolveNullFlavourFromCoding(final Coding coding) {
        if (coding == null) {
            return null;
        }
        if (StringUtils.isNotBlank(coding.getSystem())
                && !DATA_ABSENT_REASON_SYSTEMS.contains(coding.getSystem())) {
            return null;
        }
        return mapDataAbsentReasonCode(coding.getCode());
    }

    private NullFlavourAttributes mapDataAbsentReasonCode(final String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        final String normalized = code.toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "unknown":
            case "asked-unknown":
            case "temp-unknown":
            case "not-asked":
            case "not-a-number":
            case "negative-infinity":
            case "positive-infinity":
            case "not-performed":
            case "other":
                return NullFlavourAttributes.UNKNOWN;
            case "asked-declined":
            case "masked":
            case "not-permitted":
                return NullFlavourAttributes.MASKED;
            case "not-applicable":
            case "unsupported":
                return NullFlavourAttributes.NOT_APPLICABLE;
            case "as-text":
            case "error":
            default:
                return NullFlavourAttributes.NO_INFORMATION;
        }
    }

    private boolean handleIdentifier(final String path, final Base value, final JsonObject flat) {
        if (value instanceof Identifier identifier) {
            addToConstructingFlat(path + "|id", identifier.getValue(), flat);
            return true;
        } else if (value instanceof StringType identifier) {
            addToConstructingFlat(path + "|id", identifier.getValue(), flat);
            return true;
        } else {
            log.warn("openEhrType is IDENTIFIER but extracted value is not Identifier; is {}", value.getClass());
        }
        return false;
    }

    private boolean handlePartyIdentifier(final String path, final Base value, final JsonObject flat) {
        if (value instanceof StringType string) {
            addToConstructingFlat(path + "|name", string.getValue(), flat);
            return true;
        } else if (value instanceof Identifier id) {
            addToConstructingFlat(path + "|id", id.getValue(), flat);
            addToConstructingFlat(path + "|assigner", id.getSystem(), flat);
            addToConstructingFlat(path + "|type", id.getType().getText(), flat);
            // if coding.code exists, it should override the type
            addToConstructingFlat(path + "|type", id.getType().getCodingFirstRep().getCode(), flat);
            return true;
        } else {
            log.warn(
                    "openEhrType is CODE_PHRASE but extracted value is not Coding, Extension, CodeableConcept or Enumeration; is {}",
                    value.getClass());
        }
        return false;
    }

    private boolean handlePartyProxy(final String path, final Base value, final JsonObject flat) {
        if (value instanceof StringType string) {
            addToConstructingFlat(path + "|name", string.getValue(), flat);
            return true;
        } else {
            log.warn(
                    "openEhrType is CODE_PHRASE but extracted value is not Coding, Extension, CodeableConcept or Enumeration; is {}",
                    value.getClass());
        }
        return false;
    }

    private boolean handleCodePhrase(final String path, final Base value, final JsonObject flat,
                                     final String openEhrType) {
        if (value instanceof Coding coding) {
            addToConstructingFlat(path + "|code", coding.getCode(), flat);
            addToConstructingFlat(path + "|value", coding.getCode(), flat);
            addToConstructingFlat(path + "|terminology", coding.getSystem(), flat);
            return true;
        } else if (value instanceof Extension extension) {
            setFhirPathValue(path, extension.getValue(), openEhrType, flat);
            return true;
        } else if (value instanceof CodeableConcept concept) {
            setFhirPathValue(path, concept.getCodingFirstRep(), openEhrType, flat);
            return true;
        } else if (value instanceof Enumeration<?> enumeration) {
            addToConstructingFlat(path + "|code", enumeration.getValueAsString(), flat);
            addToConstructingFlat(path + "|value", enumeration.getValueAsString(), flat);
            return true;
        } else {
            log.warn(
                    "openEhrType is CODE_PHRASE but extracted value is not Coding, Extension, CodeableConcept or Enumeration; is {}",
                    value.getClass());
        }
        return false;
    }

    private boolean handleDvBool(final String path, final Base value, final JsonObject flat) {
        if (value instanceof BooleanType booleanType) {
            addToConstructingBoolean(path, booleanType.getValue(), flat);
            return true;
        } else {
            log.warn("openEhrType is DV_BOOL but extracted value is not BooleanType; is {}", value.getClass());
        }
        return false;
    }

    private void addValuePerFhirType(final Base fhirValue, final String openEhrPath,
                                     final JsonObject constructingFlat,
                                     final String openehrType) {
        if (fhirValue instanceof Quantity extractedQuantity) {
            if (extractedQuantity.getValue() != null) {
                addToConstructingFlat(openEhrPath, extractedQuantity.getValue().toPlainString(), constructingFlat);
            }
        } else if (fhirValue instanceof Coding extractedCoding) {
            handleCodePhrase(openEhrPath, extractedCoding, constructingFlat, openehrType);
        } else if (fhirValue instanceof CodeableConcept codeableConcept) {
            handleCodePhrase(openEhrPath, codeableConcept.getCodingFirstRep(), constructingFlat, openehrType);
        } else if (fhirValue instanceof DateTimeType extractedQuantity) {
            addToConstructingFlat(openEhrPath, extractedQuantity.getValueAsString(), constructingFlat);
        } else if (fhirValue instanceof Annotation extracted) {
            addToConstructingFlat(openEhrPath, extracted.getText(), constructingFlat);
        } else if (fhirValue instanceof Address extracted) {
            addToConstructingFlat(openEhrPath, extracted.getText(), constructingFlat);
        } else if (fhirValue instanceof HumanName extracted) {
            addToConstructingFlat(openEhrPath, extracted.getNameAsSingleString(), constructingFlat);
        } else if (fhirValue instanceof Extension extracted) {
            if (extracted.getValue().hasPrimitiveValue()) {
                addValuePerFhirType(extracted.getValue(), openEhrPath, constructingFlat, openehrType);
            }
//            addToConstructingFlat(openEhrPath, extracted.getValue().hasPrimitiveValue() ? extracted.getValue().primitiveValue() : null, constructingFlat);
        } else if (fhirValue.hasPrimitiveValue()) {
            addToConstructingFlat(openEhrPath, fhirValue.primitiveValue(), constructingFlat);
        } else {
            log.error("Unsupported fhir value toString!: {}", fhirValue);
        }
    }

    final void addToConstructingFlat(final String key, final String value, final JsonObject constructingFlat) {
        if (StringUtils.isEmpty(value)) {
            return;
        }

        if (isContextStartKey(key)) {
            toUpdateContextBoundary(key, value, constructingFlat, true);
            return;
        }

        if (isContextEndKey(key)) {
            toUpdateContextBoundary(key, value, constructingFlat, false);
            return;
        }

        log.debug("Setting value {} on path {}", value, key);
        constructingFlat.add(key, new JsonPrimitive(value));
    }

    private boolean isContextStartKey(final String key) {
        return key.contains("/context/") && key.endsWith("start_time");
    }

    private boolean isContextEndKey(final String key) {
        if (!key.contains("/context/")) {
            return false;
        }
        return key.endsWith("_end_time") || key.endsWith("/end_time");
    }

    private void toUpdateContextBoundary(final String key,
                                           final String candidateValue,
                                           final JsonObject constructingFlat,
                                           final boolean pickEarliest) {
        final var existing = constructingFlat.get(key);
        if (existing == null || existing.isJsonNull()) {
            constructingFlat.add(key, new JsonPrimitive(candidateValue));
            return;
        }

        final String existingValue = existing.getAsString();
        final Date existingDate = openFhirMapperUtils.stringToDate(existingValue);
        final Date candidateDate = openFhirMapperUtils.stringToDate(candidateValue);

        if (existingDate == null || candidateDate == null) {
            final boolean shouldReplace = pickEarliest
                    ? candidateValue.compareTo(existingValue) < 0
                    : candidateValue.compareTo(existingValue) > 0;
            if (shouldReplace) {
                constructingFlat.add(key, new JsonPrimitive(candidateValue));
            }
            return;
        }

        final boolean shouldReplace = pickEarliest ? candidateDate.before(existingDate)
                : candidateDate.after(existingDate);
        if (shouldReplace) {
            constructingFlat.add(key, new JsonPrimitive(candidateValue));
        }
    }

    final void addToConstructingBoolean(final String key, final Boolean value, final JsonObject constructingFlat) {
        if (value == null) {
            return;
        }
        constructingFlat.addProperty(key, value);
    }

    final void addToConstructingFlatDouble(final String key, final Double value, final JsonObject constructingFlat) {
        if (value == null) {
            return;
        }
        constructingFlat.add(key, new JsonPrimitive(value));
    }

    final void addToConstructingFlatInteger(final String key, final Integer value, final JsonObject constructingFlat) {
        if (value == null) {
            return;
        }
        constructingFlat.add(key, new JsonPrimitive(value));
    }
}
