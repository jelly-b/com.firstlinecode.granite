package com.firstlinecode.granite.lite.integration;

import org.osgi.framework.BundleContext;

import com.firstlinecode.granite.framework.core.annotations.Component;
import com.firstlinecode.granite.framework.core.commons.osgi.IBundleContextAware;
import com.firstlinecode.granite.framework.core.commons.osgi.OsgiUtils;
import com.firstlinecode.granite.framework.core.config.IConfiguration;
import com.firstlinecode.granite.framework.core.config.IConfigurationAware;
import com.firstlinecode.granite.framework.core.integration.IMessage;
import com.firstlinecode.granite.framework.core.integration.IMessageChannel;
import com.firstlinecode.granite.framework.core.repository.IComponentIdAware;

@Component(value="lite.stream.2.parsing.message.channel",
		alias={
				"lite.parsing.2.processing.message.channel",
				"lite.any.2.event.message.channel",
				"lite.any.2.routing.message.channel",
				"lite.routing.2.stream.message.channel"
		}
)
public class MessageChannel implements IMessageChannel, IConfigurationAware,
		IBundleContextAware, IComponentIdAware {
	private static final String KEY_MESSAGE_CHANNEL_INTEGRATOR = "message.channel.integrator";
	
	protected String integratorServicePid;
	protected BundleContext bundleContext;
	
	private volatile IMessageIntegrator integrator;
	
	@Override
	public void send(IMessage message) {
		getIntegrator().put(message);
	}

	private IMessageIntegrator getIntegrator() {
		if (integrator != null)
			return integrator;
		
		return getIntegratorService();
	}

	protected synchronized IMessageIntegrator getIntegratorService() {
		if (integrator != null)
			return integrator;
		
		integrator = OsgiUtils.getServiceByPID(bundleContext, IMessageIntegrator.class, integratorServicePid);
		if (integrator == null)
			throw new RuntimeException(String.format("Can't get service by pid: %s.", integratorServicePid));
		
		return integrator;
	}

	@Override
	public void setConfiguration(IConfiguration configuration) {
		integratorServicePid = configuration.getString(KEY_MESSAGE_CHANNEL_INTEGRATOR);
	}
	
	@Override
	public void setComponentId(String componentId) {
		if (integratorServicePid == null) {
			integratorServicePid = componentId.substring(0, componentId.length() - 16) + ".integrator";
		}
	}

	@Override
	public void setBundleContext(BundleContext bundleContext) {
		this.bundleContext = bundleContext;
	}

}
