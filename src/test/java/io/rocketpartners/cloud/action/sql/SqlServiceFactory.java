package io.rocketpartners.cloud.action.sql;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Arrays;
import java.util.List;

import io.rocketpartners.cloud.action.rest.RestAction;
import io.rocketpartners.cloud.model.Request;
import io.rocketpartners.cloud.model.Response;
import io.rocketpartners.cloud.service.Chain;
import io.rocketpartners.cloud.service.Service;
import io.rocketpartners.cloud.utils.Rows;
import io.rocketpartners.cloud.utils.SqlUtils;
import io.rocketpartners.cloud.utils.Utils;

public class SqlServiceFactory
{
   public static final List<Object[]> CONFIG_DBS_TO_TEST   = Arrays.asList(new Object[][]{{"h2"}, {"mysql"}});
   //public static final List<Object[]> CONFIG_DBS_TO_TEST   = Arrays.asList(new Object[][]{{"mysql"}});
   //public static final List<Object[]> CONFIG_DBS_TO_TEST   = Arrays.asList(new Object[][]{{"h2"}});

   public static final boolean        CONFIG_REBUILD_MYSQL = false;

   protected static Service           service              = null;

   protected static boolean shouldLoad(String db)
   {
      for (Object[] args : CONFIG_DBS_TO_TEST)
      {
         if (args[0].equals(db))
            return true;
      }
      return false;
   }

   static
   {
      //delete old h2 dbs
      Utils.delete(new File("./.h2"));
      //
      //      SqlUtils.addSqlListener(new SqlListener()
      //         {
      //
      //            @Override
      //            public void onError(String method, String sql, Object args, Exception ex)
      //            {
      //               // TODO Auto-generated method stub
      //
      //            }
      //
      //            @Override
      //            public void beforeStmt(String method, String sql, Object args)
      //            {
      //               //System.out.println("SQL---> " + sql.replace("\r", "").replace("\n", " ").trim().replaceAll(" +", " "));
      //            }
      //
      //            @Override
      //            public void afterStmt(String method, String sql, Object args, Exception ex, Object result)
      //            {
      //            }
      //         });
   }

   public static void main(String[] args) throws Exception
   {
      //SpringBoot.run(service(false));
   }

   public static synchronized Service service() throws Exception
   {
      return service(true);
   }

   public static synchronized Service service(boolean startup) throws Exception
   {
      if (service != null)
         return service;

      try
      {
         service = new Service()
            {
               @Override
               public void startup0()
               {
                  try
                  {
                     SqlDb sourceDb = createDb("source", "northwind-h2.ddl", "org.h2.Driver", "jdbc:h2:./.h2/northwind-source" + "-" + Utils.time(), "sa", "", "source/");

                     service.withApi("northwind")//
                            .makeEndpoint("GET,PUT,POST,DELETE", "source/", "*").withAction(new RestAction()).getApi()//
                            .withDb(sourceDb);

                     Connection conn = sourceDb.getConnection();

                     if (shouldLoad("h2"))
                     {
                        SqlDb h2Db = createDb("h2", "northwind-h2.ddl", "org.h2.Driver", "jdbc:h2:./.h2/northwind-h2" + "-" + Utils.time(), "sa", "", "h2/");

                        service.getApi("northwind")//
                               .makeEndpoint("GET,PUT,POST,DELETE", "h2/", "*").withAction(new RestAction()).getApi()//
                               .withDb(h2Db);
                     }

                     if (shouldLoad("mysql"))
                     {
                        SqlDb mysqlDb = null;
                        {
                           String mysqlUrl = "jdbc:mysql://testnorthwind.cb4bo9agap0y.us-east-1.rds.amazonaws.com:3306";

                           Class.forName("com.mysql.jdbc.Driver").newInstance();
                           Connection mysqlConn = null;
                           boolean sourceLoaded = false;
                           try
                           {
                              mysqlConn = DriverManager.getConnection(mysqlUrl, "testcase", "password");
                              SqlUtils.execute(mysqlConn, "USE northwindsource");
                              sourceLoaded = true;
                           }
                           catch (Exception ex)
                           {
                              ex.printStackTrace();
                           }
                           if (!sourceLoaded || CONFIG_REBUILD_MYSQL)
                           {
                              SqlUtils.runDdl(mysqlConn, SqlServiceFactory.class.getResourceAsStream("northwind-mysql-source.ddl"));
                           }

                           try
                           {
                              SqlUtils.runDdl(mysqlConn, SqlServiceFactory.class.getResourceAsStream("northwind-mysql-copy.ddl"));
                           }
                           catch (Exception ex)
                           {
                              ex.printStackTrace();
                              throw ex;
                           }
                           mysqlUrl += "/northwindcopy";
                           mysqlDb = createDb("mysql", null, "com.mysql.jdbc.Driver", mysqlUrl, "testcase", "password", "mysql/");

                           service.getApi("northwind")//
                                  .makeEndpoint("GET,PUT,POST,DELETE", "mysql/", "*")//
                                  .withAction(new RestAction()).getApi()//
                                  .withDb(mysqlDb);
                        }
                     }
                  }
                  catch (Exception ex)
                  {
                     ex.printStackTrace();
                     Utils.rethrow(ex);
                  }
               }

               public Chain service(Request req, Response res)
               {
                  Chain chain = super.service(req, res);

                  String debug = res.getDebug();
                  debug = debug.substring(0, debug.indexOf("<< response"));

                  System.out.print(debug);
                  return chain;
               }

               public Response service(String method, String url, String body)
               {
                  Response res = super.service(method, url, body);

                  if (Chain.size() == 1)
                  {
                     if (res.getChain().request().isGet())
                     {
                        if (res.find("meta.foundRows") == null)
                        {
                           System.out.println(res.getChain().request().getUrl());
                           System.out.println(res.meta());
                        }
                     }

                     if (!res.isSuccess())
                     {
                        System.out.println(res.getDebug());

                        if (res.getStatusCode() == 500)
                           System.exit(1);

                        if (res.getError() != null)
                           Utils.rethrow(res.getError());

                        throw new RuntimeException(res.getStatusMesg());
                     }
                  }

                  return res;
               }
            };

         if (startup)
         {
            service.startup();
         }
      }
      catch (

      Exception ex)
      {
         ex.printStackTrace();
         throw ex;
      }
      return service;
   }

