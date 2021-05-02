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

package com.lzp.lxucache.common.protocol;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class AbstractDTO implements Serializable {
    private static final Logger logger = LoggerFactory.getLogger(AbstractDTO.class);

    public byte[] toBytes(){
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try {
            ObjectOutputStream out = new ObjectOutputStream(os);
            out.writeObject(this);
            return os.toByteArray();
        } catch (IOException e) {
            logger.error(e.getMessage(),e);
        }
        return null;
    }
}
