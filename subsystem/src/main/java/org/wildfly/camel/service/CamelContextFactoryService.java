/*
 * #%L
 * Wildfly Camel :: Subsystem
 * %%
 * Copyright (C) 2013 - 2014 RedHat
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */


package org.wildfly.camel.service;

import static org.wildfly.camel.CamelLogger.LOGGER;

import java.util.Hashtable;

import javax.naming.Binding;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.Name;
import javax.naming.NameClassPair;
import javax.naming.NameParser;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.OperationNotSupportedException;

import org.jboss.as.controller.ServiceVerificationHandler;
import org.jboss.as.naming.ManagedReferenceFactory;
import org.jboss.as.naming.ManagedReferenceInjector;
import org.jboss.as.naming.ServiceBasedNamingStore;
import org.jboss.as.naming.deployment.ContextNames;
import org.jboss.as.naming.service.BinderService;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.AbstractService;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.wildfly.camel.CamelConstants;
import org.wildfly.camel.CamelContextFactory;
import org.wildfly.camel.WildFlyCamelContext;

/**
 * The {@link CamelContextFactory} service
 *
 * @author Thomas.Diesler@jboss.com
 * @since 05-Jun-2013
 */
public class CamelContextFactoryService extends AbstractService<CamelContextFactory> {

    private CamelContextFactory contextFactory;

    public static ServiceController<CamelContextFactory> addService(ServiceTarget serviceTarget, ServiceVerificationHandler verificationHandler) {
        CamelContextFactoryService service = new CamelContextFactoryService();
        ServiceBuilder<CamelContextFactory> builder = serviceTarget.addService(CamelConstants.CAMEL_CONTEXT_FACTORY_SERVICE_NAME, service);
        builder.addListener(verificationHandler);
        return builder.install();
    }

    // Hide ctor
    private CamelContextFactoryService() {
    }

    @Override
    public void start(StartContext startContext) throws StartException {
        ServiceContainer serviceContainer = startContext.getController().getServiceContainer();
        contextFactory = new WildflyCamelContextFactory(serviceContainer, startContext.getChildTarget());
    }

    @Override
    public CamelContextFactory getValue() {
        return contextFactory;
    }

    static final class WildflyCamelContextFactory implements CamelContextFactory {

        private final ServiceRegistry serviceRegistry;
        private final ServiceTarget serviceTarget;

        WildflyCamelContextFactory(ServiceRegistry serviceRegistry, ServiceTarget serviceTarget) {
            this.serviceRegistry = serviceRegistry;
            this.serviceTarget = serviceTarget;
        }

        @Override
        public WildFlyCamelContext createCamelContext() throws Exception {
            return createCamelContext(null);
        }

        @Override
        public WildFlyCamelContext createCamelContext(ClassLoader classLoader) throws Exception {
            return setup(new WildFlyCamelContext());
        }

        private WildFlyCamelContext setup(WildFlyCamelContext context) {
            try {
                context.setNamingContext(new NamingContext(serviceRegistry, serviceTarget));
            } catch (NamingException ex) {
                throw new IllegalStateException("Cannot initialize naming context", ex);
            }
            return context;
        }
    }

    static class NamingContext implements Context {

        private final ServiceRegistry serviceRegistry;
        private final ServiceTarget serviceTarget;
        private final Context context;

        private NamingContext(ServiceRegistry serviceRegistry, ServiceTarget serviceTarget) throws NamingException {
            this.serviceRegistry = serviceRegistry;
            this.serviceTarget = serviceTarget;
            this.context = new InitialContext();
        }

        @Override
        public void bind(Name name, Object obj) throws NamingException {
            addBinderService(name.toString(), obj);
        }

        @Override
        public void bind(String name, Object obj) throws NamingException {
            addBinderService(name, obj);
        }

