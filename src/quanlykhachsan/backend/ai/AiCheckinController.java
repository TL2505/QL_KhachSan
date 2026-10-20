package quanlykhachsan.backend.ai;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.*;
import quanlykhachsan.backend.utils.*;

/** Staff-only vision gateway. Identity responses are transient and never logged. */
public final class AiCheckinController implements HttpHandler {
    @FunctionalInterface public interface VisionService {String post(String path,String json) throws IOException;}
    private final VisionService service;
    public AiCheckinController(VisionService service){this.service=service;}
    public AiCheckinController(){this(new ConfiguredService());}
    private static final class ConfiguredService implements VisionService {
        private volatile AiServiceClient client;
        public String post(String path,String json) throws IOException {
            if("true".equalsIgnoreCase(System.getenv("AI_SERVICE_MOCK"))){
                return path.equals("/ocr")?"{\"id_card\":\""+"0".repeat(12)+"\",\"full_name\":\"SYNTHETIC MOCK GUEST\",\"dob\":\"2000-01-01\",\"address\":\"SYNTHETIC MOCK\"}":
                        "{\"confidence\":0.0,\"match\":false,\"recommendation\":\"MANUAL_REVIEW\"}";
            }
            if(client==null)synchronized(this){
                if(client==null)try{client=AiServiceClient.fromEnvironment();}
                catch(GeneralSecurityException | IllegalStateException | IllegalArgumentException e){throw new IOException(AiServiceClient.MANUAL_VERIFICATION);}
            }
            return client.post(path,json);
        }
    }
    @Override public void handle(HttpExchange exchange) throws IOException {
        String path=exchange.getRequestURI().getPath();
        boolean profileOcr=path.equals("/api/v1/customer-portal/identity/scan");
        if(profileOcr){
            if(!"CUSTOMER".equalsIgnoreCase(String.valueOf(exchange.getAttribute("role")))){
                ApiResponseUtil.sendError(exchange,403,"ERR_403_FORBIDDEN","Customer access required");return;
            }
        }else if(!SecurityUtil.hasPermission(exchange,1,2))return;
        exchange.getResponseHeaders().set("Cache-Control","no-store");
        boolean ocr=profileOcr||path.equals("/api/v1/ai/id-ocr");
        if(!ocr&&!path.equals("/api/v1/ai/face-verify")){ApiResponseUtil.sendError(exchange,404,"ERR_404_NOT_FOUND","Route not found");return;}
        if(!exchange.getRequestMethod().equals("POST")){ApiResponseUtil.sendError(exchange,405,"ERR_405_METHOD_NOT_ALLOWED","Method not allowed");return;}
        byte[] bytes=exchange.getRequestBody().readNBytes(14_000_001);
        if(bytes.length>14_000_000){ApiResponseUtil.sendError(exchange,413,"ERR_413_PAYLOAD_TOO_LARGE","Image payload too large");return;}
        final JsonObject request;
        try{
            request=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
            if(ocr){alias(request,"image_base64","image");if(request.has("document_type")&&!"VIETNAMESE_CCCD".equals(request.get("document_type").getAsString()))throw new IllegalArgumentException();}
            else{alias(request,"id_photo_base64","id_photo");alias(request,"live_face_base64","live_face");}
            for(String field:ocr?List.of("image"):List.of("id_photo","live_face"))validateImage(request,field);
        }catch(JsonParseException | IllegalStateException | IllegalArgumentException e){ApiResponseUtil.sendError(exchange,400,"ERR_400_BAD_REQUEST","Valid JPEG/PNG images of at most 5 MB are required");return;}
        try{
            JsonObject upstream=new JsonObject();for(String field:ocr?List.of("image"):List.of("id_photo","live_face"))upstream.add(field,request.get(field));
            JsonObject response=JsonParser.parseString(service.post(ocr?"/ocr":"/face-match",upstream.toString())).getAsJsonObject();
            JsonObject result=new JsonObject();
            if(ocr){
                for(String field:List.of("id_card","full_name","dob","address")){
                    if(!response.has(field)||!response.get(field).isJsonPrimitive()||!response.getAsJsonPrimitive(field).isString())throw new IllegalArgumentException();
                    String value=response.get(field).getAsString();if(value.length()>255)throw new IllegalArgumentException();result.addProperty(field,value);
                }
                if(!result.get("id_card").getAsString().matches("[0-9]{12}"))throw new IllegalArgumentException();
                java.time.LocalDate.parse(result.get("dob").getAsString());
                result.add("id_number",result.get("id_card"));result.add("residence_address",result.get("address"));
                for(String optional:List.of("gender","nationality","origin_address","expiry_date","confidence_score"))result.add(optional,JsonNull.INSTANCE);
            }else{
                double confidence=response.get("confidence").getAsDouble();
                if(!Double.isFinite(confidence)||confidence < -1 || confidence>1)throw new IllegalArgumentException();
                double threshold=Double.parseDouble(System.getenv().getOrDefault("AI_FACE_MATCH_THRESHOLD","0.68"));
                if(!Double.isFinite(threshold)||threshold<0.5||threshold>0.9)throw new IllegalArgumentException();
                boolean matched=confidence>=threshold;result.addProperty("match",matched);result.addProperty("confidence",confidence);
                result.addProperty("recommendation",matched?"ACCEPT":"MANUAL_REVIEW");
                result.addProperty("is_match",matched);result.addProperty("similarity_score",confidence);result.addProperty("adaptive_threshold",threshold);
                result.addProperty("decision",matched?"ACCEPT":"MANUAL_REVIEW");result.addProperty("message",matched?"Face matched":AiServiceClient.MANUAL_VERIFICATION);
            }
            ApiResponseUtil.sendSuccess(exchange,200,result,Map.of("mock","true".equalsIgnoreCase(System.getenv("AI_SERVICE_MOCK"))));
        }catch(IOException | JsonParseException | IllegalStateException | IllegalArgumentException | java.time.DateTimeException | NullPointerException e){
            ApiResponseUtil.sendError(exchange,504,"ERR_504_GATEWAY_TIMEOUT",AiServiceClient.MANUAL_VERIFICATION);
        }
    }
    private static void alias(JsonObject o,String canonical,String alias){
        if(o.has(canonical)){if(o.has(alias)&&!o.get(canonical).equals(o.get(alias)))throw new IllegalArgumentException();o.add(alias,o.get(canonical));}
    }
    private static void validateImage(JsonObject object,String field){
        if(!object.has(field)||!object.get(field).isJsonPrimitive()||!object.getAsJsonPrimitive(field).isString())throw new IllegalArgumentException();
        String encoded=object.get(field).getAsString();if(encoded.length()>6_990_508)throw new IllegalArgumentException();
        byte[] image=Base64.getDecoder().decode(encoded);
        boolean jpeg=image.length>=3&&(image[0]&255)==255&&(image[1]&255)==216&&(image[2]&255)==255;
        byte[] png={(byte)137,80,78,71,13,10,26,10};
        if(image.length>5*1024*1024||!(jpeg||(image.length>=8&&Arrays.equals(Arrays.copyOf(image,8),png))))throw new IllegalArgumentException();
    }
}
