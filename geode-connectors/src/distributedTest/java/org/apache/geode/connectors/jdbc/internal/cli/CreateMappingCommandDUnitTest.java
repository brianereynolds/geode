/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.geode.connectors.jdbc.internal.cli;

import static org.apache.geode.connectors.jdbc.internal.cli.CreateMappingCommand.CREATE_MAPPING;
import static org.apache.geode.connectors.jdbc.internal.cli.CreateMappingCommand.CREATE_MAPPING__DATA_SOURCE_NAME;
import static org.apache.geode.connectors.jdbc.internal.cli.CreateMappingCommand.CREATE_MAPPING__PDX_NAME;
import static org.apache.geode.connectors.jdbc.internal.cli.CreateMappingCommand.CREATE_MAPPING__REGION_NAME;
import static org.apache.geode.connectors.jdbc.internal.cli.CreateMappingCommand.CREATE_MAPPING__TABLE_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import org.apache.geode.connectors.jdbc.internal.JdbcConnectorService;
import org.apache.geode.connectors.jdbc.internal.configuration.RegionMapping;
import org.apache.geode.distributed.internal.InternalLocator;
import org.apache.geode.internal.cache.InternalCache;
import org.apache.geode.management.internal.cli.util.CommandStringBuilder;
import org.apache.geode.test.dunit.rules.ClusterStartupRule;
import org.apache.geode.test.dunit.rules.MemberVM;
import org.apache.geode.test.junit.categories.JDBCConnectorTest;
import org.apache.geode.test.junit.rules.GfshCommandRule;
import org.apache.geode.test.junit.rules.serializable.SerializableTestName;

@Category({JDBCConnectorTest.class})
public class CreateMappingCommandDUnitTest {

  private static final String REGION_NAME = "testRegion";
  private static final String SUBREGION_NAME = REGION_NAME + "/testSubRegion";

  @Rule
  public transient GfshCommandRule gfsh = new GfshCommandRule();

  @Rule
  public ClusterStartupRule startupRule = new ClusterStartupRule();

  @Rule
  public SerializableTestName testName = new SerializableTestName();

  private MemberVM locator;
  private MemberVM server;

  @Before
  public void before() throws Exception {
    locator = startupRule.startLocatorVM(0);
    server = startupRule.startServerVM(1, locator.getPort());

    gfsh.connectAndVerify(locator);

    gfsh.executeAndAssertThat("create region --name=" + REGION_NAME + " --type=REPLICATE")
        .statusIsSuccess();
  }

  @Test
  public void createsMappingWithAllOptions() {
    CommandStringBuilder csb = new CommandStringBuilder(CREATE_MAPPING);
    csb.addOption(CREATE_MAPPING__REGION_NAME, REGION_NAME);
    csb.addOption(CREATE_MAPPING__DATA_SOURCE_NAME, "connection");
    csb.addOption(CREATE_MAPPING__TABLE_NAME, "myTable");
    csb.addOption(CREATE_MAPPING__PDX_NAME, "myPdxClass");

    gfsh.executeAndAssertThat(csb.toString()).statusIsSuccess();

    locator.invoke(() -> {
      String xml = InternalLocator.getLocator().getConfigurationPersistenceService()
          .getConfiguration("cluster").getCacheXmlContent();
      assertThat(xml).isNotNull().contains("jdbc:mapping");
    });

    server.invoke(() -> {
      InternalCache cache = ClusterStartupRule.getCache();
      RegionMapping mapping =
          cache.getService(JdbcConnectorService.class).getMappingForRegion(REGION_NAME);
      assertThat(mapping.getDataSourceName()).isEqualTo("connection");
      assertThat(mapping.getTableName()).isEqualTo("myTable");
      assertThat(mapping.getPdxName()).isEqualTo("myPdxClass");
    });
  }

  @Test
  public void createsRegionMappingUpdatesClusterConfig() {
    CommandStringBuilder csb = new CommandStringBuilder(CREATE_MAPPING);
    csb.addOption(CREATE_MAPPING__REGION_NAME, REGION_NAME);
    csb.addOption(CREATE_MAPPING__DATA_SOURCE_NAME, "connection");
    csb.addOption(CREATE_MAPPING__TABLE_NAME, "myTable");
    csb.addOption(CREATE_MAPPING__PDX_NAME, "myPdxClass");

    gfsh.executeAndAssertThat(csb.toString()).statusIsSuccess();

    locator.invoke(() -> {
      String xml = InternalLocator.getLocator().getConfigurationPersistenceService()
          .getConfiguration("cluster").getCacheXmlContent();
      assertThat(xml).isNotNull().contains("jdbc:mapping");
    });
  }

  @Test
  public void createExistingRegionMappingFails() {
    CommandStringBuilder csb = new CommandStringBuilder(CREATE_MAPPING);
    csb.addOption(CREATE_MAPPING__REGION_NAME, REGION_NAME);
    csb.addOption(CREATE_MAPPING__DATA_SOURCE_NAME, "connection");
    csb.addOption(CREATE_MAPPING__TABLE_NAME, "myTable");
    gfsh.executeAndAssertThat(csb.toString()).statusIsSuccess();

    csb = new CommandStringBuilder(CREATE_MAPPING);
    csb.addOption(CREATE_MAPPING__REGION_NAME, REGION_NAME);
    csb.addOption(CREATE_MAPPING__DATA_SOURCE_NAME, "connection");
    csb.addOption(CREATE_MAPPING__TABLE_NAME, "bogus");
    gfsh.executeAndAssertThat(csb.toString()).statusIsError();

    locator.invoke(() -> {
      String xml = InternalLocator.getLocator().getConfigurationPersistenceService()
          .getConfiguration("cluster").getCacheXmlContent();
      assertThat(xml).isNotNull().contains("jdbc:mapping").contains("myTable")
          .doesNotContain("bogus");
    });
  }
}
