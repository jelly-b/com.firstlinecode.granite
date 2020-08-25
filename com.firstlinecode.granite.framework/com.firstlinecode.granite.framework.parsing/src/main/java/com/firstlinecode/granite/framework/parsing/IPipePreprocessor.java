package com.firstlinecode.granite.framework.parsing;

import com.firstlinecode.granite.framework.core.session.ISession;

public interface IPipePreprocessor {
	String beforeParsing(String message, ISession session);
	Object afterParsing(Object object);
}
