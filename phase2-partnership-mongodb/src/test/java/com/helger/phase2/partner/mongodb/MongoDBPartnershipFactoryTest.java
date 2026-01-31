/*
 * Copyright (C) 2015-2026 jochenberger & Philip Helger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.helger.phase2.partner.mongodb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.bson.Document;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.phase2.exception.AS2Exception;
import com.helger.phase2.partner.Partnership;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import de.flapdoodle.embed.mongo.commands.MongodArguments;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess;
import de.flapdoodle.reverse.Listener;
import de.flapdoodle.reverse.TransitionWalker;
import de.flapdoodle.reverse.transitions.Start;

public class MongoDBPartnershipFactoryTest
{
  private static final Logger LOGGER = LoggerFactory.getLogger (MongoDBPartnershipFactoryTest.class);

  private static TransitionWalker.ReachedState <RunningMongodProcess> s_aMongodExecutable;
  private static MongoClient s_aMongoClient;
  private static MongoDatabase s_aDatabase;
  private static MongoCollection <Document> s_aCollection;
  private static MongoDBPartnershipFactory s_aPartnershipFactory;

  @BeforeClass
  public static void setupSpec ()
  {
    final int nPort = 12345;

    s_aMongodExecutable = Mongod.builder ()
                                .net (Start.to (Net.class).initializedWith (Net.defaults ().withPort (nPort)))
                                .mongodArguments (Start.to (MongodArguments.class)
                                                       .initializedWith (MongodArguments.defaults ().withAuth (false)))
                                .build ()
                                .start (Version.Main.V8_0, new Listener [0]);
    s_aMongoClient = MongoClients.create (MongoClientSettings.builder ()
                                                             .applyConnectionString (new ConnectionString ("mongodb://localhost:" +
                                                                                                           nPort))
                                                             .build ());
    s_aDatabase = s_aMongoClient.getDatabase ("phase2-test");
    s_aCollection = s_aDatabase.getCollection ("partnerships");
    s_aPartnershipFactory = new MongoDBPartnershipFactory (s_aCollection, LOGGER);
    s_aDatabase.drop ();
  }

  @AfterClass
  public static void cleanupSpec ()
  {
    if (s_aMongoClient != null)
      s_aMongoClient.close ();
    if (s_aMongodExecutable != null)
      s_aMongodExecutable.close ();
  }

  @Test
  public void testAddPartnership () throws AS2Exception
  {
    final Partnership partnership = new Partnership ("Test partnership");
    assertTrue (s_aPartnershipFactory.addPartnership (partnership).isChanged ());
    assertEquals (1, s_aCollection.countDocuments ());
    assertNotNull (s_aPartnershipFactory.getPartnershipByName ("Test partnership"));
  }
}
