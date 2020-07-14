package io.xdag.net.handler;

import io.xdag.net.XdagVersion;

public interface XdagHandlerFactory {

    XdagHandler create(XdagVersion version);
}
