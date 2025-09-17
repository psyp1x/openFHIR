package com.medblocks.openfhir.util;

import static com.medblocks.openfhir.fc.FhirConnectConst.CONDITION_OPERATOR_EMPTY;
import static com.medblocks.openfhir.fc.FhirConnectConst.CONDITION_OPERATOR_NOT_EMPTY;
import static com.medblocks.openfhir.fc.FhirConnectConst.FHIR_ROOT_FC;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.medblocks.openfhir.fc.FhirConnectConst;
import com.medblocks.openfhir.fc.schema.model.Condition;
import com.medblocks.openfhir.toopenehr.FhirToOpenEhrHelper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Coding;
import org.springframework.stereotype.Component;

@Component
public class OpenFhirStringUtils {

    private final String TYPE_PATTERN = "\\[TYPE:[^]]+]";
    private final String ALL_INDEXES = ":(\\d+)";
    public static final String RESOLVE = "resolve()";
    public static final String WHERE = "where";
    public static final String RECURRING_SYNTAX = "[n]";
    public static final String RECURRING_SYNTAX_ESCAPED = "\\[n]";

    public static final String WHERE_CLAUSE = ".where(";

    public static final String FALSE_CLAUSE = "=false";

    /**
     * Adds regex pattern to the simplified flat path so that we can match all entries in a flat json
     *
     * @param simplifiedFlat simplified path as given in the fhir connect model mapings
     * @return simplified path with regex pattern
     */
    public String addRegexPatternToSimplifiedFlatFormat(final String simplifiedFlat) {
        final String[] parts = simplifiedFlat.split("/");
        final boolean lastOneHasPipe = parts[parts.length - 1].contains("|");
        if (lastOneHasPipe) {
            final String[] partsWithoutLast = Arrays.copyOf(parts, parts.length - 1);
            final String[] lastPart = parts[parts.length - 1].split("\\|");
            return String.join("(:\\d+)?/", partsWithoutLast) + "(:\\d+)?/" + lastPart[0] + "(:\\d+)?\\|" + lastPart[1];
        } else {
            return String.join("(:\\d+)?/", parts) + "(:\\d+)?(\\|.*)?";
        }
    }

    /**
     * When writing simplified openEHR flat path in FHIR Connect mappings, you may need to escape dots if they are a
     * part
     * of the archetype name. Before we adjust openEHR paths to the actual ones based on RM, this needs to be removed
     * which is what this method does.
     */
    public void fixEscapedDotsInOpenEhrPaths(final List<FhirToOpenEhrHelper> flattened) {
        for (FhirToOpenEhrHelper fhirToOpenEhrHelper : flattened) {
            fhirToOpenEhrHelper.setOpenEhrPath(fhirToOpenEhrHelper.getOpenEhrPath().replace("\\.", "."));
            if (fhirToOpenEhrHelper.getFhirToOpenEhrHelpers() != null) {
                fixEscapedDotsInOpenEhrPaths(fhirToOpenEhrHelper.getFhirToOpenEhrHelpers());
            }
        }
    }

    public String endsWithOpenEhrType(final String path) {
        final Set<String> openEhrTypes = new HashSet<>();
//        openEhrTypes.add("magnitude");
//        openEhrTypes.add("unit");
//        openEhrTypes.add("ordinal");
//        openEhrTypes.add("value");
//        openEhrTypes.add("code");
//        openEhrTypes.add("terminology");
        openEhrTypes.add("terminology_id");
        openEhrTypes.add("terminology_id/value");
        openEhrTypes.add("defining_code/code_string");
        openEhrTypes.add("defining_code");
        openEhrTypes.add("/value");
        openEhrTypes.add("code_string");
        for (String openEhrType : openEhrTypes) {
            if (path.endsWith(openEhrType)) {
                return openEhrType;
            }
        }
        return null;
    }

    public String replaceLastIndexOf(final String string, final String charToReplace, final String replaceWith) {
        int start = string.lastIndexOf(charToReplace);
        return string.substring(0, start) +
                replaceWith +
                string.substring(start + charToReplace.length());
    }

    public String replaceFirstIndex(String path, int newIndex) {
        return path.replaceFirst(":(\\d+)", ":" + newIndex);
    }

