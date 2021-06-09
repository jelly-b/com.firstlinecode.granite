package com.firstlinecode.granite.framework.adf.spring;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.pf4j.CompoundPluginLoader;
import org.pf4j.DefaultPluginFactory;
import org.pf4j.DefaultPluginLoader;
import org.pf4j.DevelopmentPluginLoader;
import org.pf4j.ExtensionFactory;
import org.pf4j.Plugin;
import org.pf4j.PluginFactory;
import org.pf4j.PluginLoader;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
import org.pf4j.spring.ExtensionsInjector;
import org.pf4j.spring.SpringExtensionFactory;
import org.pf4j.spring.SpringPluginManager;
import org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory;
import org.springframework.context.ApplicationContextAware;

import com.firstlinecode.granite.framework.core.adf.IApplicationComponentService;
import com.firstlinecode.granite.framework.core.adf.IApplicationComponentServiceAware;

public class AdfPluginManager extends SpringPluginManager implements ApplicationContextAware,
			IApplicationComponentServiceAware {
	private AdfComponentService appComponentService;
	private URL[] nonPluginDependencies;
	
	public AdfPluginManager() {
	}
	
	public AdfPluginManager(Path pluginsRoot) {
		super(pluginsRoot);
	}
	
	@Override
	protected void initialize() {
		super.initialize();
		
		List<URL> lNonPluginDependency = new ArrayList<>();
		File pluginsDir = getPluginsRoot().toFile();
		for (File file : pluginsDir.listFiles()) {
			if (!file.getName().endsWith(".jar"))
				continue;
			
			try {				
				if (!isPlugin(file)) {
					lNonPluginDependency.add(file.toURI().toURL());
				}
			} catch (Exception e) {
				throw new RuntimeException("Can't load non-plugin dependencies from plugin directory.", e);
			}
		}
		
		nonPluginDependencies = lNonPluginDependency.toArray(new URL[lNonPluginDependency.size()]);
	}
	
	private boolean isPlugin(File file) throws Exception {
		JarFile jarFile = null;
		try {
			jarFile = new JarFile(file);
			boolean idxFound = false;
			boolean pluginPropertiesFound = false;
			Enumeration<JarEntry> entries = jarFile.entries();
			while (entries.hasMoreElements()) {
				String entryName = entries.nextElement().getName();
				if ("META-INF/extensions.idx".equals(entryName)) {
					idxFound = true;
					if (pluginPropertiesFound)
						return true;
				} else if ("plugin.properties".equals(entryName)) {
					pluginPropertiesFound = true;
					if (idxFound)
						return true;
				}
			}
		} finally {
			if (jarFile != null)
				jarFile.close();
		}
		
		return false;
	}

	@Override
	protected PluginFactory createPluginFactory() {
		return new AdfPluginFactory();
	}
	
	private class AdfPluginFactory extends DefaultPluginFactory {
		@Override
		public Plugin create(PluginWrapper pluginWrapper) {
			Plugin plugin = super.create(pluginWrapper);
			if (plugin == null)
				return null;
			
			plugin = appComponentService.inject(plugin);
			if (plugin instanceof ApplicationContextAware) {
				((ApplicationContextAware)plugin).setApplicationContext(getApplicationContext());
			}
			
			return plugin;
		}
	}
	
	@Override
	protected ExtensionFactory createExtensionFactory() {
		return new AdfExtensionFactory(this);
	}
	
	private class AdfExtensionFactory extends SpringExtensionFactory {

		public AdfExtensionFactory(PluginManager pluginManager) {
			super(pluginManager);
		}
		
		@Override
		public <T> T create(Class<T> extensionClass) {
			T extension = super.create(extensionClass);
			if (extension != null)
				appComponentService.inject(extension);
			
			return extension;
		}
	}
	
	@Override
	public void setApplicationComponentService(IApplicationComponentService appComponentService) {
		if (appComponentService instanceof AdfComponentService) {
			this.appComponentService = (AdfComponentService)appComponentService;
		} else {
			throw new IllegalArgumentException(String.format("Class %s Not an ADF plugin manager.", appComponentService.getClass().getName()));
		}
	}
	
	public void init() {
		// Override super.init(). Do nothing.
	}
	
	public void injectExtensionsToSpring() {
		AbstractAutowireCapableBeanFactory beanFactory = (AbstractAutowireCapableBeanFactory)getApplicationContext().getAutowireCapableBeanFactory();
		ExtensionsInjector extensionsInjector = new ExtensionsInjector(this, beanFactory);
		extensionsInjector.injectExtensions();
	}
	
    protected PluginLoader createPluginLoader() {
        return new CompoundPluginLoader()
            .add(new DevelopmentPluginLoader(this), this::isDevelopment)
            .add(new AdfPluginLoader(this, nonPluginDependencies), this::isNotDevelopment)
            .add(new DefaultPluginLoader(this), this::isNotDevelopment);
    }
}