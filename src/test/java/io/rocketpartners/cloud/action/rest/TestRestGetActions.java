package io.rocketpartners.cloud.action.rest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringEscapeUtils;
import org.junit.Test;

import io.rocketpartners.cloud.model.ArrayNode;
import io.rocketpartners.cloud.model.ObjectNode;
import io.rocketpartners.cloud.model.Response;
import io.rocketpartners.cloud.service.Service;
import io.rocketpartners.cloud.utils.Utils;
import junit.framework.TestCase;

public abstract class TestRestGetActions extends TestCase
{
   protected abstract String collectionPath();

   protected abstract Service service() throws Exception;

   /**
    * Returns the last response handled by the service.
    * This is needed for subclasses to decorate test methods
    * @return
    */
   protected Response response() throws Exception
   {
      return service().response();
   }

   protected String url(String path)
   {
      if (path.startsWith("http"))
         return path;

      String cp = collectionPath();

      if (cp.length() == 0)
         return path;

      if (!cp.endsWith("/"))
         cp += "/";

      while (path.startsWith("/"))
         path = path.substring(1, path.length());

      //      if (path.indexOf(cp) > -1 || path.startsWith("http"))
      //         return path;

      return "http://localhost/" + cp + path;
   }

   @Test
   public void testLimit01() throws Exception
   {
      Service service = service();
      Response res = null;
      ObjectNode json = null;

      res = service.get(url("orders?limit=5")).statusOk();
      json = res.getJson();
      assertEquals(5, json.find("meta.pageSize"));
      assertEquals(5, res.data().length());

      res.assertDebug("[1]: SQL ->", "'SELECT \"ORDERS\".* FROM \"ORDERS\" ORDER BY \"ORDERS\".\"ORDERID\" ASC OFFSET 0 LIMIT 5' args=[] error=''");
      res.assertDebug("DynamoDb", "ScanSpec maxPageSize=5 scanIndexForward=true nameMap={} valueMap={} keyConditionExpression='' filterExpression='' projectionExpression=''");
   }

   @Test
   public void testSort01() throws Exception
   {
      Service service = service();
      Response res = null;

      String url = url("orders?limit=2&sort=orderid");

      res = service.get(url);
      res.assertDebug("DynamoDb", "ScanSpec:'gs3' maxPageSize=2 scanIndexForward=true nameMap={} valueMap={} keyConditionExpression='' filterExpression='' projectionExpression=''");
      res.assertDebug("h2", "'SELECT \"ORDERS\".* FROM \"ORDERS\" ORDER BY \"ORDERS\".\"ORDERID\" ASC OFFSET 0 LIMIT 2' args=[] error=''");

      assertEquals(2, res.data().length());

      String href = res.findString("data.0.href");
      assertTrue(href.indexOf("/orders/10248") > 0);

      res = service.get(url("orders?limit=2&sort=-orderid&type=ORDER"));
      res.assertDebug("DynamoDb", "QuerySpec:'gs3' maxPageSize=2 scanIndexForward=false nameMap={#var1=sk} valueMap={:val1=ORDER} keyConditionExpression='(#var1 = :val1)' filterExpression='' projectionExpression=''");
      res.assertDebug("h2", "'SELECT \"ORDERS\".* FROM \"ORDERS\" ORDER BY \"ORDERS\".\"ORDERID\" DESC OFFSET 0 LIMIT 2' args=[] error=''");

      assertEquals(2, res.data().length());
      href = res.findString("data.0.href");
      assertTrue(href.indexOf("/orders/11077") > 0);

   }

