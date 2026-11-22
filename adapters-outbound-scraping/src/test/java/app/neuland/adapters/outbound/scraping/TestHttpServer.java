package app.neuland.adapters.outbound.scraping;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Minimal HTTP server for adapter tests. */
final class TestHttpServer implements AutoCloseable {

    record Request(String method, String path, String body, com.sun.net.httpserver.Headers headers) {
    }

    interface Handler {
        void handle(Request request, Reply reply) throws Exception;
    }

    static final class Reply {
        int status = 200;
        byte[] body = new byte[0];

        Reply status(int status) {
            this.status = status;
            return this;
        }

        Reply body(String body) {
            this.body = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return this;
        }

        Reply body(byte[] body) {
            this.body = body;
            return this;
        }
    }

    private final HttpServer server;
    final List<Request> requests = new CopyOnWriteArrayList<>();

    TestHttpServer(Handler handler) {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", exchange -> respond(exchange, handler));
        server.start();
    }

    private void respond(HttpExchange exchange, Handler handler) throws IOException {
        Request request = new Request(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8),
                exchange.getRequestHeaders());
        requests.add(request);
        Reply reply = new Reply();
        try {
            handler.handle(request, reply);
        } catch (Exception e) {
            reply.status(500).body(String.valueOf(e));
        }
        exchange.sendResponseHeaders(reply.status, reply.body.length == 0 ? -1 : reply.body.length);
        if (reply.body.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(reply.body);
            }
        }
        exchange.close();
    }

    String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
