/*
 * Copyright 2008-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.client.internal;

import com.mongodb.AutoEncryptionSettings;
import com.mongodb.ClientEncryptionSettings;
import com.mongodb.MongoClientException;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoNamespace;
import com.mongodb.client.MongoClients;
import com.mongodb.crypt.capi.MongoCrypts;

import javax.net.ssl.SSLContext;
import java.security.NoSuchAlgorithmException;

import static com.mongodb.internal.capi.MongoCryptHelper.createMongoCryptOptions;

public final class Crypts {

    public static Crypt createCrypt(final SimpleMongoClient client, final AutoEncryptionSettings options) {
        return new Crypt(MongoCrypts.create(createMongoCryptOptions(options.getKmsProviders(),
                options.getSchemaMap())),
                new CollectionInfoRetriever(client),
                new CommandMarker(options.isBypassAutoEncryption(), options.getExtraOptions()),
                createKeyRetriever(client, options.getKeyVaultMongoClientSettings(), options.getKeyVaultNamespace()),
                createKeyManagementService(),
                options.isBypassAutoEncryption());
    }

    static Crypt create(final SimpleMongoClient keyVaultClient, final ClientEncryptionSettings options) {
        return new Crypt(MongoCrypts.create(
                createMongoCryptOptions(options.getKmsProviders(), null)),
                createKeyRetriever(keyVaultClient, false, options.getKeyVaultNamespace()),
                createKeyManagementService());
    }

    private static KeyRetriever createKeyRetriever(final SimpleMongoClient defaultKeyVaultClient,
                                                   final MongoClientSettings keyVaultMongoClientSettings,
                                                   final String keyVaultNamespaceString) {
        SimpleMongoClient keyVaultClient;
        boolean keyRetrieverOwnsClient;
        if (keyVaultMongoClientSettings != null) {
            keyVaultClient = SimpleMongoClients.create(MongoClients.create(keyVaultMongoClientSettings));
            keyRetrieverOwnsClient = true;
        } else {
            keyVaultClient = defaultKeyVaultClient;
            keyRetrieverOwnsClient = false;
        }

        return createKeyRetriever(keyVaultClient, keyRetrieverOwnsClient, keyVaultNamespaceString);
    }

    private static KeyRetriever createKeyRetriever(final SimpleMongoClient keyVaultClient, final boolean keyRetrieverOwnsClient,
                                                   final String keyVaultNamespaceString) {
        return new KeyRetriever(keyVaultClient, keyRetrieverOwnsClient, new MongoNamespace(keyVaultNamespaceString));
    }

    private static KeyManagementService createKeyManagementService() {
        return new KeyManagementService(getSslContext(), 443, 10000);
    }

    private static SSLContext getSslContext() {
        SSLContext sslContext;
        try {
            sslContext = SSLContext.getDefault();
        } catch (NoSuchAlgorithmException e) {
            throw new MongoClientException("Unable to create default SSLContext", e);
        }
        return sslContext;
    }

    private Crypts() {
    }
}
