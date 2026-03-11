package com.iflytek.skillhub.search;

public interface SearchRebuildService {
    void rebuildAll();
    void rebuildByNamespace(Long namespaceId);
    void rebuildBySkill(Long skillId);
}