   @Test
   public void testPagination01() throws Exception
   {
      Service service = service();
      Response res = null;

      //--
      //-- gets a sorted list of all hrefs
      res = service.get(url("orders?type=ORDER&sort=orderId&includes=href&limit=1000"));
      List<String> hrefs = new ArrayList();
      res.data().forEach(o -> hrefs.add(((ObjectNode) o).getString("href")));

      //makes sure they are all unqique
      Set uniqueHrefs = new HashSet(hrefs);
      assertEquals(hrefs.size(), uniqueHrefs.size());

      //makes sure they are sorted
      List sortedHrefs = new ArrayList(hrefs);
      Collections.sort(hrefs);
      assertEquals(hrefs.toString(), sortedHrefs.toString());

      int total = 0;
      int pages = 0;

      String start = url("orders?limit=127&sort=orderId");
      String next = start;

      Set alreadyFound = new HashSet();
      do
      {
         res = service.get(next);

         next = next.toLowerCase();
         assertTrue("There should be only one pagenum param", next.indexOf("pagenum") == next.lastIndexOf("pagenum"));
         assertTrue("There should be only one page param", next.indexOf("page") == next.lastIndexOf("page"));
         assertTrue("There should be only one offset param", next.indexOf("offset") == next.lastIndexOf("offset"));
         assertTrue("There should be only one limit param", next.indexOf("limit") == next.lastIndexOf("limit"));
         assertTrue("There should be only one after param", next.indexOf("after") == next.lastIndexOf("after"));

         if (res.data().size() == 0)
            break;

         //makes sure the indexing is correct
         int idx = pages * 127;
         String found = res.findString("data.0.href");
         String shouldBe = hrefs.get(idx);
         if (!shouldBe.equals(found)// 
               || alreadyFound.contains(found))
         {
            http : //localhost/northwind/dynamodb/orders?limit=127&sort=orderId&after(type,ORDER,orderId,10374)
            //GET: http://localhost/northwind/dynamodb/orders?limit=127&sort=orderId&after(type,ORDER,orderId,10374)&after(type,ORDER,orderId,10501)&after(type,ORDER,orderId,10628)&after(type,ORDER,orderId,10755)

            System.out.println("Request  : " + next);
            System.out.println("Index    : " + idx);
            System.out.println("Expected : " + shouldBe);
            System.out.println("Found    : " + found);
            System.out.println("The Found Href is in the sorted list at index: " + sortedHrefs.indexOf(found));

            res.dump();
            fail();
            //assertEquals(shouldBe, found);
         }
         alreadyFound.add(found);

         total += res.data().length();
         pages += 1;

         next = res.next();

         if (next != null)
         {
            assertEquals(127, res.data().length());
            assertEquals(127, res.find("meta.pageSize"));
         }
      }
      while (pages < 200 && next != null);

      assertEquals(7, pages);
      assertEquals(830, total);
   }

   @Test
   public void testEq01() throws Exception
   {
      Service service = service();
      Response res = null;
      ObjectNode json = null;
      res = service.get(url("orders?eq(orderid,10257)"));
      res.dump();
      json = res.getJson();
      assertEquals(1, res.data().size());
      assertTrue(res.findString("data.0.orderid").equals("10257"));

      res.assertDebug("DynamoDb", "QuerySpec:'Primary Index' maxPageSize=100 scanIndexForward=true nameMap={#var1=hk} valueMap={:val1=10257} keyConditionExpression='(#var1 = :val1)' filterExpression='' projectionExpression=''");
      res.assertDebug("h2", "'SELECT \"ORDERS\".* FROM \"ORDERS\" WHERE \"ORDERS\".\"ORDERID\" = ? ORDER BY \"ORDERS\".\"ORDERID\" ASC OFFSET 0 LIMIT 100' args=[10257] error=''");
      // 

   }

