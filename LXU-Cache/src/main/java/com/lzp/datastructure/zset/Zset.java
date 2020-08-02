package com.lzp.datastructure.zset;


import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
/**
 * Description:有序Set
 *
 * @author: Lu ZePing
 * @date: 2020/7/20 12:19
 */
public class Zset {
    private Set<Node> treeSet = new TreeSet();
    private Map<String, Double> map = new HashMap();

    private static class Node implements Comparable<Node> {
        private double score;
        private String e;

        public Node(double score, String e) {
            this.score = score;
            this.e = e;
        }

        @Override
        public int compareTo(Node node) {
            if (this.score == node.score) {
                return this.e.equals(node.e) ? 0 : -1;
            } else {
                return this.score - node.score > 0 ? 1 : -1;
            }
        }

        @Override
        public boolean equals(Object node) {
            if (node instanceof Node) {
                Node node1 = (Node) node;
                return this.e.equals(node1.e);
            }
            return false;
        }
    }

    public void zadd(Node node){
        Double preScore;
        if ((preScore = map.put(node.e,node.score))!=null){
            treeSet.remove(new Node(preScore,node.e));
        }
        treeSet.add(node);
    }
}
