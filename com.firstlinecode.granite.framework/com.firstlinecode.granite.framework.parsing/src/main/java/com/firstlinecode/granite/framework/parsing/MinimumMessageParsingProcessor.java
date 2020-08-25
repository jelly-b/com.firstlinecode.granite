package com.firstlinecode.granite.framework.parsing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.CopyOnWriteArrayList;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.firstlinecode.basalt.protocol.core.IError;
import com.firstlinecode.basalt.protocol.core.Protocol;
import com.firstlinecode.basalt.protocol.core.ProtocolChain;
import com.firstlinecode.basalt.protocol.core.ProtocolException;
import com.firstlinecode.basalt.protocol.core.stanza.Iq;
import com.firstlinecode.basalt.protocol.core.stanza.Stanza;
import com.firstlinecode.basalt.protocol.core.stanza.error.ServiceUnavailable;
import com.firstlinecode.basalt.protocol.core.stanza.error.StanzaError;
import com.firstlinecode.basalt.protocol.core.stream.error.InternalServerError;
import com.firstlinecode.basalt.protocol.core.stream.error.InvalidFrom;
import com.firstlinecode.basalt.protocol.core.stream.error.StreamError;
import com.firstlinecode.basalt.protocol.im.stanza.Message;
import com.firstlinecode.basalt.oxm.OxmService;
import com.firstlinecode.basalt.oxm.annotation.AnnotatedParserFactory;
import com.firstlinecode.basalt.oxm.convention.NamingConventionParserFactory;
import com.firstlinecode.basalt.oxm.parsers.SimpleObjectParserFactory;
import com.firstlinecode.basalt.oxm.parsers.core.stanza.IqParserFactory;
import com.firstlinecode.basalt.oxm.parsers.error.ErrorParserFactory;
import com.firstlinecode.basalt.oxm.parsers.error.StanzaErrorDetailsParserFactory;
import com.firstlinecode.basalt.oxm.parsing.FlawedProtocolObject;
import com.firstlinecode.basalt.oxm.parsing.IParserFactory;
import com.firstlinecode.basalt.oxm.parsing.IParsingFactory;
import com.firstlinecode.granite.framework.core.annotations.Component;
import com.firstlinecode.granite.framework.core.commons.osgi.IBundleContextAware;
import com.firstlinecode.granite.framework.core.commons.osgi.IContributionTracker;
import com.firstlinecode.granite.framework.core.commons.osgi.OsgiUtils;
import com.firstlinecode.granite.framework.core.commons.utils.CommonUtils;
import com.firstlinecode.granite.framework.core.config.IApplicationConfiguration;
import com.firstlinecode.granite.framework.core.config.IApplicationConfigurationAware;
import com.firstlinecode.granite.framework.core.config.IConfiguration;
import com.firstlinecode.granite.framework.core.config.IConfigurationAware;
import com.firstlinecode.granite.framework.core.connection.IConnectionContext;
import com.firstlinecode.granite.framework.core.integration.IMessage;
import com.firstlinecode.granite.framework.core.integration.IMessageProcessor;
import com.firstlinecode.granite.framework.core.supports.IApplicationComponentService;
import com.firstlinecode.granite.framework.core.repository.IInitializable;

