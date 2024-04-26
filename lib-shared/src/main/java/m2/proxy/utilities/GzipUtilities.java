package m2.proxy.utilities;

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GzipUtilities {

    private static final Logger log = LoggerFactory.getLogger(GzipUtilities.class);

    public static byte[] gzip(String content) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            GzipCompressorOutputStream gzip = new GzipCompressorOutputStream(out);
            gzip.write(content.getBytes(StandardCharsets.UTF_8));
            gzip.finish();
            return out.toByteArray();
        } catch (IOException ex) {
            log.error("Error zipping content. Message: {}", ex.getMessage());
        }
        return new byte[0];
    }

    public static List<String> unGzip(byte[] content) {
        try {
            GzipCompressorInputStream unzippingStream = new GzipCompressorInputStream(
                    new ByteArrayInputStream(content)
            );
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(
                            unzippingStream,
                            StandardCharsets.UTF_8
                    ))) {
                String line;
                List<String> fileContent = new ArrayList<>();
                while ((line = in.readLine()) != null) {
                    fileContent.add(line);
                }
                return fileContent;
            }
        } catch (IOException ex) {
            log.error("Error reading zip-buffer. Message: {}", ex.getMessage());
        }
        return Collections.emptyList();
    }
}
