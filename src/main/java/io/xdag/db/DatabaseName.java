package io.xdag.db;


public enum DatabaseName {

    /**
     * Block index.
     */
    INDEX,

    /**
     * Block raw data.
     */
    BLOCK,

    /**
     * Account related data.
     */
    ACCOUNT,

    /**
     * Time related block.
     */
    TIME,

    /**
     * Orphan block index
     */
    ORPHANIND
}