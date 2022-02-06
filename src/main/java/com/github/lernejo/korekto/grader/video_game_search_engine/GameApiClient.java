package com.github.lernejo.korekto.grader.video_game_search_engine;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.Query;

import java.util.List;

public interface GameApiClient {
    String SAMPLE_RESPONSE_PAYLOAD = """
        {
            "title": "my super game",
            "thumbnail": "http://somehost/somepathwithcorsdisabled",
            "short_description": "awesome game",
            "genre": "ftps",
            "platform": "PS1000",
            "publisher": "Machin produces",
            "developer": "Bidule Studios",
            "release_date": "2022-02-12"
        }
        """.stripTrailing();
    ;

    @GET("/api/games")
    @Headers("Accept:application/json")
    Call<List<Game>> getGames(@Query("query") String query);
}