    /**
     * replaces dots in a simplified openEHR path with / and replaces FHIR Connect reference to openEHR archetype with
     * the actual one
     *
     * @param openEhr simplified openEHR path
     * @param openEhrArchetypeId archetype ID of the mapping archetype
     * @return prepared openehr path
     */
    public String prepareOpenEhrSyntax(final String openEhr, final String openEhrArchetypeId) {
        if (openEhr == null) {
            return null;
        }
        String preparedOpenEhr = Pattern.compile("\\[(.*?)]")
                .matcher(openEhr)
                .replaceAll(match -> {
                    // Get the content inside brackets
                    String content = match.group(1);
                    // Replace `/` with `-`
                    return "[" + content.replace("/", "*") + "]";
                });
        return preparedOpenEhr
                .replace("/" + FhirConnectConst.OPENEHR_ROOT_FC, "")
                .replace(FhirConnectConst.OPENEHR_ROOT_FC, "")
                .replace(FhirConnectConst.OPENEHR_ARCHETYPE_FC, openEhrArchetypeId);
    }

    /**
     * Returnes last index from the openEHR path, i.e. when passing in a:1/b:1/c/d:3, the '3' will be returned
     *
     * @param path path where we're extracting the index from
     * @return index as Integer extracted from the given openEHR path
     */
    public Integer getLastIndex(final String path) {
        String RIGHT_MOST_INDEX = ":(\\d+)(?!.*:)";
        final String match = getByRegex(path, RIGHT_MOST_INDEX);
        if (match == null) {
            return -1;
        }
        return Integer.valueOf(match);
    }

    /**
     * get outer most index of all indexes in path that is the same for all paths
     */
    public Integer getLastMostCommonIndex(final List<String> paths) {
        List<List<Integer>> indicesList = new ArrayList<>();

        for (String path : paths) {
            List<Integer> indices = getAllIndexes(path);
            indicesList.add(indices);
        }

        return findCommonRightmostIndex(indicesList);
    }

    private static Integer findCommonRightmostIndex(List<List<Integer>> indicesList) {
        int minSize = indicesList.stream().mapToInt(List::size).min().orElse(0);

        for (int i = minSize - 1; i >= 0; i--) {
            Integer candidate = indicesList.get(0).get(i);
            final int iFinal = i;
            boolean allMatch = indicesList.stream()
                    .allMatch(list -> list.size() > iFinal && list.get(iFinal).equals(candidate));

            if (allMatch) {
                return candidate;
            }
        }
        return -1;
    }

    public String getCastType(final String path) {
        String CAST_TYPE = "as\\(([^()]*)\\)";
        return getByRegex(path, CAST_TYPE);
    }

    private String getByRegex(final String path,
                              final String regex) {
        final List<String> byRegexAll = getByRegexAll(path, regex);
        if (byRegexAll == null) {
            return null;
        }
        return byRegexAll.get(0);
    }

    private List<String> getByRegexAll(final String path,
                                       final String regex) {
        final Pattern compiledPattern = Pattern.compile(regex);
        final Matcher matcher = compiledPattern.matcher(path);

        final List<String> matches = new ArrayList<>();

        while (matcher.find()) {
            matches.add(matcher.group(1));
        }
        if (matches.isEmpty()) {
            return null;
        }
        return matches;
    }

    /**
     * Returnes first index from the openEHR path, i.e. when passing in a:1/b:2/c/d:3, the '1' will be returned
     *
     * @param path path where we're extracting the index from
     * @return index as Integer extracted from the given openEHR path
     */
    public Integer getFirstIndex(final String path) {
        final String byRegex = getByRegex(path, ALL_INDEXES);
        if (byRegex == null) {
            return null;
        }
        return Integer.valueOf(byRegex);
    }

    public List<Integer> getAllIndexes(final String path) {
        final List<String> matches = getByRegexAll(path, ALL_INDEXES);
        if (matches == null) {
            return Collections.emptyList();
        }
        return matches.stream().map(Integer::parseInt).collect(Collectors.toList());
    }

    /**
     * having fullPath laborbericht:1/laborbefund/pro_laboranalyt:0/bezeichnung_des_analyts|terminology
     * and element laborbericht/laborbefund/pro_laboranalyt
     *
     * @return this will return last index matching this path
     */
    public int getIndexOfElement(final String element, final String fullPath) {
        final String[] splitFullPath = fullPath.split("/");
        final String[] splitElement = element.split("/");
        for (int i = 0; i < splitElement.length; i++) {
            final String el = splitElement[i];
            final String path = splitFullPath[i];
            final String[] splitByColon = path.split(":");
            final String toMatch = splitByColon[0];
            if (toMatch.equals(el) && i == splitElement.length - 1 && splitByColon.length > 1) {
                return Integer.parseInt(splitByColon[1]);
            }
        }
        return -1;
    }

