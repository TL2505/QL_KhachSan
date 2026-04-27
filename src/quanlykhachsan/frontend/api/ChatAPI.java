package quanlykhachsan.frontend.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import quanlykhachsan.backend.interaction.Message;
import quanlykhachsan.frontend.utils.HttpUtil;
import quanlykhachsan.frontend.utils.JsonUtil;

import java.util.ArrayList;
import java.util.List;

public class ChatAPI {
    private static final Gson gson = JsonUtil.getGson();

    public static void sendMessage(Message msg) {
        try {
            HttpUtil.sendPost("/chat/send", gson.toJson(msg));
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Send message failed!", e);
        }
    }

    public static List<Message> getHistory(int u1, int u2) {
        try {
            String json = HttpUtil.sendGet("/chat/history?u1=" + u1 + "&u2=" + u2);
            JsonObject obj = gson.fromJson(json, JsonObject.class);
            if ("success".equals(obj.get("status").getAsString())) {
                JsonArray data = obj.getAsJsonArray("data");
                return gson.fromJson(data, new TypeToken<List<Message>>(){}.getType());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    public static List<Message> getInbox(int staffId) {
        try {
            String json = HttpUtil.sendGet("/chat/inbox?staffId=" + staffId);
            JsonObject obj = gson.fromJson(json, JsonObject.class);
            if ("success".equals(obj.get("status").getAsString())) {
                JsonArray data = obj.getAsJsonArray("data");
                return gson.fromJson(data, new TypeToken<List<Message>>(){}.getType());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    public static void markAsRead(int userId) {
        try {
            JsonObject req = new JsonObject();
            req.addProperty("readerId", userId);
            HttpUtil.sendPost("/chat/read", gson.toJson(req));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
