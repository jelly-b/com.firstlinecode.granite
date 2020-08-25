package com.firstlinecode.granite.framework.core.routing;

import com.firstlinecode.granite.framework.core.integration.IMessageChannel;

public interface MessageChannelAware {
    void setMessageChannel(IMessageChannel messageChannel);
    String getMessageChannelId();
}
