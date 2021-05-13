package io.xdag.rpc.modules;

import java.util.ArrayList;
import java.util.List;

public class ModuleDescription {
    private String name;
    private String version;
    private boolean enabled;

    private List<String> enabledMethods;
    private List<String> disabledMethods;

    public ModuleDescription(String name, String version, boolean enabled, List<String> enabledMethods, List<String> disabledMethods) {
        this.name = name;
        this.version = version;
        this.enabled = enabled;
        this.enabledMethods = enabledMethods == null ? new ArrayList<>() : enabledMethods;
        this.disabledMethods = disabledMethods == null ? new ArrayList<>() : disabledMethods;
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
