package com.paperradar.work.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.paperradar.work.link.WorkLink;
import com.paperradar.work.link.WorkLinkPolicy;
import com.paperradar.work.link.WorkLinkType;
import com.paperradar.work.model.WorkDetail;
import com.paperradar.work.model.WorkDetail.InstitutionRef;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ElasticsearchWorkService implements WorkService {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchWorkService.class);

    private final ElasticsearchClient client;

    @Override
    public Optional<WorkDetail> getById(String id) {
        String docId = id == null ? "" : id.trim();
        if (docId.isBlank()) {
            return Optional.empty();
        }

        try {
            var res = client.get(g -> g.index("works").id(docId), Map.class);
            if (!res.found() || res.source() == null) {
                return Optional.empty();
            }
            Map<?, ?> src = (Map<?, ?>) res.source();

            String title = asString(src.get("title"));
            String abstractText = asString(src.get("abstract"));
            LocalDate publicationDate = parseDate(asString(src.get("publication_date")));
            List<InstitutionRef> institutions = extractInstitutions(src.get("institutions"));
            String institutionsText = String.join(", ", institutions.stream().map(InstitutionRef::name).filter(this::isNonBlank).toList());
            List<String> authors = extractAuthors(src.get("authors"));
            List<String> keywords = extractStringList(src.get("keywords"));

            String doi = asString(src.get("doi"));
            String landing = asString(src.get("landing_page_url"));
            String pdf = asString(src.get("pdf_url"));
            String oa = asString(src.get("open_access_oa_url"));
            List<WorkLink> links = buildLinks(doi, landing, pdf, oa);

            return Optional.of(new WorkDetail(docId, title, abstractText, publicationDate, institutionsText, institutions, authors, keywords, links));
        } catch (Exception e) {
            log.warn("Failed to load work {}", docId, e);
            return Optional.empty();
        }
    }

    private List<WorkLink> buildLinks(String doi, String landing, String pdf, String oa) {
        List<WorkLink> out = new ArrayList<>();
        WorkLink best = WorkLinkPolicy.pickBestLink(doi, landing, pdf, oa);
        if (best != null) {
            out.add(best);
        }
        if (best == null || best.type() != WorkLinkType.DOI) {
            String doiUrl = WorkLinkPolicy.pickBestLink(doi, null, null, null) == null
                    ? null
                    : WorkLinkPolicy.pickBestLink(doi, null, null, null).url();
            if (isNonBlank(doiUrl)) {
                out.add(new WorkLink(WorkLinkType.DOI, doiUrl));
            }
        }
        if (best == null || best.type() != WorkLinkType.Landing) {
            if (isNonBlank(landing)) {
                out.add(new WorkLink(WorkLinkType.Landing, landing.trim()));
            }
        }
        if (best == null || best.type() != WorkLinkType.PDF) {
            if (isNonBlank(pdf)) {
                out.add(new WorkLink(WorkLinkType.PDF, pdf.trim()));
            } else if (isNonBlank(oa)) {
                out.add(new WorkLink(WorkLinkType.PDF, oa.trim()));
            }
        }
        return dedupe(out);
    }

    private List<WorkLink> dedupe(List<WorkLink> links) {
        Set<String> seen = new LinkedHashSet<>();
        List<WorkLink> out = new ArrayList<>();
        for (WorkLink l : links) {
            if (l == null || !isNonBlank(l.url())) continue;
            String key = l.type().name() + "|" + l.url();
            if (seen.add(key)) {
                out.add(l);
            }
        }
        return out;
    }

    private boolean isNonBlank(String s) {
        return s != null && !s.isBlank();
    }

    private List<InstitutionRef> extractInstitutions(Object institutionsValue) {
        if (!(institutionsValue instanceof List<?> list)) {
            return List.of();
        }
        Map<String, InstitutionRef> byKey = new java.util.LinkedHashMap<>();
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> m)) continue;
            String id = asString(m.get("id"));
            String name = asString(m.get("name"));
            if (!isNonBlank(id) && !isNonBlank(name)) {
                continue;
            }
            String key = isNonBlank(id) ? id : name;
            byKey.putIfAbsent(key, new InstitutionRef(id == null ? "" : id, name == null ? "" : name));
        }
        return List.copyOf(byKey.values());
    }

    private List<String> extractAuthors(Object authorsValue) {
        if (!(authorsValue instanceof List<?> list)) {
            return List.of();
        }
        Set<String> names = new LinkedHashSet<>();
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> m)) continue;
            String name = asString(m.get("name"));
            if (isNonBlank(name)) names.add(name);
        }
        return List.copyOf(names);
    }

    private List<String> extractStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            String s = asString(o);
            if (isNonBlank(s)) out.add(s);
        }
        return out.stream().distinct().toList();
    }

    private String asString(Object value) {
        if (value == null) return null;
        return String.valueOf(value);
    }

    private LocalDate parseDate(String value) {
        if (!isNonBlank(value)) return null;
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            return null;
        }
    }
}
