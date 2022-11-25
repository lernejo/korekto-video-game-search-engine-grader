package com.github.lernejo.korekto.grader.video_game_search_engine;

import com.github.lernejo.korekto.toolkit.misc.SubjectForToolkitInclusion;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.io.IOException;
import java.lang.annotation.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static com.github.lernejo.korekto.toolkit.misc.ThrowingFunction.sneaky;

@SubjectForToolkitInclusion
@Target({ElementType.ANNOTATION_TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ArgumentsSource(BrancheFileSource.BrancheFileSourceProvider.class)
public @interface BrancheFileSource {

    final class BrancheFileSourceProvider implements ArgumentsProvider {

        private static String removeFileExtension(String filename, boolean removeAllExtensions) {
            if (filename == null || filename.isEmpty()) {
                return filename;
            }

            String extPattern = "(?<!^)[.]" + (removeAllExtensions ? ".*" : "[^.]*$");
            return filename.replaceAll(extPattern, "");
        }

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext extensionContext) throws Exception {
            URL branchesDirectoryUrl = BrancheFileSource.class.getClassLoader().getResource("branches");
            Path branchesDirectory = Paths.get(branchesDirectoryUrl.toURI());
            return Files.list(branchesDirectory)
                .filter(Files::isRegularFile)
                .map(sneaky(p -> buildArguments(p)));
        }

        @NotNull
        private Arguments buildArguments(Path p) throws IOException {
            String fileName = removeFileExtension(p.getFileName().toString(), true);
            int hyphenIndex = fileName.indexOf('-');
            final String title;
            final String branchName;
            if (hyphenIndex == -1) {
                branchName = fileName;
                title = "missing -title in filename";
            } else {
                branchName = fileName.substring(0, hyphenIndex);
                title = fileName.substring(hyphenIndex + 1).replace('_', ' ');
            }
            return Arguments.arguments(title, branchName, Files.readString(p).trim());
        }
    }
}
