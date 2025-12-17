package com.paperradar.ingest.openalex;

import com.fasterxml.jackson.databind.JsonNode;
import com.paperradar.ingest.model.OpenAlexWork;
import com.paperradar.ingest.model.OpenAlexWork.AuthorRef;
import com.paperradar.ingest.model.OpenAlexWork.InstitutionRef;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

final class OpenAlexJsonMapper {

    private OpenAlexJsonMapper() {}

    static List<OpenAlexWork> parseWorks(JsonNode root) {
        JsonNode results = root.path("results");
        if (!results.isArray()) {
            return List.of();
        }
        List<OpenAlexWork> out = new ArrayList<>();
        for (JsonNode w : results) {
            OpenAlexWork mapped = parseWork(w);
            if (mapped != null) {
                out.add(mapped);
            }
        }
        return out;
    }

    private static OpenAlexWork parseWork(JsonNode w) {
        String id = text(w, "id");
        if (id.isBlank()) {
            return null;
        }
        String doi = normalizeDoi(firstNonBlank(
                text(w, "doi"),
                w.path("ids").path("doi").asText("")
        ));
        String landing = firstNonBlank(
                w.path("primary_location").path("landing_page_url").asText(""),
                w.path("best_oa_location").path("landing_page_url").asText("")
        );
        String pdf = firstNonBlank(
                w.path("primary_location").path("pdf_url").asText(""),
                w.path("best_oa_location").path("pdf_url").asText("")
        );
        String oaUrl = firstNonBlank(
                w.path("open_access").path("oa_url").asText(""),
                w.path("best_oa_location").path("url").asText("")
        );

        String title = text(w, "title");
        String abstractText = AbstractInvertedIndexUtil.toText(w.path("abstract_inverted_index"));
        LocalDate publicationDate = parseDate(text(w, "publication_date"));
        int citedBy = w.path("cited_by_count").asInt(0);

        List<String> keywords = new ArrayList<>();
        JsonNode kws = w.path("keywords");
        if (kws.isArray()) {
            for (JsonNode k : kws) {
                String kw = text(k, "keyword");
                if (!kw.isBlank()) keywords.add(kw);
            }
        } else {
            JsonNode concepts = w.path("concepts");
            if (concepts.isArray()) {
                for (JsonNode c : concepts) {
                    String name = text(c, "display_name");
                    if (!name.isBlank()) keywords.add(name);
                }
            }
        }
        keywords = keywords.stream().filter(s -> !s.isBlank()).distinct().toList();

        List<AuthorRef> authors = new ArrayList<>();
        Map<String, InstitutionRef> institutions = new HashMap<>();
        JsonNode authorships = w.path("authorships");
        if (authorships.isArray()) {
            for (JsonNode a : authorships) {
                JsonNode authorNode = a.path("author");
                String authorId = text(authorNode, "id");
                String authorName = text(authorNode, "display_name");
                if (!authorName.isBlank()) {
                    authors.add(new AuthorRef(authorId, authorName));
                }

                JsonNode insts = a.path("institutions");
                if (insts.isArray()) {
                    for (JsonNode inst : insts) {
                        String instId = text(inst, "id");
                        String instName = text(inst, "display_name");
                        if (!instId.isBlank() || !instName.isBlank()) {
                            String key = !instId.isBlank() ? instId : instName;
                            institutions.putIfAbsent(key, new InstitutionRef(instId, instName));
                        }
                    }
                }
            }
        }
        authors = authors.stream()
                .filter(a -> a.name() != null && !a.name().isBlank())
                .distinct()
                .toList();

        List<InstitutionRef> instList = institutions.values().stream()
                .filter(i -> (i.id() != null && !i.id().isBlank()) || (i.name() != null && !i.name().isBlank()))
                .sorted(Comparator.comparing(i -> Objects.toString(i.name(), "")))
                .collect(Collectors.toList());

        return new OpenAlexWork(
                id,
                doi,
                blankToNull(landing),
                blankToNull(pdf),
                blankToNull(oaUrl),
                title,
                abstractText,
                publicationDate,
                citedBy,
                keywords,
                authors,
                instList
        );
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return "";
        return v.asText("");
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static String normalizeDoi(String doiValue) {
        if (doiValue == null) {
            return null;
        }
        String trimmed = doiValue.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        String lower = trimmed.toLowerCase();
        if (lower.startsWith("https://doi.org/")) {
            return trimmed.substring("https://doi.org/".length());
        }
        if (lower.startsWith("http://doi.org/")) {
            return trimmed.substring("http://doi.org/".length());
        }
        return trimmed;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return "";
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isBlank() ? null : t;
    }
}