    public String prepareParentOpenEhrPath(String fullOpenEhrPath,
                                           final String parentOpenEhrPath) {
        // person/personendaten:2/person/geburtsname:1/vollständiger_name
        // person.personendaten.person.geburtsname
        // Step 1: Split the strings by their delimiters
        fullOpenEhrPath = fullOpenEhrPath.replace("/", ".");

        String[] withIndexesParts = parentOpenEhrPath.split("/");
        String[] withoutIndexesParts = fullOpenEhrPath.split("\\.");

        // Step 2: Iterate over the parts and add indexes
        StringBuilder result = new StringBuilder();
        int j = 0;

        for (int i = 0; i < withoutIndexesParts.length; i++) {
            String part = withoutIndexesParts[i];
            if (part.endsWith(RECURRING_SYNTAX)) {
                part = part.replace(RECURRING_SYNTAX, "");
            }
            if (j < withIndexesParts.length && withIndexesParts[j].startsWith(part)) {
                result.append(withIndexesParts[j]);
                j++;
            } else {
                result.append(part);
            }

            if (i < withoutIndexesParts.length - 1) {
                result.append("/");
            }
        }
        return result.toString();
    }

    public String replaceCommonPaths(final String parent, final String toReplace) {
        final List<String> splitParent = List.of(parent.split("\\."));
        final List<String> childSplit = List.of(toReplace.split("\\."));

        final StringJoiner sj = new StringJoiner(".");
        for (int i = 0; i < childSplit.size(); i++) {
            if (i >= splitParent.size()) {
                sj.add(childSplit.get(i));
                continue;
            }
            String parentPath = splitParent.get(i);
            if (!parentPath.equals(childSplit.get(i))) {
                sj.add(childSplit.get(i));
            }
        }
        return sj.toString();
    }

    public String fixOpenEhrPath(final String openEhrPath,
                                 final String mainOpenEhrPath) {
        if (openEhrPath.startsWith(FhirConnectConst.OPENEHR_COMPOSITION_FC + "/content")) {
            // means openehr path already has the full one
            final String compositionReplacement = mainOpenEhrPath.split("/")[0];
            return openEhrPath
                    .replace(FhirConnectConst.OPENEHR_COMPOSITION_FC, compositionReplacement);
        } else {
            return openEhrPath
                    .replace(FhirConnectConst.REFERENCE + "/", "")
                    .replace(FhirConnectConst.OPENEHR_ARCHETYPE_FC, mainOpenEhrPath)
                    .replace(FhirConnectConst.OPENEHR_COMPOSITION_FC, mainOpenEhrPath);
        }
    }

    public String fixFhirPath(final String fhirPath) {
        return fhirPath
                .replace("." + FHIR_ROOT_FC, "")
                .replace(FHIR_ROOT_FC, "");
    }

    /**
     * fixes fhirPath casting, as BooleanType is not a valid FHIR path, but boolean is.. similar to StringType > String,
     * ..
     *
     * @param originalFhirPath path as it exists up until now
     * @return fhir path with casting
     */
    public String fixFhirPathCasting(final String originalFhirPath) {
        final String replacedCasting = replaceCasting(originalFhirPath);
        // now check if resolve() was preceeded with a case to a specific Resource; if that has happened, it needs to be
        // removed because it's not handled properly by fhirPath evaluation engine
        final String[] splitPath = replacedCasting.split("\\.");
        final StringJoiner building = new StringJoiner(".");
        for (int i = 0; i < splitPath.length; i++) {
            final String firstPath = splitPath[i];
            if (splitPath.length - 1 == i) {
                building.add(firstPath);
                break;
            }
            final String secondPath = splitPath[i + 1];
            if (firstPath.startsWith("as(") && secondPath.equals(RESOLVE)) {
                i++;
                building.add(secondPath);
            } else {
                building.add(firstPath);
            }
        }
        return building.toString();
    }

    private String replaceCasting(final String originalFhirPath) {
        return originalFhirPath.replace("as(BooleanType)", "as(Boolean)")
                .replace("as(DateTimeType)", "as(DateTime)")
                .replace("as(TimeType)", "as(Time)")
                .replace("as(StringType)", "as(String)");
    }