   @Test
   public void testLike01() throws Exception
   {
      Service service = service();
      Response res = null;

      res = service.get(url("orders?limit=5&like(customerId,*VI*)")).statusOk();
      res.assertDebug("h2", "'SELECT \"ORDERS\".* FROM \"ORDERS\" WHERE \"ORDERS\".\"CUSTOMERID\" LIKE ? ORDER BY \"ORDERS\".\"ORDERID\" ASC OFFSET 0 LIMIT 5' args=[%VI%]");

      ArrayNode data = res.data();
      assertTrue(data.length() > 0);
      for (Object o : data)
      {
         assertTrue(((ObjectNode) o).getString("customerid").contains("VI"));
      }

      res = service.get(url("orders?limit=5&like(customerId,VI)")).statusOk();
      res.assertDebug("h2", "'SELECT \"ORDERS\".* FROM \"ORDERS\" WHERE \"ORDERS\".\"CUSTOMERID\" = ? ORDER BY \"ORDERS\".\"ORDERID\" ASC OFFSET 0 LIMIT 5' args=[VI]");

   }

   @Test
   public void testLike02() throws Exception
   {
      Service service = service();
      Response res = null;
      ObjectNode json = null;

      res = service.get(url("orders?limit=5&like(customerId,*ZZ*)")).statusOk();
      json = res.getJson();
      assertTrue(json.getArray("data").length() == 0);
      res.assertDebug("h2", "'SELECT \"ORDERS\".* FROM \"ORDERS\" WHERE \"ORDERS\".\"CUSTOMERID\" LIKE ? ORDER BY \"ORDERS\".\"ORDERID\" ASC OFFSET 0 LIMIT 5' args=[%ZZ%]");
   }

   @Test
   public void testW01() throws Exception
   {
      Service service = service();
      Response res = null;
      res = service.get(url("employees?w(city,ondon)")).statusOk();
      res.assertDebug("h2", "'SELECT \"EMPLOYEES\".* FROM \"EMPLOYEES\" WHERE \"EMPLOYEES\".\"CITY\" LIKE ? ORDER BY \"EMPLOYEES\".\"EMPLOYEEID\" ASC OFFSET 0 LIMIT 100' args=[%ondon%] error=''");

      assertEquals(4, res.data().length());
      for (Object obj : res.data())
      {
         assertTrue(((ObjectNode) obj).getString("city").contains("ondon"));
      }

      res = service.get(url("employees?w(city,*ondon*)")).statusOk();
      res.assertDebug("h2", "'SELECT \"EMPLOYEES\".* FROM \"EMPLOYEES\" WHERE \"EMPLOYEES\".\"CITY\" LIKE ? ORDER BY \"EMPLOYEES\".\"EMPLOYEEID\" ASC OFFSET 0 LIMIT 100' args=[%ondon%] error=''");
   }

   @Test
   public void testWo01() throws Exception
   {
      Service service = service();
      Response res = null;
      ObjectNode json = null;
      ArrayNode data = null;

      res = service.get(url("orders?eq(employeeid,5)"));
      //Utils.assertDebug(res, "DynamoDb", "");
      //Utils.assertDebug(res, "h2", "");

      json = res.getJson();
      assertEquals(42, json.find("meta.foundRows"));

      res = service.get(url("orders?eq(employeeid,5)&w(shipcountry,witze)"));
      res.assertDebug("h2", "'SELECT \"ORDERS\".* FROM \"ORDERS\" WHERE \"ORDERS\".\"EMPLOYEEID\" = ? AND \"ORDERS\".\"SHIPCOUNTRY\" LIKE ? ORDER BY \"ORDERS\".\"ORDERID\" ASC OFFSET 0 LIMIT 100' args=[5, %witze%]");
      assertTrue(res.data().size() > 0);

      res = service.get(url("orders?eq(employeeid,5)&wo(shipcountry,witze)"));
      res.assertDebug("h2", "'SELECT \"ORDERS\".* FROM \"ORDERS\" WHERE \"ORDERS\".\"EMPLOYEEID\" = ? AND (NOT (\"ORDERS\".\"SHIPCOUNTRY\" LIKE ?)) ORDER BY \"ORDERS\".\"ORDERID\" ASC OFFSET 0 LIMIT 100' args=[5, %witze%]");

      json = res.getJson();
      data = json.getArray("data");
      assertEquals(41, json.find("meta.foundRows"));
      for (Object o : data)
      {
         assertFalse(((ObjectNode) o).getString("shipcountry").contains("witze"));
      }
   }