@Component("minimum.message.parsing.processor")
public class MinimumMessageParsingProcessor implements IMessageProcessor, IBundleContextAware,
			IInitializable, IConfigurationAware, IApplicationConfigurationAware {
	private static final String CONFIGURATION_KEY_STANZA_ERROR_ATTACH_SENDER_MESSAGE = "stanza.error.attach.sender.message";

	private static final Logger logger = LoggerFactory.getLogger(MinimumMessageParsingProcessor.class);
	
	private static final String SEPARATOR_OF_PROTOCOLS = "->";
	private static final String SEPARATOR_OF_LOCALNAME_NAMESPACE = "|";
	private static final String VALUE_NULL = "null";
	private static final String PROPERTY_NAME_PROTOCOL_CHAIN = "protocol-chain";
	private static final String PROPERTY_NAME_TYPE = "type";
	private static final String PROPERTY_NAME_CLASS = "class";
	private static final String PROPERTY_NAME_PARSER_FACTORY = "parser-factory";
	private static final String PROPERTY_NAME_ANNOTATED_PARSER = "annotated-parser";
	private static final String TYPE_SIMPLE = "simple";
	private static final String TYPE_NAMING_CONVENTION = "naming-convention";
	private static final String TYPE_CUSTOM = "custom";
	private static final String TYPE_ANNOTATED = "annotated";
	private static final String SEPARATOR_PARSERS = ",";
	private static final String SEPARATOR_PREPROCESSORS = ",";
	
	protected IParsingFactory parsingFactory;
	protected BundleContext bundleContext;
	protected Map<Bundle, List<ProtocolChain>> bundleToProtocolChains;
	
	private boolean stanzaErrorAttachSenderMessage;
	
	private IApplicationConfiguration appConfiguration;
	
	private String parsersContributionKey;
	private String preprocessorsContributionKey;
	
	private Map<Bundle, List<IPipePreprocessor>> bundleToPreprocessors;
	private List<IPipePreprocessor> preprocessors;
	
	private IApplicationComponentService appComponentService;
	
	public MinimumMessageParsingProcessor(String parsersContributionKey, String preprocessorsContributionKey) {
		this.parsersContributionKey = parsersContributionKey;
		this.preprocessorsContributionKey = preprocessorsContributionKey;
		parsingFactory = OxmService.createParsingFactory();
		bundleToProtocolChains = new HashMap<>();
		bundleToPreprocessors = new HashMap<>();
		preprocessors = new CopyOnWriteArrayList<>();
	}
	
	@Override
	public void init() {
		registerPredefinedParsers();
		trackContributedParsers();
		trackContributedPreprocessors();
	}
	
	protected void registerPredefinedParsers() {
		parsingFactory.register(
				ProtocolChain.first(Iq.PROTOCOL),
				new IqParserFactory()
		);
		parsingFactory.register(
				ProtocolChain.first(StanzaError.PROTOCOL),
				new ErrorParserFactory<>(IError.Type.STANZA)
		);
		parsingFactory.register(
				ProtocolChain.first(StanzaError.PROTOCOL).next(
						StanzaError.PROTOCOL_ERROR_DEFINED_CONDITION),
				new StanzaErrorDetailsParserFactory()
		);
	}

	protected void trackContributedParsers() {
		OsgiUtils.trackContribution(bundleContext, parsersContributionKey, new ParsersTracker());
	}
	
	protected void trackContributedPreprocessors() {
		OsgiUtils.trackContribution(bundleContext, preprocessorsContributionKey, new PreprocessorsTracker());
	}
	
	private class PreprocessorsTracker implements IContributionTracker {

		@Override
		public void found(Bundle bundle, String contribution) throws Exception {
			StringTokenizer tokenizer = new StringTokenizer(contribution, SEPARATOR_PREPROCESSORS);
			
			List<IPipePreprocessor> preprocessors = new ArrayList<>();
			while (tokenizer.hasMoreTokens()) {
				String sPreprocessorClass = tokenizer.nextToken();
				Class<?> preprocessorClass = bundle.loadClass(sPreprocessorClass);
				
				if (!IPipePreprocessor.class.isAssignableFrom(preprocessorClass)) {
					throw new IllegalArgumentException(String.format("Pipe proprocessor %s must implement %s.",
							preprocessorClass.getName(), IPipePreprocessor.class.getName()));
				}
				
				IPipePreprocessor preprocessor = (IPipePreprocessor)preprocessorClass.newInstance();
				
				appComponentService.inject(preprocessor, bundleContext);
				
				preprocessors.add(preprocessor);
			}
			
			bundleToPreprocessors.put(bundle, preprocessors);
			MinimumMessageParsingProcessor.this.preprocessors.addAll(preprocessors);
		}

		@Override
		public void lost(Bundle bundle, String contribution) throws Exception {
			List<IPipePreprocessor> preprocessors = bundleToPreprocessors.remove(bundle);
			MinimumMessageParsingProcessor.this.preprocessors.removeAll(preprocessors);
		}
		
	}
	
	private class ParsersTracker implements IContributionTracker {

		@SuppressWarnings({ "unchecked", "rawtypes" })
		@Override
		public void found(Bundle bundle, String contribution) throws Exception {
			StringTokenizer tokenizer = new StringTokenizer(contribution, SEPARATOR_PARSERS);
			
			List<ProtocolChain> protocolChains = new ArrayList<>();
			while (tokenizer.hasMoreTokens()) {
				String parserString = tokenizer.nextToken();
				
				Map<String, String> properties = CommonUtils.parsePropertiesString(
						parserString,
						new String[] {
							PROPERTY_NAME_CLASS,
							PROPERTY_NAME_TYPE,
							PROPERTY_NAME_PROTOCOL_CHAIN,
							PROPERTY_NAME_PARSER_FACTORY,
							PROPERTY_NAME_ANNOTATED_PARSER
						}
				);
				
				String sProtocolChain = properties.get(PROPERTY_NAME_PROTOCOL_CHAIN);
				ProtocolChain protocolChain = parseProtocolChain(sProtocolChain);
				
				String sType = properties.get(PROPERTY_NAME_TYPE);
				if (sType == null) {
					sType = TYPE_CUSTOM;
				}
				
				IParserFactory<?> parserFactory;
				if (TYPE_CUSTOM.equals(sType)) {
					String sParserFactory = properties.get(PROPERTY_NAME_PARSER_FACTORY);
					if (sParserFactory == null)
						throw new IllegalArgumentException("Null parser factory[register parser].");
					
					Class<?> parserFactoryClass = bundle.loadClass(sParserFactory);
					
					if (!(IParserFactory.class.isAssignableFrom(parserFactoryClass)))
						throw new RuntimeException(String.format("%s must implement %s[register parser].",
								parserFactoryClass, IParserFactory.class));
						
					parserFactory = (IParserFactory<?>)parserFactoryClass.newInstance();
				} else if (TYPE_ANNOTATED.equals(sType)) {
					String sParser = properties.get(PROPERTY_NAME_ANNOTATED_PARSER);
					if (sParser == null)
						throw new IllegalArgumentException("Null annotated parser[register parser].");
					
					Class<?> clazz = bundle.loadClass(sParser);
					
					parserFactory = new AnnotatedParserFactory(clazz);
				} else {
					String sClass = properties.get(PROPERTY_NAME_CLASS);
					if (sClass == null)
						throw new IllegalArgumentException("Null class[register parser].");
					
					Class<?> clazz = bundle.loadClass(sClass);
					
					if (TYPE_SIMPLE.equals(sType)) {
						Protocol protocol = protocolChain.get(protocolChain.size() - 1);
						parserFactory = new SimpleObjectParserFactory(protocol, clazz);
					} else if (TYPE_NAMING_CONVENTION.equals(sType)) {
						parserFactory = new NamingConventionParserFactory(clazz);
					} else {
						throw new RuntimeException(String.format("Unknown parser type %s.", sType));
					}
				}
				
				parsingFactory.register(protocolChain, parserFactory);
				protocolChains.add(protocolChain);
			}
			
			bundleToProtocolChains.put(bundle, protocolChains);
		}

		@Override
		public void lost(Bundle bundle, String contribution) throws Exception {
			List<ProtocolChain> protocolChains = bundleToProtocolChains.remove(bundle);
			
			if (protocolChains != null) {
				for (ProtocolChain protocolChain : protocolChains) {
					parsingFactory.unregister(protocolChain);
				}
			}
		}
		
		private ProtocolChain parseProtocolChain(String sProtocolChain) {
			ProtocolChain protocolChain = null;
			
			StringBuilder remainsProtocols = new StringBuilder(sProtocolChain);
			while (remainsProtocols.length() != 0) {
				Protocol protocol = nextProtocol(remainsProtocols);
				
				if (protocol == null) {
					throw new IllegalArgumentException(String.format("Invalid protocol chain[%s].", sProtocolChain));
				}
				
				if (protocolChain == null) {
					protocolChain = ProtocolChain.first(protocol);
				} else {
					protocolChain.next(protocol);
				}
			}
			
			return protocolChain;
		}
		
		private Protocol nextProtocol(StringBuilder remainsProtocols) {
			Protocol protocol;
			int seperatorIndex = remainsProtocols.indexOf(SEPARATOR_OF_PROTOCOLS);
			if (seperatorIndex == -1) {
				protocol = parseProtocol(remainsProtocols.toString());
				remainsProtocols.delete(0, remainsProtocols.length());
			} else {
				protocol = parseProtocol(remainsProtocols.substring(0, seperatorIndex));
				remainsProtocols.delete(0, seperatorIndex + 2);
			}
			
			return protocol;
		}

		private Protocol parseProtocol(String sProtocol) {
			if (sProtocol.length() == 0)
				return null;
			
			String localName;
			String namespace;
			int seperator = sProtocol.indexOf(SEPARATOR_OF_LOCALNAME_NAMESPACE);
			if (seperator == -1) {
				localName = sProtocol.trim();
				namespace = VALUE_NULL;
			} else {
				localName = sProtocol.substring(0, seperator).trim();
				namespace = sProtocol.substring(seperator + 1, sProtocol.length()).trim();
			}
			
			if (localName.length() == 0 || namespace.length() == 0) {
				return null;
			}
			
			if (VALUE_NULL.equals(namespace)) {
				namespace = null;
			}
			
			Protocol protocol = new Protocol(namespace, localName);
			return protocol;
		}
	}
	
	@Override
	public void process(IConnectionContext context, IMessage message) {
		String msg = (String)message.getPayload();
		for (IPipePreprocessor preprocessor : preprocessors) {
			msg = preprocessor.beforeParsing(msg, context);
			
			if (msg == null)
				return;
		}
		
		Object out = parseMessage(context, msg);
		
		if (out == null && logger.isWarnEnabled()) {
			logger.warn("Ignored message. Session JID: {}. Message: {}.",
					message.getHeaders().get(IMessage.KEY_SESSION_JID),message.getPayload());
			return;
		}
		
		for (IPipePreprocessor preprocessor : preprocessors) {
			out = preprocessor.afterParsing(out);
			
			if (out == null)
				return;
		}
		
		context.write(out);
		
		if (out instanceof StreamError) {
			context.close();
		}
	}

	private Object parseMessage(IConnectionContext context, String message) {
		if (logger.isTraceEnabled())
			logger.trace("Parsing message: {}.", message);
		
		Object out = null;
		try {
			Object object = parsingFactory.parse(message);
			if (object instanceof Stanza) {
				Stanza stanza = (Stanza)object;
				
				if (stanza.getFrom() == null) {
					stanza.setFrom(context.getJid());
				}
				
				// (rfc3920 9.1.2)
				if (!isValidFrom(context, stanza)) {
					throw new ProtocolException(new InvalidFrom());
				}
				
				if (logger.isTraceEnabled())
					logger.trace("Stanza parsed. Original message: {}.", message);
				
				// if server doesn't understand the extended namespaces(rfc3921 2.4)
				if (FlawedProtocolObject.isFlawed(stanza)) {
					if (logger.isTraceEnabled())
						logger.trace("Flawed stanza parsed. Original message: {}.", message);
					
					if (isServerRecipient(stanza)) {
						if (stanza instanceof Iq) {
							Iq iq = (Iq)stanza;
							
							//If an entity receives an IQ stanza of type "get" or "set" containing a child element
							// qualified by a namespace it does not understand, the entity SHOULD return an
							// IQ stanza of type "error" with an error condition of <service-unavailable/>.
							if (iq.getType() == Iq.Type.SET || iq.getType() == Iq.Type.GET) {
								out = stanza;
								throw new ProtocolException(new ServiceUnavailable());
							}
						} else if (stanza instanceof Message) {
							// If an entity receives a message stanza whose only child element is qualified by a
							// namespace it does not understand, it MUST ignore the entire stanza.
							Message messageStanza = (Message)stanza;
							if (messageStanza.getSubjects().size() == 0 &&
									messageStanza.getBodies().size() == 0 &&
									messageStanza.getObjects().size() == 0 &&
									messageStanza.getThread() == null) {
								// ignore the entire stanza
								return null;
							}
						} else {
							return stanza;
						}
					} else {
						return stanza;
					}					
				}
				
				return object;
			} else {
				// ？？？
				out = object;
			}
		} catch (ProtocolException e) {
			IError error = e.getError();
			if ((error instanceof StanzaError)) {
				if (!stanzaErrorAttachSenderMessage) {
					// remove sender message
					((StanzaError)error).setOriginalMessage(null);
				}
				
				if (out instanceof Stanza) {
					Stanza stanza = (Stanza)out;
					if (stanza.getId() != null) {
						((StanzaError)error).setId(stanza.getId());
					}
				}
			}
			
			out = error;
			if (logger.isTraceEnabled())
				logger.trace("Parsing protocol exception. original message: {}.", message);
		} catch (RuntimeException e) {
			out = new InternalServerError(CommonUtils.getInternalServerErrorMessage(e));
			logger.error(String.format("Parsing error. original message: %s.", message), e);
		}
		
		return out;
	}

	private boolean isValidFrom(IConnectionContext context, Stanza stanza) {
		if (stanza.getFrom().isBareId() && context.getJid().getBareId().equals(stanza.getFrom()))
			return true;
		
		return stanza.getFrom().equals(context.getJid());
	}

	private boolean isServerRecipient(Stanza stanza) {
		return stanza.getTo() == null || appConfiguration.getDomainName().equals(stanza.getTo().toString());
	}

	@Override
	public void setBundleContext(BundleContext bundleContext) {
		this.bundleContext = bundleContext;
		appComponentService = OsgiUtils.getService(bundleContext, IApplicationComponentService.class);
	}

	@Override
	public void setConfiguration(IConfiguration configuration) {
		stanzaErrorAttachSenderMessage = configuration.getBoolean(CONFIGURATION_KEY_STANZA_ERROR_ATTACH_SENDER_MESSAGE, false);
	}

	@Override
	public void setApplicationConfiguration(IApplicationConfiguration appConfiguration) {
		this.appConfiguration = appConfiguration;
	}

}
