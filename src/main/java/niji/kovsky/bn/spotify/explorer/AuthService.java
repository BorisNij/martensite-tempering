package niji.kovsky.bn.spotify.explorer;

import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

import java.awt.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class AuthService {

    private static final String SCHEME = "https://";
    private static final String AUTH_HOST = "accounts.spotify.com";
    private static final String TOKEN_PATH = "/api/token";
    private static final int PORT = 9090;
    private static final String REDIRECT_URI = "http://localhost" + ":" + PORT;
    private static final String CLIENT_ID = "8acb3fc9c0b7438eb583e7fce44f819a";
    private static final String CLIENT_SECRET = "23df0fbc957340e59733190b8d8acc53";

    private String accessToken = "";
    private String accessCode = "";
    private HttpServer server;

    public boolean startListeningForAccessCode() {
        try {
            server = HttpServer.create();
            server.bind(new InetSocketAddress(PORT), 0);
            server.start();
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean manageToSendDefaultBrowserToAuthUri() {
        try {
            Desktop.getDesktop()
                    .browse(URI.create(authUri()));
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    public boolean manageToGetAccessCode() {
        Semaphore s = new Semaphore(0);
        final AccessCodeHolder accessCodeHolder = new AccessCodeHolder();

        server.createContext("/", exchange -> {
            try {
                String query = exchange.getRequestURI()
                        .getQuery();
                String status;
                if (query != null && query.contains("code=")) {
                    accessCodeHolder.aCode = query.substring(5);
                    status = "Got the code. Return back to your program.";
                } else {
                    status = "Not found authorization code. Try again.";
                    accessCodeHolder.aCode = null;
                }
                exchange.sendResponseHeaders(200, status.length());
                exchange.getResponseBody()
                        .write(status.getBytes());
                exchange.getResponseBody()
                        .close();
            } finally {
                s.release();
            }
        });

        try {
            s.tryAcquire(1, TimeUnit.MINUTES);
        } catch (Exception e) {
            return false;
        }
        this.accessCode = accessCodeHolder.aCode;
        return true;
    }

    public void stopListeningForAccessCode() {
        this.server.stop(1);
    }

    public boolean manageToGetAccessToken() {
        if (accessCode == null || accessCode.isEmpty()) {
            return false;
        }

        String uri = SCHEME + AUTH_HOST + TOKEN_PATH;

        HttpRequest request = HttpRequest.newBuilder()
                .header("Content-type", "application/x-www-form-urlencoded")
                .uri(URI.create(uri))
                .POST(HttpRequest.BodyPublishers.ofString("&client_id=" + CLIENT_ID
                                                          + "&client_secret=" + CLIENT_SECRET
                                                          + "&grant_type=authorization_code"
                                                          + "&code=" + accessCode
                                                          + "&redirect_uri=" + REDIRECT_URI))
                .build();
        HttpClient client = HttpClient.newBuilder()
                .build();
        String response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString())
                    .body();
        } catch (IOException | InterruptedException e) {
            return false;
        }

        if (response == null) {
            return false;
        }

        String parsedAccessToken = JsonParser.parseString(response)
                .getAsJsonObject()
                .get("access_token")
                .getAsString();

        if (parsedAccessToken == null || parsedAccessToken.toLowerCase()
                .contains("error") || parsedAccessToken.isBlank()) {
            this.accessToken = null;
            return false;
        }

        this.accessToken = parsedAccessToken;
        return true;
    }

    public boolean isAuthorized() {
        return accessToken != null && !accessToken.isBlank();
    }

    public String getAccessToken() {
        return this.accessToken;
    }

    public String authUri() {
        return "https://" + AUTH_HOST
               + "/authorize?client_id=" + CLIENT_ID
               + "&redirect_uri=" + REDIRECT_URI
               + "&response_type=code";
    }

    private static class AccessCodeHolder {
        private volatile String aCode = null;
    }
}