package com.paperradar.web.view;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("viewTimeFormat")
public class ViewTimeFormat {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ZoneId zoneId;

    public ViewTimeFormat(@Value("${APP_TIMEZONE:Asia/Seoul}") String timezone) {
        this.zoneId = ZoneId.of(timezone);
    }

    public String format(Instant instant) {
        if (instant == null) {
            return "";
        }
        return FORMATTER.withZone(zoneId).format(instant);
    }
}

