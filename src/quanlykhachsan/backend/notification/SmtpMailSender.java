package quanlykhachsan.backend.notification;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.net.ssl.*;

/** SMTP with verified TLS, bounded timeouts and conservative handling of ambiguous DATA delivery. */
public final class SmtpMailSender implements NotificationWorker.Sender {
    private final Map<String,String> env;
    public SmtpMailSender(){this(System.getenv());}
    public SmtpMailSender(Map<String,String> env){this.env=Map.copyOf(env);}
    public NotificationWorker.Outcome send(NotificationWorker.Mail mail) {
        boolean dataSent=false;
        try{
            String host=value("SMTP_HOST"),from=value("SMTP_FROM"),user=value("SMTP_USERNAME"),password=value("SMTP_APP_PASSWORD");
            if(host.isBlank()||user.isBlank()||password.isBlank())return new NotificationWorker.Outcome("RETRY","SMTP_NOT_CONFIGURED");
            if(!mailbox(from)||!mailbox(mail.recipient()))return new NotificationWorker.Outcome("FAILED","INVALID_EMAIL");
            int port=Integer.parseInt(env.getOrDefault("SMTP_PORT","465"));
            try(Socket plain=new Socket()){
                plain.connect(new InetSocketAddress(host,port),5000);plain.setSoTimeout(10000);
                SSLSocket tls;
                if(port==465)tls=tls(plain,host,port);
                else if(port==587){var in=reader(plain);var out=writer(plain);expect(in,220);command(out,in,"EHLO aurelia.local",250);command(out,in,"STARTTLS",220);tls=tls(plain,host,port);}
                else return new NotificationWorker.Outcome("FAILED","SMTP_TLS_PORT_REQUIRED");
                try(tls){var in=reader(tls);var out=writer(tls);
                    if(port==465)expect(in,220);command(out,in,"EHLO aurelia.local",250);command(out,in,"AUTH LOGIN",334);
                    command(out,in,Base64.getEncoder().encodeToString(user.getBytes(StandardCharsets.UTF_8)),334);command(out,in,Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8)),235);
                    command(out,in,"MAIL FROM:<"+from+">",250);command(out,in,"RCPT TO:<"+mail.recipient()+">",250);command(out,in,"DATA",354);
                    dataSent=true;out.write(message(from,mail));out.write("\r\n.\r\n");out.flush();expect(in,250);
                    // Acceptance of DATA is success even if QUIT or socket close fails later.
                    return new NotificationWorker.Outcome("SENT",null);
                }
            }
        }catch(Rejected r){return new NotificationWorker.Outcome(r.code>=500?"FAILED":"RETRY","SMTP_"+r.code);}
        catch(Exception ex){return new NotificationWorker.Outcome(dataSent?"UNKNOWN":"RETRY",dataSent?"SMTP_ACCEPTANCE_UNKNOWN":"SMTP_CONNECTION_FAILED");}
    }
    private static SSLSocket tls(Socket socket,String host,int port)throws IOException{
        var tls=(SSLSocket)((SSLSocketFactory)SSLSocketFactory.getDefault()).createSocket(socket,host,port,true);tls.setSoTimeout(10000);var parameters=tls.getSSLParameters();parameters.setEndpointIdentificationAlgorithm("HTTPS");tls.setSSLParameters(parameters);tls.startHandshake();return tls;
    }
    public static boolean mailbox(String address){return address!=null&&address.length()<=254&&address.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?\\.[A-Za-z]{2,63}");}
    static String message(String from,NotificationWorker.Mail mail){
        String subject=Base64.getEncoder().encodeToString(mail.subject().getBytes(StandardCharsets.UTF_8));String body=Base64.getMimeEncoder(76,"\r\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(mail.body().getBytes(StandardCharsets.UTF_8));
        return "From: <"+from+">\r\nTo: <"+mail.recipient()+">\r\nMessage-ID: <aurelia-notification-"+mail.id()+"@"+from.substring(from.indexOf('@')+1)+">\r\nSubject: =?UTF-8?B?"+subject+"?=\r\nMIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n"+body;
    }
    private String value(String key){return env.getOrDefault(key,"").trim();}
    private static BufferedReader reader(Socket s)throws IOException{return new BufferedReader(new InputStreamReader(s.getInputStream(),StandardCharsets.US_ASCII));}
    private static BufferedWriter writer(Socket s)throws IOException{return new BufferedWriter(new OutputStreamWriter(s.getOutputStream(),StandardCharsets.US_ASCII));}
    private static void command(BufferedWriter out,BufferedReader in,String line,int code)throws IOException{out.write(line+"\r\n");out.flush();expect(in,code);}
    private static void expect(BufferedReader in,int expected)throws IOException{
        for(int i=0;i<100;i++){String line=in.readLine();if(line==null||line.length()<3||line.length()>4096)throw new IOException("Invalid SMTP response");int code;try{code=Integer.parseInt(line.substring(0,3));}catch(NumberFormatException ex){throw new IOException("Invalid SMTP response");}if(line.length()>3&&line.charAt(3)=='-')continue;if(code!=expected)throw new Rejected(code);return;}throw new IOException("SMTP response too long");
    }
    private static final class Rejected extends IOException {final int code;Rejected(int code){this.code=code;}}
}
