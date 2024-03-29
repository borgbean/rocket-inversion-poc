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
package io.rocketpartners.cloud.action.elastic.v03x;

import io.rocketpartners.cloud.model.Action;
import io.rocketpartners.cloud.model.Api;
import io.rocketpartners.cloud.model.ApiException;
import io.rocketpartners.cloud.model.Endpoint;
import io.rocketpartners.cloud.model.Request;
import io.rocketpartners.cloud.model.Response;
import io.rocketpartners.cloud.model.SC;
import io.rocketpartners.cloud.service.Chain;
import io.rocketpartners.cloud.service.Service;

/**
 * 
 * @author kfrankic
 *
 */
public class ElasticDbRestAction extends Action
{
   ElasticDbGetAction get = new ElasticDbGetAction();
   //   ElasticDbDeleteHandler delete = new ElasticDbDeleteHandler();
   //   ElasticDbPostHandler   post   = new ElasticDbPostHandler();

   @Override
   public void run(Service service, Api api, Endpoint endpoint, Chain chain, Request req, Response res) throws Exception
   {
      String method = req.getMethod();
      if ("GET".equalsIgnoreCase(method))
      {
         get.run(service, api, endpoint, chain, req, res);
      }
      //      else if ("DELETE".equalsIgnoreCase(method))
      //      {
      //         delete.service(service, api, endpoint, action, chain, req, res);
      //      }
      //      else if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method))
      //      {
      //         post.service(service, api, endpoint, action, chain, req, res);
      //      }
      else
      {
         throw new ApiException(SC.SC_400_BAD_REQUEST, "This handler only supports GET requests");
      }
   }

   public void setGet(ElasticDbGetAction get)
   {
      this.get = get;
   }

   //   public void setDelete(ElasticDbDeleteHandler delete)
   //   {
   //      this.delete = delete;
   //   }
   //
   //   public void setPost(ElasticDbPostHandler post)
   //   {
   //      this.post = post;
   //   }

}
