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
    private final Set<Node> TREESET = new TreeSet();
    private final Map<String, Double> MAP = new HashMap();
    private static final long serialVersionUID = 3L;

    private static class Node implements Comparable<Node>, Serializable {
        private final double SCORE;
        private String e;

        public Node(double score, String e) {
            this.SCORE = score;
            this.e = e;
        }

        @Override
        public int compareTo(Node node) {
            if (this.SCORE == node.SCORE) {
                return this.e.equals(node.e) ? 0 : -1;
            } else {
                return this.SCORE - node.SCORE > 0 ? 1 : -1;
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
            return "{member=" + e + ",score=" + SCORE + "}";
        }
    }

    public void zadd(double score,String e) {
        Double preScore;
        if ((preScore = MAP.put(e, score)) != null) {
            TREESET.remove(new Node(preScore, e));
        }
        TREESET.add(new Node(score, e));
    }


    public String zrange(long start, long end) {
        Iterator<Node> iterator = TREESET.iterator();
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
            TREESET.remove(new Node(MAP.remove(men), men));
        }
    }

    Double zincrby(double score, String member) throws NullPointerException {
        double preScore = MAP.get(member);
        TREESET.remove(new Node(preScore, member));
        double afterSocre = preScore + score;
        MAP.put(member, afterSocre);
        TREESET.add(new Node(afterSocre, member));
        return afterSocre;
    }

    Long zrank(String member) {
        long i = -1;
        Iterator<Node> iterator = TREESET.iterator();
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
        ListIterator<Node> listIterator = new ArrayList(TREESET).listIterator(TREESET.size());
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
        ListIterator<Node> listIterator = new ArrayList(TREESET).listIterator(TREESET.size());
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
        return TREESET.size();
    }

    Double zscore(String member) {
        return MAP.get(member);
    }

    Long zcount(double min, double max) {
        long count = 0;
        for (Node node : TREESET) {
            if (node.SCORE > min) {
                if (node.SCORE > max) {
                    return count;
                }
                count++;
            }
        }
        return count;
    }

    String zrangeByScore(double min, double max) {
        StringBuilder setString = new StringBuilder();
        for (Node node : TREESET) {
            if (node.SCORE > min) {
                if (node.SCORE > max) {
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
        return TREESET.toString();
    }
}
