package quanlykhachsan.backend.ai;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Conservative deterministic safety layer. Model sentiment/semantic accuracy remains a separate gate. */
public final class EscalationPolicy {
    public record Decision(String category,boolean hard,String language){}
    private static final Pattern EMERGENCY=Pattern.compile("\\b(emergency|fire|doctor|police|medical|hospital|legal|stolen|broken)\\b|cap cuu|hoa hoan|bac si|canh sat|bi hong|mat trom|緊急|火事|医者|警察|病院|故障|紧急|火灾|医生|医院|坏了");
    private static final Pattern HARD=Pattern.compile("\\b(refund|manager|complain)\\b|hoan tien|quan ly|khieu nai|\\b(cancel|huy)\\b.{0,30}\\b(my|our|booking|reservation|dat phong|phong cua)\\b|\\b(i want|i need|please|toi muon|toi can).{0,25}\\b(cancel|huy)\\b");
    private static final Pattern POLICY=Pattern.compile("(cancel|cancellation|huy).{0,30}(policy|policies|rule|chinh sach|quy dinh)|(policy|chinh sach|quy dinh).{0,30}(cancel|huy)");
    private static final Pattern NEGATIVE=Pattern.compile("terrible|furious|unacceptable|angry|tuc gian|that vong|khong the chap nhan");
    private static final Pattern FINANCIAL=Pattern.compile("\\b(price|rates?|availability|compensation|upgrade|currency|discount|freebie)\\b|bao nhieu tien|gia phong|con phong|boi thuong|mien phi");
    private static final class State {String fingerprint;boolean monitor;long updated;}
    private final Map<String,State> states=new HashMap<>();
    /** A failed transport attempt must not turn an explicit retry into a repeated complaint. */
    public synchronized void forget(String session){states.remove(session);}
    public synchronized Decision evaluate(String session,String message,long now){
        String language=language(message),text=fold(message),fingerprint=AiInteractionDao.hash(text);
        states.entrySet().removeIf(entry->now-entry.getValue().updated>3_600_000);
        if(!states.containsKey(session)&&states.size()>=10_000)return new Decision("STAFF_REQUEST",true,language);
        State state=states.computeIfAbsent(session,key->new State());boolean repeated=fingerprint.equals(state.fingerprint);
        state.fingerprint=fingerprint;state.updated=now;
        if(EMERGENCY.matcher(text).find())return new Decision("EMERGENCY",true,language);
        if(repeated||NEGATIVE.matcher(text).find()||HARD.matcher(text).find())return new Decision("STAFF_REQUEST",true,language);
        if(text.matches(".*(not happy|not satisfied|khong hai long).*")){
            if(state.monitor)return new Decision("STAFF_REQUEST",true,language);
            state.monitor=true;return new Decision("MONITOR",false,language);
        }
        if(language.equals("unsupported"))return new Decision("UNSUPPORTED_LANGUAGE",true,language);
        if(POLICY.matcher(text).find()){state.monitor=true;return new Decision("MONITOR",false,language);}
        if(text.matches(".*\\b(cancel|huy)\\b.*")||FINANCIAL.matcher(text).find())return new Decision("POLICY_REQUEST",true,language);
        return new Decision("ANSWER",false,language);
    }
    public static String fold(String value){return Normalizer.normalize(value,Normalizer.Form.NFKD).replaceAll("\\p{M}","").replace('đ','d').replace('Đ','D').toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").trim();}
    public static String language(String value){
        if(value.matches("(?s).*[\\p{IsHiragana}\\p{IsKatakana}].*"))return "ja";
        if(value.matches("(?s).*\\p{IsHan}.*"))return "zh";
        if(value.matches("(?s).*[\\p{IsCyrillic}\\p{IsArabic}\\p{IsThai}].*"))return "unsupported";
        String text=fold(value);
        if(text.matches(".*\\b(bonjour|hola|bonjournee|gracias|danke)\\b.*"))return "unsupported";
        return !Normalizer.normalize(value,Normalizer.Form.NFKD).equals(value)||text.matches(".*\\b(toi|xin|chao|phong|khach|quy dinh|chinh sach)\\b.*")?"vi":"en";
    }
    public static String fallback(String language){return switch(language){case "vi"->"Tôi cần xác minh thông tin với đội ngũ lễ tân. Nhân viên sẽ hỗ trợ bạn.";case "ja"->"フロントデスクに確認いたします。";case "zh"->"我需要向前台核实此信息。";default->"Let me double-check with the Front Desk team.";};}
}
