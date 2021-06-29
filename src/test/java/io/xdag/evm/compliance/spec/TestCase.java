/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020-2030 The XdagJ Developers
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.xdag.evm.compliance.spec;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TestCase {

    private Info info;
    private List<Object> callcreates = null;
    private Environment environment;
    private Exec exec;
    private String gas;
    private String logs;
    private String out;
    private Map<String, Account> post;
    private Map<String, Account> pre;

    @JsonProperty("_info")
    public Info getInfo() {
        return info;
    }

    @JsonProperty("_info")
    public void setInfo(Info info) {
        this.info = info;
    }

    public List<Object> getCallcreates() {
        return callcreates;
    }

    public void setCallcreates(List<Object> callcreates) {
        this.callcreates = callcreates;
    }

    @JsonProperty("env")
    public Environment getEnvironment() {
        return environment;
    }

    @JsonProperty("env")
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    public Exec getExec() {
        return exec;
    }

    public void setExec(Exec exec) {
        this.exec = exec;
    }

    public String getGas() {
        return gas;
    }

    public void setGas(String gas) {
        this.gas = gas;
    }

    public String getLogs() {
        return logs;
    }

    public void setLogs(String logs) {
        this.logs = logs;
    }

    public String getOut() {
        return out;
    }

    public void setOut(String out) {
        this.out = out;
    }

    public Map<String, Account> getPost() {
        return post;
    }

    public void setPost(Map<String, Account> post) {
        this.post = post;
    }

    public Map<String, Account> getPre() {
        return pre;
    }

    public void setPre(Map<String, Account> pre) {
        this.pre = pre;
    }
}