    /**
     * FHIR path amended in a way that condition becomes a part of it
     *
     * @param originalFhirPath original fhir path without conditions as it exists within a model mapper
     * @param conditions conditions defined within a model mapper
     * @param resource fhir resource being used as a base
     * @return fhir path with condition elemenets included in the fhir path itself
     *         deprecated: use getFhirPathWithConditions instead! this method should be removed as soon as possible to
     *         clear up
     *         the code base and remove redundant ones
     */
    public String amendFhirPath(final String originalFhirPath, final List<Condition> conditions,
                                final String resource) {
        String fhirPath = originalFhirPath.replace(FhirConnectConst.FHIR_RESOURCE_FC, resource);
        if (fhirPath.contains(FhirConnectConst.FHIR_ROOT_FC)) {
            fhirPath = fhirPath.replace("." + FhirConnectConst.FHIR_ROOT_FC, "")
                    .replace(FhirConnectConst.FHIR_ROOT_FC, "");
        }
        if (conditions == null || conditions.isEmpty() || conditions.stream().allMatch(Objects::isNull)) {
            return fhirPath;
        }
        final StringJoiner stringJoiner = new StringJoiner(" and ");
        for (Condition condition : conditions) {
            if (condition == null) {
                continue;
            }
            final String targetAttribute = condition.getTargetAttribute();

            if (condition.getTargetRoot().startsWith(FhirConnectConst.FHIR_RESOURCE_FC)) {
                condition.setTargetRoot(condition.getTargetRoot().replace(FhirConnectConst.FHIR_RESOURCE_FC, resource));
            }
            // add condition in there within the fhirpath itself
            final String base;
            if (condition.getTargetRoot().startsWith(fhirPath)) {
                base = condition.getTargetRoot();
            } else {
                base = fhirPath;
            }
            boolean negate = FhirConnectConst.CONDITION_OPERATOR_NOT_OF.equals(condition.getOperator());

            getWhereClause(resource, stringJoiner, condition, targetAttribute, base, negate);

        }
        return stringJoiner.toString();
    }

    private void getWhereClause(final String resource, final StringJoiner stringJoiner, Condition condition,
                                final String targetAttribute, final String base, boolean negate) {
        List<String> codes = getCodesFromCriteria(condition.getCriteria());

        String joinedConditions = codes.stream()
                .map(code -> {
                    if(targetAttribute.contains("coding")){
                        return targetAttribute.replace(".code",".where")+ "(code='" + code + "').exists()";
                    } else if (targetAttribute.contains("url")) {
                        return targetAttribute +".toString().contains('"+code+"')";
                    } else {
                        return targetAttribute+".toString() = '"+code +"'";
                    }
                })
                .collect(Collectors.joining(" or "));

        String whereClause;
        if(codes.size()>1){
            whereClause = WHERE_CLAUSE + (negate ? "(" + joinedConditions + ")" + FALSE_CLAUSE : joinedConditions) + ")";
        } else{
            whereClause = WHERE_CLAUSE + (negate ?  joinedConditions + FALSE_CLAUSE : joinedConditions) + ")";
        }
        stringJoiner.add(base
                .replace(condition.getTargetRoot(), condition.getTargetRoot() + whereClause)
                .replace(FhirConnectConst.FHIR_RESOURCE_FC, resource));
    }

    public String getFhirPathWithoutConditions(final String originalFhirPath, final Condition condition,
                                               final String resource) {
        if (condition == null || condition.getTargetAttribute() == null) {
            return originalFhirPath.replace(FhirConnectConst.FHIR_RESOURCE_FC, resource);
        }
        final String targetAttribute = condition.getTargetAttribute();
        return originalFhirPath.replace(FhirConnectConst.FHIR_RESOURCE_FC, resource) + "." + targetAttribute;
    }

    public String extractWhereCondition(final String path) {
        return extractWhereCondition(path, false);
    }


    /**
     * Gets all entries from the flat path that match simplified openehr path with regex pattern
     *
     * @param withRegex simplified openehr path with regex pattern
     * @param compositionFlatPath composition in a flat path format
     * @return a list of Strings that match the given flat path with regex pattern
     */
    public List<String> getAllEntriesThatMatch(final String withRegex, final JsonObject compositionFlatPath) {
        Pattern compiledPattern = Pattern.compile(withRegex);
        final List<String> match = new ArrayList<>();
        for (Map.Entry<String, JsonElement> flatEntry : compositionFlatPath.entrySet()) {
            final Matcher matcher = compiledPattern.matcher(flatEntry.getKey());

            final List<String> matches = new ArrayList<>();

            while (matcher.find() && !isNotSame(flatEntry.getKey(), matcher.group())) {
                matches.add(matcher.group());
            }
            if (matches.isEmpty()) {
                continue;
            }
            match.addAll(matches);
        }
        return match;
    }

    /**
     * Will return all entries from compositionFlatPath where key starts with path. However all up until the pipe
     * need to match
     */
    public List<String> getAllEntriesThatMatchIgnoringPipe(final String path, final JsonObject compositionFlatPath) {
        final List<String> match = new ArrayList<>();
        for (final Map.Entry<String, JsonElement> flatEntry : compositionFlatPath.entrySet()) {
            if (flatEntry.getKey().split("\\|")[0].equals(path.split("\\|")[0])) {
                match.add(flatEntry.getValue().getAsString());
            }
        }
        return match;
    }