        private ServiceController<?> addBinderService(String name, Object obj) {
            final ContextNames.BindInfo bindInfo = ContextNames.bindInfoFor(name);
            BinderService binderService = new BinderService(bindInfo.getBindName()) {
                @Override
                public synchronized void start(StartContext context) throws StartException {
                    super.start(context);
                    LOGGER.info("Bound camel naming object: {}", bindInfo.getAbsoluteJndiName());
                }

                @Override
                public synchronized void stop(StopContext context) {
                    LOGGER.debug("Unbind camel naming object: {}", bindInfo.getAbsoluteJndiName());
                    super.stop(context);
                }
            };
            Injector<ManagedReferenceFactory> injector = binderService.getManagedObjectInjector();
            new ManagedReferenceInjector<Object>(injector).inject(obj);
            ServiceBuilder<?> builder = serviceTarget.addService(bindInfo.getBinderServiceName(), binderService);
            builder.addDependency(bindInfo.getParentContextServiceName(), ServiceBasedNamingStore.class, binderService.getNamingStoreInjector());
            return builder.install();
        }

        @Override
        public void unbind(Name name) throws NamingException {
            removeBinderService(name.toString());
        }

        @Override
        public void unbind(String name) throws NamingException {
            removeBinderService(name);
        }

        private ServiceController<?> removeBinderService(String name) {
            final ContextNames.BindInfo bindInfo = ContextNames.bindInfoFor(name);
            ServiceController<?> controller = serviceRegistry.getService(bindInfo.getBinderServiceName());
            if (controller != null) {
                controller.setMode(Mode.REMOVE);
            }
            return controller;
        }

        @Override
        public Name composeName(Name name, Name prefix) throws NamingException {
            return context.composeName(name, prefix);
        }

        @Override
        public String composeName(String name, String prefix) throws NamingException {
            return context.composeName(name, prefix);
        }

        @Override
        public Hashtable<?, ?> getEnvironment() throws NamingException {
            return context.getEnvironment();
        }

        @Override
        public String getNameInNamespace() throws NamingException {
            return context.getNameInNamespace();
        }

        @Override
        public NameParser getNameParser(Name name) throws NamingException {
            return context.getNameParser(name);
        }

        @Override
        public NameParser getNameParser(String name) throws NamingException {
            return context.getNameParser(name);
        }

        @Override
        public NamingEnumeration<NameClassPair> list(Name name) throws NamingException {
            return context.list(name);
        }

        @Override
        public NamingEnumeration<NameClassPair> list(String name) throws NamingException {
            return context.list(name);
        }

        @Override
        public NamingEnumeration<Binding> listBindings(Name name) throws NamingException {
            return context.listBindings(name);
        }

        @Override
        public NamingEnumeration<Binding> listBindings(String name) throws NamingException {
            return context.listBindings(name);
        }

        @Override
        public Object lookup(Name name) throws NamingException {
            return context.lookup(name);
        }

        @Override
        public Object lookup(String name) throws NamingException {
            return context.lookup(name);
        }

        @Override
        public Object lookupLink(Name name) throws NamingException {
            return context.lookupLink(name);
        }

        @Override
        public Object lookupLink(String name) throws NamingException {
            return context.lookupLink(name);
        }

        // Not supported opertations

        @Override
        public Object addToEnvironment(String propName, Object propVal) throws NamingException {
            throw new OperationNotSupportedException();
        }

        @Override
        public Object removeFromEnvironment(String propName) throws NamingException {
            throw new OperationNotSupportedException();
        }

        @Override
        public void close() throws NamingException {
            throw new OperationNotSupportedException();
        }

        @Override
        public Context createSubcontext(Name name) throws NamingException {
            throw new OperationNotSupportedException();
        }

        @Override
        public Context createSubcontext(String name) throws NamingException {
            throw new OperationNotSupportedException();
        }

        @Override
        public void destroySubcontext(Name name) throws NamingException {
            throw new OperationNotSupportedException();
        }

        @Override
        public void destroySubcontext(String name) throws NamingException {
            throw new OperationNotSupportedException();
        }

        @Override
        public void rebind(Name name, Object obj) throws NamingException {
            throw new OperationNotSupportedException();
        }

        @Override
        public void rebind(String name, Object obj) throws NamingException {
            throw new OperationNotSupportedException();
        }

        @Override
        public void rename(Name oldName, Name newName) throws NamingException {
            throw new OperationNotSupportedException();
        }

        @Override
        public void rename(String oldName, String newName) throws NamingException {
            throw new OperationNotSupportedException();
        }
    }
}
