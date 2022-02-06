package com.github.lernejo.korekto.grader.video_game_search_engine;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.lernejo.korekto.toolkit.GradingConfiguration;
import com.github.lernejo.korekto.toolkit.GradingContext;
import com.github.lernejo.korekto.toolkit.partgrader.MavenContext;
import com.rabbitmq.client.ConnectionFactory;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.jetbrains.annotations.NotNull;
import retrofit2.Retrofit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class LaunchingContext extends GradingContext implements MavenContext {
    public final ObjectMapper om;
    public final List<Game> games;
    public final Integer rabbitMqPort;
    public final int elasticSearchPort;
    public final int webPort = 8085;
    public final GameApiClient gameApiClient;
    private final Supplier<SilentJacksonConverterFactory.ExceptionHolder> exceptionHolderSupplier;
    private boolean compilationFailed;
    private boolean testFailed;
    public boolean rabbitQueueCreated = true;
    public boolean elasticsearchIndexCreated = true;
    public final long serverStartTimeout = Long.valueOf(System.getProperty("SERVER_START_TIMEOUT", "40"));
    public final long injectorStartTimeout = Long.valueOf(System.getProperty("INJECTOR_START_TIMEOUT", "20"));

    public LaunchingContext(GradingConfiguration configuration, ObjectMapper om, List<Game> games, Integer rabbitMqPort, int elasticSearchPort) {
        super(configuration);
        this.om = om;
        this.games = games;
        this.rabbitMqPort = rabbitMqPort;
        this.elasticSearchPort = elasticSearchPort;
        SilentJacksonConverterFactory jacksonConverterFactory = SilentJacksonConverterFactory.create(om);
        this.gameApiClient = new Retrofit.Builder()
            .baseUrl("http://localhost:" + webPort + "/")
            .addConverterFactory(jacksonConverterFactory)
            .build()
            .create(GameApiClient.class);
        this.exceptionHolderSupplier = jacksonConverterFactory::newExceptionHolder;
    }

    public ConnectionFactory newConnectionFactory() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setPort(rabbitMqPort);
        return factory;
    }

    public ElasticsearchClient newElasticsearchClient() {
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials("elastic", "admin"));
        RestClient restClient = RestClient.builder(new HttpHost("localhost", elasticSearchPort))
            .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider))
            .build();
        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper(om));
        return new ElasticsearchClient(transport);
    }

    @Override
    public void markAsCompilationFailed() {
        this.compilationFailed = true;
    }

    @Override
    public void markAsTestFailed() {
        this.testFailed = true;
    }

    @Override
    public boolean hasCompilationFailed() {
        return compilationFailed;
    }

    @Override
    public boolean hasTestFailed() {
        return testFailed;
    }

    @NotNull
    public List<Game> selectGames(int count) {
        List<Game> gameCatalog = new ArrayList<>(games);
        Collections.shuffle(gameCatalog);
        return gameCatalog.subList(0, count);
    }

    public void setRabbitQueueNotCreated() {
        this.rabbitQueueCreated = false;
    }

    public void setElasticsearchIndexNotCreated() {
        this.elasticsearchIndexCreated = false;
    }

    public SilentJacksonConverterFactory.ExceptionHolder newExceptionHolder() {
        return exceptionHolderSupplier.get();
    }
}
