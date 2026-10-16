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
        if(value==null||value.isBlank())throw new IllegalStateException(name+" is required");return value;
    }
}
