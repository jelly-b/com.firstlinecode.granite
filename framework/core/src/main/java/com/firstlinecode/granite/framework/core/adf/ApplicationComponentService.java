package com.firstlinecode.granite.framework.core.adf;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.firstlinecode.basalt.oxm.parsing.IParser;
import com.firstlinecode.basalt.oxm.translating.ITranslator;
import com.firstlinecode.granite.framework.core.adf.injection.AppComponentInjectionProvider;
import com.firstlinecode.granite.framework.core.adf.injection.FieldDependencyInjector;
import com.firstlinecode.granite.framework.core.adf.injection.IDependencyFetcher;
import com.firstlinecode.granite.framework.core.adf.injection.IDependencyInjector;
import com.firstlinecode.granite.framework.core.adf.injection.IInjectionProvider;
import com.firstlinecode.granite.framework.core.adf.injection.MethodDependencyInjector;
import com.firstlinecode.granite.framework.core.annotations.AppComponent;
import com.firstlinecode.granite.framework.core.annotations.Component;
import com.firstlinecode.granite.framework.core.config.IConfiguration;
import com.firstlinecode.granite.framework.core.config.IConfigurationAware;
import com.firstlinecode.granite.framework.core.config.IServerConfiguration;
import com.firstlinecode.granite.framework.core.config.IServerConfigurationAware;
import com.firstlinecode.granite.framework.core.event.IEvent;
import com.firstlinecode.granite.framework.core.event.IEventFirer;
import com.firstlinecode.granite.framework.core.event.IEventFirerAware;
import com.firstlinecode.granite.framework.core.pipeline.IMessageChannel;
import com.firstlinecode.granite.framework.core.pipeline.IPipelineExtender;
import com.firstlinecode.granite.framework.core.pipeline.SimpleMessage;
import com.firstlinecode.granite.framework.core.platform.IPluginManagerAware;
import com.firstlinecode.granite.framework.core.repository.CreationException;
import com.firstlinecode.granite.framework.core.repository.IComponentInfo;
import com.firstlinecode.granite.framework.core.repository.IDependencyInfo;
import com.firstlinecode.granite.framework.core.repository.IInitializable;
import com.firstlinecode.granite.framework.core.repository.IRepository;
import com.firstlinecode.granite.framework.core.repository.IRepositoryAware;
import com.firstlinecode.granite.framework.core.utils.CommonsUtils;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.InvocationHandler;

public class ApplicationComponentService implements IApplicationComponentService, IRepositoryAware {
	private static final String COMPONENT_ID_LITE_ANY_2_EVENT_MESSAGE_CHANNEL = "lite.any.2.event.message.channel";
	private static final String COMPONENT_ID_CLUSTER_ANY_2_EVENT_MESSAGE_CHANNEL = "cluster.any.2.event.message.channel";
	
	private static final Logger logger = LoggerFactory.getLogger(ApplicationComponentService.class);
	
	protected IServerConfiguration serverConfiguration;
	protected PluginManager pluginManager;
	protected IApplicationComponentConfigurations appComponentConfigurations;
	protected boolean syncPlugins;
	protected boolean started;
	protected IRepository repository;
	protected Map<String, IComponentInfo> appComponentInfos;
	protected Map<Class<?>, List<IDependencyInjector>> dependencyInjectors;
	
	public ApplicationComponentService(IServerConfiguration serverConfiguration) {
		this(serverConfiguration, null);
	}
	
	public ApplicationComponentService(IServerConfiguration serverConfiguration, PluginManager pluginManager) {
		this(serverConfiguration, pluginManager, true);
	}

	public ApplicationComponentService(IServerConfiguration serverConfiguration, PluginManager pluginManager,
			boolean syncPlugins) {
		this.serverConfiguration = serverConfiguration;
		this.syncPlugins = syncPlugins;
		
		appComponentInfos = new HashMap<>();
		dependencyInjectors = new HashMap<>();
		
		if (pluginManager == null) {
			pluginManager = createPluginManager();
		} else {
			this.pluginManager = pluginManager;
		}
		
		appComponentConfigurations = readAppComponentConfigurations(serverConfiguration);
	}
	
