import com.lzp.protocol.CommandDTO;
import com.lzp.service.ConsumeMessageService;

import java.time.Instant;

/**
 * Description:
 *
 * @author: Lu ZePing
 * @date: 2020/7/15 12:26
 */
public class Test {
    public static void main(String[] args) {
        long now = Instant.now().toEpochMilli();
        for (int i = 0 ;i<10;i++){
            new ConsumeMessageService.Message(null,null);
        }
        System.out.println(Instant.now().toEpochMilli()-now);
    }
}
