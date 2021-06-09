package com.firstlinecode.granite.framework.core.console;

import java.io.PrintStream;

import com.firstlinecode.granite.framework.core.IServerContext;

public interface IConsoleSystem {
	IServerContext getServerContext();
	PrintStream getOutputStream();
	ICommandProcessor[] getCommandProcessors();
	
	void printBlankLine();
	void printMessage(String message);
	void printMessageLine(String message);
	
	void close();
}
