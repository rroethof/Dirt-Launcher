package net.dirtcraft.dirtlauncher.Data;

import com.google.gson.*;
import net.cydhra.nidhogg.YggdrasilAgent;
import net.cydhra.nidhogg.YggdrasilClient;
import net.cydhra.nidhogg.data.AccountCredentials;
import net.cydhra.nidhogg.data.Session;
import net.dirtcraft.dirtlauncher.Main;

import javax.xml.bind.DatatypeConverter;
import java.io.File;
import java.util.*;
import java.io.IOException;
import java.nio.file.Path;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.concurrent.CompletableFuture;

public final class Accounts {

    private Session selectedAccount;
    final private List<Session> altAccounts;
    private YggdrasilClient client = null;
    private final File accountDir;
    private volatile boolean isReady = false;
    private final String finalYggdrasilClientToken;


    public Accounts(Path launcherDirectory){
        boolean saveData = false;
        JsonObject accounts;
        accountDir = launcherDirectory.resolve("account.json").toFile();
        try (FileReader reader = new FileReader(accountDir)) {
            JsonParser parser = new JsonParser();
            accounts = parser.parse(reader).getAsJsonObject();
        } catch (IOException e){
            accounts = null;
        }

        String yggdrasilClientToken;
        try {
            if (accounts != null && accounts.has("client token")) {
                yggdrasilClientToken = accounts.get("client token").getAsString();
            } else {
                throw new JsonParseException("No Selected Account");
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            byte[] bytes = new byte[16];
            new Random().nextBytes(bytes);
            yggdrasilClientToken = DatatypeConverter.printHexBinary(bytes);
            saveData = true;
        }
        finalYggdrasilClientToken = yggdrasilClientToken;
        CompletableFuture.runAsync(()->{
            client = new YggdrasilClient(finalYggdrasilClientToken);
            isReady = true;
        });

        try {
            if (accounts != null && accounts.has("selected account")) {
                selectedAccount = jsonToSession(accounts.getAsJsonObject("selected account"));
            } else {
                throw new JsonParseException("No Selected Account");
            }
        } catch (JsonParseException e){
            System.out.println(e.getMessage());
            selectedAccount = null;
        }

        altAccounts = new ArrayList<>();
        if (accounts != null && accounts.has("alt account list")) {
            for (JsonElement entry : accounts.getAsJsonArray("alt account list")){
                final Session session = jsonToSession(entry.getAsJsonObject());
                if (session != null) altAccounts.add(session);
            }
        } else {
            System.out.println("No Alternate Account List detected");
        }

        if (saveData) saveData();
    }

    private void saveData(){
        final JsonObject accounts = new JsonObject();
        final JsonObject selected = sessionToJson(selectedAccount);
        final JsonArray alts = new JsonArray();
        for (Session session : altAccounts){
            alts.add(sessionToJson(session));
        }
        accounts.add("selected account", selected);
        accounts.add("alt account list", alts);
        accounts.addProperty("client token", finalYggdrasilClientToken);
        try (FileWriter writer = new FileWriter(accountDir)) {
            writer.write(accounts.toString());
        } catch (IOException e) {
            Main.getLogger().warn(e);
        }
    }

    private JsonObject sessionToJson(Session session){
        if (session == null) return new JsonObject();
        final JsonObject json = new JsonObject();
        json.addProperty("UUID", session.getId());
        json.addProperty("Alias", session.getAlias());
        json.addProperty("AccessToken", session.getAccessToken());
        json.addProperty("ClientToken", session.getClientToken());
        return json;
    }

    private Session jsonToSession(JsonObject jsonObject){
        try {
            if (!jsonObject.has("UUID")) throw new JsonParseException("No UUID");
            if (!jsonObject.has("Alias")) throw new JsonParseException("No Alias");
            if (!jsonObject.has("AccessToken")) throw new JsonParseException("No Access Token");
            if (!jsonObject.has("ClientToken")) throw new JsonParseException("No Client Token");
            return new Session(
                    jsonObject.get("UUID").getAsString(),
                    jsonObject.get("Alias").getAsString(),
                    jsonObject.get("AccessToken").getAsString(),
                    jsonObject.get("ClientToken").getAsString()
            );
        } catch (JsonParseException e){
            System.out.println(e.getMessage());
            return null;
        }
    }

    private boolean isValid(Session session) {
        if (session != null) {
            try {
                if (client.validate(session)) return true;
            } catch (Exception ex) {
                System.out.println("Session not valid, Attempting to refresh it!");
                try {
                    client.refresh(session);
                    return true;
                } catch (Exception e) {
                    System.out.println(ex.getMessage());
                    System.out.println(e.getMessage());
                }
            }
        }
        return false;
    }

    public void setSelectedAccount(Session newAccount) {
        if (isValid(newAccount)){
            if (selectedAccount != null) altAccounts.add(selectedAccount);
            altAccounts.remove(newAccount);
            selectedAccount = newAccount;
            saveData();
        } else clearSelectedAccount();
    }

    public void setSelectedAccount(AccountCredentials credentials){
        selectedAccount = client.login(credentials, YggdrasilAgent.MINECRAFT);
        saveData();
    }

    public void clearSelectedAccount(){
        if (selectedAccount != null) altAccounts.add(selectedAccount);
        selectedAccount = null;
        saveData();
    }

    public List<Session> getAltAccounts() {
        altAccounts.removeIf(session -> !isValid(session));
        return altAccounts;
    }

    public boolean hasSelectedAccount(){
        return selectedAccount != null;
    }

    public Optional<Session> getValidSelectedAccount() {
        if (selectedAccount == null) return Optional.empty();
        if (!isValid(selectedAccount)) return Optional.empty();
        else return Optional.of(selectedAccount);
    }

    public Optional<Session> getSelectedAccount() {
        if (selectedAccount == null) return Optional.empty();
        else return Optional.of(selectedAccount);
    }

    private YggdrasilClient getClient() {
        return client;
    }

    public boolean isReady() {
        return isReady;
    }

    private class Account{
        private Session session;
        Account(Session session){
            this.session = session;
        }

        Account(JsonObject jsonObject) {
            if (!jsonObject.has("UUID")) throw new JsonParseException("No UUID");
            if (!jsonObject.has("Alias")) throw new JsonParseException("No Alias");
            if (!jsonObject.has("AccessToken")) throw new JsonParseException("No Access Token");
            if (!jsonObject.has("ClientToken")) throw new JsonParseException("No Client Token");
            this.session = new Session(
                    jsonObject.get("UUID").getAsString(),
                    jsonObject.get("Alias").getAsString(),
                    jsonObject.get("AccessToken").getAsString(),
                    jsonObject.get("ClientToken").getAsString()
            );
        }

        public Account(String uuid, String name, String accessToken, String clientToken){
            this.session = new Session(uuid, name, accessToken, clientToken);
        }

        JsonObject getSerialized(){
            if (session == null) return new JsonObject();
            final JsonObject json = new JsonObject();
            json.addProperty("UUID", session.getId());
            json.addProperty("Alias", session.getAlias());
            json.addProperty("AccessToken", session.getAccessToken());
            json.addProperty("ClientToken", session.getClientToken());
            return json;
        }

        Session getSession() {
            return session;
        }

        public String getAlias(){
            return session.getAlias();
        }

        public String getAccessToken(){
            return session.getAccessToken();
        }

        public String getId(){
            return session.getId();
        }

        public String getClientToken(){
            return session.getId();
        }

        public UUID getUuid(){
            return session.getUuid();
        }

        public boolean isValid(){
            try {
                client.validate(session);
                return true;
            } catch (Exception e){
                try {
                    client.refresh(session);
                    return true;
                } catch (Exception refreshException){
                    throw e;
                }
            }
        }
    }
}