   @Test
   public void testSw01() throws Exception
   {
      Service service = service();
      Response res = null;
      res = service.get(url("orders?limit=5&sw(customerId,VI)")).statusOk();

      res.assertDebug("DynamoDb", "ScanSpec maxPageSize=5 scanIndexForward=true nameMap={#var1=customerId} valueMap={:val1=VI} keyConditionExpression='' filterExpression='begins_with(#var1,:val1)'");
      res.assertDebug("h2", "'SELECT \"ORDERS\".* FROM \"ORDERS\" WHERE \"ORDERS\".\"CUSTOMERID\" LIKE ? ORDER BY \"ORDERS\".\"ORDERID\" ASC OFFSET 0 LIMIT 5' args=[VI%]");

      ArrayNode data = res.data();
      assertTrue(data.length() > 0);
      for (Object o : data)
      {
         assertTrue(((ObjectNode) o).getString("customerid").startsWith("VI"));
      }

      //check that the trailing * is not doubled
      res = service.get(url("orders?limit=5&sw(customerId,VI*)")).statusOk();

      res.assertDebug("DynamoDb", "ScanSpec maxPageSize=5 scanIndexForward=true nameMap={#var1=customerId} valueMap={:val1=VI} keyConditionExpression='' filterExpression='begins_with(#var1,:val1)'");
      res.assertDebug("h2", "'SELECT \"ORDERS\".* FROM \"ORDERS\" WHERE \"ORDERS\".\"CUSTOMERID\" LIKE ? ORDER BY \"ORDERS\".\"ORDERID\" ASC OFFSET 0 LIMIT 5' args=[VI%]");
   }

   @Test
   public void testSw02() throws Exception
   {
      Service service = service();
      Response res = null;

      res = service.get(url("orders?limit=5&sw(customerId,Z)")).statusOk();

      res.assertDebug("DynamoDb", "ScanSpec maxPageSize=5 scanIndexForward=true nameMap={#var1=customerId} valueMap={:val1=Z} keyConditionExpression='' filterExpression='begins_with(#var1,:val1)'");
      res.assertDebug("h2", "'SELECT \"ORDERS\".* FROM \"ORDERS\" WHERE \"ORDERS\".\"CUSTOMERID\" LIKE ? ORDER BY \"ORDERS\".\"ORDERID\" ASC OFFSET 0 LIMIT 5' args=[Z%]");

      assertTrue(res.data().size() == 0);
   }

   @Test
   public void testEw01() throws Exception
   {
      Service service = service();
      Response res = null;
      res = service.get(url("orders?ew(shipname,Chevalier)"));
      res.assertDebug("h2", "'SELECT \"ORDERS\".* FROM \"ORDERS\" WHERE \"ORDERS\".\"SHIPNAME\" LIKE ? ORDER BY \"ORDERS\".\"ORDERID\" ASC OFFSET 0 LIMIT 100' args=[%Chevalier]");

      ArrayNode data = res.data();
      assertTrue(data.size() > 0);
      for (Object o : data)
      {
         assertTrue(((ObjectNode) o).getString("shipname").endsWith("Chevalier"));
      }

      //check that the leading * is not doubled
      res = service.get(url("orders?ew(shipname,*Chevalier)"));
      res.assertDebug("h2", "'SELECT \"ORDERS\".* FROM \"ORDERS\" WHERE \"ORDERS\".\"SHIPNAME\" LIKE ? ORDER BY \"ORDERS\".\"ORDERID\" ASC OFFSET 0 LIMIT 100' args=[%Chevalier]");
   }

