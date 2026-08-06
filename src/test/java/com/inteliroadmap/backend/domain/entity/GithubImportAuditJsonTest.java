package com.inteliroadmap.backend.domain.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.inteliroadmap.backend.domain.dto.ai.SkillMatch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The JSONB columns on {@link GithubImportAudit} hold Java records, and Hibernate
 * serialises them through Jackson.
 *
 * <p>Writing works — a live import produced a readable row. Reading back is the
 * direction that fails quietly: records have no no-arg constructor and no setters,
 * so a Jackson without record support deserialises them into an exception at the
 * moment a student opens the audit screen, long after the code compiled and the
 * write looked fine.
 *
 * <p>This does not exercise Hibernate's plumbing. It pins the part that would
 * actually break, using the exact JSON shape the database now contains.
 */
class GithubImportAuditJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /** Copied from the row a real import wrote, so the shape is not invented here. */
    private static final String SOURCES_JSON = """
            [{"path":"README.md","chars":2425,"found":true},
             {"path":"package.json","chars":1500,"found":true}]""";

    private static final String MATCHES_JSON = """
            [{"skill":"JavaScript","confidence":0.9},
             {"skill":"Web Development","confidence":0.85}]""";

    @Test
    @DisplayName("the stored sources shape reads back into records")
    void sourcesDeserialize() throws Exception {
        List<GithubImportAudit.SourceRead> sources =
                mapper.readValue(SOURCES_JSON, new TypeReference<>() {});

        assertThat(sources).hasSize(2);
        assertThat(sources.get(0).path()).isEqualTo("README.md");
        assertThat(sources.get(0).chars()).isEqualTo(2425);
        assertThat(sources.get(0).found()).isTrue();
    }

    @Test
    @DisplayName("the stored matches shape reads back into records")
    void matchesDeserialize() throws Exception {
        List<SkillMatch> matches = mapper.readValue(MATCHES_JSON, new TypeReference<>() {});

        assertThat(matches).hasSize(2);
        assertThat(matches.get(0).skill()).isEqualTo("JavaScript");
        assertThat(matches.get(0).confidence()).isEqualTo(0.9);
    }

    @Test
    @DisplayName("a source that came back empty survives the round trip as empty, not as absent")
    void emptySourceRoundTrips() throws Exception {
        // The whole point of recording a file that was not found: `found=false` and
        // `chars=0` must still be there after a round trip, or the audit quietly loses
        // the single most diagnostic fact it holds.
        GithubImportAudit.SourceRead missing = new GithubImportAudit.SourceRead("pom.xml", 0, false);

        GithubImportAudit.SourceRead back = mapper.readValue(
                mapper.writeValueAsString(missing), GithubImportAudit.SourceRead.class);

        assertThat(back).isEqualTo(missing);
        assertThat(back.found()).isFalse();
        assertThat(back.chars()).isZero();
    }
}
