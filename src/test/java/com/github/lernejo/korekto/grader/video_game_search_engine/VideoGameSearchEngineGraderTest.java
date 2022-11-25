package com.github.lernejo.korekto.grader.video_game_search_engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.lernejo.korekto.toolkit.Grader;
import com.github.lernejo.korekto.toolkit.GradingConfiguration;
import com.github.lernejo.korekto.toolkit.GradingContext;
import com.github.lernejo.korekto.toolkit.GradingJob;
import com.github.lernejo.korekto.toolkit.i18n.I18nTemplateResolver;
import com.github.lernejo.korekto.toolkit.misc.OS;
import com.github.lernejo.korekto.toolkit.misc.Processes;
import com.github.lernejo.korekto.toolkit.thirdparty.git.GitNature;
import org.eclipse.jgit.api.ResetCommand;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class VideoGameSearchEngineGraderTest {

    private static final Path workspace = Paths.get("target/test_repositories").toAbsolutePath();

    private static final ObjectMapper om = new ObjectMapper();

    @BeforeAll
    static void setUpAll() {
        Processes.launch(OS.Companion.getCURRENT_OS().deleteDirectoryCommand(workspace.resolve("lernejo")), null);
    }

    @BeforeEach
    void setUp() {
        AtomicInteger counter = new AtomicInteger();
        LaunchingContext.RANDOM = i -> counter.incrementAndGet() % i; // deterministic behavior
    }

    @Test
    void github_token_is_set() {
        assertThat(System.getProperty("github_token"))
            .as("github_token system property")
            .isNotBlank();
    }

    @ParameterizedTest(name = "(branch={1}) {0}")
    @BrancheFileSource
    @EnabledIfSystemProperty(named = "github_token", matches = ".+")
    void check_project_stages(String title, String branchName, String expectedPayload) {
        Grader grader = Grader.Companion.load();
        String repoUrl = grader.slugToRepoUrl("lernejo");
        GradingConfiguration configuration = new GradingConfiguration(repoUrl, "", "", workspace);

        GradingContext context = execute(branchName, grader, configuration);

        assertThat(context)
            .as("Grading context")
            .isNotNull();

        String result = createIssueContent(context);

        assertThat(result)
            .isEqualToIgnoringWhitespace(expectedPayload);
    }

    private GradingContext execute(String branchName, Grader grader, GradingConfiguration configuration) {
        AtomicReference<GradingContext> contextHolder = new AtomicReference<>();
        new GradingJob()
            .addCloneStep()
            .addStep("switch-branch",
                (context) -> context
                    .getExercise()
                    .lookupNature(GitNature.class)
                    .get()
                    .inContext(git -> {
                        git.getGit().reset().setMode(ResetCommand.ResetType.HARD).call();
                        git.checkout(branchName);
                    }))
            .addStep("grading", grader)
            .addStep("report", context -> contextHolder.set(context))
            .run(configuration, grader::gradingContext);
        return contextHolder.get();
    }

    private String createIssueContent(GradingContext context) {
        var templateContext = new HashMap<String, Object>();
        templateContext.put("grade", context.getGradeDetails().grade());
        templateContext.put("maxGrade", context.getGradeDetails().maxGrade());
        templateContext.put("gradeParts", context.getGradeDetails().getParts());
        templateContext.put("deadline", null);
        templateContext.put("now", Instant.EPOCH);

        return new I18nTemplateResolver().process("live-issue/body.md", templateContext, Locale.FRENCH).trim();
    }
}
