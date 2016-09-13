import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.net.ssl.*;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.*;

public class Client {
    private final String hostname;
    private final int port;
    private final String token;
    private final String resource;

    private final OkHttpClient client = getUnsafeOkHttpClient();
    private final Gson gson = new Gson();
    private final Map<String, Key> keyMap = new HashMap<String, Key>();

    private final String LIST_CALL_STRING = "https://%s:%d/restapi/json/v1/resources?AUTHTOKEN=%s";
    private final String ACCOUNT_LIST_CALL_STRING = "https://%s:%d/restapi/json/v1/resources/%d/accounts?AUTHTOKEN=%s";
    private final String INFO_CALL_STRING = "https://%s:%d/restapi/json/v1/resources/resourcename/%s/accounts/accountname/%s?AUTHTOKEN=%s";
    private final String DOWNLOAD_CALL_STRING = "https://%s:%d/restapi/json/v1/resources/%d/accounts/%d/downloadfile?AUTHTOKEN=%s";

    public Client(String hostname, int port, String token, String resource) {
        this.hostname = hostname;
        this.port = port;
        this.token = token;
        this.resource = resource;
    }

    public static void main(String args[]) throws IOException {
        Client c = new Client(args[0], Integer.parseInt(args[1]), args[2], args[3]);
        for(String i : c.loadKeyIds()) System.out.println(i);
    }

    /**
     * Return a list of all resource names for this API User. 
     * @return A HashSet
     * @throws IOException
     */
    public List<Map<String, String>> listResources() throws IOException {
        final String LIST_RESOURCES_CALL = String.format(LIST_CALL_STRING,
                hostname, port, token);
        Response response = call(LIST_RESOURCES_CALL);
        ListResourceOperationWrapper listResult = gson.fromJson(response.body().charStream(), ListResourceOperationWrapper.class);

        if (listResult!=null && listResult.operation != null) {
            if(listResult.operation.Details != null) {
                return listResult.operation.Details;
            }
            else if(listResult.operation.result != null)
            {
                throw new IOException("Response from PMP server listing all resources returned: " + listResult.operation.result.get(1));
            }
            else {
                throw new IOException("Response from PMP server listing all resources returned: null");
            }
        }
        else {
            throw new IOException("Response from PMP server listing all resources returned: null");
        }
    }

    /**
     * Return a list of all account names for the specified resource. 
     * Each account name represents a Key ID that we want to store from PMP
     * @return A HashSet
     * @throws IOException
     */
    public HashSet<String> loadKeyIds() throws IOException {
        List<Map<String, String>> resourceMap = listResources();
        Integer resourceId = null;
        
        for(Map<String, String> resourceDetail : resourceMap) {
            if(resourceDetail.get("RESOURCE NAME").equals(resource)) {
                resourceId = Integer.valueOf(resourceDetail.get("RESOURCE ID"));
                break;
            }
        }
        if(resourceId == null) {
            throw new FileNotFoundException(String.format("Could not find resource: %s", resource));
        }
        
        final String INFO_CALL = String.format(ACCOUNT_LIST_CALL_STRING,
                hostname, port, resourceId, token);
        Response response = call(INFO_CALL);
        ListAccountOperationWrapper listResult = gson.fromJson(response.body().charStream(), ListAccountOperationWrapper.class);
        HashSet<String> keyResources = new HashSet<String>();

        if (listResult!=null && listResult.operation != null && listResult.operation.Details != null) {
            List<Map<String, String>> accountDetails = (List<Map<String, String>>)listResult.operation.Details.get("ACCOUNT LIST");
            for(Map<String, String> accountDetail : accountDetails) {
                keyResources.add(accountDetail.get("ACCOUNT NAME"));
            }
        }
        else
            throw new IOException("Response from PMP server listing all account names returned null");
        return keyResources;
    }

    public Key getKey(String keyId) throws IOException {
        Integer resourceId=null;
        Integer accountId=null;
        Key foundKey = keyMap.get(keyId);
        if (foundKey == null) {
            final String INFO_CALL = String.format(INFO_CALL_STRING,
                    hostname, port, resource, keyId, token);
            Response response = call(INFO_CALL);
            InfoOperationWrapper infoResult = gson.fromJson(response.body().charStream(), InfoOperationWrapper.class);

            if (infoResult!=null) {
                resourceId = infoResult.operation.Details.get("RESOURCEID");
                accountId = infoResult.operation.Details.get("ACCOUNTID");
            }
            else
                throw new IOException("Response from PMP server for keyID: "+keyId+" returned null infoResult");

            foundKey = downloadByAccountId(resourceId, accountId);
            keyMap.put(keyId, foundKey);
        }
        return foundKey;
    }

    private Key downloadByAccountId(Integer resourceId, Integer accountId) throws IOException {
        final String DOWNLOAD_CALL = String.format(DOWNLOAD_CALL_STRING,
                hostname, port, resourceId, accountId, token);
        Response response = call(DOWNLOAD_CALL);
        return gson.fromJson(response.body().charStream(), Key.class);
    }

    private Response call(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .build();
        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
        return response;
    }


    private static class InfoOperationWrapper {
        InfoOperation operation;
    }

    private static class InfoOperation {
        String name;
        Map<String, String> result;
        Map<String, Integer> Details;
    }
    
    private static class ListResourceOperationWrapper {
        ListResourceOperation operation;
    }
    
    
    private static class ListResourceOperation {
        String name;
        Map<String, String> result;
        Integer totalRows;
        List<Map<String, String>> Details;    
    }

    private static class ListAccountOperationWrapper {
        ListAccountOperation operation;
    }


    private static class ListAccountOperation {
        String name;
        Map<String, String> result;
        Map Details;
    }

    /* 
        This is to deal with a bad SSL certificate during testing.
     */
    private static OkHttpClient getUnsafeOkHttpClient() {
        try {
            // Create a trust manager that does not validate certificate chains 
            final TrustManager[] trustAllCerts = new TrustManager[]{
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

    public class Key {
        String passphrase;
        String key;
    }
}