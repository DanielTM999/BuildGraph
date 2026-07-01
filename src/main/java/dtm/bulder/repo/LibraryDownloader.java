package dtm.bulder.repo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class LibraryDownloader {

    private static final long MAX_BYTES = 128L * 1024 * 1024; 
    private static final Duration TIMEOUT = Duration.ofMinutes(5);

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    public byte[] downloadZip(String url) throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .GET()
                .build();

        HttpResponse<InputStream> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrompido: " + url, e);
        }

        if (response.statusCode() / 100 != 2) {
            throw new IOException("Download falhou (HTTP " + response.statusCode() + "): " + url);
        }

        try (InputStream in = response.body();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > MAX_BYTES) {
                    throw new IOException("Arquivo excede o limite de " + MAX_BYTES + " bytes: " + url);
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }
}
