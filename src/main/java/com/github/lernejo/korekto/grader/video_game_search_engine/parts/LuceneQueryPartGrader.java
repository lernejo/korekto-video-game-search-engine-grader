package com.github.lernejo.korekto.grader.video_game_search_engine.parts;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.github.lernejo.korekto.grader.video_game_search_engine.Game;
import com.github.lernejo.korekto.grader.video_game_search_engine.GameApiClient;
import com.github.lernejo.korekto.grader.video_game_search_engine.Identifiable;
import com.github.lernejo.korekto.grader.video_game_search_engine.LaunchingContext;
import com.github.lernejo.korekto.toolkit.GradePart;
import com.github.lernejo.korekto.toolkit.PartGrader;
import com.github.lernejo.korekto.toolkit.misc.Ports;
import com.github.lernejo.korekto.toolkit.misc.RandomSupplier;
import com.github.lernejo.korekto.toolkit.misc.SubjectForToolkitInclusion;
import com.github.lernejo.korekto.toolkit.thirdparty.maven.MavenExecutionHandle;
import com.github.lernejo.korekto.toolkit.thirdparty.maven.MavenExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Response;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.github.lernejo.korekto.grader.video_game_search_engine.parts.AmqpToEsPartGrader.INDEX_NAME;
import static com.github.lernejo.korekto.grader.video_game_search_engine.parts.MavenCompileTestAndDownloadAdditionalPluginsPartGrader.SPRING_BOOT_PLUGIN;
import static java.util.concurrent.TimeUnit.SECONDS;

public record LuceneQueryPartGrader(String name, Double maxGrade) implements PartGrader<LaunchingContext> {

    private static final Logger logger = LoggerFactory.getLogger(LuceneQueryPartGrader.class);

    @Override
    public GradePart grade(LaunchingContext context) {
        if (context.hasCompilationFailed()) {
            return result(List.of("Not trying to start server as compilation failed"), 0.0D);
        }

        double grade = maxGrade();
        List<String> errors = new ArrayList<>();

        int gamesToSerializeCount = context.getRandomSource().nextInt(50) + 20;
        List<Game> games = context.selectGames(gamesToSerializeCount);
        ElasticsearchClient elasticsearchClient = context.newElasticsearchClient();
        deleteIndexContent(elasticsearchClient, INDEX_NAME);
        sendGamesToIndex(elasticsearchClient, INDEX_NAME, games);

        int dedicatedGenreGamesCount = context.getRandomSource().nextInt(5) + 4;
        String selectedGenre = "selectedGenre";
        Set<Game> gamesWithTheSelectedGenre = IntStream.range(0, dedicatedGenreGamesCount)
            .mapToObj(i -> buildNewGame(context.getRandomSource(), selectedGenre))
            .collect(Collectors.toSet());
        sendGamesToIndex(elasticsearchClient, INDEX_NAME, gamesWithTheSelectedGenre);

        String luceneQuery = "genre:\"" + selectedGenre + "\"";

        String moduleSpec = "-pl :search-api ";
        String springArguments = "-Dserver.port=8085 -Dspring.rabbitmq.port=" + context.rabbitMqPort + " -Delasticsearch.port=" + context.elasticSearchPort;

        try
            (MavenExecutionHandle ignored = MavenExecutor.executeGoalAsync(context.getExercise(), context.getConfiguration().getWorkspace(),
                SPRING_BOOT_PLUGIN + ":run " + moduleSpec + " -Dspring-boot.run.jvmArguments='" + springArguments + "'")) {
            try {
                Ports.waitForPortToBeListenedTo(8085, SECONDS, context.serverStartTimeout);
            } catch (CancellationException e) {
                return result(List.of("Server failed to start within " + context.serverStartTimeout + " sec."), 0.0D);
            }

            try (var exHolder = context.newExceptionHolder()) {
                String query = "GET /api/games?query=" + luceneQuery;
                Response<List<Game>> response = context.gameApiClient.getGames(luceneQuery).execute();
                if (!response.isSuccessful()) {
                    grade = 0;
                    errors.add("Unsuccessful response of query " + query + ": " + response.code());
                } else if (exHolder.getLatestDeserializationProblem() != null) {
                    grade -= maxGrade() * (2.0 / 3);
                    errors.add("Bad response payload to query " + query + ", expected something like:\n```\n" + GameApiClient.SAMPLE_RESPONSE_PAYLOAD + "\n```\nBut got:\n```\n" + exHolder.getLatestDeserializationProblem().rawBody() + "\n```");
                } else {
                    Set<String> expectedTitles = gamesWithTheSelectedGenre.stream().map(Game::title).collect(Collectors.toSet());
                    Set<String> actualTitles = response.body().stream().map(Game::title).collect(Collectors.toSet());
                    if (!expectedTitles.equals(actualTitles)) {
                        grade -= maxGrade() / 3;
                        errors.add("Expected games in response of query " + query + " are expected to be " + expectedTitles + " but were " + actualTitles);
                    }
                }
            } catch (IOException e) {
                return result(List.of("Failed to call **search-api** API: " + e.getMessage()), 0.0D);
            }


        } finally {
            Ports.waitForPortToBeFreed(context.webPort, SECONDS, 5L);
        }

        return result(errors, grade);
    }

    private Game buildNewGame(RandomSupplier randomSource, String genre) {
        return new Game(
            randomSource.nextUuid().toString(),
            randomSource.nextUuid().toString(),
            "http://somehost/image.png",
            "A random desc",
            "https://thegame.com/mygame",
            genre,
            "PS100",
            "a publisher",
            "developer",
            LocalDate.now()
        );
    }

    @SubjectForToolkitInclusion
    private void deleteIndexContent(ElasticsearchClient client, String indexName) {
        try {
            if (client.indices().exists(new ExistsRequest.Builder().index(indexName).build()).value()) {
                DeleteByQueryResponse response = client.deleteByQuery(new DeleteByQueryRequest.Builder().index(List.of(indexName)).query(b -> b.matchAll(m -> m)).build());
                logger.debug("Deleted " + response.deleted() + " documents from [" + indexName + "]");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SubjectForToolkitInclusion
    private <T extends Identifiable<String>> void sendGamesToIndex(ElasticsearchClient client, String indexName, Collection<T> docs) {
        for (T doc : docs) {
            IndexRequest<T> request = new IndexRequest.Builder<T>().index(indexName).id(doc.id()).document(doc).build();
            try {
                client.index(request);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        logger.debug("Indexed " + docs.size() + " documents into [" + indexName + "]");
    }
}
