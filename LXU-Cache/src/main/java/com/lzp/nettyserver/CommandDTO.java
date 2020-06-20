package com.lzp.nettyserver;

import java.io.Serializable;

/**
 * Description:
 *
 * @author: Lu ZePing
 * @date: 2020/6/18 19:03
 */
class CommandDTO implements Serializable {
    String type;
    Object key;
    Object value;

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
}