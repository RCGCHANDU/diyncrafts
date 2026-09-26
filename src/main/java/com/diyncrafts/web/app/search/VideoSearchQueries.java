package com.diyncrafts.web.app.search;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;

import com.diyncrafts.web.app.dto.VideoSearchCriteria;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;

/**
 * Builds Elasticsearch queries for {@link VideoSearchCriteria}. Kept free of I/O so it can be unit tested.
 */
public final class VideoSearchQueries {

    static final String[] TEXT_FIELDS = {"title^3", "description^2", "materialsUsed"};

    private VideoSearchQueries() {
    }

    public static NativeQuery build(VideoSearchCriteria criteria, Pageable pageable) {
        return NativeQuery.builder()
                .withQuery(toQuery(criteria))
                .withPageable(pageable)
                // Relevance first, then newest (highest id) for ties and for filter-only searches.
                .withSort(sort -> sort.score(score -> score.order(SortOrder.Desc)))
                .withSort(sort -> sort.field(field -> field.field("id").order(SortOrder.Desc)))
                .build();
    }

    public static Query toQuery(VideoSearchCriteria criteria) {
        List<Query> must = new ArrayList<>();
        List<Query> filters = new ArrayList<>();
        if (hasText(criteria.text())) {
            must.add(Query.of(q -> q.multiMatch(m -> m
                    .query(criteria.text().trim())
                    .fields(List.of(TEXT_FIELDS))
                    .fuzziness("AUTO"))));
        }
        if (hasText(criteria.title())) {
            must.add(Query.of(q -> q.match(m -> m
                    .field("title")
                    .query(criteria.title().trim())
                    .operator(Operator.And)
                    .fuzziness("AUTO"))));
        }
        if (hasText(criteria.material())) {
            must.add(Query.of(q -> q.match(m -> m
                    .field("materialsUsed")
                    .query(criteria.material().trim())
                    .operator(Operator.And))));
        }
        addTerm(filters, "categoryName", criteria.category());
        addTerm(filters, "difficultyLevel", criteria.difficulty());
        addTerm(filters, "userName", criteria.user());
        if (must.isEmpty() && filters.isEmpty()) {
            return Query.of(q -> q.matchAll(all -> all));
        }
        return Query.of(q -> q.bool(b -> b.must(must).filter(filters)));
    }

    private static void addTerm(List<Query> filters, String field, String value) {
        if (hasText(value)) {
            filters.add(Query.of(q -> q.term(t -> t.field(field).value(value.trim()))));
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
