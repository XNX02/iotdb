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

package org.apache.iotdb.db.queryengine.transformation.dag.column.unary.scalar;

import org.apache.iotdb.db.queryengine.transformation.dag.column.ColumnTransformer;
import org.apache.iotdb.db.queryengine.transformation.dag.column.unary.UnaryColumnTransformer;

import org.apache.tsfile.block.column.Column;
import org.apache.tsfile.block.column.ColumnBuilder;
import org.apache.tsfile.read.common.type.Type;
import org.apache.tsfile.utils.Binary;

public class TrimColumnTransformer extends UnaryColumnTransformer {

  private final byte[] character;

  public TrimColumnTransformer(
      Type returnType, ColumnTransformer childColumnTransformer, String characterStr) {
    super(returnType, childColumnTransformer);
    this.character = characterStr.getBytes();
  }

  @Override
  protected void doTransform(Column column, ColumnBuilder columnBuilder) {
    for (int i = 0, n = column.getPositionCount(); i < n; i++) {
      if (!column.isNull(i)) {
        byte[] currentValue = column.getBinary(i).getValues();
        columnBuilder.writeBinary(new Binary(trim(currentValue, character)));
      } else {
        columnBuilder.appendNull();
      }
    }
  }

  public static byte[] trim(byte[] source, byte[] character) {
    if (source.length == 0 || character.length == 0) {
      return source;
    }
    int start = 0;
    int end = source.length - 1;

    while (start <= end && isContain(character, source[start])) {
      start++;
    }
    while (start <= end && isContain(character, source[end])) {
      end--;
    }
    if (start > end) {
      return new byte[0];
    } else {
      byte[] result = new byte[end - start + 1];
      System.arraycopy(source, start, result, 0, end - start + 1);
      return result;
    }
  }

  public static boolean isContain(byte[] character, byte target) {
    for (byte b : character) {
      if (b == target) {
        return true;
      }
    }
    return false;
  }
}