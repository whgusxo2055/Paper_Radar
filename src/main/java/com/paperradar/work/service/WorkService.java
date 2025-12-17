package com.paperradar.work.service;

import com.paperradar.work.model.WorkDetail;
import java.util.Optional;

public interface WorkService {
    Optional<WorkDetail> getById(String id);
}

