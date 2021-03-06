/*
 * #%L
 * Wildfly Camel :: Testsuite
 * %%
 * Copyright (C) 2013 - 2015 RedHat
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

package org.wildfly.camel.test.http4;

import javax.naming.InitialContext;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http4.HttpClientConfigurer;
import org.apache.camel.http.common.HttpOperationFailedException;
import org.apache.camel.impl.DefaultCamelContext;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.camel.test.http4.subA.Http4ClientConfigurer;
import org.wildfly.camel.test.http4.subA.MyServlet;
import org.wildfly.extension.camel.CamelAware;

@CamelAware
@RunWith(Arquillian.class)
public class Http4IntegrationTest {

    @ArquillianResource
    private InitialContext initialContext;

    @Deployment
    public static WebArchive createDeployment() {
        final WebArchive archive = ShrinkWrap.create(WebArchive.class, "simple.war");
        archive.addPackage(MyServlet.class.getPackage());
        return archive;
    }

    @Test
    public void testHttpGetRequest() throws Exception {
        HttpClientConfigurer configurer = new Http4ClientConfigurer();
        initialContext.bind("httpClientConfigurer", configurer);

        CamelContext camelctx = new DefaultCamelContext();
        camelctx.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                .to("http4://localhost:8080/simple/myservlet?httpClientConfigurer=#httpClientConfigurer");
            }
        });

        camelctx.start();
        try {
            ProducerTemplate producer = camelctx.createProducerTemplate();
            String result = producer.requestBodyAndHeader("direct:start", null, Exchange.HTTP_QUERY, "name=Kermit", String.class);
            Assert.assertEquals("Hello Kermit", result);
        } finally {
            camelctx.stop();
            initialContext.unbind("httpClientConfigurer");
        }
    }

    @Test
    public void testHttpPostRequestFailure() throws Exception {
        CamelContext camelctx = new DefaultCamelContext();
        camelctx.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                .to("http4://localhost:8080/simple/myservlet");
            }
        });

        camelctx.start();
        try {
            ProducerTemplate producer = camelctx.createProducerTemplate();
            Exchange exchange = producer.send("direct:start", new Processor() {
                @Override
                public void process(Exchange exchange) throws Exception {
                    exchange.getIn().setBody("q=test1234");
                }
            });

            Assert.assertNotNull("Expected exchange to not  be null", exchange);
            Assert.assertTrue("Expected exchange to be flagged as failed", exchange.isFailed());

            HttpOperationFailedException exception = (HttpOperationFailedException) exchange.getException();
            Assert.assertNotNull(exception);
        } finally {
            camelctx.stop();
        }
    }
}