    /**
     * If the only difference is a digit, for example
     * diagnose/diagnose:0/klinischer_status/klinischer_status2
     * matching
     * diagnose/diagnose:0/klinischer_status/klinischer_status
     * then we need to make sure it's actually not a match
     */
    private boolean isNotSame(final String lookingFor, final String found) {
        final String diff = lookingFor.replace(found, "");
        return StringUtils.isNotBlank(diff) && Character.isDigit(diff.charAt(0));
    }

    public String extractWhereCondition(final String path, final boolean last) {
        String start = "where(";  // We start after 'where('
        int startIndex = last ? path.lastIndexOf(start) : path.indexOf(start);

        if (startIndex == -1) {
            return null; // No match found
        }

        int openParenthesisCount = 1;  // Start counting after 'where('
        int endIndex = startIndex + start.length();  // Start looking from the character after 'where('

        // Traverse the string and count parentheses
        while (endIndex < path.length()) {
            char currentChar = path.charAt(endIndex);

            if (currentChar == '(') {
                openParenthesisCount++;
            } else if (currentChar == ')') {
                openParenthesisCount--;

                // If the count reaches 0, we've found the matching closing parenthesis
                if (openParenthesisCount == 0) {
                    break;
                }
            }
            endIndex++;
        }

        if (openParenthesisCount != 0) {
            return null; // Parentheses weren't balanced
        }

        // Return the matched string
        return path.substring(startIndex, endIndex + 1); // Include the closing parenthesis

    }

    public String constructFhirPathNoConditions(final String originalFhirPath,
                                                final String parentPath) {
        // only make sure parent's where path is added to the child
        if (StringUtils.isEmpty(parentPath)) {
            return originalFhirPath;
        }
        final String parentsWhereCondition = extractWhereCondition(parentPath);
        if (StringUtils.isEmpty(parentsWhereCondition)) {
            return originalFhirPath;
        } else {
            // find the correct place within children's path to add parent's where
            if (originalFhirPath.contains(parentPath)) {
                // all is done already
                return originalFhirPath;
            } else {
                return setParentsWherePathToTheCorrectPlace(originalFhirPath, parentPath);
            }
        }
    }

    public String constructFhirPathWithConditions(final String originalFhirPath,
                                                  final String parentPath,
                                                  final Condition condition,
                                                  final String resource) {
        // append parent's where path first
        String withParentsWhereInPlace;
        final String remainingItems;
        final String actualConditionTargetRoot = condition.getTargetRoot()
                .replace(FhirConnectConst.FHIR_RESOURCE_FC, resource)
                .replace(FHIR_ROOT_FC, "");
        if (originalFhirPath.startsWith(actualConditionTargetRoot)) {
            // then we use target root as the base path
            withParentsWhereInPlace = setParentsWherePathToTheCorrectPlace(actualConditionTargetRoot, parentPath);
            final String addedWhere = parentPath == null ? "" : extractWhereCondition(parentPath, true);
            final String remainingFromCondition = actualConditionTargetRoot.replace(
                    withParentsWhereInPlace.replace("." + addedWhere, ""), "");
            if (!withParentsWhereInPlace.equals(remainingFromCondition)) {
                withParentsWhereInPlace += remainingFromCondition;
            }
            remainingItems = originalFhirPath.replace(actualConditionTargetRoot, "");
        } else {
            withParentsWhereInPlace = setParentsWherePathToTheCorrectPlace(originalFhirPath, parentPath);
            remainingItems = "";
        }

        boolean negate = FhirConnectConst.CONDITION_OPERATOR_NOT_OF.equals(condition.getOperator());
        String targetAttr = condition.getTargetAttribute();
        List<String> codes = getCodesFromCriteria(condition.getCriteria());

        String joinedConditions = codes.stream()
                .map(code -> {
                    if(targetAttr.contains("coding")){
                        return targetAttr.replace(".code",".where")+ "(code='" + code + "').exists()";
                    } else if (targetAttr.contains("url")) {
                        return targetAttr +".toString().contains('"+code+"')";
                    } else {
                        return targetAttr+".toString() = '"+code +"'";
                    }
                })
                .collect(Collectors.joining(" or "));

        if (actualConditionTargetRoot.startsWith(resource) && withParentsWhereInPlace.equals(originalFhirPath)) {
            final String commonPath = setParentsWherePathToTheCorrectPlace(originalFhirPath,
                    actualConditionTargetRoot); // path right before the condition should start
            final String remainingToEndUpInWhere = actualConditionTargetRoot
                    .replace(commonPath + ".", "")
                    .replace(commonPath, "");
            final String remainingToAdd =
                    StringUtils.isBlank(remainingToEndUpInWhere) ? "" : (remainingToEndUpInWhere + ".");

            final String whereClause;
            if(codes.size()>1){
                whereClause = WHERE_CLAUSE + remainingToAdd + (negate ? "(" + joinedConditions + ")" + FALSE_CLAUSE : joinedConditions) + ")";
            } else{
                whereClause = WHERE_CLAUSE + remainingToAdd + (negate ? joinedConditions + FALSE_CLAUSE : joinedConditions) + ")";
            }
            final String remainingItemsFromParent = originalFhirPath.replace(commonPath, "");
            return commonPath + whereClause + remainingItemsFromParent;
        } else {
            final String whereClause;
            if(codes.size() >1){
                whereClause = WHERE_CLAUSE + (negate ? "(" + joinedConditions + ")" + FALSE_CLAUSE : joinedConditions) + ")";
            } else{
                whereClause = WHERE_CLAUSE + (negate ? joinedConditions + FALSE_CLAUSE : joinedConditions) + ")";
            }
            // then suffix with whatever is left from the children's path
            return withParentsWhereInPlace + whereClause + (StringUtils.isBlank(remainingItems) ? ""
                    : (remainingItems.startsWith(".") ? remainingItems : ("." + remainingItems)));
        }

    }

