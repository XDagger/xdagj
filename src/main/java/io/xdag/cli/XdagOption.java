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

package io.xdag.cli;

/**
 * Enum class defining command line options for XDAG CLI
 */
public enum XdagOption {

    /**
     * Display help information
     */
    HELP("help"),

    /**
     * Display version information
     */
    VERSION("version"),

    /**
     * Account management operations
     */
    ACCOUNT("account"),

    /**
     * Change wallet password
     */
    CHANGE_PASSWORD("changepassword"),

    /**
     * Password option
     */
    PASSWORD("password"),

    /**
     * Export private key for an address
     */
    DUMP_PRIVATE_KEY("dumpprivatekey"),

    /**
     * Import a private key
     */
    IMPORT_PRIVATE_KEY("importprivatekey"),

    /**
     * Import wallet using mnemonic phrase
     */
    IMPORT_MNEMONIC("importmnemonic"),

    /**
     * Convert wallet from old format
     */
    CONVERT_OLD_WALLET("convertoldwallet"),

    /**
     * Enable snapshot functionality
     */
    ENABLE_SNAPSHOT("enablesnapshot"),

    /**
     * Create a new snapshot
     */
    MAKE_SNAPSHOT("makesnapshot");

    private final String name;

    /**
     * Constructor
     * @param s Option name string
     */
    XdagOption(String s) {
        name = s;
    }

    @Override
    public String toString() {
        return this.name;
    }

}
