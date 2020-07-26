import com.lzp.cache.AutoDeleteMap;
import com.lzp.protocol.CommandDTO;
import com.lzp.service.ConsumeMessageService;
import com.lzp.util.SeriallUtil;

import java.time.Instant;
import java.util.*;

/**
 * Description:
 *
 * @author: Lu ZePing
 * @date: 2020/7/15 12:26
 */
public class Test {
    public static void main(String[] args) {
        List<String> list = new ArrayList<>();
        list.add("1");
        list.add("2");
        Set set = new HashSet(list);
        AutoDeleteMap map = new AutoDeleteMap(1000);
        Map map1 = new HashMap();
        map1.put("2","#");
        map1.put("4","5");
        map.put("1","2");
        map.put("2",list);
        map.put("3",set);
        map.put("4",map1);

        String s=SeriallUtil.CacheToString(map);
        System.out.println(s);
        AutoDeleteMap map2 = SeriallUtil.StringToLruCache(s);
        System.out.println((map2));
    }
}
