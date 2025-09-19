package com.medblocks.openfhir.db;

import com.medblocks.openfhir.OpenFhirMappingContext;
import com.medblocks.openfhir.db.entity.OptEntity;
import com.medblocks.openfhir.db.repository.OptRepository;
import com.medblocks.openfhir.util.OpenEhrCachedUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.xmlbeans.XmlException;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;
import org.openehr.schemas.v1.TemplateDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Component
@Slf4j
@Transactional
public class OptService {
    private final OptRepository optRepository;

    private final OpenEhrCachedUtils openEhrApplicationScopedUtils;

    @Autowired
    public OptService(OptRepository optRepository, OpenEhrCachedUtils openEhrApplicationScopedUtils) {
        this.optRepository = optRepository;
        this.openEhrApplicationScopedUtils = openEhrApplicationScopedUtils;
    }

    /**
     * Creates an operational template in the database.
     *
     * @param opt string payload of the operational template
     * @return created OptEntity without the content (just with the ID assigned by the database)
     * @throws IllegalArgumentException if validation of a template fails (if it can not be parsed)
     */
    public OptEntity upsert(final String opt, final String id, final String reqId) {
        log.debug("Receive CREATE/UPDATE OPT, id {}, reqId: {}", id, reqId);
        // parse opt to validate it's ok
        try {
            final OPERATIONALTEMPLATE operationaltemplate = parseOptFromString(opt);
            final String normalizedTemplateId = OpenFhirMappingContext.normalizeTemplateId(operationaltemplate.getTemplateId().getValue());
            openEhrApplicationScopedUtils.parseWebTemplate(operationaltemplate);
            final OptEntity existingByTemplate = optRepository.findByTemplateId(normalizedTemplateId);

            // Behavior:
            // - POST (id is empty): create new, but fail if templateId already exists
            // - PUT  (id present): update existing; allow overwrite if the templateId belongs to the same record
            //                       fail if the payload's templateId exists on a DIFFERENT record
            String entityId;
            if (StringUtils.isEmpty(id)) {
                if (existingByTemplate != null) { // Create flow
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Template with templateId " + operationaltemplate.getTemplateId() +
                                    " (normalized to: " + normalizedTemplateId + ") already exists.");
                }
                entityId = null; // let DB generate
            } else { // Update flow
                if (existingByTemplate != null && !existingByTemplate.getId().equals(id)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Template with templateId " + operationaltemplate.getTemplateId() +
                                    " (normalized to: " + normalizedTemplateId + ") already exists under a different id.");
                }
                // If existingByTemplate is this same record, use its id (ensures update). Otherwise update record by provided id.
                entityId = existingByTemplate != null ? existingByTemplate.getId() : id;
            }

            // get name from it
            final OptEntity entity = new OptEntity(entityId, opt, normalizedTemplateId,
                                                   operationaltemplate.getTemplateId().getValue(),
                                                   operationaltemplate.getTemplateId().getValue());
            final OptEntity insert = optRepository.save(entity);
            final OptEntity copied = insert.copy();
            copied.setContent("redacted");
            return copied;
        } catch (final Exception e) {
            log.error("Couldn't create/update a template, reqId: {}", reqId, e);
            if (e instanceof ResponseStatusException rse) {
                throw rse;
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Couldn't create/update a template. " + e.getMessage());
        }
    }

    public List<OptEntity> all(final String reqId) {
        return optRepository.findAll();
    }


    public String getContentByTemplateId(final String templateId, final String reqId) {
        final OptEntity byTemplateId = optRepository.findByTemplateId(templateId);
        return byTemplateId == null ? null : byTemplateId.getContent();
    }

    public String getContent(final String id, final String reqId) {
        final OptEntity optEntity = optRepository.byId(id);
        return optEntity == null ? null : optEntity.getContent();
    }

    /**
     * Ignore any white character at the beginning of the payload and parse content to OPERATIONALTEMPLATE
     *
     * @param content XML that represents a serialized operational template
     * @return parsed OPERATIONALTEMPLATE from the given payload
     * @throws XmlException if content is invalid XML after removing the white characters
     */
    private OPERATIONALTEMPLATE parseOptFromString(final String content) throws XmlException {
        return TemplateDocument.Factory.parse(content.trim().replaceFirst("^(\\W+)<", "<")).getTemplate();
    }
}
