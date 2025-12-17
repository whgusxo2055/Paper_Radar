package com.paperradar.work.link;

public final class WorkLinkPolicy {

    private WorkLinkPolicy() {}

    public static WorkLink pickBestLink(String doi, String landingPageUrl, String pdfUrl, String openAccessUrl) {
        String doiUrl = doiUrl(doi);
        if (doiUrl != null) {
            return new WorkLink(WorkLinkType.DOI, doiUrl);
        }
        if (isNonBlank(landingPageUrl)) {
            return new WorkLink(WorkLinkType.Landing, landingPageUrl.trim());
        }
        if (isNonBlank(pdfUrl)) {
            return new WorkLink(WorkLinkType.PDF, pdfUrl.trim());
        }
        if (isNonBlank(openAccessUrl)) {
            return new WorkLink(WorkLinkType.PDF, openAccessUrl.trim());
        }
        return null;
    }

    private static String doiUrl(String doi) {
        if (!isNonBlank(doi)) {
            return null;
        }
        String raw = doi.trim();
        String lower = raw.toLowerCase();
        if (lower.startsWith("https://doi.org/") || lower.startsWith("http://doi.org/")) {
            return raw;
        }
        return "https://doi.org/" + raw;
    }

    private static boolean isNonBlank(String s) {
        return s != null && !s.isBlank();
    }
}

