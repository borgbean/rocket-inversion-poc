/*
 * Copyright (c) 2015-2018 Rocket Partners, LLC
 * http://rocketpartners.io
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package io.rocketpartners.cloud.action.elastic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.rocketpartners.cloud.model.ApiException;
import io.rocketpartners.cloud.model.Column;
import io.rocketpartners.cloud.model.Db;
import io.rocketpartners.cloud.model.ObjectNode;
import io.rocketpartners.cloud.model.Request;
import io.rocketpartners.cloud.model.Response;
import io.rocketpartners.cloud.model.Results;
import io.rocketpartners.cloud.model.SC;
import io.rocketpartners.cloud.model.Table;
import io.rocketpartners.cloud.rql.Term;
import io.rocketpartners.cloud.utils.HttpUtils;
import io.rocketpartners.cloud.utils.Rows.Row;
import io.rocketpartners.cloud.utils.Utils;

public class ElasticsearchDb extends Db<ElasticsearchDb>
{
   protected static String      url                      = null;

   protected static int         maxRequestDuration       = 10;                  // duration in seconds.

   protected static final int[] allowedFailResponseCodes = {400, 401, 403, 404};

   @Override
   protected void startup0()
   {
      reflectDb();
      configApi();
   }

   public Results<Row> select(Table table, List<Term> columnMappedTerms) throws Exception
   {
      return null;
   }

   public String upsert(Table table, Map<String, Object> values) throws Exception
   {
      return null;
   }

   public void delete(Table table, String entityKey) throws Exception
   {

   }

   private void reflectDb()
   {
      if (!isBootstrap())
      {
         return;
      }

      try
      {
         // 'GET _all' returns all indices/aliases/mappings
         Response allResp = HttpUtils.get(url + "/_all").get(maxRequestDuration, TimeUnit.SECONDS);

         if (allResp.isSuccess())
         {
            // we now have the indices, aliases for each index, and mappings (and settings if we need them)

            ObjectNode jsObj = Utils.parseJsonObject(allResp.getContent());

            Map<String, ObjectNode> jsContentMap = jsObj.asMap();

            // a map is needed when building tables to keep track of which alias'ed indexes, such as 'all', have previously been built.
            Map<String, Table> tableMap = new HashMap<String, Table>();

            for (Map.Entry<String, ObjectNode> entry : jsContentMap.entrySet())
            {
               // we now have the index and with it, it's aliases and mappings
               buildAliasTables(entry.getKey(), entry.getValue(), tableMap);
            }
         }
         else
         {
            throw new ApiException(SC.matches(allResp.getStatusCode(), allowedFailResponseCodes) ? SC.SC_MAP.get(allResp.getStatusCode()) : SC.SC_500_INTERNAL_SERVER_ERROR);
         }
      }
      catch (Exception ex)
      {
         Utils.rethrow(ex);
      }
   }

   private void configApi()
   {
      //      for (Table t : getTables())
      //      {
      //         List<Column> cols = t.getColumns();
      //         Collection collection = new Collection();
      //
      //         collection.withName(beautifyCollectionName(t.getName()));
      //
      //         Entity entity = new Entity();
      //         entity.withTable(t);
      //         entity.withHint(t.getName());
      //         entity.withCollection(collection);
      //
      //         collection.withEntity(entity);
      //
      //         for (Column col : cols)
      //         {
      //            Attribute attr = new Attribute();
      //            attr.withEntity(entity);
      //            attr.withName(col.getName());
      //            attr.withColumn(col);
      //            attr.withHint(col.getTable().getName() + "." + col.getName());
      //            attr.withType(col.getType());
      //
      //            entity.withAttribute(attr);
      //         }
      //
      //         api.withCollection(collection);
      //         collection.withApi(api);
      //      }
   }

   /**
    * At the time of writing, there is no need to parse settings.
    * This method creates tables based on alias names of 
    * elastic indexes.  If no alias exists, no table is created.
    * 
    * The name of the elastic index will be used as a table index.
    * Most tables will only have one index. An example of a 
    * table with multiple indexes would be the alias 'all'.
    * @param indexName
    * @param jsIndex
    * @return
    */
   private void buildAliasTables(String elasticName, ObjectNode jsIndex, Map<String, Table> tableMap)
   {

      String aliasName = null;
      Map<String, ObjectNode> jsMappingsDocProps = jsIndex.getNode("mappings").getNode("_doc").getNode("properties").asMap();
      Map<String, ObjectNode> jsAliasProps = jsIndex.getNode("aliases").asMap();
      for (Map.Entry<String, ObjectNode> propEntry : jsAliasProps.entrySet())
      {
         aliasName = propEntry.getKey();

         Table table = null;

         // use the previously created table if it exists.
         if (tableMap.containsKey(aliasName))
            table = tableMap.get(aliasName);
         else
         {
            table = new Table(this, aliasName);
            tableMap.put(aliasName, table);
         }

         withTable(table);

         // use the mapping to add columns to the table.
         addColumns(table, false, jsMappingsDocProps, "");
      }
   }

   /**
    * @param table - add the column to this table
    * @param nullable - lets the column nullable
    * @param jsPropsMap - contains the parent's nested properties
    * @param parentPrefix - necessary for 'nested' column names.
    */
   private void addColumns(Table table, boolean nullable, Map<String, ObjectNode> jsPropsMap, String parentPrefix)
   {
      int columnNumber = 0;
      for (Map.Entry<String, ObjectNode> propEntry : jsPropsMap.entrySet())
      {
         columnNumber += 1;

         String colName = parentPrefix + propEntry.getKey();
         ObjectNode propValue = propEntry.getValue();

         // potential types include: keyword, long, nested, object, boolean
         Column column = null;
         if (propValue.containsKey("type"))
         {
            column = new Column(table, columnNumber, colName, propValue.getString("type"), true);
            table.withColumn(column);
         }
      }
   }

   public static String getURL()
   {
      return url;
   }

}
