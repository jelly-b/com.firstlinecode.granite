package com.firstlinecode.granite.framework.core.pipes.stream;

import com.firstlinecode.granite.framework.core.connection.IConnectionManagerAware;
import com.firstlinecode.granite.framework.core.pipes.IMessageReceiver;

public interface IDeliveryMessageReceiver extends IMessageReceiver, IConnectionManagerAware {}