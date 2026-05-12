package com.labless.processor;

import com.labless.model.JobProgress;

@FunctionalInterface
public interface ProgressListener {
    void onProgress(JobProgress progress);
}
