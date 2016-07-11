import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.net.ssl.*;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Map;

public class Client {
    private final String hostname;
    private final int port;
    private final String token;
    private final String resource;

    public static void main(String args[]) throws IOException {
        Client c = new Client("localhost", 7272, "08391BC4-8717-499A-8720-902EC828EAEF", "archive-staging-customer123");
        c.getKey("key1");
    }

    public Client(String hostname, int port, String token, String resource) {
        this.hostname = hostname;
        this.port = port;
        this.token = token;
        this.resource = resource;
    }

    private final OkHttpClient client = getUnsafeOkHttpClient();
    private final Gson gson = new Gson();


    public void getKey(String keyId) throws IOException {
        final String INIT_CALL = String.format("https://%s:%d/restapi/json/v1/resources/resourcename/%s/accounts/accountname/%s?AUTHTOKEN=%s",
                hostname, port, resource, keyId, token);
        Response response = call(INIT_CALL);
        InfoOperationWrapper infoResult = gson.fromJson(response.body().charStream(), InfoOperationWrapper.class);
        Integer resourceId = infoResult.operation.Details.get("RESOURCEID");
        Integer accountId = infoResult.operation.Details.get("ACCOUNTID");
        downloadByAccountId(resourceId, accountId);
    }
    
    private Key downloadByAccountId(Integer resourceId, Integer accountId) throws IOException {
        final String DOWNLOAD_CALL = String.format("https://%s:%d/restapi/json/v1/resources/%d/accounts/%d/downloadfile?AUTHTOKEN=%s", 
                hostname, port, resourceId, accountId, token);
        Response response = call(DOWNLOAD_CALL);
        Key key = gson.fromJson(response.body().charStream(), Key.class);
        return key;
    }

    private Response call(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .build();
        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
        return response;
    }
    
    

    static class InfoOperationWrapper {
        InfoOperation operation;
    }

    static class InfoOperation {
        String name;
        Map<String, String> result;
        Map<String, Integer> Details;
    }

    static class Key {
        String passphrase;
        String key;
    }

    private static OkHttpClient getUnsafeOkHttpClient() {
        try {
            // Create a trust manager that does not validate certificate chains 
            final TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                        }

                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[]{};
                        }
                    }
            };

            // Install the all-trusting trust manager 
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            // Create an ssl socket factory with our all-trusting manager 
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.sslSocketFactory(sslSocketFactory);
            builder.hostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            });

            OkHttpClient okHttpClient = builder.build();
            return okHttpClient;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