    /**
     * Return originalFhirPath amended with the actual condition .where elements. This method will construct a fhir
     * path from Condition and add that to the original fhir path
     *
     * @param originalFhirPath original fhir path that will be amended with conditions
     * @param condition condition we'll use when constructing a .where clause
     * @param resource resource type
     * @param parentPath parent fhir path, if one exists
     * @return fhir path amended with the .where clause as constructed from the given Condition
     */
    public String getFhirPathWithConditions(String originalFhirPath,
                                            final Condition condition,
                                            final String resource,
                                            final String parentPath) {
        originalFhirPath = originalFhirPath.replace(FhirConnectConst.FHIR_RESOURCE_FC, resource);
        if (condition != null
                && condition.getTargetAttribute() == null
                && condition.getTargetAttributes() != null
                && !condition.getTargetAttributes().isEmpty()) {
            // fallback until it's entirely deprecated
            condition.setTargetAttribute(condition.getTargetAttributes().get(0));
        }
        if (condition == null
                || condition.getTargetAttribute() == null
                || condition.getOperator().equals(CONDITION_OPERATOR_NOT_EMPTY)
                || condition.getOperator().equals(CONDITION_OPERATOR_EMPTY)) {
            // only make sure parent's where path is added to the child
            return constructFhirPathNoConditions(originalFhirPath, parentPath);
        } else {
            return constructFhirPathWithConditions(originalFhirPath, parentPath, condition, resource);
        }
    }

    public String setParentsWherePathToTheCorrectPlace(final String child,
                                                       final String parent) {
        if (StringUtils.isEmpty(parent)) {
            return child;
        }
        StringJoiner childPathJoiner = new StringJoiner(".");
        final String[] parents = parent.split("\\.");
        final String[] children = child.split("\\.");

        int parentSubstringCount = 0;
        int parentIndex = 0;
        for (int i = 0; i < children.length; i++) {
            final String childPath = children[i];
            if (parentIndex >= parents.length || childPath.equals(parents[parentIndex])) {
                childPathJoiner.add(childPath);
                if (parentIndex < parents.length) {
                    parentSubstringCount += parents[parentIndex].length();
                }
                parentIndex++;
            } else {
                final String string = parents[parentIndex];
                if (string.startsWith(WHERE)) {
                    // a where follows
                    final String substringForRelevantWhere = parent.substring(Arrays.stream(parents)
                                                                                      .limit(parentIndex)
                                                                                      .mapToInt(String::length)
                                                                                      .sum());
                    final String firstWhereCondition = extractWhereCondition(substringForRelevantWhere);
                    childPathJoiner.add(firstWhereCondition);
                    childPathJoiner.add(childPath);
                    parentIndex += (int) (firstWhereCondition.chars().filter(ch -> ch == '.').count() + 2);
                } else {
                    childPathJoiner.add(childPath);
                }
            }
        }

        // if all that's left is a where at the end of parent's path, this where needs to be added too
        final String constructedChildPath = childPathJoiner.toString();
        if(parent.startsWith(constructedChildPath)) {
            final String onlyWhere = parent.replace(constructedChildPath, "");
            final String extractedWhere = extractWhereCondition(onlyWhere);
            if(extractedWhere != null && (extractedWhere.equals(onlyWhere) || ("."+ extractedWhere).equals(onlyWhere))) {
                return constructedChildPath + onlyWhere;
            }
        }

        return constructedChildPath;
    }

