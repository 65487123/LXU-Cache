 /* Copyright zeping lu
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  *   http://www.apache.org/licenses/LICENSE-2.0
  *
  *  Unless required by applicable law or agreed to in writing, software
  *  distributed under the License is distributed on an "AS IS" BASIS,
  *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  *  See the License for the specific language governing permissions and
  *  limitations under the License.
  */

package com.lzp.lxucache.common.datastructure.set;


import java.io.Serializable;
import java.util.*;

/**
 * Description:有序Set
 *
 * @author: Lu ZePing
 * @date: 2019/7/20 12:19
 */
public class Zset implements Serializable {
    private Set<Node> treeSet = new TreeSet();
    private Map<String, Double> map = new HashMap();
    private static final long serialVersionUID = 3L;

    private static class Node implements Comparable<Node>, Serializable {
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

        @Override
        public int hashCode() {
            return this.e.hashCode();
        }

        @Override
        public String toString() {
            return "{member=" + e + ",score=" + score + "}";
        }
    }

    public void zadd(double score,String e) {
        Double preScore;
        if ((preScore = map.put(e, score)) != null) {
            treeSet.remove(new Node(preScore, e));
        }
        treeSet.add(new Node(score, e));
    }


    public String zrange(long start, long end) {
        Iterator<Node> iterator = treeSet.iterator();
        StringBuilder setString = new StringBuilder();
        for (long i = 0; i < start; i++) {
            if (!iterator.hasNext()) {
                break;
            }
            iterator.next();
        }
        for (long i = start; i <= end; i++) {
            if (!iterator.hasNext()) {
                break;
            }
            setString.append(iterator.next().e).append("È");
        }
        setString.deleteCharAt(setString.length() - 1);
        return setString.toString();
    }

    public void zrem(String... member) {
        for (String men : member) {
            treeSet.remove(new Node(map.remove(men), men));
        }
    }

    Double zincrby(double score, String member) throws NullPointerException {
        double preScore = map.get(member);
        treeSet.remove(new Node(preScore, member));
        double afterSocre = preScore + score;
        map.put(member, afterSocre);
        treeSet.add(new Node(afterSocre, member));
        return afterSocre;
    }

    Long zrank(String member) {
        long i = -1;
        Iterator<Node> iterator = treeSet.iterator();
        while (iterator.hasNext()) {
            i++;
            Node node = iterator.next();
            if (node.e.equals(member)) {
                return i;
            }
        }
        return i;
    }

    Long zrevrank(String key, String member) {
        ListIterator<Node> listIterator = new ArrayList(treeSet).listIterator(treeSet.size());
        long rank = -1;
        while (listIterator.hasPrevious()) {
            rank++;
            if (listIterator.previous().e.equals(member)) {
                return rank;
            }
        }
        return rank;
    }

    String zrevrange(String key, long start, long end) {
        ListIterator<Node> listIterator = new ArrayList(treeSet).listIterator(treeSet.size());
        StringBuilder setString = new StringBuilder();
        for (long i = 0; i < start; i++) {
            if (!listIterator.hasPrevious()) {
                break;
            }
            listIterator.previous();
        }
        for (long i = start; i <= end; i++) {
            if (!listIterator.hasPrevious()) {
                break;
            }
            setString.append(listIterator.previous().e).append("È");
        }
        setString.deleteCharAt(setString.length() - 1);
        return setString.toString();
    }

    public long size() {
        return treeSet.size();
    }

    Double zscore(String member) {
        return map.get(member);
    }

    Long zcount(double min, double max) {
        long count = 0;
        for (Node node : treeSet) {
            if (node.score > min) {
                if (node.score > max) {
                    return count;
                }
                count++;
            }
        }
        return count;
    }

    String zrangeByScore(double min, double max) {
        StringBuilder setString = new StringBuilder();
        for (Node node : treeSet) {
            if (node.score > min) {
                if (node.score > max) {
                    break;
                }
                setString.append(node.e).append("È");
            }
        }
        setString.deleteCharAt(setString.length() - 1);
        return setString.toString();
    }


    @Override
    public String toString() {
        return treeSet.toString();
    }
}
