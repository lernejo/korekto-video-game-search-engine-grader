package com.github.lernejo.korekto.grader.video_game_search_engine.parts;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.github.lernejo.korekto.grader.video_game_search_engine.Game;
import com.github.lernejo.korekto.grader.video_game_search_engine.LaunchingContext;
import com.github.lernejo.korekto.toolkit.GradePart;
import com.github.lernejo.korekto.toolkit.PartGrader;
import com.github.lernejo.korekto.toolkit.misc.Ports;
import com.github.lernejo.korekto.toolkit.thirdparty.amqp.AmqpCapable;
import com.github.lernejo.korekto.toolkit.thirdparty.maven.MavenExecutionHandle;
import com.github.lernejo.korekto.toolkit.thirdparty.maven.MavenExecutor;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.jetbrains.annotations.NotNull;
import org.testcontainers.shaded.org.awaitility.core.ConditionTimeoutException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static com.github.lernejo.korekto.grader.video_game_search_engine.parts.MavenCompileTestAndDownloadAdditionalPluginsPartGrader.SPRING_BOOT_PLUGIN;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

public record AmqpToEsPartGrader(String name, Double maxGrade) implements PartGrader<LaunchingContext>, AmqpCapable {

    public static final String QUEUE_NAME = "game_info";
    public static final String INDEX_NAME = "games";

    @Override
    public @NotNull
    GradePart grade(LaunchingContext context) {
        if (context.hasCompilationFailed()) {
            return result(List.of("Not trying to start server as compilation failed"), 0.0D);
        }

        List<Game> games = selectGames(context, 4);

        ConnectionFactory factory = context.newConnectionFactory();
        deleteQueue(factory, QUEUE_NAME);
        ElasticsearchClient elasticsearchClient = context.newElasticsearchClient();
        deleteIndex(elasticsearchClient, INDEX_NAME);

        String moduleSpec = "-pl :search-api ";
        String springArguments = "-Dserver.port=8085 -Dspring.rabbitmq.port=" + context.rabbitMqPort + " -Delasticsearch.port=" + context.elasticSearchPort;

        try
            (MavenExecutionHandle ignored = MavenExecutor.executeGoalAsync(context.getExercise(), context.getConfiguration().getWorkspace(),
                SPRING_BOOT_PLUGIN + ":run " + moduleSpec + " -Dspring-boot.run.jvmArguments='" + springArguments + "'")) {
            try {
                Ports.waitForPortToBeListenedTo(8085, SECONDS, 40L);
            } catch (CancellationException e) {
                return result(List.of("Server failed to start within 20 sec."), 0.0D);
            }

            double grade = maxGrade();
            List<String> errors = new ArrayList<>();

            try (Connection connection = factory.newConnection();
                 Channel channel = connection.createChannel()) {

                try {
                    await().atMost(5, SECONDS).until(() -> doesQueueExists(connection, QUEUE_NAME));
                } catch (ConditionTimeoutException e) {
                    grade = 0;
                    errors.add("No queue named `" + QUEUE_NAME + "` was created by the server when starting");
                    return result(errors, grade);
                }

                AMQP.BasicProperties basicProperties = new AMQP.BasicProperties().builder().contentType("application/json").deliveryMode(2).build();
                for (Game game : games) {
                    channel.basicPublish("", QUEUE_NAME, true, false, basicProperties.builder().headers(Map.of("game_id", game.id())).build(), context.om.writeValueAsBytes(game));
                }

                try {
                    await().atMost(5, SECONDS).until(() -> channel.messageCount(QUEUE_NAME) == 0L);
                } catch (ConditionTimeoutException e) {
                    grade -= maxGrade() / 2;
                    errors.add("Messages published to `" + QUEUE_NAME + "` were not consumed within 5 sec");
                }
            } catch (IOException | TimeoutException e) {
                throw new IllegalStateException("Could not connect to the dockerized RabbitMQ", e);
            }

            try {
                if (!elasticsearchClient.indices().exists(new ExistsRequest.Builder().index(INDEX_NAME).build()).value()) {
                    grade -= maxGrade() / 2;
                    errors.add("No index `" + INDEX_NAME + "` were created after message consumption");
                } else {
                    try {
                        List<Game> indexedGames = await().atMost(5, SECONDS).until(() -> searchAll(elasticsearchClient, INDEX_NAME, Game.class), gs -> gs.size() == 4);

                        Set<String> indexedTitles = indexedGames.stream().map(Game::title).collect(Collectors.toSet());
                        Set<String> expectedTitles = games.stream().map(Game::title).collect(Collectors.toSet());
                        if (!expectedTitles.equals(indexedTitles)) {
                            grade -= maxGrade() / 3;
                            errors.add("Index `" + INDEX_NAME + "` contains these games: " + indexedTitles + " whereas those were expected: " + expectedTitles);
                        }
                    } catch (ConditionTimeoutException e) {
                        grade -= maxGrade() / 3;
                        errors.add("Index `" + INDEX_NAME + "` does not contains the 4 games sent of the " + QUEUE_NAME + " queue.");
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Unable to check index: " + e.getMessage(), e);
            }

            return result(errors, grade);
        } finally {
            Ports.waitForPortToBeFreed(8085, SECONDS, 5L);
        }
    }

    private <T> List<T> searchAll(ElasticsearchClient client, String indexName, Class<T> targetItemClass) {
        try {
            return client.search(
                s -> s.index(indexName),
                targetItemClass)
                .hits().hits().stream().map(Hit::source).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to search index: " + e.getMessage(), e);
        }
    }

    @NotNull
    private List<Game> selectGames(LaunchingContext context, int count) {
        List<Game> gameCatalog = new ArrayList<>(context.games);
        Collections.shuffle(gameCatalog);
        return gameCatalog.subList(0, count);
    }

    private void deleteIndex(ElasticsearchClient client, String indexName) {
        try {
            if (client.indices().exists(new ExistsRequest.Builder().index(indexName).build()).value()) {
                client.indices().delete(new DeleteIndexRequest.Builder().index(indexName).build());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to delete index: " + e.getMessage(), e);
        }
    }
}
