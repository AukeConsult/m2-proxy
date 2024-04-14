package m2.proxy.utilities;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class FileUtilities {

    public static void renameFile(String sourceFile, String destinationFile) throws IOException {
        Files.move(getPath(sourceFile), getPath(destinationFile));
    }

    public static void copyFile(String sourceFile, String destinationFile) throws IOException {
        Files.copy(getPath(sourceFile), getPath(destinationFile));
    }

    public static boolean exists(String sourceFile) {
        return Files.exists(getPath(sourceFile));
    }

    public static String generateFileTimeStamp() {
        DateFormat dateFormat = new SimpleDateFormat("yyMMdd-HHmmss");
        Date date = new Date();
        return dateFormat.format(date);
    }

    public static String getApplicationAbsolutePath() {
        Path appAbsolutePath = Paths.get(".").toAbsolutePath().normalize();
        if(appAbsolutePath!=null&&appAbsolutePath.getFileName()!=null) {
            if (appAbsolutePath.getFileName().toString().equals("jar"))
                appAbsolutePath = appAbsolutePath.getParent().getParent();
            return appAbsolutePath.toString();
        } else {
            return System.getProperty("user.dir");
        }
    }

    public static Path getPath(String sourcePath) {
        return Paths.get(sourcePath);
    }

    public static Path getRoot(String sourcePath) {
        Path rootPath = Paths.get(sourcePath).getRoot();
        if (rootPath == null) {
            rootPath = Paths.get(getApplicationAbsolutePath()).getRoot();
        }
        return rootPath;
    }

    public static void createFile(String name, String content) throws IOException {
        FileWriter fileWriter = new FileWriter(name);
        fileWriter.write(content);
        fileWriter.flush();
        fileWriter.close();
    }
}