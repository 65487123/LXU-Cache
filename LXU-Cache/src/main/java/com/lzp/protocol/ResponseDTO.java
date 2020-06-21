package com.lzp.protocol;

/**
 * Description:
 *
 * @author: Lu ZePing
 * @date: 2020/6/20 16:32
 */
public class ResponseDTO extends AbstractDTO {
    private String type;

    private Object result;

    public ResponseDTO(String type, Object result) {
        this.type = type;
        this.result = result;
    }

    public String getType() {
        return type;
    }

    public Object getResult() {
        return result;
    }
}
