package quanlykhachsan.backend.ai;

import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.net.ssl.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AiMutualTlsTest {
    @TempDir Path dir;
    String password=UUID.randomUUID().toString();
    void keytool(String... args)throws Exception{
        var command=new ArrayList<String>();command.add(Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"keytool.exe":"keytool").toString());
        command.addAll(List.of(args));command.addAll(List.of("-storepass",password,"-noprompt"));
        var process=new ProcessBuilder(command).redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();assertEquals(0,process.waitFor(),"Disposable PKI generation failed");
    }
    SSLContext context(String identity,String trust)throws Exception{
        KeyManager[] keys=null;
        if(identity!=null){var store=KeyStore.getInstance("PKCS12");try(var in=Files.newInputStream(dir.resolve(identity))){store.load(in,password.toCharArray());}
            var km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(store,password.toCharArray());keys=km.getKeyManagers();}
        var store=KeyStore.getInstance("PKCS12");try(var in=Files.newInputStream(dir.resolve(trust))){store.load(in,password.toCharArray());}
        var tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());tm.init(store);
        var tls=SSLContext.getInstance("TLSv1.3");tls.init(keys,tm.getTrustManagers(),null);return tls;
    }
    @Test void authenticatesBothPeersAndRejectsMissingIdentityAndWrongHostname()throws Exception{
        for(String peer:List.of("server","client")){
            keytool("-genkeypair","-alias",peer,"-keyalg","EC","-groupname","secp256r1","-validity","1","-dname","CN="+peer,
                "-ext",peer.equals("server")?"SAN=dns:localhost":"EKU=clientAuth","-keystore",dir.resolve(peer+".p12").toString(),"-storetype","PKCS12");
            keytool("-exportcert","-alias",peer,"-keystore",dir.resolve(peer+".p12").toString(),"-file",dir.resolve(peer+".cer").toString());
        }
        keytool("-importcert","-alias","server","-file",dir.resolve("server.cer").toString(),"-keystore",dir.resolve("client-trust.p12").toString(),"-storetype","PKCS12");
        keytool("-importcert","-alias","client","-file",dir.resolve("client.cer").toString(),"-keystore",dir.resolve("server-trust.p12").toString(),"-storetype","PKCS12");
        var tls=context("server.p12","server-trust.p12");
        var server=HttpsServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.setHttpsConfigurator(new HttpsConfigurator(tls){public void configure(HttpsParameters p){var params=tls.getDefaultSSLParameters();params.setNeedClientAuth(true);p.setSSLParameters(params);}});
        server.createContext("/ocr",e->{byte[] data="{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);e.sendResponseHeaders(200,data.length);try(var out=e.getResponseBody()){out.write(data);}});server.start();
        try{
            String port=":"+server.getAddress().getPort();
            assertEquals("{}",new AiServiceClient(URI.create("https://localhost"+port),context("client.p12","client-trust.p12")).post("/ocr","{}"));
            assertThrows(java.io.IOException.class,()->new AiServiceClient(URI.create("https://localhost"+port),context(null,"client-trust.p12")).post("/ocr","{}"));
            assertThrows(java.io.IOException.class,()->new AiServiceClient(URI.create("https://127.0.0.1"+port),context("client.p12","client-trust.p12")).post("/ocr","{}"));
        }finally{server.stop(0);}
    }
}
