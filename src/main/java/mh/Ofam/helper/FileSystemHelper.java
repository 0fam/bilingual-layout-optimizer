package mh.Ofam.helper;

import mh.Ofam.layout.Analyzer;

import java.net.URISyntaxException;
import java.nio.file.Path;

public class FileSystemHelper {
    public static Path getResourcePathForDir(String dir) throws URISyntaxException {
        return Path.of(Analyzer.class.getResource("/" + dir).toURI());
    }
}
