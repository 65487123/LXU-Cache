package com.lzp.protocol;

/**
 * Description:
 *
 * @author: Lu ZePing
 * @date: 2020/6/18 19:03
 */
public class CommandDTO extends AbstractDTO {

    private String type;
    private Object key;
    private Object value;

    public CommandDTO(String type, Object key, Object value) {
        this.type = type;
        this.key = key;
        this.value = value;
    }

    public String getType() {
        return type;
    }

    @Override
    public String toString() {
        return "CommandDTO{" +
                "type='" + type + '\'' +
                ", key=" + key +
                ", value=" + value +
                '}';
    }

    public Object getKey() {
        return key;
    }

    public Object getValue() {
        return value;
    }
}