   /**
    * This restores select tables to a default state so test case writers
    * can modify data and then have it restored before the next test case runs.
    * 
    * Tables restored include Orders, OrderDetails, IndexLog  
    *   
    * @param db
    * @throws Exception
    */
   public static void prepData(String db) throws Exception
   {
      Service service = service();
      SqlDb destDb = ((SqlDb) service.getApi("northwind").getDb(db));
      Connection destCon = destDb.getConnection();

      //-- Clear and restore the Order and OrderDetails tables.  OrderDetails must be
      //-- cleared first and restored second because of foreign key dependencies
      
      String orderDetailsTbl = destDb.getTable("OrderDetails").getName();
      String orderTbl = destDb.getTable("Orders").getName();

      SqlUtils.execute(destCon, "DELETE FROM " + destDb.quoteCol(orderDetailsTbl));
      SqlUtils.execute(destCon, "DELETE FROM " + destDb.quoteCol(orderTbl));
      
      
      int rows = SqlUtils.selectInt(destCon, "SELECT count(*) FROM " + destDb.quoteCol(destDb.getTable("Orders").getName()));
      Utils.assertEq(0, rows);

      SqlDb sourceDb = ((SqlDb) service.getApi("northwind").getDb("source"));
      Connection sourceConn = sourceDb.getConnection();

      Rows orders = SqlUtils.selectRows(sourceConn, "SELECT * FROM \"ORDERS\" WHERE \"SHIPNAME\" = ? OR \"CUSTOMERID\" = ?", "Blauer See Delikatessen", "HILAA");
      assertEquals(25, orders.size());

      SqlUtils.insertMaps(destCon, orderTbl, orders);
      assertEquals(25, SqlUtils.selectInt(destCon, "SELECT count(*) FROM " + destDb.quoteCol(orderTbl)));

      Rows orderDetails = SqlUtils.selectRows(sourceConn, "SELECT * FROM \"ORDERDETAILS\" WHERE \"ORDERID\" IN ( SELECT \"ORDERID\" FROM \"ORDERS\" WHERE \"SHIPNAME\" = ? OR \"CUSTOMERID\" = ?)", "Blauer See Delikatessen", "HILAA");
      assertEquals(59, orderDetails.size());

      SqlUtils.insertMaps(destCon, orderDetailsTbl, orderDetails);
      assertEquals(59, SqlUtils.selectInt(destCon, "SELECT count(*) FROM " + destDb.quoteCol(orderDetailsTbl)));
      
      //-- Restore the IndexLog table from source
      String indexLogTbl = destDb.getTable("IndexLog").getName();
      SqlUtils.execute(destCon, "DELETE FROM " + destDb.quoteCol(indexLogTbl));
      
      Rows indexLogs = SqlUtils.selectRows(sourceConn, "SELECT * FROM \"INDEXLOG\"");
      SqlUtils.insertMaps(destCon, indexLogTbl, indexLogs);
      
   }

   public static SqlDb createDb(String name, String ddl, String driver, String url, String user, String pass, String collectionPath)
   {
      SqlDb db = new SqlDb();
      db.withName(name);
      db.withDriver(driver);
      db.withUrl(url);
      db.withUser(user);
      db.withPass(pass);
      db.withCollectionPath(collectionPath);

      if (ddl != null)
      {
         try
         {
            Connection conn = db.getConnection();
            SqlUtils.runDdl(conn, SqlServiceFactory.class.getResourceAsStream(ddl));
            conn.commit();

            //System.out.println("INITIALIZING DB: " + name + " - " + ddl + " - " + url + " - " + SqlUtils.selectRows(conn, "SHOW TABLES") + " - " + SqlUtils.selectRows(conn, "SELECT CUSTOMERID FROM CUSTOMERS LIMIT 1"));
         }
         catch (Exception ex)
         {
            System.out.println("error initializing " + ddl);
            ex.printStackTrace();
            Utils.rethrow(ex);
         }
      }

      return db;
   }

}