	private ApplicationComponentConfigurations readAppComponentConfigurations(IServerConfiguration serverConfiguration) {
		return new ApplicationComponentConfigurations(serverConfiguration.getConfigurationDir());
	}
	
	protected PluginManager createPluginManager() {
		return new AppComponentPluginManager(this);
	}
	
	@Override
	public PluginManager getPluginManager() {
		return pluginManager;
	}
	
	public IApplicationComponentConfigurations getApplicationComponentConfigurations() {
		return appComponentConfigurations;
	}

	@Override
	public <T> List<Class<? extends T>> getExtensionClasses(Class<T> type) {
		return pluginManager.getExtensionClasses(type);
	}

	@Override
	public <T> T createExtension(Class<T> type) {
		T extension = createRawExtension(type);
		
		if (extension == null)
			return null;
		
		return inject(extension);
	}
	
	@Override
	public <T> T createRawExtension(Class<T> type) {
		T extension = null;
		try {
			extension = type.newInstance();
		} catch (Exception e) {
			throw new RuntimeException(String.format("Can't create raw extension which's type is '%s'.", type.getName()), e);
		}
		
		return extension;
	}

	@Override
	public void start() {
		if (started)
			return;
		
		loadContributedAppComponents();
		
		if (syncPlugins)
			initPlugins();
			
		started = true;
	}
	
	protected void loadContributedAppComponents() {
		// TODO Auto-generated method stub
		List<IAppComponentsContributor> componentContributors = pluginManager.getExtensions(IAppComponentsContributor.class);
		if (componentContributors == null || componentContributors.size() == 0)
			return;
		
		for (IAppComponentsContributor componentContributor : componentContributors) {
			Class<?>[] appComponentClasses = componentContributor.getAppComponentClasses();
			if (appComponentClasses == null || appComponentClasses.length == 0)
				continue;
			
			for (Class<?> appComponentClass : appComponentClasses) {
				AppComponent appComponentAnnotation = appComponentClass.getAnnotation(AppComponent.class);
				if (appComponentAnnotation == null) {
					throw new IllegalArgumentException(
							String.format("Class '%s' isn't an legal application component. " +
									"You need to add @AppComponent to the class.", appComponentClass.getName()));
				}
				
				registerAppComponentInfo(appComponentAnnotation, appComponentClass);
			}
		}
	}

	protected void registerAppComponentInfo(AppComponent appComponentAnnotation, Class<?> appComponentClass) {
		String appComponentId = appComponentAnnotation.value();
		IComponentInfo componentInfo = new AppComponentInfo(appComponentId, appComponentClass,
				appComponentAnnotation.isSingleton());
		if (appComponentInfos.containsKey(appComponentId)) {
			throw new RuntimeException(String.format("Reduplicated application component ID: %s", appComponentId));
		}
		
		appComponentInfos.put(appComponentId, componentInfo);
	}
	
	private class AppComponentInfo implements IComponentInfo {
		private String id;
		private Class<?> type;
		private boolean singleton;
		private volatile Object instance;
		private Object singletonLock = new Object();
		
		public AppComponentInfo(String id, Class<?> type, boolean singleton) {
			this.id = id;
			this.type = type;
			this.singleton = singleton;
		}
		
		@Override
		public String getId() {
			return id;
		}
		