   @Test
   public void testN01() throws Exception
   {
      Service service = service();
      Response res = null;
      ObjectNode json = null;
      ArrayNode data = null;
      res = service.get(url("orders?limit=500&n(shipregion)"));

      //DynamoDb  ScanSpec maxPageSize=500 scanIndexForward=true nameMap={#var1=shipregion, #var2=shipregion} valueMap={:val1=null} keyConditionExpression='' filterExpression='(attribute_not_exists(#var1) or (#var2 = :val1))' projectionExpression=''

      res.assertDebug("DynamoDb", "ScanSpec maxPageSize=500 scanIndexForward=true nameMap={#var1=shipregion, #var2=shipregion} valueMap={:val1=null} keyConditionExpression='' filterExpression='(attribute_not_exists(#var1) or (#var2 = :val1))'");
      res.assertDebug("h2", "'SELECT \"ORDERS\".* FROM \"ORDERS\" WHERE \"ORDERS\".\"SHIPREGION\" IS NULL ORDER BY \"ORDERS\".\"ORDERID\" ASC OFFSET 0 LIMIT 500'");

      res.statusOk();
      json = res.getJson();
      data = json.getArray("data");
      assertTrue(data.length() > 0);
      for (Object o : data)
      {
         String shipRegion = ((ObjectNode) o).getString("shipRegion");
         if (!"null".equalsIgnoreCase(shipRegion + ""))
         {
            System.out.println("should be null: '" + StringEscapeUtils.escapeJava(shipRegion) + "'");
            fail("should be null: '" + StringEscapeUtils.escapeJava(shipRegion) + "'");
         }
      }
   }

   @Test
   public void testN02() throws Exception
   {
      Service service = service();
      assertTrue(service.get(url("orders?limit=5&nn(shipcountry)")).statusOk().data().size() > 0);
      assertTrue(service.get(url("orders?limit=5&n(shipcountry)")).statusOk().data().size() == 0);
   }

   @Test
   public void testEmp01() throws Exception
   {
      Service service = service();
      Response res = null;

      res = service.get(url("orders?limit=500&nemp(shipregion)"));
      assertTrue(res.data().size() > 0);
      res.assertDebug("DynamoDb", "ScanSpec maxPageSize=500 scanIndexForward=true nameMap={#var1=shipregion, #var2=shipregion} valueMap={:val1=null} keyConditionExpression='' filterExpression='attribute_exists(#var1) and (#var2 <> :val1)' projectionExpression=''");
      res.assertDebug("h2", "'SELECT \"ORDERS\".* FROM \"ORDERS\" WHERE (\"ORDERS\".\"SHIPREGION\" IS NOT NULL AND \"ORDERS\".\"SHIPREGION\" != '') ORDER BY \"ORDERS\".\"ORDERID\" ASC OFFSET 0 LIMIT 500'");

      List<ObjectNode> list = res.data().asList();
      for (ObjectNode result : list)
      {
         String shipregion = result.getString("shipregion");
         assertTrue("shipregion not supposed to be empty but was: '" + shipregion + "'", !Utils.empty(shipregion));
      }

      res = service.get(url("orders?limit=500&emp(shipregion)"));
      res.assertDebug("DynamoDb", "ScanSpec maxPageSize=500 scanIndexForward=true nameMap={#var1=shipregion, #var2=shipregion} valueMap={:val1=null} keyConditionExpression='' filterExpression='(attribute_not_exists(#var1) or (#var2 = :val1))' projectionExpression=''");
      res.assertDebug("h2", "'SELECT \"ORDERS\".* FROM \"ORDERS\" WHERE (\"ORDERS\".\"SHIPREGION\" IS NULL OR \"ORDERS\".\"SHIPREGION\" = '') ORDER BY \"ORDERS\".\"ORDERID\" ASC OFFSET 0 LIMIT 500'");

      list = res.data().asList();
      for (ObjectNode result : list)
      {
         String shipregion = result.getString("shipregion");
         assertTrue("shipregion was supposed to be empty but was: '" + shipregion + "'", Utils.empty(shipregion));
      }
   }

