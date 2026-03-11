package com.iflytek.skillhub.search;

import java.util.List;

public record SearchResult(
        List<Long> skillIds,
        long total,
        int page,
        int size
) {}
