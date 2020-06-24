package io.xdag.net.handler;

import io.xdag.Kernel;
import io.xdag.net.XdagChannel;
import io.xdag.net.XdagVersion;

/**
 * @ClassName XdagHandlerFactoryImpl
 * @Description
 * @Author punk
 * @Date 2020/4/22 16:22
 * @Version V1.0
 **/
public class XdagHandlerFactoryImpl implements XdagHandlerFactory {

	protected XdagChannel channel;
	protected Kernel kernel;

	public XdagHandlerFactoryImpl(Kernel kernel, XdagChannel channel) {
		this.kernel = kernel;
		this.channel = channel;
	}

	@Override
	public XdagHandler create(XdagVersion version) {
		switch (version) {
		case V03:
			return new Xdag03(kernel, channel);

		default:
			throw new IllegalArgumentException("Xdag " + version + " is not supported");
		}
	}
}
