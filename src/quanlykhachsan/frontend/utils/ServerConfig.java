package quanlykhachsan.frontend.utils;

/** One explicit connection configuration for desktop HTTP callers. */
public class ServerConfig {
    protected ServerConfig(){}
    public static String getBaseUrl(){
        String url=quanlykhachsan.frontend.api.ApiConfig.getBaseUrl();
        while(url.endsWith("/"))url=url.substring(0,url.length()-1);
        if(url.endsWith("/api/v1"))url=url.substring(0,url.length()-7);
        return url;
    }
    public static String getApiBaseUrl(){return getBaseUrl()+"/api/v1";}
    public static void resetCache(){}
}
