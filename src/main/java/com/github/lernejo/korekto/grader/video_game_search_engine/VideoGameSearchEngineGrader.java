package com.github.lernejo.korekto.grader.video_game_search_engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.lernejo.korekto.grader.video_game_search_engine.parts.AmqpToEsPartGrader;
import com.github.lernejo.korekto.grader.video_game_search_engine.parts.FileToAmqpPartGrader;
import com.github.lernejo.korekto.grader.video_game_search_engine.parts.LuceneQueryPartGrader;
import com.github.lernejo.korekto.grader.video_game_search_engine.parts.MavenCompileTestAndDownloadAdditionalPluginsPartGrader;
import com.github.lernejo.korekto.toolkit.GradePart;
import com.github.lernejo.korekto.toolkit.Grader;
import com.github.lernejo.korekto.toolkit.GradingConfiguration;
import com.github.lernejo.korekto.toolkit.PartGrader;
import com.github.lernejo.korekto.toolkit.misc.HumanReadableDuration;
import com.github.lernejo.korekto.toolkit.partgrader.GitHistoryPartGrader;
import com.github.lernejo.korekto.toolkit.partgrader.GitHubActionsPartGrader;
import com.github.lernejo.korekto.toolkit.partgrader.JacocoCoveragePartGrader;
import com.github.lernejo.korekto.toolkit.partgrader.PmdPartGrader;
import com.github.lernejo.korekto.toolkit.thirdparty.docker.MappedPortsContainer;
import com.github.lernejo.korekto.toolkit.thirdparty.pmd.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class VideoGameSearchEngineGrader implements Grader<LaunchingContext> {

    private final Logger logger = LoggerFactory.getLogger(VideoGameSearchEngineGrader.class);

    private final ObjectMapper om = new ObjectMapper()
        .findAndRegisterModules()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final List<Game> games;
    private final MappedPortsContainer rabbitMqContainer;
    private final MappedPortsContainer elasticSearchContainer;

    public VideoGameSearchEngineGrader() throws IOException {
        games = om.readValue(VideoGameSearchEngineGrader.class.getClassLoader().getResourceAsStream("games.json"), new TypeReference<List<Game>>() {
        });

        rabbitMqContainer = new MappedPortsContainer(
            "rabbitmq:3.9.7-management-alpine",
            5672,
            (sp, sps) -> "RabbitMQ up on " + sp + " (management on http://localhost:" + sps.get(0) + " )",
            15672)
            .startAndWaitForServiceToBeUp();
        elasticSearchContainer = new MappedPortsContainer(
            "elasticsearch:7.16.3",
            9200)
            .withEnv("discovery.type", "single-node")
            .withEnv("xpack.security.enabled", "true")
            .withEnv("ELASTIC_PASSWORD", "admin")
            .withEnv("bootstrap.memory_lock", "true")
            .withEnv("ES_JAVA_OPTS", "-Xms256m -Xmx512m")
            .startAndWaitForServiceToBeUp();
    }

    @Override
    public void close() {
        rabbitMqContainer.stop();
    }

    @Override
    public String slugToRepoUrl(String slug) {
        return "https://github.com/" + slug + "/video_game_search_engine";
    }

    @Override
    public boolean needsWorkspaceReset() {
        return true;
    }

    @Override
    public LaunchingContext gradingContext(GradingConfiguration configuration) {
        return new LaunchingContext(configuration, om, games, rabbitMqContainer.getServicePort(), elasticSearchContainer.getServicePort());
    }

    @Override
    public void run(LaunchingContext context) {
        context.getGradeDetails().getParts().addAll(grade(context));
    }

    private Collection<? extends GradePart> grade(LaunchingContext context) {
        return graders().stream()
            .map(g -> applyPartGrader(context, g))
            .collect(Collectors.toList());
    }

    private GradePart applyPartGrader(LaunchingContext context, PartGrader<LaunchingContext> g) {
        long startTime = System.currentTimeMillis();
        try {
            return g.grade(context);
        } finally {
            logger.debug(g.name() + " in " + HumanReadableDuration.toString(System.currentTimeMillis() - startTime));
        }
    }

    private Collection<? extends PartGrader<LaunchingContext>> graders() {
        return List.of(
            new MavenCompileTestAndDownloadAdditionalPluginsPartGrader("Part 1 - Compilation & Tests", 4.0D),
            new GitHubActionsPartGrader<>("Part 2 - CI", 2.0D),
            new JacocoCoveragePartGrader<>("Part 3 - Code Coverage", 4.0D, 0.9D),
            new AmqpToEsPartGrader("Part 4 - AMQP -> ES", 4.0D),
            new FileToAmqpPartGrader("Part 5 - File -> AMQP", 4.0D),
            new LuceneQueryPartGrader("Part 6 - Lucene querying", 4.0D),

            new GitHistoryPartGrader<>("Git (proper descriptive messages)", -4.0D),
            new PmdPartGrader<>("Coding style", -4.0D,
                Rule.buildExcessiveClassLengthRule(50),
                Rule.buildExcessiveMethodLengthRule(15),
                Rule.buildFieldMandatoryModifierRule("private", "final", "!static")
            )
        );
    }
}
