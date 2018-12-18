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
package io.rcktapp.api.handler.elastic;

import io.rcktapp.api.Action;
import io.rcktapp.api.Api;
import io.rcktapp.api.ApiException;
import io.rcktapp.api.Chain;
import io.rcktapp.api.Endpoint;
import io.rcktapp.api.Handler;
import io.rcktapp.api.Request;
import io.rcktapp.api.Response;
import io.rcktapp.api.SC;
import io.rcktapp.api.service.Service;

/**
 * 
 * @author kfrankic
 *
 */
public class ElasticDbRestHandler implements Handler
{
   ElasticDbGetHandler    get    = new ElasticDbGetHandler();
//   ElasticDbDeleteHandler delete = new ElasticDbDeleteHandler();
//   ElasticDbPostHandler   post   = new ElasticDbPostHandler();

   @Override
   public void service(Service service, Api api, Endpoint endpoint, Action action, Chain chain, Request req, Response res) throws Exception
   {
      String method = req.getMethod();
      if ("GET".equalsIgnoreCase(method))
      {
         get.service(service, api, endpoint, action, chain, req, res);
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

   public void setGet(ElasticDbGetHandler get)
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
