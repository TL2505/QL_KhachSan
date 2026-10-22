package quanlykhachsan.backend.utils;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.IOException;

/** Docker secret file support; values and secret paths are never logged. */
public final class SecretConfiguration {
    private SecretConfiguration(){}
    public static String required(String name){
        String value=System.getenv(name),file=System.getenv(name+"_FILE");
        if(value!=null&&!value.isBlank()&&file!=null&&!file.isBlank())throw new IllegalStateException("Configure only one source for "+name);
        if(file!=null&&!file.isBlank()){
            try{value=Files.readString(Path.of(file),StandardCharsets.UTF_8).strip();}
            catch(IOException e){throw new IllegalStateException("Required secret file is unavailable");}
        }
        if(value==null||value.isBlank()){
            throw new IllegalStateException(name+" is required");
        }
        return value;
    }

    public static byte[] key256(String name) {
        byte[] bytes;
        try { bytes = java.util.Base64.getDecoder().decode(required(name)); }
        catch (IllegalArgumentException e) { throw new IllegalStateException(name + " must be Base64 encoded"); }
        if (bytes.length != 32) {
            java.util.Arrays.fill(bytes, (byte) 0);
            throw new IllegalStateException(name + " must decode to exactly 32 bytes");
        }
        return bytes;
    }
}
