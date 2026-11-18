import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Application-level Docker health check; requires only the existing Java runtime. */
public class HttpHealthCheck {
    public static void main(String[] args) {
        try {
            var request = HttpRequest.newBuilder(URI.create(
                    "http://127.0.0.1:8081/api/auth/sso/providers"))
                    .timeout(Duration.ofSeconds(2)).GET().build();
            var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            System.exit(status == 200 ? 0 : 1);
        } catch (Exception ignored) {
            // Do not print response bodies, environment values, or configuration.
            System.exit(1);
        }
    }
}
