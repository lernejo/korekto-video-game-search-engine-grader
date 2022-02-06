package com.github.lernejo.korekto.grader.video_game_search_engine.parts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.lernejo.korekto.grader.video_game_search_engine.Game;
import com.github.lernejo.korekto.grader.video_game_search_engine.LaunchingContext;
import com.github.lernejo.korekto.toolkit.GradePart;
import com.github.lernejo.korekto.toolkit.PartGrader;
import com.github.lernejo.korekto.toolkit.thirdparty.amqp.AmqpCapable;
import com.github.lernejo.korekto.toolkit.thirdparty.maven.MavenExecutionHandle;
import com.github.lernejo.korekto.toolkit.thirdparty.maven.MavenExecutor;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.shaded.org.awaitility.core.ConditionTimeoutException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static com.github.lernejo.korekto.grader.video_game_search_engine.parts.AmqpToEsPartGrader.QUEUE_NAME;
import static com.github.lernejo.korekto.grader.video_game_search_engine.parts.MavenCompileTestAndDownloadAdditionalPluginsPartGrader.SPRING_BOOT_PLUGIN;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

public record FileToAmqpPartGrader(String name, Double maxGrade) implements PartGrader<LaunchingContext>, AmqpCapable {

    private static final Logger logger = LoggerFactory.getLogger(FileToAmqpPartGrader.class);

    @Override
    public GradePart grade(LaunchingContext context) {
        if (context.hasCompilationFailed()) {
            return result(List.of("Not trying to start server as compilation failed"), 0.0D);
        } else if (!context.rabbitQueueCreated) {
            return result(List.of("Not trying as RabbitMQ queue was not created by `search-api`"), 0.0D);
        }

        double grade = maxGrade();
        List<String> errors = new ArrayList<>();

        int gamesToSerializeCount = context.getRandomSource().nextInt(10);
        List<Game> games = context.selectGames(gamesToSerializeCount);
        Path gamesFilePath = createNewJsonGamesFile(context.om, games);

        ConnectionFactory factory = context.newConnectionFactory();

        String moduleSpec = "-pl :file-injector ";
        String mainArguments = gamesFilePath.toString();
        String springArguments = "-Dspring.rabbitmq.port=" + context.rabbitMqPort;

        try
            (MavenExecutionHandle ignored = MavenExecutor.executeGoalAsync(context.getExercise(), context.getConfiguration().getWorkspace(),
                SPRING_BOOT_PLUGIN + ":run " + moduleSpec + " -Dspring-boot.run.arguments='" + mainArguments + "'" + " -Dspring-boot.run.jvmArguments='" + springArguments + "'")) {

            try (Connection connection = factory.newConnection();
                 Channel channel = connection.createChannel()) {

                try {
                    await().atMost(context.injectorStartTimeout, SECONDS).until(() -> channel.messageCount(QUEUE_NAME) == gamesToSerializeCount);
                } catch (ConditionTimeoutException e) {
                    grade -= maxGrade() / 2;
                    errors.add("Messages in given file where not published to `" + QUEUE_NAME + "` within " + context.injectorStartTimeout + " sec");
                }
            } catch (ConditionTimeoutException e) {
                grade = 0;
                errors.add("No queue named `" + QUEUE_NAME + "` was created by the server when starting");
                return result(errors, grade);
            } catch (IOException | TimeoutException e) {
                throw new IllegalStateException("Could not connect to the dockerized RabbitMQ", e);
            }
        }

        return result(errors, grade);
    }

    private Path createNewJsonGamesFile(ObjectMapper om, List<Game> games) {
        try {
            Path path = Files.createTempFile("korekto", "games.json").toAbsolutePath();
            Files.write(path, om.writeValueAsBytes(games));
            logger.info("Injecting JSON games from file: " + path);
            return path;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
