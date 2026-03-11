package com.iflytek.skillhub.search.postgres;

import com.iflytek.skillhub.search.SearchQuery;
import com.iflytek.skillhub.search.SearchQueryService;
import com.iflytek.skillhub.search.SearchResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PostgresFullTextQueryService implements SearchQueryService {

    private final EntityManager entityManager;

    public PostgresFullTextQueryService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public SearchResult search(SearchQuery query) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT skill_id FROM skill_search_document WHERE 1=1 ");

        // Visibility filtering
        sql.append("AND (visibility = 'PUBLIC' ");
        if (query.visibilityScope().userId() != null) {
            sql.append("OR (visibility = 'NAMESPACE' AND namespace_id IN :memberNamespaceIds) ");
            sql.append("OR (visibility = 'PRIVATE' AND namespace_id IN :adminNamespaceIds) ");
        }
        sql.append(") ");

        // Status filtering
        sql.append("AND status = 'ACTIVE' ");

        // Namespace filtering
        if (query.namespaceId() != null) {
            sql.append("AND namespace_id = :namespaceId ");
        }

        // Full-text search
        if (query.keyword() != null && !query.keyword().isBlank()) {
            sql.append("AND to_tsvector('english', search_text) @@ plainto_tsquery('english', :keyword) ");
        }

        // Sorting
        if ("downloads".equals(query.sortBy())) {
            sql.append("ORDER BY (SELECT download_count FROM skill WHERE id = skill_id) DESC ");
        } else if ("newest".equals(query.sortBy())) {
            sql.append("ORDER BY (SELECT created_at FROM skill WHERE id = skill_id) DESC ");
        } else if ("relevance".equals(query.sortBy()) && query.keyword() != null && !query.keyword().isBlank()) {
            sql.append("ORDER BY ts_rank(to_tsvector('english', search_text), plainto_tsquery('english', :keyword)) DESC ");
        } else {
            sql.append("ORDER BY updated_at DESC ");
        }

        // Pagination
        sql.append("LIMIT :limit OFFSET :offset");

        Query nativeQuery = entityManager.createNativeQuery(sql.toString());

        if (query.visibilityScope().userId() != null) {
            nativeQuery.setParameter("memberNamespaceIds", query.visibilityScope().memberNamespaceIds());
            nativeQuery.setParameter("adminNamespaceIds", query.visibilityScope().adminNamespaceIds());
        }

        if (query.namespaceId() != null) {
            nativeQuery.setParameter("namespaceId", query.namespaceId());
        }

        if (query.keyword() != null && !query.keyword().isBlank()) {
            nativeQuery.setParameter("keyword", query.keyword());
        }

        nativeQuery.setParameter("limit", query.size());
        nativeQuery.setParameter("offset", query.page() * query.size());

        @SuppressWarnings("unchecked")
        List<Long> skillIds = (List<Long>) nativeQuery.getResultList().stream()
                .map(obj -> ((Number) obj).longValue())
                .toList();

        // Count total
        String countSql = sql.toString().replaceFirst("SELECT skill_id", "SELECT COUNT(*)");
        countSql = countSql.substring(0, countSql.indexOf("ORDER BY"));
        countSql = countSql.substring(0, countSql.indexOf("LIMIT"));

        Query countQuery = entityManager.createNativeQuery(countSql);

        if (query.visibilityScope().userId() != null) {
            countQuery.setParameter("memberNamespaceIds", query.visibilityScope().memberNamespaceIds());
            countQuery.setParameter("adminNamespaceIds", query.visibilityScope().adminNamespaceIds());
        }

        if (query.namespaceId() != null) {
            countQuery.setParameter("namespaceId", query.namespaceId());
        }

        if (query.keyword() != null && !query.keyword().isBlank()) {
            countQuery.setParameter("keyword", query.keyword());
        }

        long total = ((Number) countQuery.getSingleResult()).longValue();

        return new SearchResult(skillIds, total, query.page(), query.size());
    }
}
