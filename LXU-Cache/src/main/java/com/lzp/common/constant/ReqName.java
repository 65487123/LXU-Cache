
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

package com.lzp.common.constant;

/**
 * Description:常量类，都是一些请求名字
 * 牺牲微小的内存占用，提高可读、可维护性。
 * 别的类用到这个类里的常量，在编译的时候就会把这个类里的常量编译到那个类的class常量池中。
 *
 * @author: Zeping Lu
 * @date: 2021/1/18 11:53
 */
public class ReqName {
    public static final String PUT = "put";
    public static final String INCR = "incr";
    public static final String DECR = "decr";
    public static final String HPUT = "hput";
    public static final String HMERGE = "hmerge";
    public static final String LPUSH = "lpush";
    public static final String SADD = "sadd";
    public static final String ZADD = "zadd";
    public static final String REMOVE = "remove";
    public static final String GET = "get";
    public static final String  HSET = "hset";
    public static final String  HGET = "hget";
    public static final String  GET_LIST = "getList";
    public static final String  GET_SET = "getSet";
    public static final String  SCONTAIN = "scontain";
    public static final String  EXPIRE = "expire";
    public static final String ZRANGE = "zrange";
    public static final String ZREM = "zrem";
    public static final String ZRANK = "zrank";
    public static final String ZREVRANK = "zrevrank";
    public static final String ZREVRANGE = "zrevrange";
    public static final String ZCARD = "zcard";
    public static final String ZSCORE = "zscore";
    public static final String ZCOUNT = "zcount";
    public static final String ZRANGEBYSCORE = "zrangeByScore";
    public static final String ZINCRBY = "zincrby";
    public static final String FULL_SYNC = "fullSync";
    public static final String GET_MASTER = "getMaster";
    public static final String NOTICE = "notice";
    public static final String REFRESH_MASTER = "refreshMaster";
}
