package io.xdag.config;

import io.xdag.config.spec.AdminSpec;
import io.xdag.config.spec.PoolSpec;
import io.xdag.config.spec.NodeSpec;
import io.xdag.config.spec.WalletSpec;
import io.xdag.core.XdagField;

/**
 * The Xdag blockchain configurations.
 */
public interface Config {

    /**
     * Config File Name.
     */
    String getConfigName();

    /**
     * Pool Specification.
     */
    PoolSpec getPoolSpec();

    /**
     * Node Specification.
     */
    NodeSpec getNodeSpec();

    /**
     * Admin Specification.
     */
    AdminSpec getAdminSpec();

    /**
     * Wallet Specification.
     */
    WalletSpec getWalletSpec();

    long getMainStartAmount();

    long getXdagEra();

    long getApolloForkHeight();

    long getApolloForkAmount();

    XdagField.FieldType getXdagFieldHeader();

    void changePara(String[] args);
    void setDir();
    void initKeys() throws Exception;

}