   @Test
   public void testNn01() throws Exception
   {
      Service service = service();
      Response res = null;
      ObjectNode json = null;
      ArrayNode data = null;

      res = service.get(url("orders?limit=500&nn(shipregion)")).statusOk();

      res.assertDebug("DynamoDb", "ScanSpec maxPageSize=500 scanIndexForward=true nameMap={#var1=shipregion, #var2=shipregion} valueMap={:val1=null} keyConditionExpression='' filterExpression='attribute_exists(#var1) and (#var2 <> :val1)' projectionExpression=''");
      res.assertDebug("h2", "'SELECT \"ORDERS\".* FROM \"ORDERS\" WHERE \"ORDERS\".\"SHIPREGION\" IS NOT NULL ORDER BY \"ORDERS\".\"ORDERID\" ASC OFFSET 0 LIMIT 500' args=[]");

      json = res.getJson();
      data = json.getArray("data");
      assertTrue(data.length() > 0);
      for (Object o : data)
      {
         assertNotNull(((ObjectNode) o).getString("shipregion"));
      }
   }

   @Test
   public void testNemp01() throws Exception
   {
      Service service = service();
      Response res = null;

      res = service.get(url("orders?limit=1&emp(shipregion)"));
      res.assertDebug("DynamoDb", "ScanSpec maxPageSize=1 scanIndexForward=true nameMap={#var1=shipregion, #var2=shipregion} valueMap={:val1=null} keyConditionExpression='' filterExpression='(attribute_not_exists(#var1) or (#var2 = :val1))' projectionExpression=''");
      res.assertDebug("h2", "'SELECT \"ORDERS\".* FROM \"ORDERS\" WHERE (\"ORDERS\".\"SHIPREGION\" IS NULL OR \"ORDERS\".\"SHIPREGION\" = '') ORDER BY \"ORDERS\".\"ORDERID\" ASC OFFSET 0 LIMIT 1' args=[]");

      assertTrue(res.data().size() > 0);

      //SELECT * FROM "ORDERS" WHERE ("SHIPREGION" IS NOT NULL AND "SHIPREGION" != '')
      res = service.get(url("orders?limit=500&nemp(shipregion)"));
      res.assertDebug("DynamoDb", "ScanSpec maxPageSize=500 scanIndexForward=true nameMap={#var1=shipregion, #var2=shipregion} valueMap={:val1=null} keyConditionExpression='' filterExpression='attribute_exists(#var1) and (#var2 <> :val1)' projectionExpression=''");
      res.assertDebug("h2", "'SELECT \"ORDERS\".* FROM \"ORDERS\" WHERE (\"ORDERS\".\"SHIPREGION\" IS NOT NULL AND \"ORDERS\".\"SHIPREGION\" != '') ORDER BY \"ORDERS\".\"ORDERID\" ASC OFFSET 0 LIMIT 500' args=[] error=''");

      List<ObjectNode> list = res.data().asList();
      for (ObjectNode result : list)
      {
         String shipregion = result.getString("shipregion");
         assertFalse("shipregion was not supposed to be empty but was: '" + shipregion + "'", Utils.empty(shipregion));
      }
   }

   @Test
   public void testIn01() throws Exception
   {
      Service service = service();
      Response res = null;
      res = service.get(url("orders?in(orderid,10249,10258,10252)"));

      //ScanSpec maxPageSize=100 scanIndexForward=true nameMap={#var1=hk} valueMap={:val1=10249, :val2=10258, :val3=10252} keyConditionExpression='' filterExpression='(#var1 IN (:val1, :val2, :val3))' projectionExpression=''
      res.assertDebug("DynamoDb", "ScanSpec maxPageSize=100 scanIndexForward=true nameMap={#var1=hk} valueMap={:val1=10249, :val2=10258, :val3=10252} keyConditionExpression='' filterExpression='(#var1 IN (:val1, :val2, :val3))' projectionExpression=''");
      res.assertDebug("h2", "'SELECT \"ORDERS\".* FROM \"ORDERS\" WHERE \"ORDERS\".\"ORDERID\" IN(?, ?, ?) ORDER BY \"ORDERS\".\"ORDERID\" ASC OFFSET 0 LIMIT 100' args=[10249, 10258, 10252]");

      ArrayNode data = res.data();
      List<String> list = Arrays.asList("10249", "10258", "10252");
      assertEquals(3, data.length());
      for (Object obj : data)
      {
         assertTrue(list.contains(((ObjectNode) obj).getString("orderId")));
      }
   }

