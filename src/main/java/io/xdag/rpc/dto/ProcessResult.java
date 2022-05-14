package io.xdag.rpc.dto;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class ProcessResult {

    private boolean res; // success:true failed:false
    private String resInfo; // if success return tx hash, else return errMsg

    public boolean getRes() {
        return res;
    }

    public void setRes(boolean res) {
        this.res = res;
    }

    public String getResInfo() {
        return resInfo;
    }

    public void setResInfo(String resInfo) {
        this.resInfo = resInfo;
    }
}