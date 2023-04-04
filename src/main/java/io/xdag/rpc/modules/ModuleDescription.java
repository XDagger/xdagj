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

package io.xdag.rpc.modules;

import java.util.List;

import com.google.common.collect.Lists;

public class ModuleDescription {

    private final String name;
    private final String version;
    private final boolean enabled;

    private final List<String> enabledMethods;
    private final List<String> disabledMethods;

    public ModuleDescription(String name, String version, boolean enabled, List<String> enabledMethods,
            List<String> disabledMethods) {
        this.name = name;
        this.version = version;
        this.enabled = enabled;
        this.enabledMethods = enabledMethods == null ? Lists.newArrayList() : enabledMethods;
        this.disabledMethods = disabledMethods == null ? Lists.newArrayList() : disabledMethods;
    }

    public String getName() {
        return this.name;
    }

    public String getVersion() {
        return this.version;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public List<String> getEnabledMethods() {
        return this.enabledMethods;
    }

    public List<String> getDisabledMethods() {
        return this.disabledMethods;
    }

    public boolean methodIsInModule(String methodName) {
        if (methodName == null) {
            return false;
        }

        if (!methodName.startsWith(this.name)) {
            return false;
        }

        if (methodName.length() == this.name.length()) {
            return false;
        }

        if (methodName.charAt(this.name.length()) != '_') {
            return false;
        }

        return true;
    }

    public boolean methodIsEnable(String methodName) {
        if (!this.isEnabled()) {
            return false;
        }

        if (!this.methodIsInModule(methodName)) {
            return false;
        }

        if (this.disabledMethods.contains(methodName)) {
            return false;
        }

        if (this.enabledMethods.isEmpty() && this.disabledMethods.isEmpty()) {
            return true;
        }

        if (this.enabledMethods.contains(methodName)) {
            return true;
        }

        if (!this.enabledMethods.isEmpty()) {
            return false;
        }

        return true;
    }
}
