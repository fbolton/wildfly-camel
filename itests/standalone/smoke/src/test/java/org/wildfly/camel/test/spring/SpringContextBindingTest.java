/*
 * #%L
 * Wildfly Camel :: Testsuite
 * %%
 * Copyright (C) 2013 - 2017 RedHat
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
package org.wildfly.camel.test.spring;

import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.camel.test.common.http.HttpRequest;
import org.wildfly.camel.test.common.http.HttpRequest.HttpResponse;
import org.wildfly.camel.test.spring.subE.SimpleServlet;
import org.wildfly.extension.camel.CamelAware;
import org.wildfly.extension.camel.CamelContextRegistry;

@RunWith(Arquillian.class)
@CamelAware
public class SpringContextBindingTest {

    @Deployment
    public static WebArchive createDeployment() {
        return ShrinkWrap.create(WebArchive.class, "camel-spring-binding-tests.war")
            .addClasses(SimpleServlet.class, HttpRequest.class)
            .addAsResource("spring/transform5-camel-context.xml", "camel-context.xml");
    }

    @ArquillianResource
    private CamelContextRegistry contextRegistry;

    @Test
    public void testCamelJndiBindingsInjectable() throws Exception {
        CamelContext camelctx = contextRegistry.getCamelContext("transform5");
        Assert.assertNotNull("Expected transform5 to not be null", camelctx);
        Assert.assertEquals(ServiceStatus.Started, camelctx.getStatus());

        HttpResponse response = HttpRequest.get("http://localhost:8080/camel-spring-binding-tests").getResponse();
        Assert.assertEquals("@Resource lookup\n@Resource mappedName\n@Resource name", response.getBody());
    }
}
