package io.rocketpartners.cloud.action.sql;

import io.rocketpartners.cloud.model.*;
import io.rocketpartners.cloud.service.Chain;
import io.rocketpartners.cloud.service.Service;

public class DescribeSqlApiAction extends Action<DescribeSqlApiAction> {

    public DescribeSqlApiAction() {
        super(null, null, null);
        withMethods("GET");
    }

    @Override
    public void run(Service service, Api api, Endpoint endpoint, Chain chain, Request req, Response res) throws Exception {
        //go through the tables in the API, and spit out a JSON with their definitions.
        ArrayNode tablesNode = new ArrayNode();

        for (Collection collection : api.getCollections()) {
            ObjectNode tableNode = new ObjectNode();
            tablesNode.add(tableNode);

            tableNode.put("name", collection.getName());
            tableNode.put("database", collection.getTable().getDb().getName());

            ArrayNode colsNode = new ArrayNode();
            tableNode.put("cols", colsNode);
            for (Attribute column : collection.getEntity().getAttributes()) {
                ObjectNode colNode = new ObjectNode();
                colsNode.add(colNode);

                colNode.put("name", column.getName());
                colNode.put("type", column.getType());
            }
        }

        res.withStatus(SC.SC_200_OK);
        res.withJson(tablesNode);
    }

}