    /**
     * i.e.
     * growth_chart/body_weight/any_event:2/weight|unit
     * and
     * growth_chart/body_weight/any_event:2/weight|magnitude
     * will be joined together, as they are a single object
     */
    public Map<String, List<String>> joinValuesThatAreOne(final List<String> matchingEntries) {
        final Map<String, List<String>> matchings = new HashMap<>();
        for (String matchingEntry : matchingEntries) {
            final String[] split = matchingEntry.split("\\|");
            final String root = split[0];
            if (split.length == 1) {
                final List<String> list = new ArrayList<>();
                list.add(root);
                matchings.put(root, list);
            } else {
                if (!matchings.containsKey(root)) {
                    matchings.put(root, new ArrayList<>());
                }
                matchings.get(root).add(matchingEntry);
            }
        }
        return matchings;
    }


    /**
     * [$snomed.1104341000000101]"
     */
    public Coding getStringFromCriteria(final String criteria) {
        if (criteria == null) {
            return null;
        }
        final String[] criterias = criteria.replace("[",
                                                    "") // todo: crazy stuff in the FHIR Connect spec...... criteria is a string array, $loinc, ...
                .replace("]", "").split(",");
        // todo: should be an OR inbetween these separate criterias.. right now it just takes the first
        final String codingCode = criterias[0]
                .replace("$loinc.", "")
                .replace("$snomed.", "");
        final String system;
        if (criteria.contains("$loinc")) {
            system = "http://loinc.org";
        } else if (criteria.contains("$snomed")) {
            system = "http://snomed.info/sct";
        } else {
            system = criteria.replace("[",
                                      "") // // todo: crazy stuff in the FHIR Connect spec...... criteria is a string array, $loinc, ...
                    .replace("]", "").split("\\.")[0];
        }
        return new Coding(system, codingCode, null);
    }

    public List<String> getCodesFromCriteria(final String criteria) {
        if (criteria == null) {
            return Collections.emptyList();
        }

        // Handle simple code without brackets
        if (!criteria.startsWith("[") && !criteria.endsWith("]")) {
            return Collections.singletonList(criteria.trim());
        }

        // Remove brackets and split by comma
        String[] criterias = criteria.replace("[", "")
                .replace("]", "")
                .split(",");

        return Collections.unmodifiableList(Arrays.stream(criterias)
                .map(code -> code.trim())
                .map(code -> {
                    // Handle StructureDefinition URLs
                    if (code.startsWith("http://") || code.startsWith("https://")) {
                        return code;
                    }

                    // Handle SNOMED and LOINC codes - maintain backward compatibility
                    if (code.startsWith("$snomed.")) {
                        return code.substring(8); // Just return the code part
                    }
                    if (code.startsWith("$loinc.")) {
                        return code.substring(7); // Just return the code part
                    }

                    // Handle simple codes
                    return code;
                })
                .toList());
    }

    /**
     * removes TYPE from openEhr path,
     * i.e. medication_order/medication_order[TYPE:INSTRUCTION]/order[n][TYPE:ACTIVITY]/medication_item[TYPE:ELEMENT]
     * should become
     * medication_order/medication_order/order[n]/medication_item
     */
    public String removeTypes(final String openEhrPath) {
        if (StringUtils.isEmpty(openEhrPath)) {
            return openEhrPath;
        }
        return openEhrPath.replaceAll(TYPE_PATTERN, "");
    }

    public String getLastType(final String openEhrPath) {
        final Pattern compiledPattern = Pattern.compile(TYPE_PATTERN);
        final Matcher matcher = compiledPattern.matcher(openEhrPath);

        final List<String> matches = new ArrayList<>();

        while (matcher.find()) {
            matches.add(matcher.group());
        }
        if (matches.isEmpty()) {
            return null;
        }
        return matches.get(matches.size() - 1).replace("[TYPE:", "").replace("]", "");
    }

    public Set<String> getPossibleRmTypeValue(final String val) {
        if (val == null) {
            return null;
        }
        return switch (val) {
            case "QUANTITY" -> new HashSet<>(
                    Arrays.asList(FhirConnectConst.DV_QUANTITY, FhirConnectConst.DV_COUNT, FhirConnectConst.DV_ORDINAL,
                                  FhirConnectConst.DV_PROPORTION));
            case "DATETIME" -> Collections.singleton(FhirConnectConst.DV_DATE_TIME);
            case "TIME" -> Collections.singleton(FhirConnectConst.DV_TIME);
            case "DATE" -> Collections.singleton(FhirConnectConst.DV_DATE);
            case "CODEABLECONCEPT" -> Collections.singleton(FhirConnectConst.DV_CODED_TEXT);
            case "CODING" -> Collections.singleton(FhirConnectConst.CODE_PHRASE);
            case "STRING" -> Collections.singleton(FhirConnectConst.DV_TEXT);
            case "BOOL" -> Collections.singleton(FhirConnectConst.DV_BOOL);
            case "IDENTIFIER" -> Collections.singleton(FhirConnectConst.DV_IDENTIFIER);
            case "MEDIA" -> Collections.singleton(FhirConnectConst.DV_MULTIMEDIA);
            case "PROPORTION" -> Collections.singleton(FhirConnectConst.DV_PROPORTION);
            default -> Collections.singleton(val);
        };
    }

