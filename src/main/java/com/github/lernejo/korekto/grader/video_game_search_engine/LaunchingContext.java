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

import java.util.List;

public class LaunchingContext extends GradingContext implements MavenContext {
    public final ObjectMapper om;
    public final List<Game> games;
    public final Integer rabbitMqPort;
    public final int elasticSearchPort;
    private boolean compilationFailed;
    private boolean testFailed;

    public LaunchingContext(GradingConfiguration configuration, ObjectMapper om, List<Game> games, Integer rabbitMqPort, int elasticSearchPort) {
        super(configuration);
        this.om = om;
        this.games = games;
        this.rabbitMqPort = rabbitMqPort;
        this.elasticSearchPort = elasticSearchPort;
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
}
