package quanlykhachsan.backend.invoice;
import java.math.BigDecimal;
public final class VietnameseMoneyWords {
    private static final String[] DIGITS={"không","một","hai","ba","bốn","năm","sáu","bảy","tám","chín"};
    private static final String[] SCALE={""," nghìn"," triệu"," tỷ"," nghìn tỷ"," triệu tỷ"};
    public static String format(BigDecimal amount) {
        if(amount.signum()<0 || amount.compareTo(new BigDecimal("1000000000000000000"))>=0)throw new IllegalArgumentException("Unsupported amount");
        long n=amount.longValue();String words="";boolean lower=false;
        for(int i=0;n>0;i++,n/=1000){int group=(int)(n%1000);if(group!=0){words=group(group,lower && n>=1000)+SCALE[i]+(words.isEmpty()?"":" "+words);lower=true;}}
        if(words.isEmpty())words="không";
        words=Character.toUpperCase(words.charAt(0))+words.substring(1)+" đồng";
        int fractional=amount.remainder(BigDecimal.ONE).movePointRight(2).intValueExact();
        return words+(fractional==0?" chẵn":" và "+group(fractional,false)+" phần trăm đồng");
    }
    private static String group(int n,boolean full) {
        int h=n/100,t=n/10%10,u=n%10;String s=h>0 || full?DIGITS[h]+" trăm":"";
        if(t>=2)s+=(s.isEmpty()?"":" ")+DIGITS[t]+" mươi";
        else if(t==1)s+=(s.isEmpty()?"":" ")+"mười";
        else if(u>0 && !s.isEmpty())s+=" lẻ";
        if(u>0)s+=(s.isEmpty()?"":" ")+(u==1 && t>=2?"mốt":u==5 && t>0?"lăm":DIGITS[u]);
        return s;
    }
}