		@Override
		public void addDependency(IDependencyInfo dependency) {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public void removeDependency(IDependencyInfo dependency) {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public IDependencyInfo[] getDependencies() {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public boolean isAvailable() {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public boolean isService() {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public Object create() throws CreationException {
			if (!singleton) {
				try {
					return doCreate();
				} catch (Exception e) {
					throw new CreationException(String.format("Can't create application component %s.", id), e);
				}
			}
			
			synchronized (singletonLock) {
				if (instance == null) {
					try {
						instance = doCreate();
					} catch (Exception e) {
						throw new CreationException(String.format("Can't create application component %s.", id), e);
					}
				}
				
				return instance;
			}
		}

		private Object doCreate() throws InstantiationException, IllegalAccessException {
			Object component = type.newInstance();
			inject(component);
			
			return component;
		}
		
		@Override
		public boolean isSingleton() {
			return singleton;
		}
		
		@Override
		public IComponentInfo getAliasComponent(String alias) {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public String toString() {
			return String.format("Application component['%s', '%s'].", id, type.getName());
		}

		@Override
		public Class<?> getType() {
			return type;
		}
	}
	
	@Override
	public boolean isStarted() {
		return started;
	}

	private void initPlugins() {
		pluginManager.loadPlugins();
		pluginManager.startPlugins();
	}

	@Override
	public void stop() {
		if (!started)
			return;

		if (syncPlugins) {
			destroyPlugins();
		}
		
		started = false;
	}

	private void destroyPlugins() {
		pluginManager.stopPlugins();
		pluginManager.unloadPlugins();
	}

	@Override
	public <T> T inject(T rawInstance) {
		Class<?> clazz = rawInstance.getClass();
		
		for (IDependencyInjector injector : getDependencyInjectors(clazz)) {
			injector.inject(rawInstance);
		}
		
		injectByAwareInterfaces(rawInstance);
		
		if (rawInstance instanceof IInitializable) {
			((IInitializable)rawInstance).init();
		}
		
		return rawInstance;
	}

	private <T> void injectByAwareInterfaces(T rawInstance) {
		if (rawInstance instanceof IServerConfigurationAware) {
			((IServerConfigurationAware)rawInstance).setServerConfiguration(serverConfiguration);
		}
		
		if (rawInstance instanceof IConfigurationAware) {
			Class<?> type = rawInstance.getClass();
			PluginWrapper plugin = pluginManager.whichPlugin(type);
			if (plugin == null)
				throw new IllegalArgumentException(
					String.format("Can't determine which plugin the extension which's class name is '%s' is load from.", type));
			
			IConfiguration configuration = appComponentConfigurations.getConfiguration(plugin.getDescriptor().getPluginId());
			((IConfigurationAware)rawInstance).setConfiguration(configuration);
		}
		
		if (rawInstance instanceof IPluginManagerAware) {
			((IPluginManagerAware)rawInstance).setPluginManager(pluginManager);
		}
		
		if (rawInstance instanceof IApplicationComponentServiceAware) {
			((IApplicationComponentServiceAware)rawInstance).setApplicationComponentService(this);
		}
		
		if (rawInstance instanceof IEventFirerAware) {
			if (repository == null)
				throw new RuntimeException("Can't create event firer because the repository hasn't been set yet.");
			
			((IEventFirerAware)rawInstance).setEventFirer(createEventFirer());
		}
	}
	
	protected List<IDependencyInjector> getDependencyInjectors(Class<?> clazz) {
		List<IDependencyInjector> injectors = dependencyInjectors.get(clazz);
		
		if (injectors != null)
			return injectors;
		
		IInjectionProvider[] injectionProviders = getInjectionProviders();
		
		injectors = new ArrayList<>();
		for (Field field : getClassFields(clazz, null)) {
			for (IInjectionProvider injectionProvider : injectionProviders) {
				Object dependencyAnnotation = field.getAnnotation(injectionProvider.getAnnotationType());				
				if (dependencyAnnotation != null) {
					IDependencyFetcher fetcher = injectionProvider.getFetcher(dependencyAnnotation);
					IDependencyInjector injector = new FieldDependencyInjector(field, fetcher);
					injectors.add(injector);
				}
			}
		}
		
		for (Method method : clazz.getMethods()) {
			for (IInjectionProvider injectionProvider : injectionProviders) {
				if (method.getDeclaringClass().equals(Object.class))
					continue;
				
				int modifiers = method.getModifiers();
				if (!Modifier.isPublic(modifiers) ||
						Modifier.isAbstract(modifiers) ||
						Modifier.isStatic(modifiers))
					continue;
				
				Object dependencyAnnotation = method.getAnnotation(injectionProvider.getAnnotationType());				
				if (dependencyAnnotation != null) {
					if (!CommonsUtils.isSetterMethod(method))
						logger.warn(String.format("Dependency method '%s' isn't a setter method.", method));
						
					IDependencyFetcher fetcher = injectionProvider.getFetcher(dependencyAnnotation);
					IDependencyInjector injector = new MethodDependencyInjector(method, fetcher);
					injectors.add(injector);
				}
			}
		}
		
		List<IDependencyInjector> old = dependencyInjectors.putIfAbsent(clazz, injectors);
		
		return old == null ? injectors : old;
	}
	
	protected IInjectionProvider[] getInjectionProviders() {
		return new IInjectionProvider[] {new AppComponentInjectionProvider(this)};
	}
	
	private List<Field> getClassFields(Class<?> clazz, List<Field> fields) {
		if (fields == null)
			fields = new ArrayList<Field>();
		
		for (Field field : clazz.getDeclaredFields()) {
			fields.add(field);
		}
		
		Class<?> parent = clazz.getSuperclass();
		if (!isPipeExtender(parent) && parent.getAnnotation(Component.class) == null)
			return fields;
		
		return getClassFields(parent, fields);
	}
	
	private boolean isPipeExtender(Class<?> clazz) {
		return IParser.class.isAssignableFrom(clazz) ||
			ITranslator.class.isAssignableFrom(clazz) ||
			IPipelineExtender.class.isAssignableFrom(clazz);
	}

	private IEventFirer createEventFirer() {
		return new EventFirer(repository);
	}
	
	private class EventFirer implements IEventFirer {
		private IRepository repository;
		private IMessageChannel messageChannel;
		
		public EventFirer(IRepository repository) {
			this.repository = repository;
		}

		@Override
		public void fire(IEvent event) {
			if (messageChannel == null) {
				messageChannel = getAnyToEventMessageChannel();
			}
			
			messageChannel.send(new SimpleMessage(event));
		}
		
		private IMessageChannel getAnyToEventMessageChannel() {
			IMessageChannel messageChannerl = (IMessageChannel)repository.get(COMPONENT_ID_CLUSTER_ANY_2_EVENT_MESSAGE_CHANNEL);
			if (messageChannerl == null)
				messageChannerl = (IMessageChannel)repository.get(COMPONENT_ID_LITE_ANY_2_EVENT_MESSAGE_CHANNEL);
			
			if (messageChannerl == null)
				throw new RuntimeException("Can't fire event because the any to event message channel is null.");
			
			return messageChannerl;
		}
	}

	@Override
	public void setRepository(IRepository repository) {
		this.repository = repository;
	}

	@Override
	public Object getAppComponent(String id, Class<?> type) {
		Enhancer enhancer = new Enhancer();
		
		enhancer.setSuperclass(type);
		enhancer.setCallback(new LazyLoadComponentInvocationHandler(id));
		enhancer.setUseFactory(false);
		enhancer.setClassLoader(type.getClassLoader());
		
		return enhancer.create();
	}
	
	private class LazyLoadComponentInvocationHandler implements InvocationHandler {
		private String id;
		private volatile Object component = null;
		
		public LazyLoadComponentInvocationHandler(String id) {
			this.id = id;
		}
		
		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			component = getComponent();
			
			if (component == null)
				throw new IllegalStateException(String.format("Null dependency '%s'.", id));
			
			if (Proxy.isProxyClass(component.getClass())) {
				return Proxy.getInvocationHandler(component).invoke(component, method, args);
			} else if (net.sf.cglib.proxy.Proxy.isProxyClass(component.getClass())) {
				return net.sf.cglib.proxy.Proxy.getInvocationHandler(component).invoke(component, method, args);				
			} else {
				return method.invoke(component, args);				
			}
		}

		private Object getComponent() throws CreationException {
			if (component != null)
				return component;
			
			synchronized (this) {
				if (component != null)
					return component;
				
				IComponentInfo componentInfo = appComponentInfos.get(id);
				if (componentInfo == null)
					return null;
				
				component = componentInfo.create();
			}
			
			return component;
		}
		
	}
}
