/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.discovery.ec2;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.http.IdleConnectionReaper;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.OpenSearchException;
import org.opensearch.common.Strings;
import org.opensearch.common.util.LazyInitializable;

import java.util.concurrent.atomic.AtomicReference;

class AwsEc2ServiceImpl implements AwsEc2Service {

    private static final Logger logger = LogManager.getLogger(AwsEc2ServiceImpl.class);

    private final AtomicReference<LazyInitializable<AmazonEc2Reference, OpenSearchException>> lazyClientReference =
            new AtomicReference<>();

    private AmazonEC2 buildClient(Ec2ClientSettings clientSettings) {
        final AWSCredentialsProvider credentials = buildCredentials(logger, clientSettings);
        final ClientConfiguration configuration = buildConfiguration(logger, clientSettings);
        return buildClient(credentials, configuration, clientSettings.endpoint);
    }

    // proxy for testing
    AmazonEC2 buildClient(AWSCredentialsProvider credentials, ClientConfiguration configuration, String endpoint) {
        final AmazonEC2ClientBuilder builder = AmazonEC2ClientBuilder.standard().withCredentials(credentials)
            .withClientConfiguration(configuration);
        if (Strings.hasText(endpoint)) {
            logger.debug("using explicit ec2 endpoint [{}]", endpoint);
            builder.withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpoint, null));
        }
        return SocketAccess.doPrivileged(builder::build);
    }

    // pkg private for tests
    static ClientConfiguration buildConfiguration(Logger logger, Ec2ClientSettings clientSettings) {
        final ClientConfiguration clientConfiguration = new ClientConfiguration();
        // the response metadata cache is only there for diagnostics purposes,
        // but can force objects from every response to the old generation.
        clientConfiguration.setResponseMetadataCacheSize(0);
        clientConfiguration.setProtocol(clientSettings.protocol);
        if (Strings.hasText(clientSettings.proxyHost)) {
            // TODO: remove this leniency, these settings should exist together and be validated
            clientConfiguration.setProxyHost(clientSettings.proxyHost);
            clientConfiguration.setProxyPort(clientSettings.proxyPort);
            clientConfiguration.setProxyUsername(clientSettings.proxyUsername);
            clientConfiguration.setProxyPassword(clientSettings.proxyPassword);
        }
        // Increase the number of retries in case of 5xx API responses
        clientConfiguration.setMaxErrorRetry(10);
        clientConfiguration.setSocketTimeout(clientSettings.readTimeoutMillis);
        return clientConfiguration;
    }

    // pkg private for tests
    static AWSCredentialsProvider buildCredentials(Logger logger, Ec2ClientSettings clientSettings) {
        final AWSCredentials credentials = clientSettings.credentials;
        if (credentials == null) {
            logger.debug("Using default provider chain");
            return DefaultAWSCredentialsProviderChain.getInstance();
        } else {
            logger.debug("Using basic key/secret credentials");
            return new AWSStaticCredentialsProvider(credentials);
        }
    }

    @Override
    public AmazonEc2Reference client() {
        final LazyInitializable<AmazonEc2Reference, OpenSearchException> clientReference = this.lazyClientReference.get();
        if (clientReference == null) {
            throw new IllegalStateException("Missing ec2 client configs");
        }
        return clientReference.getOrCompute();
    }

    /**
     * Refreshes the settings for the AmazonEC2 client. The new client will be build
     * using these new settings. The old client is usable until released. On release it
     * will be destroyed instead of being returned to the cache.
     */
    @Override
    public void refreshAndClearCache(Ec2ClientSettings clientSettings) {
        final LazyInitializable<AmazonEc2Reference, OpenSearchException> newClient = new LazyInitializable<>(
                () -> new AmazonEc2Reference(buildClient(clientSettings)), clientReference -> clientReference.incRef(),
                clientReference -> clientReference.decRef());
        final LazyInitializable<AmazonEc2Reference, OpenSearchException> oldClient = this.lazyClientReference.getAndSet(newClient);
        if (oldClient != null) {
            oldClient.reset();
        }
    }

    @Override
    public void close() {
        final LazyInitializable<AmazonEc2Reference, OpenSearchException> clientReference = this.lazyClientReference.getAndSet(null);
        if (clientReference != null) {
            clientReference.reset();
        }
        // shutdown IdleConnectionReaper background thread
        // it will be restarted on new client usage
        IdleConnectionReaper.shutdown();
    }

}
