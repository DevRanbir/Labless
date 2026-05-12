package com.labless.gmail;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.json.GenericJson;
import com.google.api.client.util.Key;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;

public class GoogleAuthManager {
    private static final String APPLICATION_NAME = "Java GUI Mail Labeler";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";

    private static final List<String> SCOPES = Arrays.asList(
            GmailScopes.GMAIL_READONLY,
            GmailScopes.GMAIL_LABELS,
            GmailScopes.GMAIL_MODIFY,
            GmailScopes.GMAIL_COMPOSE,
            "openid",
            "https://www.googleapis.com/auth/userinfo.email",
            "https://www.googleapis.com/auth/userinfo.profile"
    );

    private static final String CREDENTIALS_FILE_PATH = "/credentials.json";

    public static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws Exception {
        InputStream in = GoogleAuthManager.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH + " (Ensure it is in src/main/resources)");
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));


        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(-1).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    public static Gmail createGmailClient() throws Exception {
        return createAuthorizedSession().gmail();
    }

    public static AuthorizedGmailSession createAuthorizedSession() throws Exception {
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        Credential credential = getCredentials(HTTP_TRANSPORT);
        Gmail gmail = new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
        return new AuthorizedGmailSession(gmail, credential, HTTP_TRANSPORT);
    }

    public static String fetchProfileImageUrl(Credential credential, NetHttpTransport transport) {
        if (credential == null || transport == null || credential.getAccessToken() == null) {
            return null;
        }
        try {
            HttpRequest request = transport.createRequestFactory()
                .buildGetRequest(new GenericUrl("https://www.googleapis.com/oauth2/v3/userinfo"));
            request.getHeaders().setAuthorization("Bearer " + credential.getAccessToken());
            request.setParser(new JsonObjectParser(JSON_FACTORY));
            UserInfoResponse response = request.execute().parseAs(UserInfoResponse.class);
            if (response == null || response.getPicture() == null || response.getPicture().isBlank()) {
                return null;
            }
            return response.getPicture();
        } catch (Exception ex) {
            return null;
        }
    }

    public record AuthorizedGmailSession(Gmail gmail, Credential credential, NetHttpTransport transport) {}

    public static final class UserInfoResponse extends GenericJson {
        @Key("picture")
        private String picture;

        public String getPicture() {
            return picture;
        }
    }
}