   @Test
   public void testOut01() throws Exception
   {
      Service service = service();
      Response res = service.get(url("orders?out(orderid,10249,10258,10252)"));
      res.assertDebug("DynamoDb", "ScanSpec maxPageSize=100 scanIndexForward=true nameMap={#var1=hk} valueMap={:val1=10249, :val2=10258, :val3=10252} keyConditionExpression='' filterExpression='(NOT #var1 IN (:val1, :val2, :val3))' projectionExpression=''");
      res.assertDebug("h2", "'SELECT \"ORDERS\".* FROM \"ORDERS\" WHERE \"ORDERS\".\"ORDERID\" NOT IN(?, ?, ?) ORDER BY \"ORDERS\".\"ORDERID\" ASC OFFSET 0 LIMIT 100' args=[10249, 10258, 10252]");

      Set ids = new HashSet(Utils.explode(",", "10249,10258,10252"));
      for (Object obj : res.data())
      {
         assertFalse(ids.contains(((ObjectNode) obj).find("orderId").toString()));
      }
   }

   @Test
   public void testLt01() throws Exception
   {
      Service service = service();
      Response res = null;

      res = service.get(url("orders?limit=1000&gt(freight,2)"));
      assertEquals(777, res.data().size());
      res.assertDebug("DynamoDb", "ScanSpec maxPageSize=1000 scanIndexForward=true nameMap={#var1=freight} valueMap={:val1=2} keyConditionExpression='' filterExpression='(#var1 > :val1)' projectionExpression=''");
      res.assertDebug("h2", "'SELECT \"ORDERS\".* FROM \"ORDERS\" WHERE \"ORDERS\".\"FREIGHT\" > ? ORDER BY \"ORDERS\".\"ORDERID\" ASC OFFSET 0 LIMIT 1000' args=[2]");

      res = service.get(url("orders?limit=1000&lt(freight,2)"));
      res.assertDebug("DynamoDb", "ScanSpec maxPageSize=1000 scanIndexForward=true nameMap={#var1=freight} valueMap={:val1=2} keyConditionExpression='' filterExpression='(#var1 < :val1)' projectionExpression=''");
      res.assertDebug("h2", "'SELECT \"ORDERS\".* FROM \"ORDERS\" WHERE \"ORDERS\".\"FREIGHT\" < ? ORDER BY \"ORDERS\".\"ORDERID\" ASC OFFSET 0 LIMIT 1000' args=[2]");

      ArrayNode data = res.data();
      assertTrue(data.size() > 0);
      for (Object o : data)
      {
         assertTrue(Float.parseFloat(((ObjectNode) o).getString("freight")) < 2);
      }
   }

   @Test
   public void testLe01() throws Exception
   {
      Service service = service();
      Response res = null;

      res = service.get(url("orders?limit=1000&lt(freight,2.94)"));
      int found = res.data().size();
      assertTrue(found > 0);

      res = service.get(url("orders?limit=1000&eq(freight,2.94)"));
      int found2 = res.data().size();
      assertTrue(found2 > 0);

      res = service.get(url("orders?limit=1000&le(freight,2.94)"));
      int found3 = res.data().size();
      res.dump();
      assertEquals(found + found2, found3);

      ArrayNode data = res.data();
      assertTrue(data.size() > 0);
      for (Object o : data)
      {
         float val = Float.parseFloat(((ObjectNode) o).getString("freight"));
         if (val > 2.94f)
         {
            fail("Value is greater than threshold: " + val);
         }
      }
   }

}
