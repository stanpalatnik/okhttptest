import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.Map;

public class Client {
    private final String hostname;
    private final int port;
    private final String token;
    private final String resource;

    public static void main(String args[]) throws IOException {
        Client c = new Client("localhost", 7272, "26438F65-0827-4D7A-B113-C640199B52EC", "archive-staging-customer123");
        c.getKey("12B07BCBX");
    }

    public Client(String hostname, int port, String token, String resource) {
        System.setProperty("https.protocols", "TLSv1");
        this.hostname = hostname;
        this.port = port;
        this.token = token;
        this.resource = resource;
    }

    private final OkHttpClient client = new OkHttpClient();
    private final Gson gson = new Gson();

    /* public void run() throws Exception {
        Request request = new Request.Builder()
                .url("https://api.github.com/gists/c2a7c39532239ff261be")
                .build();
        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

        Gist gist = gson.fromJson(response.body().charStream(), Gist.class);
        for (Map.Entry<String, GistFile> entry : gist.files.entrySet()) {
            System.out.println(entry.getKey());
            System.out.println(entry.getValue().content);
        }
    }          */

    public void getKey(String keyId) throws IOException {
        final String INIT_CALL = String.format("https://%s:%d/restapi/json/v1/resources/resourcename/%s/accounts/accountname/%s?AUTHTOKEN=%s",
                hostname, port, resource, keyId, token);
        Response response = call(INIT_CALL);
        System.out.println( response.body().string());
        Wrapper gist = gson.fromJson(response.body().charStream(), Wrapper.class);
        gist.toString();
    }

    private Response call(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .build();
        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
        return response;
    }

    static class Wrapper {
        Operation op;
    }

    static class Operation {
        String name;
        Map<String, String> result;
        Map<String, Integer> details;
    }
}
