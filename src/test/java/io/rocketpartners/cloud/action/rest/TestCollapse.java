package io.rocketpartners.cloud.action.rest;

import java.util.Arrays;
import java.util.HashSet;

import org.junit.Test;

import io.rocketpartners.cloud.model.ArrayNode;
import io.rocketpartners.cloud.model.ObjectNode;
import io.rocketpartners.cloud.utils.Utils;
import junit.framework.TestCase;

public class TestCollapse extends TestCase
{
   @Test
   public void testCollapses1()
   {
      ObjectNode parent = new ObjectNode();
      parent.put("name", "testing");

      ObjectNode child1 = new ObjectNode();
      parent.put("child1", child1);
      child1.put("href", "http://child1");
      child1.put("name", "child1");

      ObjectNode child2 = new ObjectNode();
      parent.put("child2", child2);

      child2.put("href", "http://child2");
      child2.put("name", "child2");

      ObjectNode collapsed = Utils.parseObjectNode(parent.toString());

      RestPostAction.collapse(collapsed, false, new HashSet(Arrays.asList("child2")), "");

      ObjectNode benchmark = Utils.parseObjectNode(parent.toString());
      benchmark = Utils.parseObjectNode(parent.toString());
      benchmark.remove("child2");
      benchmark.put("child2", new ObjectNode("href", "http://child2"));

      assertTrue(benchmark.toString().equals(collapsed.toString()));

   }

   @Test
   public void testCollapses2()
   {
      ObjectNode parent = new ObjectNode();
      parent.put("name", "testing");

      ObjectNode child1 = new ObjectNode();
      parent.put("child1", child1);
      child1.put("href", "http://child1");
      child1.put("name", "child1");

      ArrayNode arrChildren = new ArrayNode();
      for (int i = 0; i < 5; i++)
      {
         arrChildren.add(new ObjectNode("href", "href://child" + i, "name", "child" + i));
      }

      parent.put("arrChildren", arrChildren);

      ObjectNode collapsed = Utils.parseObjectNode(parent.toString());

      RestPostAction.collapse(collapsed, false, new HashSet(Arrays.asList("arrChildren")), "");

      ObjectNode benchmark = Utils.parseObjectNode(parent.toString());
      benchmark = Utils.parseObjectNode(parent.toString());
      benchmark.remove("arrChildren");
      arrChildren = new ArrayNode();
      for (int i = 0; i < 5; i++)
      {
         arrChildren.add(new ObjectNode("href", "href://child" + i));
      }
      benchmark.put("arrChildren", arrChildren);

      assertTrue(benchmark.toString().equals(collapsed.toString()));

   }

   @Test
   public void testCollapses3()
   {
      ObjectNode parent = new ObjectNode();
      parent.put("name", "testing");

      ObjectNode child1 = new ObjectNode();
      parent.put("child1", child1);
      child1.put("href", "http://child1");
      child1.put("name", "child1");

      ObjectNode child2 = new ObjectNode();
      parent.put("child2", child2);
      child2.put("href", "http://child2");
      child2.put("name", "child2");

      ObjectNode child3 = new ObjectNode();
      child2.put("child3", child3);
      child3.put("href", "http://child3");
      child3.put("name", "child3");

      ObjectNode collapsed = Utils.parseObjectNode(parent.toString());

      RestPostAction.collapse(collapsed, false, new HashSet(Arrays.asList("child2.child3")), "");

      ObjectNode benchmark = Utils.parseObjectNode(parent.toString());
      benchmark = Utils.parseObjectNode(parent.toString());
      benchmark.getNode("child2").getNode("child3").remove("name");

      assertTrue(benchmark.toString().equals(collapsed.toString()));

   }

}
