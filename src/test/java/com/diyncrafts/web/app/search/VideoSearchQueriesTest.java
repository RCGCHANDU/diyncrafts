package com.diyncrafts.web.app.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;

import com.diyncrafts.web.app.dto.VideoSearchCriteria;
import com.jayway.jsonpath.JsonPath;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;

class VideoSearchQueriesTest {

    private static final VideoSearchCriteria NONE = VideoSearchCriteria.empty();

    @Test
    void titleSearchUsesTheSuppliedTitle() {
        String json = json(VideoSearchQueries.toQuery(NONE.withTitle(" Bird house ")));
        assertThat(JsonPath.<String>read(json, "$.bool.must[0].match.title.query")).isEqualTo("Bird house");
        assertThat(JsonPath.<String>read(json, "$.bool.must[0].match.title.operator")).isEqualTo("and");
    }

    @Test
    void textSearchIsMultiFieldAndFuzzy() {
        String json = json(VideoSearchQueries.toQuery(NONE.withText("birdhouse")));
        assertThat(JsonPath.<String>read(json, "$.bool.must[0].multi_match.query")).isEqualTo("birdhouse");
        assertThat(JsonPath.<List<String>>read(json, "$.bool.must[0].multi_match.fields"))
                .containsExactly("title^3", "description^2", "materialsUsed");
        assertThat(JsonPath.<String>read(json, "$.bool.must[0].multi_match.fuzziness")).isEqualTo("AUTO");
    }

    @Test
    void allFiltersAreHonoured() {
        String json = json(VideoSearchQueries.toQuery(NONE.withText("shelf").withCategory("Woodworking")
                .withDifficulty("Beginner").withMaterial("pine").withUser("alice")));
        assertThat(JsonPath.<List<Object>>read(json, "$.bool.must")).hasSize(2);
        assertThat(JsonPath.<String>read(json, "$.bool.must[1].match.materialsUsed.query")).isEqualTo("pine");
        List<Map<String, Object>> filters = JsonPath.read(json, "$.bool.filter");
        assertThat(filters).hasSize(3);
        assertThat(JsonPath.<String>read(json, "$.bool.filter[0].term.categoryName.value")).isEqualTo("Woodworking");
        assertThat(JsonPath.<String>read(json, "$.bool.filter[1].term.difficultyLevel.value")).isEqualTo("Beginner");
        assertThat(JsonPath.<String>read(json, "$.bool.filter[2].term.userName.value")).isEqualTo("alice");
    }

    @Test
    void generatedDslIsValidBoolSyntax() {
        String json = json(VideoSearchQueries.toQuery(NONE.withText("x").withCategory("y")));
        // Regression: the old advanced query contained an invalid "match": 1 inside "bool".
        Map<String, Object> bool = JsonPath.read(json, "$.bool");
        assertThat(bool.keySet()).containsOnly("must", "filter");
    }

    @Test
    void emptyOrBlankCriteriaMatchAll() {
        assertThat(json(VideoSearchQueries.toQuery(NONE))).isEqualTo("{\"match_all\":{}}");
        assertThat(json(VideoSearchQueries.toQuery(NONE.withText("  ").withCategory(""))))
                .isEqualTo("{\"match_all\":{}}");
    }

    @Test
    void pagingAndSortingAreExplicit() {
        NativeQuery query = VideoSearchQueries.build(NONE.withText("x"), PageRequest.of(2, 15));
        assertThat(query.getPageable().getPageNumber()).isEqualTo(2);
        assertThat(query.getPageable().getPageSize()).isEqualTo(15);
        assertThat(query.getSortOptions()).hasSize(2);
        assertThat(query.getSortOptions().get(0).toString()).contains("_score").contains("desc");
        assertThat(query.getSortOptions().get(1).toString()).contains("\"id\"").contains("desc");
    }

    private static String json(Query query) {
        String text = query.toString();
        return text.substring(text.indexOf('{'));
    }
}
