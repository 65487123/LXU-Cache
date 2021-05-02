import com.lzp.lxucache.common.cache.AutoDeleteMap;

import java.io.*;
import java.time.Instant;
import java.util.*;

/**
 * Description:
 *
 * @author: Lu ZePing
 * @date: 2019/7/15 12:26
 */
public class Test {
    public static void main(String[] args) throws IOException, ClassNotFoundException {
        AutoDeleteMap<String, Object> map = new AutoDeleteMap(1000000);

        Map<String, Object> map1 = new HashMap<>();
        Set<String> set = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            set.add(String.valueOf(i));
        }
        List<String> list = new ArrayList<>(set);
        for (int i = 0; i < 100000; i++) {
            map.put(String.valueOf(i), String.valueOf(i));
            map1.put(String.valueOf(i), String.valueOf(i));
        }
        for (int i = 100000; i < 200000; i++) {
            map.put(String.valueOf(i), set);
        }
        for (int i = 200000; i < 300000; i++) {
            map.put(String.valueOf(i), list);
        }
        for (int i = 300000; i < 400000; i++) {
            map.put(String.valueOf(i), map1);
        }


        long now = Instant.now().toEpochMilli();
        File file = new File("./d.txt");
        //FileWriter outputStream = new FileWriter(file);
        OutputStream outputStream = new FileOutputStream(file);
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);
        objectOutputStream.writeObject(map);
        System.out.println(Instant.now().toEpochMilli()-now);
        now = Instant.now().toEpochMilli();
        ObjectInputStream objectInputStream = new ObjectInputStream(new FileInputStream(file));
        AutoDeleteMap map2 = (AutoDeleteMap) objectInputStream.readObject();
        System.out.println(Instant.now().toEpochMilli()-now);
        //outputStream.write(SeriallUtil.CacheToString(map));
        /*FileReader fileReader = new FileReader(file);*/
        /*StringBuilder stringBuilder = new StringBuilder(16384);
        int c;
        while ((c = fileReader.read())!=-1){
            stringBuilder.append((char)c);
        }
        Map autoDeleteMap = JSON.parseObject(stringBuilder.toString(),Map.class);
        System.out.println(Instant.now().toEpochMilli()-now);
        System.out.println(autoDeleteMap.get("444").equals(map.get("444")));*/
    }
}
