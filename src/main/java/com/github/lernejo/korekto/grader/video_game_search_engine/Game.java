package com.github.lernejo.korekto.grader.video_game_search_engine;

import java.time.LocalDate;

public record Game(String id,
                   String title,
                   String thumbnail,
                   String short_description,
                   String game_url,
                   String genre,
                   String platform,
                   String publisher,
                   String developer,
                   LocalDate release_date
) implements Identifiable<String> {
}
