/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.iotdb.db.engine.load;

import org.apache.iotdb.commons.exception.IllegalPathException;
import org.apache.iotdb.tsfile.exception.write.PageException;
import org.apache.iotdb.tsfile.utils.ReadWriteIOUtils;
import org.apache.iotdb.tsfile.write.writer.TsFileIOWriter;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public interface TsFileData {
  long getDataSize();

  void writeToFileWriter(TsFileIOWriter writer) throws IOException;

  boolean isModification();

  void serialize(DataOutputStream stream, File tsFile) throws IOException;

  static TsFileData deserialize(InputStream stream)
      throws IOException, PageException, IllegalPathException {
    boolean isModification = ReadWriteIOUtils.readBool(stream);
    return isModification ? DeletionData.deserialize(stream) : ChunkData.deserialize(stream);
  }
}
