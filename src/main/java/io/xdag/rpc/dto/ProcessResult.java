package io.xdag.rpc.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class ProcessResult {

    private int code; // success:0 failed:err code
    private List<String> result; // if success return tx hash, else return errMsg
    private String errMsg;

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public List<String> getResInfo() {
        return result;
    }

    public void setResInfo(List<String> result) {
        this.result = result;
    }
}