package com.github.lernejo.korekto.grader.video_game_search_engine;

import com.github.lernejo.korekto.toolkit.*;
import com.github.lernejo.korekto.toolkit.misc.RandomSupplier;
import com.github.lernejo.korekto.toolkit.misc.SubjectForToolkitInclusion;
import com.github.lernejo.korekto.toolkit.thirdparty.git.GitNature;
import org.eclipse.jgit.api.ResetCommand;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class VideoGameSearchEngineGraderTest {

    private static final Path workspace = Paths.get("target/test_repositories").toAbsolutePath();
    private static final Grader grader = Grader.Companion.load();

    @BeforeAll
    static void setUpAll() {
        //Processes.launch(OS.Companion.getCURRENT_OS().deleteDirectoryCommand(workspace.resolve("lernejo")), null);
        System.setProperty("SERVER_START_TIMEOUT", "20");
    }

    @SubjectForToolkitInclusion(additionalInfo = "in test module")
    @ParameterizedTest(name = "(branch={1}) {0}")
    @MethodSource("branches")
    @EnabledIfSystemProperty(named = "github_token", matches = ".+")
    void check_project_stages(String title, String branchName, List<GradePart> expectedGradeParts) {
        String repoUrl = grader.slugToRepoUrl("lernejo");
        GradingConfiguration configuration = new GradingConfiguration(repoUrl, "", "", workspace);

        GradingContext context = execute(branchName, configuration);

        assertThat(context)
            .as("Grading context")
            .isNotNull();

        assertThat(context.getGradeDetails().getParts())
            .usingComparatorForType(new GrapePartComparator(), GradePart.class)
            .containsExactlyElementsOf(expectedGradeParts);
    }

    private GradingContext execute(String branchName, GradingConfiguration configuration) {
        AtomicReference<GradingContext> contextHolder = new AtomicReference<>();
        new GradingJob()
            .addCloneStep()
            .addStep("replace randomSource", context -> context.setRandomSource(RandomSupplier.createDeterministic()))
            .addStep("switch-branch",
                context -> context
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

    static Stream<Arguments> branches() {
        return Stream.of(
            arguments("Complete exercise", "main", List.of(
                new GradePart("Part 1 - Compilation & Tests", 2.0D, 4.0D, List.of("There are test failures, see `mvn verify`")),
                new GradePart("Part 2 - CI", 1.0D, 2.0D, List.of("Latest CI run of branch `main` was expected to be in *success* state but found: failure")),
                new GradePart("Part 3 - Code Coverage", 0.0D, 4.0D, List.of("Coverage not available when there is test failures")),
                new GradePart("Part 4 - AMQP -> ES", 4.0D, 4.0D, List.of()),
                new GradePart("Part 5 - File -> AMQP", 4.0D, 4.0D, List.of()),
                new GradePart("Part 6 - Lucene querying", 4.0D, 4.0D, List.of()),
                new GradePart("Git (proper descriptive messages)", -0.5D, null, List.of("`e8267ed` All-in-One --> 1 word is too short")),
                new GradePart("Coding style", -4.0D, null, List.of("""
                    fr.lernejo.fileinjector.Launcher
                                * L.21: The field `om` must not have modifier `static`
                                * L.25: The field `MESSAGES_TYPE_REF` must not have modifier `static`
                                * L.28: Method has 19 lines, exceeding the maximum of 15
                    """.stripTrailing(), """
                    fr.lernejo.search.api.AmqpConfiguration
                                * L.10: The field `GAME_INFO_QUEUE` must have modifier `private` and must not have modifier `static`
                    """.stripTrailing(), """
                    fr.lernejo.search.api.ElasticSearchConfiguration
                                * L.23: The field `logger` must not have modifier `static`
                                * L.24: The field `GAME_INDEX` must have modifier `private` and must not have modifier `static`
                    """
                ))
            ))
        );
    }

    @SubjectForToolkitInclusion(additionalInfo = "in test module")
    public static final class GrapePartComparator implements Comparator<GradePart> {

        private static final Logger logger = LoggerFactory.getLogger(GrapePartComparator.class);

        @Override
        public int compare(GradePart o1, GradePart o2) {
            boolean sameId = Objects.equals(o1.getId(), o2.getId());
            boolean sameGrade = Objects.equals(o1.getGrade(), o2.getGrade());
            boolean sameMaxGrade = Objects.equals(o1.getMaxGrade(), o2.getMaxGrade());

            int differentMessages = 0;
            List<String> leftComments = new ArrayList<>(o1.getComments());
            for (String expectedComment : o2.getComments()) {
                if (expectedComment.startsWith("\\p")) {
                    String rawPattern = expectedComment.substring(2);
                    Pattern expectedPattern = Pattern.compile(rawPattern);
                    Optional<String> first = leftComments.stream().filter(c -> expectedPattern.matcher(c).matches()).findFirst();
                    if (first.isPresent()) {
                        leftComments.remove(first.get());
                    } else {
                        //logger.warn("Pattern [" + rawPattern + "] did not match anything among: " + o1.getComments());
                        differentMessages++;
                    }
                } else {
                    if (leftComments.contains(expectedComment)) {
                        leftComments.remove(expectedComment);
                    } else {
                        differentMessages++;
                    }
                }
            }

            return (sameId ? 0 : 10000) + (sameGrade ? 0 : 1000) + (sameMaxGrade ? 0 : 100) + differentMessages;
        }
    }
}
