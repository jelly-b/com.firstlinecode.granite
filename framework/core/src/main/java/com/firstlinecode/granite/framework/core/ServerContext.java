package com.firstlinecode.granite.framework.core;

import com.firstlinecode.granite.framework.core.adf.ApplicationComponentService;
import com.firstlinecode.granite.framework.core.adf.IApplicationComponentConfigurations;
import com.firstlinecode.granite.framework.core.adf.IApplicationComponentService;
import com.firstlinecode.granite.framework.core.config.IServerConfiguration;
import com.firstlinecode.granite.framework.core.repository.IRepository;

public class ServerContext implements IServerContext {
	private IServer server;
	private IRepository repository;
	private ApplicationComponentService appComponentService;
	
	public ServerContext(IServer server, IRepository repository,
			ApplicationComponentService appComponentService) {
		this.server = server;
		this.repository = repository;
		this.appComponentService = appComponentService;
	}
	
	@Override
	public IServer getServer() {
		return server;
	}
	
	@Override
	public IServerConfiguration getServerConfiguration() {
		return server.getConfiguration();
	}
	
	@Override
	public IRepository getRepository() {
		return repository;
	}
	
	@Override
	public IApplicationComponentService getApplicationComponentService() {
		return appComponentService;
	}

	@Override
	public IApplicationComponentConfigurations getApplicationComponentConfigurations() {
		return appComponentService.getApplicationComponentConfigurations();
	}
}