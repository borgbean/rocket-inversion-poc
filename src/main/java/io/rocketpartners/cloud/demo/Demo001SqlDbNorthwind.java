/*
 * Copyright (c) 2015-2019 Rocket Partners, LLC
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
package io.rocketpartners.cloud.demo;

import io.rocketpartners.cloud.action.rest.RestAction;
import io.rocketpartners.cloud.action.sql.DescribeSqlApiAction;
import io.rocketpartners.cloud.action.sql.H2SqlDb;
import io.rocketpartners.cloud.action.sql.SqlDb;
import io.rocketpartners.cloud.model.*;
import io.rocketpartners.cloud.service.Chain;
import io.rocketpartners.cloud.service.Inversion;
import io.rocketpartners.cloud.service.Service;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * This simple demo launches an API that exposes SQL database tables 
 * as REST collection endpoints.  The demo supports full GET,PUT,POST,DELETE
 * operations with an extensive Resource Query Language (RQL) for GET
 * requests.
 * <br>  
 * The demo connects to an in memory H2 sql db that gets initialized from
 * scratch each time this demo is run.  That means you can fully explore
 * modifying operations (PUT,POST,DELETE) and 'break' whatever you want
 * then restart and have a clean demo app again.
 * <br>
 * If you want to explore your own JDBC DB, you can swap the "withDb()"
 * line below with the commented out one and fill in your connection info.
 * Currently, Inversion only ships with MySQL drivers out of the box but
 * has SQL syntax support for MySQL, SqlServer, and PostgreSQL.
 * <br>
 * Northwind is a demo db that has shipped with various Microsoft products
 * for years.  Some of its table designs seem strange or antiquated 
 * compared to modern conventions but it makes a great demo and test
 * specifically because it shows how Inversion can accommodate a broad
 * range of database design patterns.  
 * 
 * @see Demo1SqlDbNorthwind.ddl for more details on the db
 * @see https://github.com/RocketPartners/rocket-inversion for more information 
 *      on building awesome APIs with Inversion
 *  
 * @author wells
 *
 */
public class Demo001SqlDbNorthwind
{
   /**
    * This simple factory method is static so that other  
    * demos can use and extend this api configuration.
    */
   public static Api buildApi()
   {
      return new Api("northwind")//
                .withName("northwind")
                .withDb(new H2SqlDb("db", "DemoSqlDbNorthwind1.db", Demo001SqlDbNorthwind.class.getResource("northwind.h2.ddl").toString()))//
                //.withDb(new SqlDb("db", "YOUR_JDBC_DRIVER", "YOUR_JDBC_URL", "YOUR_JDBC_USERNAME", "YOUR_JDBC_PASSWORD")))//
                //add endpoint for our 'describe api' call - we prefix table queries below with /data/ to make this work.
                .withEndpoint("GET", "/describe_api", new DescribeSqlApiAction())
                //simple action here to just serve the HTML I added to resources, done anonymously
                .withEndpoint("GET", "/schema_browser", new Action() {
                    @Override
                    public void run(Service service, Api api, Endpoint endpoint, Chain chain, Request req, Response res) throws Exception {
                        res.withStatus(SC.SC_200_OK);
                        StringBuilder out = new StringBuilder();
                        try (BufferedReader r = new BufferedReader(new InputStreamReader(new ClassPathResource("io/rocketpartners/cloud/demo/schema_explorer.html").getInputStream()))) {
                            String line;
                            while((line=r.readLine()) != null) {
                                out.append(line);
                                out.append("\n");
                            }
                        }

                        //NOTE: the internal system which routes responses doesn't like me doing this (it logs an error),
                        //but ideally you'd have this HTML on a static server or something anyway, and it makes it
                        //easier to run the demo.
                        res.withJson(null);
                        res.withOutput(out.toString());
                    }
                })
                .withEndpoint("GET,PUT,POST,DELETE", "/data/*", new RestAction());
    }

   public static void main(String[] args) throws Exception
   {
      Inversion.run(buildApi());
      
      System.out.println("\r\n");
      System.out.println("Your API is running at 'http://localhost:8080/northwind'.");
      System.out.println("REST collection endpoints have been created for each db entity");
      System.out.println("");
      System.out.println("You can get started by exploring some of these urls:");
      System.out.println("  - GET http://localhost:8080/northwind/products");
      System.out.println("  - GET http://localhost:8080/northwind/orders?expands=orderDetails&page=2");
      System.out.println("  - GET http://localhost:8080/northwind/customers?in(country,France,Spain)&sort=-customerid&pageSize=10");
      System.out.println("  - GET http://localhost:8080/northwind/customers?orders.shipCity=Mannheim");
      System.out.println("");
      System.out.println("Append '&explain=true' to any query string to see an explanation of what is happening under the covers");
      System.out.println("  - GET http://localhost:8080/northwind/employees?title='Sales Representative'&sort=employeeid&pageSize=2&page=2&explain=true");
      System.out.println("");
      System.out.println("See https://github.com/RocketPartners/rocket-inversion for more information on building awesome APIs with Inversion");

   }

}
