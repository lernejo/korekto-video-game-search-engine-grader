package com.github.lernejo.korekto.grader.video_game_search_engine.parts;

import com.github.lernejo.korekto.grader.video_game_search_engine.LaunchingContext;
import com.github.lernejo.korekto.toolkit.partgrader.MavenCompileAndTestPartGrader;
import com.github.lernejo.korekto.toolkit.thirdparty.maven.MavenExecutor;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public class MavenCompileTestAndDownloadAdditionalPluginsPartGrader extends MavenCompileAndTestPartGrader<LaunchingContext> {

    public static final String SPRING_BOOT_PLUGIN = "org.springframework.boot:spring-boot-maven-plugin:2.6.2";

    public MavenCompileTestAndDownloadAdditionalPluginsPartGrader(String name, double maxGrade) {
        super(name, maxGrade);
    }

    @Override
    public void afterCompile(LaunchingContext context, @NotNull Path root) {
        MavenExecutor.executeGoal(context.getExercise(), context.getConfiguration().getWorkspace(),
            SPRING_BOOT_PLUGIN + ":help");
    }
}
