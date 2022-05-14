package io.xdag.rpc.dto;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class ProcessResult {

    private int code; // success:0 failed:err code
    private String resInfo; // if success return tx hash, else return errMsg

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getResInfo() {
        return resInfo;
    }

    public void setResInfo(String resInfo) {
        this.resInfo = resInfo;
    }
}