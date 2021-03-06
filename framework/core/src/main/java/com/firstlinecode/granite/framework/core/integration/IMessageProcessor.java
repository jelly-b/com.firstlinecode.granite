package com.firstlinecode.granite.framework.core.integration;

import com.firstlinecode.granite.framework.core.connection.IConnectionContext;

public interface IMessageProcessor {
	void process(IConnectionContext context, IMessage message);
}
