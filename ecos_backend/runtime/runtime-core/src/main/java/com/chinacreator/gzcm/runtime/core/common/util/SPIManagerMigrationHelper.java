package com.chinacreator.gzcm.runtime.core.common.util;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * SPI Manager Migration Helper
 * 
 * This class provides a migration path from frameworkset's BaseSPIManager
 * to Spring's ApplicationContext. It implements ApplicationContextAware
 * to get access to the Spring context.
 * 
 * Usage:
 * <pre>
 * // Old way:
 * Object provider = SPIManagerMigrationHelper.getProvider("serviceName");
 * 
 * // New way:
 * @Autowired
 * private YourService yourService;
 * 
 * // Or using ApplicationContext:
 * @Autowired
 * private ApplicationContext applicationContext;
 * Object provider = applicationContext.getBean("serviceName");
 * </pre>
 */
@Component
public class SPIManagerMigrationHelper implements ApplicationContextAware {
    
    private static ApplicationContext applicationContext;
    
    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        applicationContext = context;
    }
    
    /**
     * Get a bean by name (compatible with BaseSPIManager.getProvider)
     * 
     * @param name bean name
     * @return bean instance
     */
    public static Object getProvider(String name) {
        if (applicationContext == null) {
            throw new IllegalStateException("ApplicationContext not initialized");
        }
        return applicationContext.getBean(name);
    }
    
    /**
     * Get a bean by type
     * 
     * @param type bean type
     * @param <T> type parameter
     * @return bean instance
     */
    public static <T> T getBean(Class<T> type) {
        if (applicationContext == null) {
            throw new IllegalStateException("ApplicationContext not initialized");
        }
        return applicationContext.getBean(type);
    }
    
    /**
     * Get a bean by name and type
     * 
     * @param name bean name
     * @param type bean type
     * @param <T> type parameter
     * @return bean instance
     */
    public static <T> T getBean(String name, Class<T> type) {
        if (applicationContext == null) {
            throw new IllegalStateException("ApplicationContext not initialized");
        }
        return applicationContext.getBean(name, type);
    }
}