    /**
     * Replaces parts of the original string with parts from the replacement string, based on specific patterns.
     * <p>
     * The original and replacement strings are split by "/" and processed part by part. The following rules are
     * applied:
     * - If a replacement part contains a numeric suffix in the format "part:number", the corresponding part from the
     * replacement is used.
     * - If a replacement part contains a suffix in the format "part[n]", the original structure of the part is
     * retained.
     * - If no special pattern is found, the replacement part is used, unless it's significantly different from the
     * original part, in which case the original part is kept.
     * - The method returns the new string with appropriate replacements and maintains the "/" as the separator.
     *
     * @param original the original string to be processed (parts separated by "/")
     * @param replacement the replacement string to be used (parts separated by "/")
     * @return a new string where parts from the original are replaced with parts from the replacement
     */
    public String replacePattern(String original, String replacement) {
        // Split the original and replacement strings into parts based on "/"
        String[] originalParts = original.split("/");
        String[] replacementParts = replacement.split("/");

        StringBuilder result = new StringBuilder();

        // Iterate through the parts and replace the parts from the original with the replacement, when needed
        for (int i = 0; i < originalParts.length; i++) {
            if (i < replacementParts.length && replacementParts[i].matches(".*:\\d+")) {
                // If replacement part has a numeric suffix, use it
                result.append(replacementParts[i]);
            } else if (i < replacementParts.length && replacementParts[i].matches(".*\\[\\d*]")) {
                // If the replacement part has a [n] suffix, use the original structure
                result.append(originalParts[i]);
            } else if (i < replacementParts.length) {
                // Use the original part
                final String orig = originalParts[i].contains(RECURRING_SYNTAX) ? replaceLastIndexOf(originalParts[i],
                                                                                                     RECURRING_SYNTAX,
                                                                                                     "")
                        : originalParts[i];
                final String repl = replacementParts[i].contains(":") ? replacementParts[i].replace(":", "")
                        .replace(String.valueOf(getLastIndex(replacementParts[i])), "") : replacementParts[i];
                if (!orig.startsWith(repl)) { // means it's a completely different one, need to take original
                    result.append(originalParts[i]);
                } else {
                    result.append(replacementParts[i]);
                }
            } else {
                // If no matching replacement, use the original part
                result.append(originalParts[i]);
            }

            // Add the separator
            if (i < originalParts.length - 1) {
                result.append("/");
            }
        }
        return result.toString();
    }

    /**
     * Return true if child path starts with a parent path, i.e. if a parent openEHR flat path is direct
     * variation of a parent openEHR path
     */
    public boolean childStartsWithParent(final String child, final String parent) {
        final List<String> childSplit = Arrays.asList(child.split("/"));
        final List<String> parentSplit = Arrays.asList(parent.split("/"));

        for (int i = 0; i < childSplit.size(); i++) {
            String childPath = childSplit.get(i);
            if (i >= parentSplit.size()) {
                return true;
            }
            if (childPath.contains("|")) {
                childPath = childPath.split("\\|")[0];
            }
            if (!childPath.equals(parentSplit.get(i))) {
                return false;
            }
        }
        return true;
    }

    public boolean childHasParentRecurring(final String child, final String parent) {
        final List<String> childSplit = Arrays.asList(child.split("/"));
        final List<String> parentSplit = Arrays.asList(parent.split("/"));

        for (int i = 0; i < childSplit.size(); i++) {
            String childPath = childSplit.get(i);
            if (i >= parentSplit.size()) {
                return true;
            }
            if (childPath.contains("|")) {
                childPath = childPath.split("\\|")[0];
            }
            final String parentPath = parentSplit.get(i);
            if (childPath.endsWith("[n]") && parentPath.contains(":")) {
                final String baseChildPath = childPath.replace("[n]", "");
                final String baseParentPath = parentPath.substring(0, parentPath.indexOf(":"));
                if (baseChildPath.equals(baseParentPath)) {
                    return true;
                }
            }
            if (!childPath.equals(parentPath)) {
                return false;
            }
        }
        return true;
    }

}
